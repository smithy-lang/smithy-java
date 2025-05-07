import com.github.jengelman.gradle.plugins.shadow.transformers.AppendingTransformer

plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-base")
    id("com.gradleup.shadow").version("8.3.5")
}

dependencies {
    val smithyJavaVersion: String by project

    smithyBuild("software.amazon.smithy.java:plugins:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:mcp-server:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:server-proxy:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:server-netty:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-server-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-client-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-client-awsjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-service-bundle:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:mcp-bundle-api:$smithyJavaVersion")
}

// Add generated Java files to the main sourceSet
afterEvaluate {
    val serverPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-server-codegen")
    sourceSets {
        main {
            java {
                srcDir(serverPath)
            }
        }
    }
}

tasks {
    compileJava {
        dependsOn(smithyBuild)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

tasks.shadowJar {
    val shadePrefix = "software.amazon.smithy.java.internal"
    relocate("com.jsoniter", "$shadePrefix.jsoniter")
    relocate("com.fasterxml.jackson", "$shadePrefix.com.fasterxml.jackson")
    relocate("META-INF/native/libnetty", "META-INF/native/lib${shadePrefix.replace('.', '_')}_netty")
    relocate("META-INF/native/netty", "META-INF/native/${shadePrefix.replace('.', '_')}_netty")
    exclude("META-INF/maven/**")
    mergeServiceFiles()
    transform(AppendingTransformer::class.java) {
        resource = "META-INF/smithy/manifest"
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

