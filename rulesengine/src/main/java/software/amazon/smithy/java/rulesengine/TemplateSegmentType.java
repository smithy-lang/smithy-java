/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.rulesengine;

/**
 * Segment tags used by the inline {@link Opcodes#BUILD_TEMPLATE} encoding.
 */
final class TemplateSegmentType {
    static final byte LITERAL = 0;
    static final byte REGISTER = 1;
    static final byte REGISTER_PROPERTY = 2;

    private TemplateSegmentType() {}
}
