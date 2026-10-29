
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

configure<SourceSetContainer> {
    val main by getting
    val test by getting
    create("it") {
        compileClasspath += main.output + configurations["testRuntimeClasspath"] + configurations["testCompileClasspath"]
        runtimeClasspath += output + compileClasspath + test.runtimeClasspath + test.output
    }
}

configurations["itCompileOnly"].extendsFrom(configurations["testCompileOnly"])

// Add the integ test task
tasks.register<Test>("integ") {
    useJUnitPlatform()
    testClassesDirs = project.the<SourceSetContainer>()["it"].output.classesDirs
    classpath = project.the<SourceSetContainer>()["it"].runtimeClasspath
}

// Enable model resolution with configureIntegTests { awsModelTests = true } and -PawsModelsTests.
// The awsModels configuration contains the jars.
// Set awsModelsOnTestClasspath = false to exclude them from the it classpath.
interface IntegTestExtension {
    var awsModelTests: Boolean
    var awsModelsOnTestClasspath: Boolean
}

val awsModels by configurations.creating {
    description = "AWS API model jars pinned by the aws-api-models BOM; empty unless -PawsModelsTests is set"
    isCanBeConsumed = false
}

val configureIntegTests = extensions.create<IntegTestExtension>("configureIntegTests").apply {
    awsModelTests = false
    awsModelsOnTestClasspath = true
}

afterEvaluate {
    if (!configureIntegTests.awsModelTests || !project.hasProperty("awsModelsTests")) {
        return@afterEvaluate
    }

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val bom = libs.findLibrary("aws-api-models-bom").get().get()
    val bomCoords = "${bom.module.group}:${bom.module.name}:${bom.version}"

    val bomFile = configurations.detachedConfiguration(
        dependencies.platform(bomCoords),
        dependencies.create("$bomCoords@pom")
    ).apply {
        isTransitive = false
    }.files.single { it.extension == "pom" }

    val doc = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(bomFile)

    val onTestClasspath = configureIntegTests.awsModelsOnTestClasspath
    val deps = doc.getElementsByTagName("dependency")
    dependencies.add(awsModels.name, dependencies.platform(bomCoords))
    if (onTestClasspath) {
        dependencies.add("itImplementation", dependencies.platform(bomCoords))
    }

    for (i in 0 until deps.length) {
        val dep = deps.item(i) as? Element ?: continue
        val g = dep.getElementsByTagName("groupId").item(0)?.textContent ?: continue
        val a = dep.getElementsByTagName("artifactId").item(0)?.textContent ?: continue

        if (g == "software.amazon.api.models") {
            dependencies.add(awsModels.name, "$g:$a")
            if (onTestClasspath) {
                dependencies.add("itImplementation", "$g:$a")
            }
        }
    }

    if (onTestClasspath) {
        tasks.named<Test>("integ") {
            systemProperty("awsModelsTests", "true")
            maxHeapSize = "4g"
        }
    }
}
