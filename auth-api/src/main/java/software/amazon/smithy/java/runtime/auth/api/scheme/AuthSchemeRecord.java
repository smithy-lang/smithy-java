/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.scheme;

import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.model.shapes.ShapeId;

record AuthSchemeRecord<RequestT, IdentityT extends Identity>(
    ShapeId schemeId, Class<RequestT> requestClass,
    Class<IdentityT> identityClass, Signer<RequestT, IdentityT> signer
) implements AuthScheme<RequestT, IdentityT> {
}
