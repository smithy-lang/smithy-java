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
    api(project(":smithy-ai-traits"))
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
                exclude("META-INF/services/**") // Exclude original service files, use merged ones instead
            }
        }
    }
}

tasks.named("compileJava") {
    dependsOn("smithyBuild")
}

// TODO remove once we move to codegen modes instead of plugins.
val serviceFilesMerger =
    tasks.register("mergeServiceFiles") {
        dependsOn(tasks.smithyBuild)

        val outputServiceDir = layout.buildDirectory.dir("merged-services/META-INF/services")
        outputs.dir(outputServiceDir)

        val projectDir = project.projectDir

        doLast {
            // Use hardcoded paths because of https://docs.gradle.org/8.14.3/userguide/configuration_cache.html#config_cache:requirements:disallowed_types
            val sourceServiceDirs =
                listOf(
                    File(projectDir, "build/smithyprojections/mcp-schemas/source/java-type-codegen/META-INF/services"),
                    File(projectDir, "build/smithyprojections/mcp-schemas/source/java-server-codegen/META-INF/services"),
                )

            val serviceEntries = mutableMapOf<String, MutableSet<String>>()

            sourceServiceDirs.forEach { serviceDir ->
                if (serviceDir.exists() && serviceDir.isDirectory) {
                    serviceDir.listFiles()?.forEach { serviceFile ->
                        if (serviceFile.isFile) {
                            val serviceName = serviceFile.name
                            serviceEntries
                                .computeIfAbsent(serviceName) { mutableSetOf() }
                                .addAll(serviceFile.readLines().map { it.trim() })
                        }
                    }
                }
            }

            val outputDir = outputServiceDir.get().asFile
            outputDir.mkdirs()

            serviceEntries.forEach { (serviceName, lines) ->
                val serviceFile = File(outputDir, serviceName)
                serviceFile.writeText(lines.sorted().joinToString("\n") + "\n")
            }
        }
    }

// processResources will include merged service files in the main resources
tasks.processResources {
    dependsOn(serviceFilesMerger)
    from(layout.buildDirectory.dir("merged-services")) {
        into(".")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Ensure sourcesJar waits for smithyBuild to complete and includes merged service files
tasks.sourcesJar {
    dependsOn(tasks.smithyBuild, serviceFilesMerger)
    from(layout.buildDirectory.dir("merged-services"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
