
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

// Extension for configuring integration test behavior.
// Projects can opt in to AWS API model tests by adding:
//   configureIntegTests { awsModelTests = true }
// Models are only resolved when the `-PawsModelTests` Gradle property is also set.
interface IntegTestExtension {
    var awsModelTests: Boolean
}

val configureIntegTests = extensions.create<IntegTestExtension>("configureIntegTests").apply {
    awsModelTests = false
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

    val deps = doc.getElementsByTagName("dependency")
    dependencies.add("itImplementation", dependencies.platform(bomCoords))

    for (i in 0 until deps.length) {
        val dep = deps.item(i) as? Element ?: continue
        val g = dep.getElementsByTagName("groupId").item(0)?.textContent ?: continue
        val a = dep.getElementsByTagName("artifactId").item(0)?.textContent ?: continue

        if (g == "software.amazon.api.models") {
            dependencies.add("itImplementation", "$g:$a")
        }
    }

    tasks.named<Test>("integ") {
        systemProperty("awsModelsTests", "true")
        maxHeapSize = "4g"
    }
}
