/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.smithy.java.logging.InternalLogger;

public final class SingleThreadOrchestrator implements ObservableOrchestrator {

    private static final AtomicInteger ORCHESTRATOR_ID_GENERATOR = new AtomicInteger(1);
    private static final InternalLogger LOG = InternalLogger.getLogger(SingleThreadOrchestrator.class);

    private final List<Handler> handlers;
    private final LinkedBlockingDeque<Runnable> queue;
    private final Thread workerThread;
    private final AtomicInteger inflightJobs = new AtomicInteger();

    public SingleThreadOrchestrator(List<Handler> handlers) {
        this.handlers = handlers;
        this.queue = new LinkedBlockingDeque<>();
        this.workerThread = new Thread(
                new ConsumerTask(queue),
                "SingleThreadOrchestrator-" + ORCHESTRATOR_ID_GENERATOR.getAndIncrement());
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    @Override
    public CompletableFuture<Void> enqueue(Job job) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        inflightJobs.incrementAndGet();
        queue.add(new JobWork(job, handlers, queue, future));
        return future.whenComplete((r, e) -> inflightJobs.decrementAndGet());
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public int inflightJobs() {
        return inflightJobs.get();
    }

    private final class JobWork implements Runnable {

        private final Job job;
        private final Queue<Handler> queue;
        private final BlockingQueue<Runnable> workQueue;
        private final CompletableFuture<Void> signal;
        private final Deque<Handler> soFar;
        private State state = State.BEFORE;

        private JobWork(
                Job job,
                List<Handler> handlers,
                BlockingQueue<Runnable> workQueue,
                CompletableFuture<Void> signal
        ) {
            this.job = job;
            this.queue = new ArrayDeque<>(handlers);
            this.workQueue = workQueue;
            this.signal = signal;
            this.soFar = new ArrayDeque<>();
        }

        private enum State {
            BEFORE,
            AFTER,
            DONE
        }

        @Override
        public void run() {
            try {
                if ((job.isCompleted() || job.isFailure()) && state == State.BEFORE) {
                    state = State.AFTER;
                }

                while (state == State.BEFORE) {
                    if (queue.isEmpty() || job.isFailure() || job.isCompleted()) {
                        state = State.AFTER;
                        break;
                    }
                    Handler handler = queue.poll();
                    soFar.push(handler);
                    CompletableFuture<Void> cf = handler.before(job);
                    if (!cf.isDone()) {
                        cf.whenComplete((e, t) -> {
                            if (t != null) {
                                job.setFailure(t);
                            }
                            SingleThreadOrchestrator.this.queue.add(this);
                        });
                        break;
                    }
                    if (cf.isCompletedExceptionally()) {
                        cf.exceptionally(t -> {
                            job.setFailure(t);
                            return null;
                        });
                        state = State.AFTER;
                        break;
                    }
                }
                if (state == State.AFTER) {
                    while (!soFar.isEmpty()) {
                        Handler handler = soFar.pop();
                        CompletableFuture<Void> cf = handler.after(job);
                        if (!cf.isDone()) {
                            cf.whenComplete((e, t) -> {
                                if (t != null) {
                                    job.setFailure(t);
                                }
                                SingleThreadOrchestrator.this.queue.add(this);
                            });
                            break;
                        }
                        if (cf.isCompletedExceptionally()) {
                            cf.exceptionally(t -> {
                                job.setFailure(t);
                                return null;
                            });
                        }
                    }
                    state = State.DONE;
                    // TODO: For some reason with a serializable implicit exception this causes
                    //  the server to throw an Internal Service error (CompletionError wrapping the original exception)
                    //  instead of just the serializable exception
                    if (job.isFailure()) {
                        signal.completeExceptionally(job.getFailure());
                    } else {
                        signal.complete(null);
                    }
                }
            } catch (Exception e) {
                signal.completeExceptionally(e);
            }
        }
    }

    private record ConsumerTask(BlockingQueue<Runnable> queue) implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    queue.take().run();
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                } catch (Throwable throwable) {
                    LOG.error("Got an unexpected exception during orchestration", throwable);
                }
            }
        }
    }
}
