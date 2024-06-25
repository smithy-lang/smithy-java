/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.util.Set;

final class PrefixConstants {
    private PrefixConstants() {}

    static final Set<String> OMITTED_HEADER_NAMES = Set.of(
        "authorization",
        "connection",
        "content-length",
        "expect",
        "host",
        "max-forwards",
        "proxy-authenticate",
        "server",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
        "user-agent",
        "www-authenticate",
        "x-forwarded-for"
    );
}
