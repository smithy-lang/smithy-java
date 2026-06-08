/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.List;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * SPI for {@link ServerProtocol} discovery via Java's
 * {@link java.util.ServiceLoader}. Implementations are advertised via
 * {@code META-INF/services/software.amazon.smithy.java.server.core.ServerProtocolProvider}
 * resources in each protocol module.
 */
public interface ServerProtocolProvider {

    ServerProtocol provideProtocolHandler(List<Service> candidateServices);

    ShapeId getProtocolId();

    /**
     * Precedence rank for this protocol when multiple protocols are
     * available on a single service. <b>Lower values are higher
     * precedence</b> — sorted ascending by both
     * {@link ProtocolResolver} and the Vert.x bridge, so the protocol
     * with the smallest {@code precision()} value is tried first.
     *
     * <p>The AWS service-protocol precision order from the
     * <a href="https://smithy.io/2.0/guides/wire-protocol-selection.html">Smithy 2.0
     * Wire protocol selection</a> guide is encoded as the
     * canonical scale:
     *
     * <table>
     *   <caption>AWS protocol precedence values</caption>
     *   <tr><th>Protocol</th><th>{@code precision()}</th></tr>
     *   <tr><td>{@code rpcv2Cbor}</td><td>{@code 1}</td></tr>
     *   <tr><td>{@code rpcv2Json}</td><td>{@code 2}</td></tr>
     *   <tr><td>{@code awsJson1_0}</td><td>{@code 3}</td></tr>
     *   <tr><td>{@code awsJson1_1}</td><td>{@code 4}</td></tr>
     *   <tr><td>{@code awsQuery}</td><td>{@code 5}</td></tr>
     *   <tr><td>{@code ec2Query}</td><td>{@code 6}</td></tr>
     *   <tr><td>{@code restJson1}</td><td>{@code 7}</td></tr>
     *   <tr><td>{@code restXml}</td><td>{@code 8}</td></tr>
     * </table>
     *
     * <p>Third-party protocols not in the AWS list should pick a value
     * that places them where they belong on this scale; ties are
     * broken by classpath order, which is non-deterministic across
     * builds and should be avoided.
     */
    int precision();
}
