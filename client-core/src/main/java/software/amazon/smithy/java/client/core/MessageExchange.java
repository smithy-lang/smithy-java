/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

/**
 * A marker interface used to define the request and response types of a message exchange.
 *
 * @param <RequestT> Request type.
 * @param <ResponseT> Response type.
 */
public interface MessageExchange<RequestT, ResponseT> extends ClientPlugin {
    @Override
    default void configureClient(ClientConfig.Builder config) {
        // do nothing by default.
    }
}
