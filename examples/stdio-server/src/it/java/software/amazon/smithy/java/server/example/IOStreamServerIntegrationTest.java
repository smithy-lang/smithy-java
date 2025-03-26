package software.amazon.smithy.java.server.example;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.jsonrpc.model.JsonRpcRequest;
import software.amazon.smithy.java.jsonrpc.model.JsonRpcResponse;
import software.amazon.smithy.java.server.example.model.AddBeerInput;
import software.amazon.smithy.java.server.example.model.AddBeerOutput;
import software.amazon.smithy.java.server.example.model.Beer;
import software.amazon.smithy.java.server.example.model.GetBeerInput;
import software.amazon.smithy.java.server.example.model.GetBeerOutput;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IOStreamServerIntegrationTest {
    private static final Codec CODEC = JsonCodec.builder().build();

    private TestInputStream input;
    private TestOutputStream output;
    private StdioServerExample server;

    @BeforeEach
    public void beforeEach() {
        input = new TestInputStream();
        output = new TestOutputStream();
        server = new StdioServerExample(input, output);
        server.run();
    }

    @AfterEach
    public void afterEach() {
        server.stop();
    }

    @Test
    public void test() {
        var beer = Beer.builder()
            .name("to contemplate eternity")
            .quantity(100)
            .build();
        write("add-beer", AddBeerInput.builder().beer(beer).build());

        var added = read(AddBeerOutput.builder());
        assertEquals(1, added.id());

        write("get-beer", GetBeerInput.builder().id(added.id()).build());

        var retrieved = read(GetBeerOutput.builder());
        assertEquals(beer, retrieved.beer());
    }

    private <T extends SerializableStruct> T read(ShapeBuilder<T> builder) {
        var line = output.read();
        var jsonResponse = CODEC.deserializeShape(line, JsonRpcResponse.builder());
        return jsonResponse.result().asShape(builder);
    }

    private int id = 1;

    private void write(String method, SerializableStruct obj) {
        var request = JsonRpcRequest.builder()
            .id(id++)
            .method(method)
            .params(Document.of(obj))
            .jsonrpc("2.0")
            .build();
        input.write(CODEC.serializeToString(request));
        input.write("\n");
    }

    static final class TestInputStream extends InputStream {
        private byte[] onDeck;
        private int pos;
        private final BlockingQueue<byte[]> bytes = new LinkedBlockingQueue<>();

        void write(String s) {
            bytes.add(s.getBytes(StandardCharsets.UTF_8));
        }

        void write(byte[] bytes) {
            this.bytes.add(bytes);
        }

        @Override
        public int read() {
            load(true);
            return onDeck[pos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            int rem = len;
            int read = 0;
            boolean first = true;
            while (rem > 0) {
                if (load(first) || onDeck == null) {
                    break;
                }
                first = false;
                int toRead = Math.min(onDeck.length - pos, rem);
                System.arraycopy(onDeck, pos, b, off, toRead);
                pos += toRead;
                off += toRead;
                rem -= toRead;
                read += toRead;
            }
            return read;
        }

        private boolean load(boolean first) {
            try {
                if (onDeck == null || pos == onDeck.length) {
                    onDeck = first ? bytes.take() : bytes.poll();
                    pos = 0;
                }
                return false;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() throws IOException {
            super.close();
        }
    }

    static final class TestOutputStream extends OutputStream {
        private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        @Override
        public void write(int b) {
            baos.write(b);
            if (b == '\n') {
                lines.add(baos.toString(StandardCharsets.UTF_8));
                baos.reset();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            int rem = len;
            int pos = off;
            while (rem > 0) {
                int nl = Arrays.binarySearch(b, pos, pos + rem, (byte) '\n');
                if (nl == -1) {
                    baos.write(b, off, len);
                    return;
                } else {
                    int toWrite = nl - off;
                    baos.write(b, off, toWrite);
                    lines.add(baos.toString(StandardCharsets.UTF_8));
                    baos.reset();
                    rem -= toWrite;
                    pos += toWrite;
                }
            }
        }

        String read() {
            try {
                return lines.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
