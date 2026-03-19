plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-base")
}

dependencies {
    val smithyJavaVersion: String by project

    smithyBuild("software.amazon.smithy.java:codegen-plugin:$smithyJavaVersion")
    smithyBuild("software.amazon.smithy.java:client-core:$smithyJavaVersion")

    implementation("software.amazon.api.models:transcribe-streaming:1.0.7")
    implementation("software.amazon.smithy.java:aws-client-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:client-core:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-sigv4:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:client-rulesengine:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:aws-client-rulesengine:$smithyJavaVersion")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation(libs.smithy.aws.endpoints)
    implementation(libs.smithy.aws.smoke.test.model)
    implementation(libs.smithy.aws.traits)

    // Test dependencies
    testImplementation(project(":aws:sdkv2:aws-sdkv2-auth"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Add generated Java sources to the main sourceset
afterEvaluate {
    val clientPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets {
        main {
            java {
                srcDir("$clientPath/java")
            }
            resources {
                srcDir("$clientPath/resources")
            }
        }
        create("it") {
            compileClasspath += main.get().output + configurations["testRuntimeClasspath"] + configurations["testCompileClasspath"]
            runtimeClasspath += output + compileClasspath + test.get().runtimeClasspath + test.get().output
        }
    }
}

tasks {
    val smithyBuild by getting
    compileJava {
        dependsOn(smithyBuild)
    }
    processResources {
        dependsOn(smithyBuild)
    }
    val integ by registering(Test::class) {
        useJUnitPlatform()
        testClassesDirs = sourceSets["it"].output.classesDirs
        classpath = sourceSets["it"].runtimeClasspath
    }
}


repositories {
    mavenLocal()
    mavenCentral()
}
