plugins {
    `java-library`
    id("io.quarkus") version "3.35.3"
}

// This example is a standalone Gradle build (it is *not* included in
// smithy-java's root settings.gradle.kts). All smithy-java dependencies
// are resolved from mavenLocal, the same way a real customer would
// consume them. To rebuild after changing smithy-java sources, run
// `gradle publishToMavenLocal` from the smithy-java root first.
repositories {
    mavenLocal()
    mavenCentral()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project
val smithyJavaVersion: String by project

dependencies {
    // The quarkus-smithy extension. Brings in:
    //   - SmithyVertxRecorder (mounts services on Quarkus's HTTP router)
    //   - the deployment-time CodeGenProvider that runs Smithy code generation
    //     during quarkusGenerateCode (no smithy-base Gradle plugin needed)
    //   - quarkus-vertx-http transitively (Smithy operations share the
    //     Quarkus HTTP server's port, per ADR-0003)
    //   - the upstream :server:server-vertx module
    implementation("software.amazon.smithy.java:quarkus-smithy:$smithyJavaVersion")

    // Quarkus runtime
    implementation(enforcedPlatform("$quarkusPlatformGroupId:$quarkusPlatformArtifactId:$quarkusPlatformVersion"))
    implementation("io.quarkus:quarkus-arc")

    // Server-side protocol implementations. The bridge looks for
    // ServerProtocol providers via ServiceLoader; the user adds
    // whichever protocol jar(s) their .smithy services declare. The
    // CoffeeShop service in this example declares all three:
    // @restJson1 + @rpcv2Cbor + @rpcv2Json. Each request resolves to
    // exactly one protocol per the precision-ordered list (rpcv2Cbor
    // first, then rpcv2Json, then restJson1).
    implementation("software.amazon.smithy.java:aws-server-restjson:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:server-rpcv2-cbor:$smithyJavaVersion")
    implementation("software.amazon.smithy.java:server-rpcv2-json:$smithyJavaVersion")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

// .smithy files live under src/main/smithy/ — Quarkus's CodeGenProvider
// finds them automatically and IntelliJ's Gradle import surfaces them
// without extra source-set wiring.
