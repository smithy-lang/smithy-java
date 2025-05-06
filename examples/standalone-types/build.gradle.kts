
plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-base")
}

dependencies {
    val smithyJavaVersion: String by project

    smithyBuild("software.amazon.smithy.java:plugins:$smithyJavaVersion")
    api("software.amazon.smithy.java:core:$smithyJavaVersion")
    api("software.amazon.smithy.java:framework-errors:$smithyJavaVersion")

    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

afterEvaluate {
    val typePath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-type-codegen")
    sourceSets {
        main {
            java {
                srcDir(typePath)
                include("software/**")
            }
            resources {
                srcDir(typePath)
                include("META-INF/**")
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
