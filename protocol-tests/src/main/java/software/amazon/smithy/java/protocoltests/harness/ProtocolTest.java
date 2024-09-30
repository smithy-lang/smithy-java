/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.annotation.Testable;

/**
 * Junit5 Test Extension that sets up protocol tests for a given service (the "service under test").
 *
 * <p>This extension runs as part of the {@code BeforeAll} lifecycle stage of test execution, resolving common information
 * needed by all protocol tests and storing this common information in an {@link ExtensionContext.Store} for use by
 * specific protocol tests.
 *
 * <p><strong>Note</strong>: This extension does not actually provide any protocol tests, instead, use one of the following
 * protocol test providers on a test method in your test class to actually execute specific protocol tests:
 * <ul>
 *     <li>{@link HttpClientRequestTests}</li>
 *     <li>{@link HttpClientResponseTests}</li>
 *     <li>{@link HttpServerRequestTests}</li>
 *     <li>{@link HttpServerResponseTests}</li>
 * </ul>
 *
 * <p>This extension is applied to a test class and is configured for a single service shape.
 * For example:
 * <pre>{@code
 * @ProtocolTest(service = "my.service.under.test#ServiceUnderTest")
 * public class ProtocolTestsForThisService {
 *  ...
 * }
 * }</pre>
 *
 * <p>The following information is resolved by this extension and made available to Protocol Test providers
 * via the {@link ProtocolTestExtension#getSharedTestData(ExtensionContext, Class)} method:
 * <dl>
 *     <dt>Protocols</dt>
 *     <dd>Initialized implementations for all possible protocols for the service.
 *     <strong>Note</strong>: The service under test must have at least one protocol trait applied and must have at least one
 *     protocol test factory implementation available on the classpath for a protocol trait on the service.</dd>
 *     <dt>AuthSchemes</dt>
 *     <dd>Initialized implementation for all possible auth schemes. These auth schemes can be used in some protocol tests.
 *     <strong>Note:</strong> if an auth scheme implementation is not found on the classpath then that auth scheme is
 *     simply skipped.</dd>
 *     <dt>Operations</dt>
 *     <dd>A list of {@code HttpTestOperation}'s. These contain the operation ID, the {@code ApiOperation} model
 *     for the operation, and all protocol test cases associated with the operation</dd>
 * </dl>
 */
@Testable
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ProtocolTestExtension.class)
public @interface ProtocolTest {
    String service();

    TestType testType();
}
