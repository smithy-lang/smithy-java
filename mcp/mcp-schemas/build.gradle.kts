plugins {
    id("smithy-java.module-conventions")
    id("software.amazon.smithy.gradle.smithy-base")
}

description = "This module provides a schemas for MCP integration"

extra["displayName"] = "Smithy :: Java :: MCP Schemas"
extra["moduleName"] = "software.amazon.smithy.mcp.schemas"

dependencies {
    api(project(":core"))
    api(libs.smithy.model)
    api(project(":server:server-api"))
    smithyBuild(project(":codegen:plugins:types-codegen"))
    smithyBuild(project(":codegen:plugins:server-codegen"))
}

sourceSets {
    main {
        java {
            srcDir("model")
        }
    }
}

afterEvaluate {
    val typePath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-type-codegen")
    val serverPath = smithy.getPluginProjectionPath(smithy.sourceProjection.get(), "java-server-codegen")
    sourceSets {
        main {
            java {
                srcDir(typePath)
                srcDir(serverPath)
                include("software/**")
            }
            resources {
                srcDir(typePath)
                srcDir(serverPath)
                include("META-INF/**")
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

// Needed because sources-jar needs to run after smithy-build is done
tasks.sourcesJar {
    mustRunAfter(tasks.compileJava)
    configureServiceFileMerging()
}

tasks.processResources {
    dependsOn(tasks.compileJava)
    configureServiceFileMerging()
}

interface Injected {
    @get:Inject val fs: FileSystemOperations
}

fun AbstractCopyTask.configureServiceFileMerging() {
    val serviceEntries = mutableMapOf<String, MutableSet<String>>()
    val tempServicesDir = temporaryDir // Capture at configuration time

    // Configure Jar tasks to include temp directory at configuration time
    if (this is Jar) {
        from(tempServicesDir)
    }

    eachFile {
        if (path.startsWith("META-INF/services/")) {
            val serviceName = path.substring("META-INF/services/".length)

            if (!serviceEntries.containsKey(serviceName)) {
                serviceEntries[serviceName] = mutableListOf()
            }

            serviceEntries[serviceName]!!.addAll(
                file
                    .readLines()
                    .filter { it.trim().isNotEmpty() }
                    .map { it.trim() },
            )

            exclude()
        }
    }

    doLast {
        val outputDir =
            when (this) {
                is ProcessResources -> this.destinationDir
                else -> tempServicesDir
            }

        val servicesDir = File(outputDir, "META-INF/services")
        servicesDir.mkdirs()

        serviceEntries.forEach { (serviceName, lines) ->
            val serviceFile = File(servicesDir, serviceName)
            serviceFile.writeText(lines.joinToString("\n") + "\n")
        }
    }
}
