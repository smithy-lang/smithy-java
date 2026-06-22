/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.s3;

import software.amazon.smithy.java.aws.client.core.settings.S3EndpointSettings;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.rulesengine.aws.language.functions.IsVirtualHostableS3Bucket;

/**
 * Removes the redundant bucket segment from an S3 request path when the bucket is going to be
 * placed in the host as a virtual-hosted-style subdomain.
 *
 * <p><strong>Why this is needed.</strong> S3 models {@code Bucket} twice: as an {@code @httpLabel}
 * (so the operation's {@code @http} URI, e.g. {@code /{Bucket}?list-type=2}, serializes the bucket
 * into the path) <em>and</em> as a {@code smithy.rules#contextParam} feeding the endpoint rule set
 * (which, for virtual-hosted-style addressing, places the bucket in the host and resolves the path
 * to {@code /}). The generic HTTP machinery concatenates the operation path onto the resolved
 * endpoint path, so without intervention the wire request gets the bucket twice:
 * {@code https://<bucket>.s3.<region>.amazonaws.com/<bucket>?list-type=2}. S3 then interprets the
 * leading path segment as an object key (yielding {@code NoSuchKey}). This is the same problem the
 * S3 Express {@code S3VirtualHostStyleInterceptor} solves; this one handles general-purpose S3.
 *
 * <p><strong>When it runs.</strong> {@code modifyBeforeSigning}, which is before the endpoint is
 * resolved and merged onto the request ({@code setServiceEndpoint}). At that point the request host
 * is empty and the path is {@code /<bucket>[/...]}. We strip the leading {@code /<bucket>}; the
 * later endpoint merge then yields a single, correct path. SigV4 signs the corrected request.
 *
 * <p><strong>When it does NOT run.</strong> The bucket is left in the path when path-style
 * addressing is in effect — either because {@code AWS::S3::ForcePathStyle} is set, or because the
 * bucket name is not DNS-compatible (and therefore cannot be a virtual-host subdomain). In those
 * cases the endpoint rules resolve to a path-style host and the bucket legitimately belongs in the
 * path. This mirrors the bucket-addressing decision in the AWS SDKs.
 */
final class S3VirtualHostInterceptor implements ClientInterceptor {

    static final S3VirtualHostInterceptor INSTANCE = new S3VirtualHostInterceptor();

    private S3VirtualHostInterceptor() {}

    @Override
    public <RequestT> RequestT modifyBeforeSigning(RequestHook<?, ?, RequestT> hook) {
        if (!(hook.request() instanceof HttpRequest req)) {
            return hook.request();
        }

        // Found a Bucket?
        var inputSchema = hook.operation().inputSchema();
        var bucketMember = inputSchema.member("Bucket");
        if (bucketMember == null) {
            return hook.request();
        }

        // Bucket set?
        Object bucketValue = hook.input().getMemberValue(bucketMember);
        if (!(bucketValue instanceof String bucket) || bucket.isEmpty()) {
            return hook.request();
        }

        // Do we actually want the Bucket in the path?
        Boolean forcePathStyle = hook.context().get(S3EndpointSettings.S3_FORCE_PATH_STYLE);
        if (Boolean.TRUE.equals(forcePathStyle) || !IsVirtualHostableS3Bucket.isVirtualHostableBucket(bucket, false)) {
            return hook.request();
        }

        var uri = req.uri();
        var path = uri.getPath();
        if (!startsWithBucketSegment(path, bucket)) {
            return hook.request();
        }

        String rest = path.substring(bucket.length() + 1); // drop leading "/<bucket>".
        if (rest.isEmpty()) {
            rest = "/";
        }

        var modifiable = req.toModifiable();
        modifiable.setUri(uri.withPath(rest));
        return hook.asRequestType(modifiable);
    }

    private static boolean startsWithBucketSegment(String path, String bucket) {
        int bucketLength = bucket.length();
        if (path.length() < bucketLength + 1 || path.charAt(0) != '/') {
            return false;
        } else if (!path.regionMatches(1, bucket, 0, bucketLength)) {
            return false;
        } else {
            // Either exactly "/<bucket>" or "/<bucket>/...".
            return path.length() == bucketLength + 1 || path.charAt(bucketLength + 1) == '/';
        }
    }
}
