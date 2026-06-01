/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.s3express;

import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.http.api.HttpRequest;

/**
 * Strips the bucket name from the request path so the resolved endpoint, which already places
 * the bucket in the host as a virtual-hosted-style subdomain, doesn't end up with the bucket
 * appearing twice.
 *
 * <p>The S3 model defines operations with paths like {@code /{Bucket}/{Key+}}, and our path
 * serializer substitutes the bucket value into the {@code {Bucket}} label at serialization
 * time. After endpoint resolution, the rules engine returns a host of {@code <bucket>.s3express-…}
 * for S3 Express directory buckets — so without this interceptor the wire request ends up with
 * the bucket appearing once in the host and once at the start of the path. S3 still accepts
 * this (it canonicalizes), but it's wasted bytes and signing work.
 *
 * <p>This runs in {@code modifyBeforeSigning} — BEFORE {@code setServiceEndpoint} merges the
 * endpoint URI onto the request URI. At that point the request URI host is empty and the path
 * is {@code /<bucket>/<key>}. We strip the leading {@code /<bucket>} from the path; the
 * subsequent endpoint merge produces {@code https://<bucket>.s3express-…/<key>}. SigV4 signs
 * the corrected path.
 *
 * <p>Activates only when {@link S3ExpressContext#BUCKET} is set, since S3 Express is always
 * virtual-hosted-style and the path will always have the bucket prefix.
 */
final class S3VirtualHostStyleInterceptor implements ClientInterceptor {

    static final S3VirtualHostStyleInterceptor INSTANCE = new S3VirtualHostStyleInterceptor();

    private S3VirtualHostStyleInterceptor() {}

    @Override
    public <RequestT> RequestT modifyBeforeSigning(RequestHook<?, ?, RequestT> hook) {
        var bucket = hook.context().get(S3ExpressContext.BUCKET);
        if (bucket == null || bucket.isEmpty()) {
            return hook.request();
        }
        if (!(hook.request() instanceof HttpRequest req)) {
            return hook.request();
        }

        var uri = req.uri();
        var path = uri.getPath();
        if (path == null) {
            return hook.request();
        }

        var prefix = "/" + bucket;
        if (!path.startsWith(prefix)) {
            return hook.request();
        }
        if (path.length() > prefix.length() && path.charAt(prefix.length()) != '/') {
            return hook.request();
        }
        String rest = path.substring(prefix.length());
        // "/<bucket>" → "/" (CreateSession-shaped), "/<bucket>/key" → "/key".
        if (rest.isEmpty()) {
            rest = "/";
        }
        if (rest.equals(path)) {
            return hook.request();
        }

        var modifiable = req.toModifiable();
        modifiable.setUri(uri.withPath(rest));
        return hook.asRequestType(modifiable);
    }
}
