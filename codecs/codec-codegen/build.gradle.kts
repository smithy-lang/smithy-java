plugins {
    id("smithy-java.module-conventions")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.release.set(25)
}

description = "Runtime code generation framework for specialized codec serializers/deserializers"

extra["displayName"] = "Smithy :: Java :: Codec Codegen"
extra["moduleName"] = "software.amazon.smithy.java.codegen.rt"

dependencies {
    api(project(":core"))
}
