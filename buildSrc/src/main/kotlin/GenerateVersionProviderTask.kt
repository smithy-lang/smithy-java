import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task that generates a version marker resource for a module.
 *
 * Each module gets a {@code META-INF/smithy-java/versions.properties} file
 * containing the module name and version. At test time, all such files are
 * discovered via {@code ClassLoader.getResources()} to validate version consistency.
 */
abstract class GenerateVersionProviderTask : DefaultTask() {

    @get:Input
    var moduleName: String = ""

    @get:Input
    var moduleVersion: String = ""

    @get:OutputDirectory
    var outputDir: File = project.layout.buildDirectory.dir("generated/version-provider").get().asFile

    @TaskAction
    fun generate() {
        val dir = File(outputDir, "resources/META-INF/smithy-java")
        dir.mkdirs()
        File(dir, "versions.properties").writeText(
            "module=$moduleName\nversion=$moduleVersion\n"
        )
    }
}
