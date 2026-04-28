/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache;

import org.apache.hc.core5.http.ProtocolVersion;
import software.amazon.smithy.java.http.api.HttpVersion;

final class ApacheResponses {
    private ApacheResponses() {}

    static HttpVersion toSmithyVersion(ProtocolVersion version) {
        if (version == null) {
            return HttpVersion.HTTP_1_1;
        }
        return version.getMajor() >= 2 ? HttpVersion.HTTP_2 : HttpVersion.HTTP_1_1;
    }
}
