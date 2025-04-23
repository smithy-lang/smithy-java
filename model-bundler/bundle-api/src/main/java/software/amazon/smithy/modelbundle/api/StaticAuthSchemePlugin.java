/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;

public final class StaticAuthSchemePlugin implements ClientPlugin {
    private final AuthScheme<?, ?> authScheme;

    public StaticAuthSchemePlugin(AuthScheme<?, ?> authScheme) {
        this.authScheme = authScheme;
    }

    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.authSchemeResolver(StaticAuthSchemeResolver.INSTANCE)
                .putSupportedAuthSchemes(StaticAuthSchemeResolver.staticScheme(authScheme));
    }
}
