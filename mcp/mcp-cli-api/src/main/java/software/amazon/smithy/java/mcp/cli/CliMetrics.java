/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.mcp.cli.model.TelemetryData;

public final class CliMetrics implements AutoCloseable {

    private final TelemetryPublisher telemetryPublisher;
    private final TelemetryData.Builder telemetryData;
    private final Map<String, Long> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> timings = new ConcurrentHashMap<>();
    private final Map<String, String> properties = new ConcurrentHashMap<>();
    private final long startTime;

    CliMetrics(final TelemetryPublisher telemetryPublisher, TelemetryData.Builder telemetryData) {
        this.telemetryPublisher = telemetryPublisher;
        this.telemetryData = telemetryData;
        this.startTime = System.nanoTime();
    }

    public void addCount(String name, long count) {
        counters.put(name, count);
    }

    public void addTiming(String name, long time) {
        timings.put(name, time);
    }

    public void addProperty(String name, String value) {
        properties.put(name, value);
    }

    public int exitCode(int exitCode) {
        telemetryData.exitCode(exitCode);
        return exitCode;
    }

    @Override
    public void close() {
        timings.put("ExecutionTime", System.nanoTime() - startTime);
        telemetryData
                .counters(counters)
                .timings(timings)
                .properties(properties)
                .build();
        try {
            telemetryPublisher.publish(telemetryData.build());
        } catch (Exception ignored) {
            //Do not fail if we can't publish telemetry.
        }
    }
}
