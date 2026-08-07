/**
 * Code generation driven by a modeled shape closure.
 *
 * The `types` subproject generates only the event types in the closure, with no
 * service. The `server` subproject generates the service alongside those same types
 * using combined mode, and publishes events to SNS. The `client` subproject generates
 * only a client. The `consumer` subproject generates nothing and depends on `types`
 * to decode the published events.
 */

pluginManagement {
    val smithyGradleVersion: String by settings

    plugins {
        id("software.amazon.smithy.gradle.smithy-base").version(smithyGradleVersion)
    }

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "ClosureTypes"

include("types")
include("server")
include("client")
include("consumer")
