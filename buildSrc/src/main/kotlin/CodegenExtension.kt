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
    val output = layout.buildDirectory.dir("generated-src").get()
    var taskName = "generateSources"
    if (name != null) {
        taskName += name
    }
    val sourceSets = project.the<SourceSetContainer>()
    val task = tasks.register<JavaExec>(taskName) {
        delete(files(output))
        dependsOn("test")
        classpath = sourceSets["test"].runtimeClasspath + sourceSets["test"].output + sourceSets["it"].resources.sourceDirectories
        mainClass = className
        environment("output", output)
        service?.let { environment("service", it) }
        environment("mode", mode)
        systemProperty("java.util.logging.config.file", "${project.rootDir}/config/logging/logging.properties")
    }
    tasks.getByName("integ").dependsOn(task)
    tasks.getByName("compileItJava").dependsOn(task)
    return task
}

fun Project.addGenerateSrcsTask(className: String): TaskProvider<JavaExec> {
    return addGenerateSrcsTask(className, null, null)
}
