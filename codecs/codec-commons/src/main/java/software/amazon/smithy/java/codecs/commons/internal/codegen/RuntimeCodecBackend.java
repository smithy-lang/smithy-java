/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codecs.commons.internal.codegen;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.utils.SmithyInternalApi;

/** Generation-time backend for a runtime codec. */
@SmithyInternalApi
public interface RuntimeCodecBackend<T> {
    /** Stable token used by properties, logs, and generated class names. */
    String id();

    /** Diagnostic class-name suffix for backend settings. */
    default String variant() {
        return "";
    }

    Class<T> codecType();

    Class<?> lookupHost();

    default Budgets budgets() {
        return Budgets.unbounded();
    }

    /** Returns whether the backend requires read-side builder metadata. */
    default Mode mode() {
        return Mode.READ_WRITE;
    }

    /** Returns a stable selector for root members; nested shapes are always complete. */
    default MemberSelector memberSelector() {
        return MemberSelector.all();
    }

    Emission emit(RuntimeCodecPlan plan, String generatedName);

    record Emission(byte[] bytecode, int methodCount) {}

    /** Whether a backend generates both directions or only writes. */
    enum Mode {
        WRITE_ONLY,
        READ_WRITE
    }

    /** Stable selector for root members. */
    @FunctionalInterface
    interface MemberSelector {
        boolean select(Schema root, Schema member);

        static MemberSelector all() {
            return All.INSTANCE;
        }
    }

    final class All {
        private static final MemberSelector INSTANCE = (root, member) -> true;

        private All() {}
    }

    record Budgets(
            int writerBytecodeLimit,
            int readerBytecodeLimit,
            int maxMembersPerWriterMethod,
            int maxMembersPerReaderBucket) {
        public Budgets {
            if (writerBytecodeLimit <= 0
                    || readerBytecodeLimit <= 0
                    || maxMembersPerWriterMethod <= 0
                    || maxMembersPerReaderBucket <= 0) {
                throw new IllegalArgumentException("Runtime codegen budgets must be positive");
            }
        }

        public static Budgets unbounded() {
            return new Budgets(
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE);
        }
    }
}
