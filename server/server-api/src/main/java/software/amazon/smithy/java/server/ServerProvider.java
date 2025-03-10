/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

public interface ServerProvider {

    String name();

    ServerBuilder<?> serverBuilder();

    int priority();
}
