package software.amazon.smithy.java.runtime;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import software.amazon.smithy.java.runtime.myservice.model.PutPersonInput;
import software.amazon.smithy.java.runtime.serde.any.Any;
import software.amazon.smithy.java.runtime.serde.json.JsonCodec;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class SerdeBenchmarks {

    private PutPersonInput input;
    private JsonCodec codec;
    private Any inputAsAny;

    @Setup
    public void prepare() {
        input = PutPersonInput.builder()
                .name("Michael")
                .age(999)
                .favoriteColor("Green")
                .birthday(Instant.now())
                .build();
        codec = JsonCodec.builder().useJsonName(true).useTimestampFormat(true).build();
        inputAsAny = Any.of(input);
    }

    @Benchmark
    public void shapeToJson(Blackhole bh) {
        bh.consume(codec.serializeToString(input));
    }

    @Benchmark
    public void jsonToShape(Blackhole bh) {
        String json = "{\"name\":\"Michael\",\"Age\":999,\"birthday\":1709591329,"
                      + "\"favoriteColor\":\"Green\",\"binary\":\"SGVsbG8=\"}";
        bh.consume(codec.deserializeShape(json, PutPersonInput.builder()));
    }

    @Benchmark
    public void shapeToString(Blackhole bh) {
        bh.consume(input.toString());
    }

    @Benchmark
    public void shapeToAny(Blackhole bh) {
        bh.consume(Any.of(input));
    }

    @Benchmark
    public void anyToShape(Blackhole bh) {
        bh.consume(inputAsAny.asShape(PutPersonInput.builder()));
    }
}
