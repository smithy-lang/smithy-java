import java.io.File
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*

// Tracks resource directories per project for the merge task
private val projectResourceDirs = mutableMapOf<String, MutableList<String>>()

/**
 * Creates a task to execute building of Java classes using an executable class.
 * Generated classes can then be used by integration tests and benchmarks
 */
fun Project.addGenerateSrcsTask(
    className: String,
    name: String?,
    service: String?,
    mode: String = "client"
): TaskProvider<JavaExec> {
    var taskName = "generateSources"
    var generatedDir = "generated-src"
    if (name != null) {
        taskName += name
        generatedDir = "$generatedDir-$name"
    }
    val taskOutput = layout.buildDirectory.dir(generatedDir).get()
    val sourceSets = project.the<SourceSetContainer>()
    sourceSets.named("it") {
        java.srcDir("${taskOutput}/java")
    }
    val itResources = project.file("src/it/resources")
    val task = tasks.register<JavaExec>(taskName) {
        dependsOn("test")
        classpath = sourceSets["test"].runtimeClasspath + sourceSets["test"].output + files(itResources)
        mainClass = className
        doFirst { taskOutput.asFile.deleteRecursively() }
        environment("output", taskOutput)
        service?.let { environment("service", it) }
        environment("mode", mode)
        systemProperty("java.util.logging.config.file", "${project.rootDir}/config/logging/logging.properties")
        outputs.dir(taskOutput)
    }
    tasks.getByName("integ").dependsOn(task)
    tasks.getByName("compileItJava").dependsOn(task)

    // Track resource dir for this task
    val resourceDirs = projectResourceDirs.getOrPut(project.path) { mutableListOf() }
    resourceDirs.add("${taskOutput}/resources")

    // Register a merge task for resource files if not already registered.
    // This merges META-INF/services entries from multiple codegen tasks
    // and copies other resource files (e.g. .bdd files) to a single output directory.
    val mergeTaskName = "mergeItServiceFiles"
    val mergeTask = if (tasks.names.contains(mergeTaskName)) {
        tasks.named(mergeTaskName)
    } else {
        val mergedDir = layout.buildDirectory.dir("merged-it-services")
        sourceSets.named("it") {
            resources.srcDir(mergedDir)
        }
        val dirs = resourceDirs
        tasks.register(mergeTaskName) {
            outputs.dir(mergedDir)
            doLast {
                val outputDir = mergedDir.get().asFile
                outputDir.deleteRecursively()
                val serviceEntries = mutableMapOf<String, MutableSet<String>>()
                dirs.forEach { dirPath ->
                    val dir = File(dirPath)
                    if (dir.exists() && dir.isDirectory) {
                        dir.walkTopDown().filter { it.isFile }.forEach { file ->
                            val relativePath = file.relativeTo(dir).path
                            if (relativePath.startsWith("META-INF/services/")) {
                                serviceEntries
                                    .computeIfAbsent(relativePath) { mutableSetOf() }
                                    .addAll(file.readLines().map { it.trim() }.filter { it.isNotEmpty() })
                            } else {
                                val outFile = File(outputDir, relativePath)
                                outFile.parentFile.mkdirs()
                                file.copyTo(outFile, overwrite = true)
                            }
                        }
                    }
                }
                serviceEntries.forEach { (relativePath, lines) ->
                    val outFile = File(outputDir, relativePath)
                    outFile.parentFile.mkdirs()
                    outFile.writeText(lines.sorted().joinToString("\n") + "\n")
                }
            }
        }
    }
    mergeTask.configure {
        dependsOn(task)
        inputs.dir("${taskOutput}/resources")
    }
    tasks.getByName("processItResources").dependsOn(mergeTask)

    return task
}

fun Project.addGenerateSrcsTask(className: String): TaskProvider<JavaExec> {
    return addGenerateSrcsTask(className, null, null)
}
