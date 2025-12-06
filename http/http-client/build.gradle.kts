import java.net.Socket
import java.util.Properties

plugins {
    id("smithy-java.module-conventions")
    id("me.champeau.jmh") version "0.7.3"
}

description = "Smithy's generic blocking HTTP client with bidirectional streaming"

extra["displayName"] = "Smithy :: Java :: HTTP :: Client"
extra["moduleName"] = "software.amazon.smithy.java.http.client"

// Separate source set for benchmark server (runs in separate process for clean flame graphs)
sourceSets {
    create("jmhServer") {
        java.srcDir("src/jmhServer/java")
    }
}

val jmhServerImplementation by configurations.getting

dependencies {
    api(project(":http:http-api"))
    api(project(":context"))
    api(project(":logging"))

    // Netty for HTTP/2 integration tests
    testImplementation("io.netty:netty-all:4.2.7.Final")
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    // Jackson for HPACK test suite JSON parsing
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    // Add Apache HttpClient for benchmarking comparison
    jmh("org.apache.httpcomponents.client5:httpclient5:5.3.1")

    // Helidon WebClient for benchmarking comparison
    jmh("io.helidon.webclient:helidon-webclient:4.1.6")
    jmh("io.helidon.webclient:helidon-webclient-http2:4.1.6")

    // Netty for raw HTTP/2 benchmarking
    jmh("io.netty:netty-all:4.2.7.Final")

    // Benchmark server dependencies (Netty runs in separate process)
    jmhServerImplementation("io.netty:netty-all:4.2.7.Final")
    jmhServerImplementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}

// Fixed ports for benchmark server (matches BenchmarkServer.java defaults)
val benchmarkH1Port = 18080
val benchmarkH2Port = 18443
val benchmarkH2cPort = 18081

val benchmarkPidFile = layout.buildDirectory.file("benchmark-server.pid")

// Capture classpath at configuration time for config cache compatibility
val jmhServerClasspath = sourceSets["jmhServer"].runtimeClasspath

// Task to start the benchmark server in a background process
val startBenchmarkServer by tasks.registering {
    dependsOn("jmhServerClasses")
    notCompatibleWithConfigurationCache("Starts external process")

    doLast {
        val pidFile = benchmarkPidFile.get().asFile
        pidFile.parentFile.mkdirs()

        // Build classpath for server
        val serverClasspath = jmhServerClasspath.asPath

        val processBuilder =
            ProcessBuilder(
                "java",
                "-cp",
                serverClasspath,
                "software.amazon.smithy.java.http.client.BenchmarkServer",
            )
        processBuilder.inheritIO()

        val process = processBuilder.start()

        // Store PID for later cleanup
        pidFile.writeText(process.pid().toString())

        // Wait for server to be ready (try connecting)
        var attempts = 0
        var ready = false
        while (!ready && attempts < 50) {
            Thread.sleep(100)
            attempts++
            try {
                Socket("localhost", benchmarkH2cPort).close()
                ready = true
            } catch (e: Exception) {
                // Server not ready yet
            }
        }

        if (!ready) {
            process.destroyForcibly()
            throw GradleException("Benchmark server failed to start (not ready after 5s)")
        }

        println("Benchmark server started (PID: ${process.pid()})")
        println("  H1:  http://localhost:$benchmarkH1Port")
        println("  H2:  https://localhost:$benchmarkH2Port")
        println("  H2C: http://localhost:$benchmarkH2cPort")
    }
}

// Task to stop the benchmark server
val stopBenchmarkServer by tasks.registering {
    notCompatibleWithConfigurationCache("Stops external process")

    doLast {
        val pidFile = benchmarkPidFile.get().asFile
        if (pidFile.exists()) {
            val pid = pidFile.readText().trim().toLong()
            try {
                ProcessHandle.of(pid).ifPresent { handle ->
                    handle.destroy()
                    println("Stopped benchmark server (PID: $pid)")
                }
            } catch (e: Exception) {
                println("Warning: Could not stop server: ${e.message}")
            }
            pidFile.delete()
        }
    }
}

// Configure JMH
jmh {
    includes = listOf(".*nettyH2c.*")

    warmupIterations = 2
    iterations = 3
    fork = 1
    profilers.add("async:output=flamegraph")
    // profilers.add("gc")
}

// Task that runs JMH with external server (starts server, runs jmh, stops server)
val jmhWithServer by tasks.registering(Exec::class) {
    dependsOn(startBenchmarkServer)
    finalizedBy(stopBenchmarkServer)
    notCompatibleWithConfigurationCache("Runs JMH with external server")

    workingDir = project.rootDir
    commandLine(
        "./gradlew",
        ":http:http-client:jmh",
    )
}
