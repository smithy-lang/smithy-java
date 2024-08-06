plugins {
    id("smithy-java.codegen-plugin-conventions")
}

description = "This module provides the codegen plugin for Smithy java client codegen"

extra["displayName"] = "Smithy :: Java :: Codegen :: Client"
extra["moduleName"] = "software.amazon.smithy.java.codegen.client"

dependencies {
    implementation(project(":client-core"))
    implementation(project(":client-http"))

    testImplementation(project(":client-aws-rest-json1"))
    testImplementation(libs.smithy.aws.traits)

    itImplementation(project(":client-aws-rest-json1"))
}

tasks {
    val generateSrcTask = addGenerateSrcsTask("software.amazon.smithy.java.codegen.client.TestServerJavaClientCodegenRunner")

    integ {
        dependsOn(generateSrcTask)
    }
    compileItJava {
        dependsOn(generateSrcTask)
    }
<<<<<<< HEAD
}

sourceSets {
    it {
        compileClasspath += sourceSets["test"].output
=======
    test {
        finalizedBy(integ)
    }
    spotbugsIt {
        enabled = false
>>>>>>> 2683dbc4 (Initial implementation of default plugins for clients.)
    }
}

sourceSets {
    it {
        // Add test plugin to classpath
        compileClasspath += sourceSets["test"].output
    }
}
