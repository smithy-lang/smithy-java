/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;

/**
 * A trait that marks operations as proxies to delegate operations.
 * 
 * <p>Operations annotated with this trait are treated as proxies to the 
 * delegate operation specified by this trait. Proxy operations enable 
 * additional input processing while maintaining compatibility with the 
 * original operation interface.</p>
 * 
 * <h3>Proxy Operation Input Handling</h3>
 * <p>Proxy operation inputs are expected to be a superset of the original 
 * operation input. For operations with existing input, a new input structure 
 * is created that contains all original input members plus an 
 * {@code additionalInput} field containing the additional parameters. For 
 * operations with no input, the additional input structure is used directly.</p>
 * 
 * <h3>Additional Input Access</h3>
 * <p>The additional input data can be accessed in Dynamic client interceptors 
 * using the {@code ProxyService.PROXY_INPUT} context key. This enables 
 * interceptors to process the extra data before the request is forwarded 
 * to the delegate service.</p>
 * 
 * <h3>Input Stripping</h3>
 * <p>The proxy service automatically strips out the additional input before 
 * sending the request to the delegate service, ensuring that only the 
 * expected input parameters are forwarded to the original operation.</p>
 * 
 * @see ProxyService#PROXY_INPUT
 */
public final class ProxyOperationTrait implements Trait {

    private static final ShapeId SHAPE_ID = ShapeId.from("smithy.server.api#proxyOperation");
    private final ShapeId delegateOperation;
    private final String inputMemberName;
    private final String additionalInputMemberName;

    public ProxyOperationTrait(ShapeId delegateOperation, String inputMemberName, String additionalInputMemberName) {
        this.delegateOperation = delegateOperation;
        this.inputMemberName = inputMemberName;
        this.additionalInputMemberName = additionalInputMemberName;
    }

    @Override
    public ShapeId toShapeId() {
        return SHAPE_ID;
    }

    @Override
    public Node toNode() {
        return null;
    }

    public ShapeId getDelegateOperation() {
        return delegateOperation;
    }

    /**
     * Gets the name of the member containing the original input in the wrapper structure.
     *
     * @return the input member name, or null if the original operation had no input
     */
    public String getInputMemberName() {
        return inputMemberName;
    }

    /**
     * Gets the name of the member containing the additional input in the wrapper structure.
     *
     * @return the additional input member name
     */
    public String getAdditionalInputMemberName() {
        return additionalInputMemberName;
    }
}
