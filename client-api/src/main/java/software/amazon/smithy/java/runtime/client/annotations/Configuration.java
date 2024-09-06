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
 * Declares a method as a configuration setter for {@code ClientPlugin}'s.
 *
 * <p>Methods on {@link software.amazon.smithy.java.runtime.client.core.ClientPlugin} implementations
 * that are marked with this annotation can be used by code generation to create builder setters for
 * default plugins.
 *
 * <p><strong>Note</strong>: Methods marked with this annotation should return {@code void}.
 *
 * <p>For example, if we wanted to have a region setting in our generated clients we might define the following
 * client plugin:
 *
 * <pre>{@code
 * class RegionSetting implements ClientPlugin {
 *     static final Context.Key<String> REGION = Context.key("Region.");
 *
 *     private final String region;
 *
 *     // Identify this method as a setter that we want to have a
 *     // corresponding setter generated for in the client build.
 *     @Configuration
 *     public void region(String region) {
 *         this.region = region;
 *     }
 *
 *     @Override
 *     public void configureClient(Config.builder config) {
 *         // If we require that the region is set when this
 *         // plugin is applied then we can check it here.
 *         Objects.nonNull(region, "Region must be set");
 *         config.putConfig(REGION, region);
 *     }
 * }
 * }</pre>
 *
 * <p>This plugin will set a configured Region value in the Client context for use by interceptors and other
 * client pipeline components. If we then identify this {@code RegionSetting} plugin as a default plugin
 * when code generating a client then a setter matching the {@code @Configuration} marked method will be generated
 * on the builder. We can then call the generated method as:
 *
 * <pre>{@code
 *     var foo = FooClient.builder()
 *          .region("my-region")
 *          .build();
 * }</pre>
 *
 * <p>The {@code FooClient.Builder#region} method will delegate to the {@code RegionSetting#region} method, setting the
 * parameter on the RegionSetting plugin.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Configuration {
    // Optional name to use for setter. Otherwise, just use name of setter method that this annotates
    String value() default "";

    // Optional description to add as a docstring to generated method
    String description() default "";
}
