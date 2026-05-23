/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("smithy-java.java-conventions")
    id("smithy-java.publishing-conventions")
}

description = "Experimental Quarkus extension for Smithy-Java :: deployment"

extra["displayName"] = "Smithy :: Java :: Quarkus :: Deployment"
extra["moduleName"] = "software.amazon.smithy.java.quarkus.deployment"

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

// Quarkus's junit5-internal test machinery references Project at execution
// time, which Gradle's configuration cache disallows. Opt this task out so
// the rest of the repo (config-cache enabled in gradle.properties) keeps
// its caching benefit.
tasks.named("test").configure {
    notCompatibleWithConfigurationCache("io.quarkus:quarkus-junit5-internal references DefaultProject at execution time")
}

// Quarkus loads -deployment artifacts via a separate classloader at build time
// only. They never reach the user's runtime classpath. The artifact GAV
// software.amazon.smithy.java:quarkus-smithy-deployment:<ver> matches the
// descriptor written by :quarkus-smithy.

dependencies {
    // Quarkus build-time API (BuildStep, BuildItems, Recorders, CodeGenProvider).
    implementation(platform("io.quarkus.platform:quarkus-bom:$quarkusPlatformVersion"))
    implementation("io.quarkus:quarkus-arc-deployment")
    implementation("io.quarkus:quarkus-core-deployment")

    // Bundle Smithy-Java codegen so users do not need to declare it. (See
    // /docs/adr/0002-bundle-codegen-in-deployment-artifact.md and
    // /docs/adr/0004-limit-deployment-bundling-scope.md.)
    implementation(project(":codegen:codegen-plugin"))

    // SmithyBuild + SmithyBuildConfig.load + ModelAssembler. Pulled in transitively
    // via codegen-plugin, but declared explicitly so a refactor upstream does not
    // silently remove it from this module's classpath.
    implementation(libs.smithy.model)
    implementation(libs.smithy.utils)

    // Trait-defining jars (smithy-aws-traits, smithy-protocol-traits, smithy-rules)
    // are deliberately NOT bundled here. They reach the deployment classloader
    // transitively via the user's runtime protocol deps (e.g. aws-server-restjson,
    // aws-client-restjson). See ADR-0004 for the rationale.

    // Quarkus's vertx-http deployment artifact: VertxWebRouterBuildItem
    // is the producer of the main Router that the bridge mounts on.
    implementation("io.quarkus:quarkus-vertx-http-deployment")

    // The runtime module is on the deployment classpath so we can reference
    // recorder + bean classes from @BuildStep methods.
    implementation(project(":quarkus-smithy"))

    // For InternalLogger used inside SmithyCodeGenProvider.
    implementation(project(":logging"))

    // The Quarkus extension annotation processor scans @BuildStep methods and
    // generates META-INF/quarkus-build-steps.list. Activated automatically via
    // the io.quarkus.extension plugin applied to :quarkus-smithy, but the
    // deployment module needs the processor on its compile classpath too.
    annotationProcessor("io.quarkus:quarkus-extension-processor:$quarkusPlatformVersion")

    // Tests.
    testImplementation("io.quarkus:quarkus-junit5-internal")
    // The codegen integration tests run code generation; they need
    // restJson1 traits on the classpath to compile the test smithy
    // models. server-netty is gone — Phase 2 of ADR-0006 made the
    // Vert.x bridge the single transport.
    testImplementation(project(":aws:server:aws-server-restjson"))
}
