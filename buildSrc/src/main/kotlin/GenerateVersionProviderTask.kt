import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task that generates a SmithyVersionProvider SPI implementation for a module.
 *
 * The implementation is generated into the module's own package to avoid classpath
 * conflicts when multiple modules are on the classpath.
 */
abstract class GenerateVersionProviderTask : DefaultTask() {

    @get:Input
    var moduleName: String = ""

    @get:Input
    var moduleVersion: String = ""

    @get:OutputDirectory
    var outputDir: File = project.layout.buildDirectory.dir("generated/version-provider").get().asFile

    companion object {
        private const val SPI_PACKAGE = "software.amazon.smithy.java.versionspi"
        private const val INTERFACE_NAME = "SmithyVersionProvider"
        private const val RECORD_NAME = "ModuleVersion"
        private const val IMPL_NAME = "GeneratedVersionProvider"
    }

    @TaskAction
    fun generate() {
        val parts = moduleVersion.split(".")
        val major = parts.getOrElse(0) { "0" }
        val minor = parts.getOrElse(1) { "0" }
        val patch = parts.getOrElse(2) { "0" }.replace(Regex("[^0-9].*"), "")

        // Use the module's own package for the implementation class
        val implPackage = moduleName
        val packageDir = File(outputDir, "java/${implPackage.replace('.', '/')}")
        packageDir.mkdirs()

        val fqInterface = "$SPI_PACKAGE.$INTERFACE_NAME"
        val fqRecord = "$SPI_PACKAGE.$RECORD_NAME"
        val fqImpl = "$implPackage.$IMPL_NAME"

        // Generate the implementation in the module's own package
        File(packageDir, "$IMPL_NAME.java").writeText(
            """
            |package $implPackage;
            |
            |import $fqInterface;
            |import $fqRecord;
            |
            |public final class $IMPL_NAME implements $INTERFACE_NAME {
            |    private static final $RECORD_NAME VERSION = new $RECORD_NAME("$moduleName", $major, $minor, $patch);
            |
            |    @Override
            |    public $RECORD_NAME getModuleVersion() {
            |        return VERSION;
            |    }
            |}
            """.trimMargin()
        )

        // Generate META-INF/services file
        val servicesDir = File(outputDir, "resources/META-INF/services")
        servicesDir.mkdirs()
        File(servicesDir, fqInterface).writeText(
            "$fqImpl\n"
        )
    }
}
