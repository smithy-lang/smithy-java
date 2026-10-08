plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.jmh-conventions")
    id("smithy-java.protocol-testing-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "This module provides the implementation of AWS Query and EC2 Query protocols"

extra["displayName"] = "Smithy :: Java :: AWS :: Client :: AWS Query"
extra["moduleName"] = "software.amazon.smithy.java.aws.client.awsquery"

dependencies {
    api(project(":client:client-http"))
    api(project(":codecs:xml-codec"))
    api(project(":codecs:codec-commons", configuration = "shadow"))
    api(project(":io"))
    api(libs.smithy.aws.traits)

    // Protocol test dependencies
    testImplementation(libs.smithy.aws.protocol.tests)

    // Shapes under model/ are generated into the test source set so the differential tests can
    // compare the generated and interpreted serializers over real shape classes.
    smithyBuild(project(":codegen:codegen-plugin"))
}

protocolTestRuns {
    run("native") { systemProperty("smithy-java.xml-provider", "smithy") }
    run("stax") { }
    // Runs the same protocol suites through generated serializers. Strict mode turns an emitter bug
    // into a failure instead of a silent fallback, without which a green run would prove nothing.
    //
    // awsQuery writes requests with the Query serializer and reads responses as XML, so enabling both
    // backends covers the whole round trip: "awsquery" the write side, "xml" the read side. The XML
    // reader gets exercised here without the HTTP-binding request path in the way.
    run("codegen") {
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(25)
            }
        systemProperty("smithy-java.xml-provider", "smithy")
        systemProperty("smithy-java.runtime-codegen.awsquery", "strict")
        systemProperty("smithy-java.runtime-codegen.xml", "strict")
        systemProperty("smithy-java.runtime-codegen.http-binding", "strict")
    }
}

val generator = "software.amazon.smithy.java.protocoltests.generators.ProtocolTestGenerator"
addGenerateSrcsTask(generator, "awsQuery", "aws.protocoltests.query#AwsQuery")
addGenerateSrcsTask(generator, "ec2Query", "aws.protocoltests.ec2#AwsEc2")

afterEvaluate {
    val typePath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets.named("test") {
        java {
            srcDir("$typePath/java")
        }
        resources {
            srcDir("$typePath/resources")
        }
    }
}

tasks.named("compileTestJava") {
    dependsOn("smithyBuild")
}

tasks.named("processTestResources") {
    dependsOn("smithyBuild")
}

val jdk25CodegenTest =
    tasks.register<Test>("jdk25CodegenTest") {
        description = "Run the Query test suite with runtime code generation enabled."
        group = "verification"

        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        javaLauncher =
            javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(25)
            }
        systemProperty("smithy-java.runtime-codegen.awsquery", "strict")
        systemProperty("smithy-java.xml-provider", "smithy")
        useJUnitPlatform()
    }

tasks.named("check") {
    dependsOn(jdk25CodegenTest)
}
