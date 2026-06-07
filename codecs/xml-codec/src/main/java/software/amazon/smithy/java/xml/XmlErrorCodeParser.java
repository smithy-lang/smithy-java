/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.xml;

/**
 * Interface for XML deserializers that can parse error code names from error response bodies.
 */
interface XmlErrorCodeParser {
    String parseErrorCodeName();
}
