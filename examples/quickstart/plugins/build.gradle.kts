description = "Defines plugins to configure generated clients"

plugins {
    `java-library`
}

dependencies {
    implementation(libs.smithy.client.core)
}
