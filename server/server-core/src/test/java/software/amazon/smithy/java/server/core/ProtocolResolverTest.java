/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.framework.model.UnknownOperationException;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Route;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Tests for {@link ProtocolResolver}, covering both
 * {@link ProtocolResolver#resolve(ServiceProtocolResolutionRequest)} and
 * {@link ProtocolResolver#resolveOrEmpty(ServiceProtocolResolutionRequest)}
 * across the three observable outcomes: claim-and-resolve, no-claim, and
 * claim-and-reject. The Vert.x server relies on the second method's
 * no-claim/reject distinction to decide between {@code ctx.next()} and 404.
 */
public class ProtocolResolverTest {

    private static ServiceProtocolResolutionRequest request() {
        return new ServiceProtocolResolutionRequest(
                URI.create("http://localhost/menu"),
                new TestStructs.TestModifiableHttpHeaders(),
                Context.create(),
                "GET");
    }

    private static ServiceMatcher singleServiceMatcher(Service service) {
        return new ServiceMatcher(List.of(
                Route.builder().pathPrefix("/").services(List.of(service)).build()));
    }

    /** A protocol that always returns null (= "this request is not mine"). */
    private static final class NeverClaimsProtocol extends ServerProtocol {
        NeverClaimsProtocol() {
            super(List.of());
        }

        @Override
        public ShapeId getProtocolId() {
            return ShapeId.from("test#NeverClaims");
        }

        @Override
        public ServiceProtocolResolutionResult resolveOperation(
                ServiceProtocolResolutionRequest request,
                List<Service> candidates
        ) {
            return null;
        }

        @Override
        public CompletableFuture<Void> deserializeInput(Job job) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output, boolean isError) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * A protocol that claims every request and returns a fixed result.
     * The result's operation/service fields are not exercised in these
     * tests; identity comparison on the protocol is enough.
     */
    private static final class AlwaysClaimsProtocol extends ServerProtocol {
        AlwaysClaimsProtocol() {
            super(List.of());
        }

        @Override
        public ShapeId getProtocolId() {
            return ShapeId.from("test#AlwaysClaims");
        }

        @Override
        public ServiceProtocolResolutionResult resolveOperation(
                ServiceProtocolResolutionRequest request,
                List<Service> candidates
        ) {
            return new ServiceProtocolResolutionResult(candidates.get(0), null, this);
        }

        @Override
        public CompletableFuture<Void> deserializeInput(Job job) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output, boolean isError) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * A protocol that claims the request (it's mine) but throws because
     * the input is malformed — the second of the two failure modes the
     * resolver must distinguish.
     */
    private static final class ClaimsAndRejectsProtocol extends ServerProtocol {
        ClaimsAndRejectsProtocol() {
            super(List.of());
        }

        @Override
        public ShapeId getProtocolId() {
            return ShapeId.from("test#ClaimsAndRejects");
        }

        @Override
        public ServiceProtocolResolutionResult resolveOperation(
                ServiceProtocolResolutionRequest request,
                List<Service> candidates
        ) {
            throw UnknownOperationException.builder().message("malformed").build();
        }

        @Override
        public CompletableFuture<Void> deserializeInput(Job job) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output, boolean isError) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class StubService implements Service {
        @Override
        public <I extends SerializableStruct,
                O extends SerializableStruct> Operation<I, O> getOperation(String operationName) {
            return null;
        }

        @Override
        public List<Operation<? extends SerializableStruct, ? extends SerializableStruct>> getAllOperations() {
            return List.of();
        }

        @Override
        public Schema schema() {
            return Schema.createService(ShapeId.from("test#Stub"));
        }

        @Override
        public TypeRegistry typeRegistry() {
            return TypeRegistry.EMPTY;
        }

        @Override
        public SchemaIndex schemaIndex() {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    public void resolveReturnsResultOnClaim() {
        var service = new StubService();
        var matcher = singleServiceMatcher(service);
        var protocol = new AlwaysClaimsProtocol();
        var resolver = new ProtocolResolver(matcher, List.of(protocol));

        var result = resolver.resolve(request());

        assertThat(result).isNotNull();
        assertThat(result.protocol()).isSameAs(protocol);
    }

    @Test
    public void resolveThrowsOnNoClaim() {
        var service = new StubService();
        var matcher = singleServiceMatcher(service);
        var resolver = new ProtocolResolver(matcher, List.of(new NeverClaimsProtocol()));

        assertThatThrownBy(() -> resolver.resolve(request()))
                .isInstanceOf(UnknownOperationException.class);
    }

    @Test
    public void resolveThrowsOnClaimAndReject() {
        var service = new StubService();
        var matcher = singleServiceMatcher(service);
        var resolver = new ProtocolResolver(matcher,
                List.of(new ClaimsAndRejectsProtocol()));

        assertThatThrownBy(() -> resolver.resolve(request()))
                .isInstanceOf(UnknownOperationException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    public void resolveOrEmptyReturnsResultOnClaim() {
        var service = new StubService();
        var matcher = singleServiceMatcher(service);
        var protocol = new AlwaysClaimsProtocol();
        var resolver = new ProtocolResolver(matcher, List.of(protocol));

        var result = resolver.resolveOrEmpty(request());

        assertThat(result).isPresent();
        assertThat(result.get().protocol()).isSameAs(protocol);
    }

    @Test
    public void resolveOrEmptyReturnsEmptyOnNoClaim() {
        var service = new StubService();
        var matcher = singleServiceMatcher(service);
        var resolver = new ProtocolResolver(matcher,
                List.of(new NeverClaimsProtocol()));

        var result = resolver.resolveOrEmpty(request());

        assertThat(result).isEmpty();
    }

    @Test
    public void resolveOrEmptyPropagatesClaimAndRejectAsThrow() {
        // The whole point of the tri-state distinction: claim-and-reject
        // must NOT collapse into Optional.empty(). Callers downstream
        // (e.g. the Vert.x bridge) rely on the throw to translate to 404
        // rather than ctx.next().
        var service = new StubService();
        var matcher = singleServiceMatcher(service);
        var resolver = new ProtocolResolver(matcher,
                List.of(new ClaimsAndRejectsProtocol()));

        assertThatThrownBy(() -> resolver.resolveOrEmpty(request()))
                .isInstanceOf(UnknownOperationException.class);
    }

    @Test
    public void resolveOrEmptyHonorsListOrderAndStopsOnFirstClaim() {
        // The list is treated as precision-ordered. If the first protocol
        // claims, no later protocol is consulted — and crucially, a
        // claim-and-reject second protocol is never reached.
        var service = new StubService();
        var matcher = singleServiceMatcher(service);
        var winner = new AlwaysClaimsProtocol();
        var resolver = new ProtocolResolver(matcher,
                List.of(winner, new ClaimsAndRejectsProtocol()));

        var result = resolver.resolveOrEmpty(request());

        assertThat(result).isPresent();
        assertThat(result.get().protocol()).isSameAs(winner);
    }
}
