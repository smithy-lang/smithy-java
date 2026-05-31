plugins {
    id("smithy-java.module-conventions")
}

description = "BoringSSL (netty-tcnative) SSLEngine provider for the Smithy native HTTP client"

extra["displayName"] = "Smithy :: Java :: Client :: HTTP :: BoringSSL"
extra["moduleName"] = "software.amazon.smithy.java.client.http.boringssl"

dependencies {
    // The netty-free TLS seam (ClientSslEngineFactory) lives in http-client; this module is the only
    // place io.netty/tcnative types are allowed, keeping the rest of the HTTP stack provider-agnostic.
    api(project(":http:http-client"))
    implementation(project(":logging"))

    implementation("io.netty:netty-handler:4.2.13.Final")
    implementation("io.netty:netty-buffer:4.2.13.Final")

    // netty-tcnative (BoringSSL): base jar carries the Java classes; the native library ships in
    // per-platform classifier artifacts. We pull the classifiers for the platforms we build/benchmark
    // on (dev: macOS arm64/x64; benchmark + prod: Linux x64/arm64). At runtime Netty loads whichever
    // matches the host; the others are inert. tcnative is optional — BoringSslEngineFactory.isAvailable()
    // reports false when the native lib is absent, and callers fall back to the JDK provider.
    implementation("io.netty:netty-tcnative-boringssl-static:2.0.77.Final")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:2.0.77.Final:osx-aarch_64")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:2.0.77.Final:osx-x86_64")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:2.0.77.Final:linux-x86_64")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:2.0.77.Final:linux-aarch_64")

    testImplementation(project(":codecs:json-codec", configuration = "shadow"))
}
