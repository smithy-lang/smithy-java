/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model.Dimension;
import software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model.MetricDatum;
import software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model.PutMetricDataInput;
import software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model.StatisticSet;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.RuntimeCodegenBenchmarkSupport;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Compares generated Smithy JSON serde with Fory JSON over equivalent CloudWatch-shaped graphs.
 *
 * <p>Fory cannot bind Smithy's immutable builder-based model directly, so its input and output use
 * mutable field-based DTOs with the same JSON names and value types. Setup validates the complete
 * Fory output by deserializing it into the Smithy model and comparing it to the original. Both
 * deserialization benchmarks consume the same Smithy-generated UTF-8 payload.
 */
@State(Scope.Benchmark)
public class JsonRuntimeCodegenForyBenchmark {
    private static final String GENERATED_PACKAGE =
            "software.amazon.smithy.java.benchmarks.serde.generated.awsjson10.model";
    private static final ShapeId SERVICE_ID =
            ShapeId.from("com.amazonaws.sdk.benchmark#AwsJsonRpc10DataPlane");

    @Param({
            "awsQuery_PutMetricDataRequest_S",
            "awsQuery_PutMetricDataRequest_M",
            "awsQuery_PutMetricDataRequest_L"
    })
    public String testCaseId;

    private JsonCodec smithy;
    private PutMetricDataInput smithyValue;
    private ForyPutMetricDataInput foryValue;
    private byte[] input;
    private Object fory;

    @Setup
    public void setup() {
        String previous = System.getProperty("smithy-java.runtime-codegen");
        try {
            System.setProperty("smithy-java.runtime-codegen", "json");
            smithy = JsonCodec.builder().useJsonName(true).useTimestampFormat(true).build();
        } finally {
            restore("smithy-java.runtime-codegen", previous);
        }

        SerializeState state = SerializeState.forTestCase(testCaseId, GENERATED_PACKAGE, SERVICE_ID);
        smithyValue = (PutMetricDataInput) state.input;
        foryValue = ForyPutMetricDataInput.from(smithyValue);
        fory = ForyAccess.create();

        input = bytes(smithy.serialize(smithyValue));
        RuntimeCodegenBenchmarkSupport.requireGenerated(smithy);

        byte[] foryBytes = ForyAccess.serialize(fory, foryValue);
        PutMetricDataInput decoded = smithy.deserializeShape(foryBytes, PutMetricDataInput.builder());
        if (!smithyValue.equals(decoded)) {
            throw new IllegalStateException("Fory JSON is not semantically equivalent to Smithy JSON: "
                    + new String(foryBytes, StandardCharsets.UTF_8));
        }
        Object foryDecoded = ForyAccess.deserialize(fory, input, ForyPutMetricDataInput.class);
        if (foryDecoded == null) {
            throw new IllegalStateException("Fory failed to deserialize the canonical Smithy payload");
        }
        byte[] foryRoundTrip = ForyAccess.serialize(fory, foryDecoded);
        PutMetricDataInput roundTrip = smithy.deserializeShape(foryRoundTrip, PutMetricDataInput.builder());
        if (!smithyValue.equals(roundTrip)) {
            throw new IllegalStateException("Fory failed to round-trip the canonical Smithy payload");
        }

        // Ensure both runtime generators finish before JMH warmup begins.
        smithy.deserializeShape(input, PutMetricDataInput.builder());
    }

    @Benchmark
    public ByteBuffer smithySerialize() {
        return smithy.serialize(smithyValue);
    }

    @Benchmark
    public byte[] forySerialize() {
        return ForyAccess.serialize(fory, foryValue);
    }

    @Benchmark
    public PutMetricDataInput smithyDeserialize() {
        return smithy.deserializeShape(input, PutMetricDataInput.builder());
    }

    @Benchmark
    public Object foryDeserialize() {
        return ForyAccess.deserialize(fory, input, ForyPutMetricDataInput.class);
    }

    private static byte[] bytes(ByteBuffer buffer) {
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    private static void restore(String property, String value) {
        if (value == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, value);
        }
    }

    private static final class ForyAccess {
        private static final MethodHandle TO_JSON_BYTES;
        private static final MethodHandle FROM_JSON;

        static {
            try {
                Class<?> type = Class.forName("org.apache.fory.json.ForyJson");
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                TO_JSON_BYTES = lookup.findVirtual(
                        type,
                        "toJsonBytes",
                        MethodType.methodType(byte[].class, Object.class))
                        .asType(MethodType.methodType(byte[].class, Object.class, Object.class));
                FROM_JSON = lookup.findVirtual(
                        type,
                        "fromJson",
                        MethodType.methodType(Object.class, byte[].class, Class.class))
                        .asType(MethodType.methodType(
                                Object.class,
                                Object.class,
                                byte[].class,
                                Class.class));
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private static Object create() {
            try {
                Class<?> type = Class.forName("org.apache.fory.json.ForyJson");
                Object builder = type.getMethod("builder").invoke(null);
                Class<?> builderType = builder.getClass();
                builder = builderType.getMethod("withFieldMode", boolean.class).invoke(builder, true);
                builder = builderType.getMethod("withAsyncCompilation", boolean.class).invoke(builder, false);
                builder = builderType.getMethod("withConcurrencyLevel", int.class).invoke(builder, 1);
                return builderType.getMethod("build").invoke(builder);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Fory JSON is unavailable; add fory-json and fory-core to the JMH classpath",
                        e);
            }
        }

        private static byte[] serialize(Object runtime, Object value) {
            try {
                return (byte[]) TO_JSON_BYTES.invokeExact(runtime, value);
            } catch (Throwable e) {
                throw rethrow(e);
            }
        }

        private static Object deserialize(Object runtime, byte[] input, Class<?> type) {
            try {
                return FROM_JSON.invokeExact(runtime, input, type);
            } catch (Throwable e) {
                throw rethrow(e);
            }
        }

        private static RuntimeException rethrow(Throwable throwable) {
            if (throwable instanceof RuntimeException runtimeException) {
                return runtimeException;
            }
            if (throwable instanceof Error error) {
                throw error;
            }
            return new IllegalStateException(throwable);
        }
    }

    public static final class ForyPutMetricDataInput {
        public String Namespace;
        public List<ForyMetricDatum> MetricData;
        public Boolean StrictEntityValidation;

        public ForyPutMetricDataInput() {}

        private ForyPutMetricDataInput(PutMetricDataInput value) {
            Namespace = value.getNamespace();
            MetricData = map(value.getMetricData(), ForyMetricDatum::new);
            StrictEntityValidation = value.isStrictEntityValidation();
        }

        static ForyPutMetricDataInput from(PutMetricDataInput value) {
            return new ForyPutMetricDataInput(value);
        }
    }

    public static final class ForyMetricDatum {
        public String MetricName;
        public List<ForyDimension> Dimensions;
        public Long Timestamp;
        public Double Value;
        public ForyStatisticSet StatisticValues;
        public List<Double> Values;
        public List<Double> Counts;
        public String Unit;
        public Integer StorageResolution;

        public ForyMetricDatum() {}

        private ForyMetricDatum(MetricDatum value) {
            MetricName = value.getMetricName();
            Dimensions = value.hasDimensions()
                    ? map(value.getDimensions(), ForyDimension::new)
                    : null;
            Timestamp = epochSecond(value.getTimestamp());
            Value = value.getValue();
            StatisticValues = ForyStatisticSet.from(value.getStatisticValues());
            Values = value.hasValues() ? value.getValues() : null;
            Counts = value.hasCounts() ? value.getCounts() : null;
            Unit = value.getUnit() == null ? null : value.getUnit().getValue();
            StorageResolution = value.getStorageResolution();
        }

        private static Long epochSecond(Instant value) {
            if (value == null) {
                return null;
            }
            if (value.getNano() != 0) {
                throw new IllegalArgumentException("Fory comparison requires whole-second timestamps");
            }
            return value.getEpochSecond();
        }
    }

    public static final class ForyDimension {
        public String Name;
        public String Value;

        public ForyDimension() {}

        private ForyDimension(Dimension value) {
            Name = value.getName();
            Value = value.getValue();
        }
    }

    public static final class ForyStatisticSet {
        public double SampleCount;
        public double Sum;
        public double Minimum;
        public double Maximum;

        public ForyStatisticSet() {}

        private ForyStatisticSet(StatisticSet value) {
            SampleCount = value.getSampleCount();
            Sum = value.getSum();
            Minimum = value.getMinimum();
            Maximum = value.getMaximum();
        }

        static ForyStatisticSet from(StatisticSet value) {
            return value == null ? null : new ForyStatisticSet(value);
        }
    }

    private static <T, R> List<R> map(List<T> values, Function<T, R> mapper) {
        List<R> result = new ArrayList<>(values.size());
        for (T value : values) {
            result.add(mapper.apply(value));
        }
        return result;
    }
}
