/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import software.amazon.smithy.java.server.core.attributes.HttpAttributes;

import java.net.http.HttpHeaders;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpHandler implements SyncHandler {
    @Override
    public void doBefore(Job job) {

    }

    @Override
    public void doAfter(Job job) {
        Reply reply = job.getReply();
        Map<String, List<String>> header = new HashMap<>();

        reply.getContext().put(HttpAttributes.HTTP_HEADERS, HttpHeaders.of(header, (k, v) -> true));
    }
}
