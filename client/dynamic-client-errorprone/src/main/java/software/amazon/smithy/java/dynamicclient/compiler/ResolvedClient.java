/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicclient.compiler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * The resolution result for one {@code DynamicClient} variable: the model it is built from and the service selected on
 * it, plus a precomputed operation-name lookup mirroring {@code DynamicClient}'s own runtime table.
 */
final class ResolvedClient {

    private final Model model;
    private final ServiceShape service;
    private final Map<String, OperationShape> operations;

    private ResolvedClient(Model model, ServiceShape service, Map<String, OperationShape> operations) {
        this.model = model;
        this.service = service;
        this.operations = operations;
    }

    /**
     * Build a resolved client from a model and an optional explicit service ID. Mirrors {@code DynamicClient.Builder}:
     * an explicit service is used if present; otherwise a single service in the model is auto-detected. If the service
     * cannot be determined (zero or multiple, none specified), returns a client with a null model so callers abstain.
     */
    static ResolvedClient of(Model model, String serviceIdLiteral) {
        if (model == null) {
            return new ResolvedClient(null, null, Map.of());
        }
        ServiceShape service = selectService(model, serviceIdLiteral);
        if (service == null) {
            // Same abstain-if-ambiguous stance the runtime takes by throwing; here we simply can't validate.
            return new ResolvedClient(null, null, Map.of());
        }
        Map<String, OperationShape> ops = new LinkedHashMap<>();
        for (OperationShape op : TopDownIndex.of(model).getContainedOperations(service)) {
            ops.put(op.getId().getName(), op);
        }
        return new ResolvedClient(model, service, ops);
    }

    private static ServiceShape selectService(Model model, String serviceIdLiteral) {
        if (serviceIdLiteral != null) {
            try {
                return model.getShape(ShapeId.from(serviceIdLiteral))
                        .flatMap(s -> s.asServiceShape())
                        .orElse(null);
            } catch (RuntimeException e) {
                return null;
            }
        }
        var services = model.getServiceShapes();
        return services.size() == 1 ? services.iterator().next() : null;
    }

    Model model() {
        return model;
    }

    ServiceShape service() {
        return service;
    }

    Map<String, OperationShape> operations() {
        return operations;
    }

    List<String> sortedOperationNames() {
        return operations.keySet().stream().sorted().toList();
    }
}
