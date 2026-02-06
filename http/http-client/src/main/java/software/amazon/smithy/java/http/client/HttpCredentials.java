/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;

/**
 * Credentials for HTTP authentication.
 *
 * <p>Implementations handle both preemptive auth (e.g., Basic, Bearer) and challenge-response auth (e.g.,
 * Digest, NTLM, Negotiate).
 *
 * <p>Used for both proxy authentication (CONNECT requests) and server authentication (normal requests).
 */
public interface HttpCredentials {
    /**
     * Apply authentication to an HTTP request.
     *
     * <p>Called before sending the request (preemptive), and again if a 401/407 challenge is received (reactive).
     * Implementations can handle multi-round handshakes by tracking state internally.
     *
     * @param request the request builder to add auth headers to
     * @param priorResponse null on first call, or the 401/407 response for reactive auth
     * @return true if auth was applied and should retry, false to give up
     */
    boolean authenticate(HttpRequest.Builder request, HttpResponse priorResponse);

    /**
     * HTTP Basic authentication credentials.
     *
     * <p>Sends credentials preemptively in the Authorization or Proxy-Authorization header.
     *
     * @param username Username to send.
     * @param password Password to send.
     * @param forProxy True if this is for Proxy-Authorization, false for Authorization.
     */
    record Basic(String username, String password, boolean forProxy) implements HttpCredentials {

        public Basic {
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(password, "password");
        }

        /**
         * Create Basic credentials for server authentication.
         */
        public Basic(String username, String password) {
            this(username, password, false);
        }

        @Override
        public boolean authenticate(HttpRequest.Builder request, HttpResponse priorResponse) {
            // Basic auth is preemptive. If we already tried and got a challenge, give up.
            if (priorResponse != null) {
                return false;
            }

            String credentials = Base64.getEncoder()
                    .encodeToString((username + ':' + password).getBytes(StandardCharsets.UTF_8));
            String header = forProxy ? "Proxy-Authorization" : "Authorization";
            request.withReplacedHeader(header, List.of("Basic " + credentials));
            return true;
        }
    }

    /**
     * HTTP Bearer token authentication (RFC 6750).
     *
     * <p>Sends the token preemptively in the Authorization or Proxy-Authorization header.
     * The token is sent as-is (no encoding applied).
     *
     * @param token Bearer token. Sent as-is on the wire.
     * @param forProxy True if this is for Proxy-Authorization, false for Authorization.
     */
    record Bearer(String token, boolean forProxy) implements HttpCredentials {

        public Bearer {
            Objects.requireNonNull(token, "token");
        }

        /**
         * Create Bearer credentials for server authentication.
         */
        public Bearer(String token) {
            this(token, false);
        }

        @Override
        public boolean authenticate(HttpRequest.Builder request, HttpResponse priorResponse) {
            if (priorResponse != null) {
                return false;
            }

            String header = forProxy ? "Proxy-Authorization" : "Authorization";
            request.withReplacedHeader(header, List.of("Bearer " + token));
            return true;
        }
    }
}
