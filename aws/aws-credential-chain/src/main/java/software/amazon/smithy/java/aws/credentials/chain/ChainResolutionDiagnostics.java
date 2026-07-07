/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import java.util.List;

/**
 * A structured, machine-readable breakdown of why an {@link IdentityChain} failed to resolve an identity.
 *
 * <p>To observe diagnostics, register a sink under {@link IdentityChain#DIAGNOSTICS} on the request {@code Context}
 * before calling {@link IdentityChain#resolveIdentity}. When resolution fails, the chain invokes the sink with an
 * instance of this record. The same information is always present in the human-readable {@code IdentityResult.error()}
 * message; this record just exposes it without string parsing.
 *
 * @param providersTried the ordered names of every provider the chain tried, empty when the chain had no providers.
 * @param moduleSuggestions dependency coordinates for credential sources that were detected but had no registered
 *                          provider, in slot order; empty when there is nothing to suggest.
 */
public record ChainResolutionDiagnostics(List<String> providersTried, List<String> moduleSuggestions) {
    public ChainResolutionDiagnostics {
        providersTried = List.copyOf(providersTried);
        moduleSuggestions = List.copyOf(moduleSuggestions);
    }
}
