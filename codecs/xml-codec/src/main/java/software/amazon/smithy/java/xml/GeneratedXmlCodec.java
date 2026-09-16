/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;

/**
 * The interface every generated XML codec implements.
 *
 * <p>This type is also the lookup host, so generated classes are nestmates of it and reach the
 * package-private helpers in {@code software.amazon.smithy.java.xml} directly.
 */
interface GeneratedXmlCodec {
    /**
     * Writes {@code value} as a complete document.
     *
     * <p>The root element's tags are parameters rather than baked constants because the name does not
     * belong to the shape being written: an {@code @httpPayload} member can rename the element with
     * {@code @xmlName}, so the same structure is {@code <Hola>} under one member and {@code <Hello>}
     * at the top level. Everything below the root is baked, because nothing above it can rename it.
     *
     * @param value the structure to write, which must be an instance of the shape class this codec was
     *              generated for.
     * @param writer the sink.
     * @param open the opening run: {@code '<'}, the resolved element name, and any namespace
     *             declaration, with no trailing {@code '>'}.
     * @param close the closing run, {@code "</name>"}.
     */
    void write(SerializableShape value, XmlCodegenWriter writer, byte[] open, byte[] close);

    /**
     * Reads the attributes and children of the element {@code reader} is positioned on into
     * {@code builder}, and consumes that element's end tag.
     *
     * <p>The root element's name is not checked here, because the caller has already consumed the start
     * element and decided whether the name was acceptable: {@code strictRootElement} and the AWS Query
     * error wrappers are both settled by {@code SmithyXmlDeserializer.enter} before this is reached.
     *
     * @param reader the deserializer, positioned on an already-parsed start element.
     * @param builder the builder to populate.
     * @return true when the builder was the one this codec was generated for and the element was read;
     *         false when it was not, in which case nothing has been consumed.
     */
    boolean read(SmithyXmlDeserializer reader, ShapeBuilder<?> builder);
}
