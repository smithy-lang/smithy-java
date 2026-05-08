/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.imds;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Minimal IMDSv2 client. Handles token acquisition, caching, and retries with exponential backoff.
 */
final class ImdsClient {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(ImdsClient.class);
    private static final String TOKEN_PATH = "/latest/api/token";
    private static final String CREDENTIALS_EXTENDED_PATH = "/latest/meta-data/iam/security-credentials-extended";
    private static final String CREDENTIALS_LEGACY_PATH = "/latest/meta-data/iam/security-credentials";
    private static final Duration TOKEN_TTL = Duration.ofSeconds(21600);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RETRIES = 3;

    private final URI endpoint;
    private final SimpleHttpClient httpClient;
    private volatile String cachedToken;
    private volatile Instant tokenExpiry;
    private volatile boolean apiVersionKnown = false;
    private volatile boolean useLegacyPath = false;
    private volatile String cachedProfileName;

    /** Minimal HTTP client interface for testability. */
    @FunctionalInterface
    interface SimpleHttpClient {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    private interface RetryableCall<T> {
        String execute(T ctx) throws IOException, InterruptedException;
    }

    ImdsClient(URI endpoint) {
        this(endpoint, defaultClient());
    }

    ImdsClient(URI endpoint, SimpleHttpClient httpClient) {
        this.endpoint = endpoint;
        this.httpClient = httpClient;
    }

    private static SimpleHttpClient defaultClient() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        return request -> client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Fetch credentials JSON from IMDS.
     */
    String fetchCredentials(String profileName) throws IOException, InterruptedException {
        String resolvedProfile = profileName;
        if (resolvedProfile == null) {
            resolvedProfile = discoverProfileName();
        }

        String basePath = useLegacyPath ? CREDENTIALS_LEGACY_PATH : CREDENTIALS_EXTENDED_PATH;
        String body = retryGet(basePath + "/" + resolvedProfile);

        // 404: could be API version mismatch or profile change.
        if (body == null && !useLegacyPath && !apiVersionKnown) {
            // API version unknown and extended returned 404 — try legacy.
            useLegacyPath = true;
            if (profileName == null) {
                cachedProfileName = null;
                resolvedProfile = discoverProfileName();
            }
            body = retryGet(CREDENTIALS_LEGACY_PATH + "/" + resolvedProfile);
        } else if (body == null && profileName == null) {
            // API version known but profile returned 404 — profile changed, re-discover.
            cachedProfileName = null;
            resolvedProfile = discoverProfileName();
            basePath = useLegacyPath ? CREDENTIALS_LEGACY_PATH : CREDENTIALS_EXTENDED_PATH;
            body = retryGet(basePath + "/" + resolvedProfile);
        }

        if (body == null) {
            throw new IOException("Failed to fetch IMDS credentials after retries");
        }

        return body;
    }

    private String discoverProfileName() throws IOException, InterruptedException {
        String cached = cachedProfileName;
        if (cached != null) {
            return cached;
        }
        String basePath = useLegacyPath ? CREDENTIALS_LEGACY_PATH : CREDENTIALS_EXTENDED_PATH;
        String body = retryGet(basePath);
        if (body == null && !useLegacyPath) {
            useLegacyPath = true;
            body = retryGet(CREDENTIALS_LEGACY_PATH);
        }
        if (body == null) {
            throw new IOException("Failed to discover IMDS instance profile name");
        }
        apiVersionKnown = true;
        cachedProfileName = body.strip();
        return cachedProfileName;
    }

    private String retryGet(String path) throws IOException, InterruptedException {
        return retry(path, p -> {
            String token = getToken();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(endpoint.resolve(p))
                    .header("X-aws-ec2-metadata-token", token)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request);
            if (response.statusCode() == 200) {
                return response.body();
            } else if (response.statusCode() == 404) {
                return null;
            } else {
                throw new IOException("IMDS returned status " + response.statusCode() + " for " + p);
            }
        });
    }

    private String getToken() throws IOException, InterruptedException {
        String token = cachedToken;
        if (token != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
            return token;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint.resolve(TOKEN_PATH))
                .header("X-aws-ec2-metadata-token-ttl-seconds", String.valueOf(TOKEN_TTL.toSeconds()))
                .timeout(REQUEST_TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        String newToken = retry(request, r -> {
            HttpResponse<String> response = httpClient.send(r);
            if (response.statusCode() == 200) {
                return response.body();
            }
            throw new IOException("IMDS token request returned status " + response.statusCode());
        });

        if (newToken == null) {
            throw new IOException("Failed to acquire IMDSv2 token after retries");
        } else {
            cachedToken = newToken;
            tokenExpiry = Instant.now().plus(TOKEN_TTL);
            return newToken;
        }
    }

    /**
     * Retry a callable with exponential backoff. Returns null if the callable returns null (e.g., 404).
     * Throws the last IOException if all retries are exhausted.
     */
    private static <T> String retry(T ctx, RetryableCall<T> call) throws IOException, InterruptedException {
        IOException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            if (attempt > 0) {
                Thread.sleep((long) Math.pow(2, attempt - 1) * 100);
            }
            try {
                return call.execute(ctx);
            } catch (IOException e) {
                LOGGER.debug("IMDS request failed (attempt {}): {}", attempt + 1, e.getMessage());
                lastException = e;
            }
        }

        throw lastException;
    }
}
