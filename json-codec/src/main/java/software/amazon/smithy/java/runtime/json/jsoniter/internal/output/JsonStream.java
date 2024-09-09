/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter.internal.output;

import java.io.IOException;
import java.io.OutputStream;
import software.amazon.smithy.java.runtime.json.jsoniter.internal.JsonException;

public final class JsonStream extends OutputStream {

    int indention = 0;
    private OutputStream out;
    byte buf[];
    int count;

    private int indentionStep = 0;
    boolean escapeUnicode = true;

    public JsonStream(OutputStream out, int bufSize) {
        if (bufSize < 32) {
            throw new JsonException("buffer size must be larger than 32: " + bufSize);
        }
        this.out = out;
        this.buf = new byte[bufSize];
    }

    public void reset(OutputStream out) {
        this.out = out;
        this.count = 0;
    }

    public void ensure(int minimal) throws IOException {
        int available = buf.length - count;
        if (available < minimal) {
            if (count > 1024) {
                flushBuffer();
            }
            growAtLeast(minimal);
        }
    }

    private void growAtLeast(int minimal) {
        int toGrow = buf.length;
        if (toGrow < minimal) {
            toGrow = minimal;
        }
        byte[] newBuf = new byte[buf.length + toGrow];
        System.arraycopy(buf, 0, newBuf, 0, buf.length);
        buf = newBuf;
    }

    public void write(int b) throws IOException {
        ensure(1);
        buf[count++] = (byte) b;
    }

    public void write(byte b1, byte b2) throws IOException {
        ensure(2);
        buf[count++] = b1;
        buf[count++] = b2;
    }

    public void write(byte b1, byte b2, byte b3) throws IOException {
        ensure(3);
        buf[count++] = b1;
        buf[count++] = b2;
        buf[count++] = b3;
    }

    public void write(byte b1, byte b2, byte b3, byte b4) throws IOException {
        ensure(4);
        buf[count++] = b1;
        buf[count++] = b2;
        buf[count++] = b3;
        buf[count++] = b4;
    }

    public void write(byte b1, byte b2, byte b3, byte b4, byte b5) throws IOException {
        ensure(5);
        buf[count++] = b1;
        buf[count++] = b2;
        buf[count++] = b3;
        buf[count++] = b4;
        buf[count++] = b5;
    }

    public void write(byte b1, byte b2, byte b3, byte b4, byte b5, byte b6) throws IOException {
        ensure(6);
        buf[count++] = b1;
        buf[count++] = b2;
        buf[count++] = b3;
        buf[count++] = b4;
        buf[count++] = b5;
        buf[count++] = b6;
    }

    public void write(byte b[], int off, int len) throws IOException {
        if (out == null) {
            ensure(len);
        } else {
            if (len >= buf.length - count) {
                if (len >= buf.length) {
                    /* If the request length exceeds the size of the output buffer,
                       flush the output buffer and then write the data directly.
                       In this way buffered streams will cascade harmlessly. */
                    flushBuffer();
                    out.write(b, off, len);
                    return;
                }
                flushBuffer();
            }
        }
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public void flush() throws IOException {
        flushBuffer();
        out.flush();
    }

    @Override
    public void close() throws IOException {
        if (out == null) {
            return;
        }
        if (count > 0) {
            flushBuffer();
        }
        out.close();
        this.out = null;
        count = 0;
    }

    void flushBuffer() throws IOException {
        if (out == null) {
            return;
        }
        out.write(buf, 0, count);
        count = 0;
    }

    public void writeVal(String val) throws IOException {
        if (val == null) {
            writeNull();
        } else {
            StreamImplString.writeString(this, val);
        }
    }

    public void writeRaw(String val) throws IOException {
        writeRaw(val, val.length());
    }

    public void writeRaw(String val, int remaining) throws IOException {
        if (out == null) {
            ensure(remaining);
            val.getBytes(0, remaining, buf, count);
            count += remaining;
            return;
        }
        int i = 0;
        for (;;) {
            int available = buf.length - count;
            if (available < remaining) {
                remaining -= available;
                int j = i + available;
                val.getBytes(i, j, buf, count);
                count = buf.length;
                flushBuffer();
                i = j;
            } else {
                int j = i + remaining;
                val.getBytes(i, j, buf, count);
                count += remaining;
                return;
            }
        }
    }

    public void writeVal(Boolean val) throws IOException {
        if (val == null) {
            writeNull();
        } else {
            if (val) {
                writeTrue();
            } else {
                writeFalse();
            }
        }
    }

    public void writeVal(boolean val) throws IOException {
        if (val) {
            writeTrue();
        } else {
            writeFalse();
        }
    }

    public void writeTrue() throws IOException {
        write((byte) 't', (byte) 'r', (byte) 'u', (byte) 'e');
    }

    public void writeFalse() throws IOException {
        write((byte) 'f', (byte) 'a', (byte) 'l', (byte) 's', (byte) 'e');
    }

    public void writeVal(Short val) throws IOException {
        if (val == null) {
            writeNull();
        } else {
            writeVal(val.intValue());
        }
    }

    public void writeVal(short val) throws IOException {
        writeVal((int) val);
    }

    public void writeVal(Integer val) throws IOException {
        if (val == null) {
            writeNull();
        } else {
            writeVal(val.intValue());
        }
    }

    public void writeVal(int val) throws IOException {
        StreamImplNumber.writeInt(this, val);
    }


    public void writeVal(Long val) throws IOException {
        if (val == null) {
            writeNull();
        } else {
            writeVal(val.longValue());
        }
    }

    public void writeVal(long val) throws IOException {
        StreamImplNumber.writeLong(this, val);
    }


    public void writeVal(Float val) throws IOException {
        if (val == null) {
            writeNull();
        } else {
            writeVal(val.floatValue());
        }
    }

    public void writeVal(float val) throws IOException {
        StreamImplNumber.writeFloat(this, val);
    }

    public void writeVal(Double val) throws IOException {
        if (val == null) {
            writeNull();
        } else {
            writeVal(val.doubleValue());
        }
    }

    public void writeVal(double val) throws IOException {
        StreamImplNumber.writeDouble(this, val);
    }

    public void writeNull() throws IOException {
        write((byte) 'n', (byte) 'u', (byte) 'l', (byte) 'l');
    }

    public void writeEmptyObject() throws IOException {
        write((byte) '{', (byte) '}');
    }

    public void writeEmptyArray() throws IOException {
        write((byte) '[', (byte) ']');
    }

    public void writeArrayStart() throws IOException {
        indention += indentionStep;
        write('[');
    }

    public void writeMore() throws IOException {
        write(',');
        writeIndention();
    }

    public void writeIndention() throws IOException {
        writeIndention(0);
    }

    private void writeIndention(int delta) throws IOException {
        if (indention == 0) {
            return;
        }
        write('\n');
        int toWrite = indention - delta;
        ensure(toWrite);
        for (int i = 0; i < toWrite && count < buf.length; i++) {
            buf[count++] = ' ';
        }
    }

    public void writeArrayEnd() throws IOException {
        writeIndention(indentionStep);
        indention -= indentionStep;
        write(']');
    }

    public void writeObjectStart() throws IOException {
        indention += indentionStep;
        write('{');
    }

    public void writeObjectField(String field) throws IOException {
        writeVal(field);
        if (indention > 0) {
            write((byte) ':', (byte) ' ');
        } else {
            write(':');
        }
    }

    // Custom method to write object fields that are known the be JSON safe.
    public void writeRawObjectField(String field) throws IOException {
        ensure(3 + field.length());
        write((byte) '"');
        writeRaw(field);
        if (indention > 0) {
            write((byte) '"', (byte) ':', (byte) ' ');
        } else {
            write((byte) '"', (byte) ':');
        }
    }

    public void writeObjectEnd() throws IOException {
        writeIndention(indentionStep);
        indention -= indentionStep;
        write('}');
    }

    public void setIndentionStep(int indentionStep) {
        this.indentionStep = indentionStep;
    }

    public void setEscapeUnicode(boolean escapeUnicode) {
        this.escapeUnicode = escapeUnicode;
    }
}
