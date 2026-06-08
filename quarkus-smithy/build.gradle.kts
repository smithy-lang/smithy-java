/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("smithy-java.java-conventions")
    id("smithy-java.publishing-conventions")
    // Generates META-INF/quarkus-extension.properties and quarkus-extension.yaml,
    // and enables the quarkus-extension-processor annotation processor that
    // emits META-INF/quarkus-build-steps.list (and other extension metadata).
    id("io.quarkus.extension")
}

description = "Experimental Quarkus extension for Smithy-Java :: runtime"

extra["displayName"] = "Smithy :: Java :: Quarkus :: Runtime"
extra["moduleName"] = "software.amazon.smithy.java.quarkus.runtime"

val quarkusPlatformVersion = "3.35.3"

// Override the JDK 21 default from smithy-java.java-conventions: the Quarkus
// extension targets JDK 25.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(25)
}

// SpotBugs 4.8.x cannot parse Java 25 (class file 69) bytecode. Bump locally.
spotbugs {
    toolVersion = "4.9.8"
}

// Tell the io.quarkus.extension plugin which sibling project provides our
// deployment artifact. The plugin writes this into quarkus-extension.properties
// for Quarkus's bootstrap to resolve at build time.
quarkusExtension {
    deploymentModule.set(":quarkus-smithy-deployment")
}

// The io.quarkus.extension plugin's tasks call Task.project at execution
// time and reference Configuration/Project objects in their state, which
// Gradle's configuration cache disallows. Opt those tasks out so the rest
// of the repo (which does work with config cache, enabled in
// gradle.properties) keeps its caching benefit.
tasks.named("extensionDescriptor").configure {
    notCompatibleWithConfigurationCache("io.quarkus.extension.gradle.tasks.ExtensionDescriptorTask uses Project at execution time")
}
// validateExtension reads sibling project jars on the runtime classpath to
// validate the extension's dependency graph; under -parallel it races with
// the :jar tasks of those projects and can read partially-written zips.
// Force it to run after the runtime classpath's :jar tasks have all
// completed.
tasks.named("validateExtension").configure {
    notCompatibleWithConfigurationCache("io.quarkus.extension.gradle.tasks.ValidateExtensionTask uses Task.project at execution time")
    dependsOn(
        configurations.runtimeClasspath.map { rc ->
            rc.allDependencies
                .withType<ProjectDependency>()
                .map { dep -> "${dep.path}:jar" }
        },
    )
}

dependencies {
    // Quarkus core + Arc CDI.
    implementation(platform("io.quarkus.platform:quarkus-bom:$quarkusPlatformVersion"))
    implementation("io.quarkus:quarkus-arc")
    // The bridge mounts on quarkus-vertx-http's main Router. This dep
    // is what gives users one ingress port (per ADR-0003).
    implementation("io.quarkus:quarkus-vertx-http")

    // Smithy-Java server runtime API (Service, Operation, RequestContext)
    // and the Vert.x server module that mounts services on a Router.
    api(project(":server:server-api"))
    api(project(":server:server-vertx"))

    // For InternalLogger used by the recorder.
    implementation(project(":logging"))

    // For @SmithyUnstableApi on package-info.
    implementation(libs.smithy.utils)
}
