import java.net.Socket

plugins {
    java
    id("me.champeau.jmh") version "0.7.3"
}

repositories {
    mavenLocal()
    mavenCentral()
}

sourceSets {
    create("jmhServer") {
        java.srcDir("src/jmhServer/java")
    }
}

val jmhServerImplementation by configurations.getting

dependencies {
    jmh(project(":client:client-http"))

    jmhServerImplementation("io.netty:netty-all:4.2.7.Final")
    jmhServerImplementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
}

val benchmarkH2cPort = 18081
val benchmarkPidFile = layout.buildDirectory.file("benchmark-server.pid")
val jmhServerClasspath = sourceSets["jmhServer"].runtimeClasspath

val startBenchmarkServer by tasks.registering {
    dependsOn("jmhServerClasses")
    notCompatibleWithConfigurationCache("Starts external process")

    doLast {
        val pidFile = benchmarkPidFile.get().asFile
        pidFile.parentFile.mkdirs()

        val process =
            ProcessBuilder(
                "java",
                "-cp",
                jmhServerClasspath.asPath,
                "software.amazon.smithy.java.http.client.BenchmarkServer",
            ).inheritIO().start()

        pidFile.writeText(process.pid().toString())

        var attempts = 0
        var ready = false
        while (!ready && attempts < 50) {
            Thread.sleep(100)
            attempts++
            if (!process.isAlive) {
                pidFile.delete()
                throw GradleException("Benchmark server process exited before becoming ready")
            }
            try {
                Socket("localhost", benchmarkH2cPort).close()
                ready = true
            } catch (_: Exception) {
                // Server not ready yet.
            }
        }

        if (!ready) {
            process.destroyForcibly()
            throw GradleException("Benchmark server failed to start (not ready after 5s)")
        }
    }
}

val stopBenchmarkServer by tasks.registering {
    notCompatibleWithConfigurationCache("Stops external process")

    doLast {
        val pidFile = benchmarkPidFile.get().asFile
        if (pidFile.exists()) {
            val pid = pidFile.readText().trim().toLong()
            try {
                ProcessHandle.of(pid).ifPresent { handle -> handle.destroy() }
            } catch (_: Exception) {
                // Best effort cleanup.
            }
            pidFile.delete()
        }
    }
}

jmh {
    val includesProp = project.findProperty("jmh.includes")?.toString()
    val jvmArgsProp = project.findProperty("jmh.jvmArgsAppend")?.toString()
    val profilersProp = project.findProperty("jmh.profilers")?.toString()
    val defaultJvmArgs = listOf("-Djdk.httpclient.allowRestrictedHeaders=host")

    includes = if (includesProp != null) listOf(includesProp) else listOf(".*")
    warmupIterations = 3
    iterations = 3
    fork = 1
    resultFormat = "CSV"
    resultsFile = project.file("build/reports/jmh/results.csv")

    if (jvmArgsProp != null) {
        jvmArgsAppend = defaultJvmArgs + jvmArgsProp.split(Regex("\\s*;\\s*")).filter { it.isNotEmpty() }
    } else {
        jvmArgsAppend = defaultJvmArgs
    }
    if (profilersProp != null) {
        val profilerSpecs =
            if (profilersProp.contains(";;")) {
                profilersProp.split(Regex("\\s*;;\\s*")).filter { it.isNotEmpty() }
            } else {
                listOf(profilersProp)
            }
        profilers.addAll(profilerSpecs)
    }
}

tasks.named("jmh") {
    dependsOn(startBenchmarkServer)
    finalizedBy(stopBenchmarkServer)
}
