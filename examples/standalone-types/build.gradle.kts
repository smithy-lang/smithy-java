
plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-base")
}

dependencies {
    val smithyJavaVersion: String by project

    smithyBuild("software.amazon.smithy.java:codegen-plugin:$smithyJavaVersion")
    api("software.amazon.smithy.java:core:$smithyJavaVersion")
    api("software.amazon.smithy.java:framework-errors:$smithyJavaVersion")

    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

afterEvaluate {
    val typesPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-codegen").get()
    sourceSets {
        main {
            java {
                srcDir("$typesPath/java")
            }
            resources {
                srcDir("$typesPath/resources")
            }
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
    withType<Test> {
        useJUnitPlatform()
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}
