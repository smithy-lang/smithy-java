import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer

plugins {
    java
    id("software.amazon.smithy.java.gradle.smithy-java")
    id("com.gradleup.shadow")
}

dependencies {
    val smithyJavaVersion: String by project

    implementation("software.amazon.smithy.java:smithy-ai-traits:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:mcp-server:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:server-proxy:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:server-netty:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-server-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-client-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-client-awsjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-service-bundle:$smithyJavaVersion")
}

repositories {
    mavenLocal()
    mavenCentral()
}

tasks.shadowJar {
    mergeServiceFiles()
    transform(AppendingTransformer::class.java) {
        resource = "META-INF/smithy/manifest"
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
