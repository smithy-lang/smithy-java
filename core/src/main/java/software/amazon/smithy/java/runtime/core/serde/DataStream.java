/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Abstraction for reading streams of data.
 */
public interface DataStream extends AutoCloseable {
    /**
     * Length of the data stream, if known.
     *
     * <p>Return a negative number to indicate an unknown length.
     *
     * @return Returns the content length if known, or a negative number if unknown.
     */
    long contentLength();

    /**
     * Check if the stream has a known content-length.
     *
     * @return true if the length is known.
     */
    default boolean hasKnownLength() {
        return contentLength() >= 0;
    }

    /**
     * Returns the content-type of the data, if known.
     *
     * @return the optionally available content-type.
     */
    Optional<String> contentType();

    /**
     * Get the InputStream.
     *
     * @return the underlying InputStream.
     */
    InputStream inputStream();

    /**
     * Attempt to rewind the input stream to the beginning of the stream.
     *
     * @return Returns true if the stream could be rewound.
     */
    boolean rewind();

    /**
     * Close underlying resources, if necessary.
     *
     * <p>If the resource is already closed, this method does nothing.
     */
    @Override
    default void close() {
        try {
            inputStream().close();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to close input stream in data stream", e);
        }
    }

    /**
     * Read the contents of the stream to an in-memory byte array.
     *
     * @param maxLength Maximum number of bytes to read.
     * @return Returns the in-memory byte array.
     */
    default byte[] readToBytes(int maxLength) {
        try (InputStream stream = inputStream()) {
            return stream.readNBytes(maxLength);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Read the contents of the stream to an in-memory String.
     *
     * @param maxLength Maximum number of bytes to read.
     * @return Returns the in-memory string.
     */
    default String readToString(int maxLength) {
        return new String(readToBytes(maxLength), StandardCharsets.UTF_8);
    }

    /**
     * Create an empty data stream.
     *
     * @return the empty data stream.
     */
    static DataStream ofEmpty() {
        return new DataStream() {
            @Override
            public long contentLength() {
                return 0;
            }

            @Override
            public Optional<String> contentType() {
                return Optional.empty();
            }

            @Override
            public InputStream inputStream() {
                return InputStream.nullInputStream();
            }

            @Override
            public boolean rewind() {
                return true;
            }
        };
    }

    /**
     * Create a data stream from an InputStream.
     *
     * @param inputStream InputStream to wrap.
     * @return the non-rewindable data stream.
     */
    static DataStream ofInputStream(InputStream inputStream) {
        return ofInputStream(inputStream, null);
    }

    /**
     * Create a data stream from an InputStream.
     *
     * @param inputStream InputStream to wrap.
     * @param contentType Content-Type of the data stream if known, or null.
     * @return the non-rewindable data stream.
     */
    static DataStream ofInputStream(InputStream inputStream, String contentType) {
        return ofInputStream(inputStream, contentType, -1);
    }

    /**
     * Create a data stream from an InputStream.
     *
     * @param inputStream   InputStream to wrap.
     * @param contentType   Content-Type of the data stream if known, or null.
     * @param contentLength Bytes in the stream if known, or -1.
     * @return the non-rewindable data stream.
     */
    static DataStream ofInputStream(InputStream inputStream, String contentType, long contentLength) {
        return new DataStream() {
            @Override
            public long contentLength() {
                return contentLength;
            }

            @Override
            public Optional<String> contentType() {
                return Optional.ofNullable(contentType);
            }

            @Override
            public InputStream inputStream() {
                return inputStream;
            }

            @Override
            public boolean rewind() {
                return false;
            }
        };
    }

    /**
     * Create a DataStream from an in-memory UTF-8 string.
     *
     * @param data Data to stream.
     * @return the rewindable data stream.
     */
    static DataStream ofString(String data) {
        return ofString(data, null);
    }

    /**
     * Create a DataStream from an in-memory UTF-8 string.
     *
     * @param data        Data to stream.
     * @param contentType Content-Type of the data stream if known, or null.
     * @return the rewindable data stream.
     */
    static DataStream ofString(String data, String contentType) {
        return ofBytes(data.getBytes(StandardCharsets.UTF_8), contentType);
    }

    /**
     * Create a DataStream from an in-memory byte array.
     *
     * @param bytes Bytes to read.
     * @return the rewindable data stream.
     */
    static DataStream ofBytes(byte[] bytes) {
        return ofBytes(bytes, null);
    }

    /**
     * Create a DataStream from an in-memory byte array.
     *
     * @param bytes       Bytes to read.
     * @param contentType Content-Type of the data stream if known, or null.
     * @return the rewindable data stream.
     */
    static DataStream ofBytes(byte[] bytes, String contentType) {
        var result = new DataStream() {
            private InputStream inputStream;

            @Override
            public long contentLength() {
                return bytes.length;
            }

            @Override
            public Optional<String> contentType() {
                return Optional.ofNullable(contentType);
            }

            @Override
            public InputStream inputStream() {
                return inputStream;
            }

            @Override
            public boolean rewind() {
                inputStream = new ByteArrayInputStream(bytes);
                return true;
            }
        };
        result.rewind();
        return result;
    }

    /**
     * Create a DataStream from a file on disk.
     *
     * <p>This implementation will attempt to probe the content-type of the file using
     * {@link Files#probeContentType(Path)}. To avoid this, call {@link #ofFile(Path, String)} and pass in a null
     * {@code contentType} argument.
     *
     * @param file File to read.
     * @return the rewindable data stream.
     */
    static DataStream ofFile(Path file) {
        try {
            return ofFile(file, Files.probeContentType(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Create a DataStream from a file on disk.
     *
     * @param file        File to read.
     * @param contentType Content-Type of the data stream if known, or null.
     * @return the rewindable data stream.
     */
    static DataStream ofFile(Path file, String contentType) {
        try {
            long initialLength = Files.size(file);
            InputStream initialStream = Files.newInputStream(file);
            return new DataStream() {
                private static final System.Logger LOGGER = System.getLogger(DataStream.class.getName());
                private long length = initialLength;
                private InputStream inputStream = initialStream;

                @Override
                public long contentLength() {
                    return length;
                }

                @Override
                public Optional<String> contentType() {
                    return Optional.ofNullable(contentType);
                }

                @Override
                public InputStream inputStream() {
                    return inputStream;
                }

                @Override
                public boolean rewind() {
                    try {
                        inputStream = Files.newInputStream(file);
                        length = Files.size(file);
                        return true;
                    } catch (IOException e) {
                        LOGGER.log(
                            System.Logger.Level.WARNING,
                            () -> "Unable to rewind file data stream for " + file + ": " + e.getMessage()
                        );
                        return false;
                    }
                }
            };
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to open file for data stream " + file + ": " + e.getMessage(), e);
        }
    }
}
