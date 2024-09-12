/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Sets a name to use for a parameter in a default plugin setter method.
 *
 * <p>A default plugin setter method is a method on a {@link software.amazon.smithy.java.runtime.client.ClientPlugin}
 * implementation annotated with {@link Configuration} and with no return value.
 *
 * <p>The {@code @Parameter} annotation is used to set the name of a setter argument.
 * A setter argument without this annotation is generated as {@code $setterName}, {@code $setterName1}, etc.
 * (assuming a setter method named {@code $setterName}). Adding this annotation will allow code generation to add a
 * user-friendly name for the parameter.
 *
 * <p>For example, the following default plugin setter:
 * <pre>{@code
 * @Configuration
 * public void regions(
 *  @Parameter("regionA") String regionA,
 *  @Parameter("regionB") String regionB
 * ) {
 *     this.regionA = regionA;
 *     this.regionB = regionB;
 * }
 * }</pre>
 *
 * <p>Will generate the following client builder setter:
 * <pre>{@code
 * public void regions(String regionA, String regionB) {
 *     regionPlugin.regions(regionA, regionB);
 * }
 * }</pre>
 * <p>Without the {@code @Parameter} annotation the generated
 * client builder setter would have the following method signature:
 * <pre>{@code
 * public void regions(String region, String region1) {
 *     regionPlugin.regions(region, region1);
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Parameter {
    String value();
}
