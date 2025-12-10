import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*

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
        java.srcDir(taskOutput)
    }
    val task = tasks.register<JavaExec>(taskName) {
        dependsOn("test")
        classpath = sourceSets["test"].runtimeClasspath + sourceSets["test"].output + sourceSets["it"].resources.sourceDirectories
        mainClass = className
        environment("output", taskOutput)
        service?.let { environment("service", it) }
        environment("mode", mode)
        systemProperty("java.util.logging.config.file", "${project.rootDir}/config/logging/logging.properties")
        outputs.dir(taskOutput)
    }
    tasks.getByName("integ").dependsOn(task)
    tasks.getByName("compileItJava").dependsOn(task)
    return task
}

fun Project.addGenerateSrcsTask(className: String): TaskProvider<JavaExec> {
    return addGenerateSrcsTask(className, null, null)
}
