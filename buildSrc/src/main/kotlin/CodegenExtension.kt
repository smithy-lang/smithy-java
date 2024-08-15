import gradle.kotlin.dsl.accessors._6a08c79e5efb9941460fb21bc6022abc.sourceSets
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*

/**
 * Creates a task to execute building of Java classes using an executable class.
 * Generated classes can then be used by integration tests and benchmarks
 */
fun Project.addGenerateSrcsTask(className: String): TaskProvider<JavaExec> {
    val generatedSrcDir = layout.buildDirectory.dir("generated-src").get()
    return tasks.register<JavaExec>("generateSources") {
        delete(files(generatedSrcDir))
        dependsOn("test")
        classpath = sourceSets["test"].runtimeClasspath + sourceSets["test"].output + sourceSets["it"].resources.sourceDirectories
        mainClass = className
        environment("output", generatedSrcDir)
    }
}
