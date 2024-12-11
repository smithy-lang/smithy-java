/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.example.middleware;

import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.ClientPlugin;

public class CustomMiddlewarePlugin implements ClientPlugin {
    @Override
    public void configureClient(ClientConfig.Builder config) {
        config.putSupportedAuthSchemes(new CustomAuthScheme());
        config.addInterceptor(new TokenRefreshInterceptor());
    }
}
