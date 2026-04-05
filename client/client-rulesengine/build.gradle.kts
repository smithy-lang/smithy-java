plugins {
    id("smithy-java.module-conventions")
}

description = "Client plugin that wires the rules engine endpoint resolver into the client"

extra["displayName"] = "Smithy :: Java :: Client :: Endpoint Rules"
extra["moduleName"] = "software.amazon.smithy.java.client.endpointrules"

dependencies {
    api(project(":client:client-core"))
    api(project(":rulesengine"))
    implementation(project(":logging"))

    testImplementation(project(":aws:client:aws-client-awsjson"))
    testImplementation(project(":client:dynamic-client"))
    testImplementation(project(":aws:client:aws-client-rulesengine"))
}

configureIntegTests {
    awsModelTests = true
}
