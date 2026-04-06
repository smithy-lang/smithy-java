plugins {
    application
    id("smithy-java.module-conventions")
    id("software.amazon.smithy.java.codegen")
}

description = "This module implements the model-bundle utility"

extra["displayName"] = "Smithy :: Java :: Model Bundle"
extra["moduleName"] = "software.amazon.smithy.java.modelbundle.api"

smithyJava {
}

dependencies {

    implementation(project(":core"))
    implementation(project(":logging"))
    implementation(libs.smithy.model)
    api(project(":client:client-auth-api"))
    api(project(":client:client-core"))
    api(project(":dynamic-schemas"))
    api(project(":server:server-api"))
    api(project(":server:server-proxy"))
}
