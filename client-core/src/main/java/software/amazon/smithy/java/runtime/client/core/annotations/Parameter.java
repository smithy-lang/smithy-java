/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets a name to use for a parameter in a default plugin setter method.
 *
 * <p>A default plugin setter is method on a {@link software.amazon.smithy.java.runtime.client.core.ClientPlugin}
 * implementation annotated with {@link Configuration} and with no return value.
 * <p>The {@code @Parameter} annotation is used to set the name of a setter argument.
 * A setter argument without this annotation will be generated as {@code arg0}, {@code arg1}, etc.
 * unless the {@code -parameters} compiler argument is set. Adding this annotation will allow code
 * generation to add a user-friendly name to the parameter on any generated setter methods.
 *
 * <p>For example, the following default plugin setter:
 * <pre>{@code
 * @Configuration
 * public void region(@Parameter("region") String region) {
 *     this.region = region;
 * }
 * }</pre>
 *
 * <p>Will generate the following client builder setter:
 * <pre>{@code
 * public void region(String region) {
 *     regionPlugin.region(region);
 * }
 * }</pre>
 * <p>Without the {@code @Parameter} annotation the generated
 * client builder setter would have the following method signature:
 * <pre>{@code
 * public void region(String arg0) {
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Parameter {
    String value();
}
