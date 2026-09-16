/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import software.amazon.smithy.java.core.schema.SerializableShape;

/**
 * Interface implemented by every codec {@link QueryRuntimeCodegenBackend} emits.
 *
 * <p>Write-only by design: a Query request is form-urlencoded but the response is XML, so the read
 * direction belongs to the XML codec and never appears here.
 *
 * <p>Also the lookup host for the generated hidden classes, which is why it carries no members
 * beyond the entry point: a generated class is a nestmate of this interface and lands in its
 * package, so everything it touches is reachable without widening any visibility.
 */
interface GeneratedQueryCodec {
    void write(SerializableShape value, QueryFormWriter writer);
}
