/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.scheme;

import software.amazon.smithy.java.runtime.auth.api.AuthProperties;

record AuthSchemeOptionRecord(String schemeId, AuthProperties identityProperties,
        AuthProperties signerProperties) implements AuthSchemeOption {
}
