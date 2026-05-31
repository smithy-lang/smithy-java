plugins {
    id("smithy-java.module-conventions")
}

description = "Client transport using Netty for HTTP/1.1, HTTP/2, and HTTP/2 cleartext"

extra["displayName"] = "Smithy :: Java :: Client :: HTTP :: Netty"
extra["moduleName"] = "software.amazon.smithy.java.client.http.netty"

dependencies {
    api(project(":client:client-http"))
    implementation(project(":logging"))

    implementation("io.netty:netty-codec-http2:4.2.13.Final")
    implementation("io.netty:netty-codec-http:4.2.13.Final")
    implementation("io.netty:netty-handler:4.2.13.Final")
    implementation("io.netty:netty-buffer:4.2.13.Final")
    implementation("io.netty:netty-transport:4.2.13.Final")

    // netty-tcnative (BoringSSL) provides the native TLS engine used by the VT-blocking transport.
    // The base artifact carries only the Java classes; the native library ships in per-platform
    // classifier artifacts. We pull the classifiers for the platforms we build/benchmark on
    // (dev: macOS arm64/x64; benchmark + prod: Linux x64/arm64). At runtime Netty loads whichever
    // matches the host; the others are inert. tcnative is optional at runtime — the transport falls
    // back to the JDK SSLEngine when OpenSsl.isAvailable() is false.
    implementation("io.netty:netty-tcnative-boringssl-static:2.0.77.Final")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:2.0.77.Final:osx-aarch_64")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:2.0.77.Final:osx-x86_64")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:2.0.77.Final:linux-x86_64")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:2.0.77.Final:linux-aarch_64")

    testImplementation(project(":codecs:json-codec", configuration = "shadow"))
}
