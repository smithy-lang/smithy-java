/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("smithy-java.java-conventions")
}

description = "Experimental Quarkus extension for Smithy-Java :: integration tests"

extra["displayName"] = "Smithy :: Java :: Quarkus :: Integration Tests"
extra["moduleName"] = "software.amazon.smithy.java.quarkus.integration"

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

dependencies {
    // Test the deployment-side code (CodeGenProvider) directly.
    testImplementation(project(":quarkus-smithy-deployment"))
    testImplementation(project(":codegen:codegen-plugin"))

    // JavaCodegenPlugin.validateDependencies() does Class.forName checks on the
    // current classloader for each codegen mode. server-api covers SERVER mode;
    // client-core covers CLIENT mode. TYPES mode needs neither.
    testImplementation(project(":server:server-api"))
    testImplementation(project(":client:client-core"))

    // ClientInterfaceGenerator.getFactory() resolves a ClientProtocolFactory via
    // ServiceLoader when the model declares a protocol trait. The restJson1
    // factory ships with aws-client-restjson; without it, client-mode codegen
    // for an @restJson1 service fails at the codegen stage (not the model load).
    testImplementation(project(":aws:client:aws-client-restjson"))

    // CodeGenContext lives in quarkus-core-deployment.
    testImplementation(platform("io.quarkus.platform:quarkus-bom:$quarkusPlatformVersion"))
    testImplementation("io.quarkus:quarkus-core-deployment")

    testImplementation(libs.smithy.model)
    testImplementation(libs.smithy.utils)
}
