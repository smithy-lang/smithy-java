
plugins {
    id("smithy-java.java-conventions")
    id("smithy-java.integ-test-conventions")
    id("smithy-java.publishing-conventions")
    id("jacoco")
}

val smithyJavaVersion = project.file("${project.rootDir}/VERSION").readText().replace(System.lineSeparator(), "")

group = "software.amazon.smithy.java"
version = smithyJavaVersion

/*
 * Licensing
 * ============================
 */
// Reusable license copySpec
val licenseSpec = copySpec {
    from("${project.rootDir}/LICENSE")
    from("${project.rootDir}/NOTICE")
}

/*
 * Extra Jars
 * ============================
 */
java {
    withJavadocJar()
    withSourcesJar()
}

// TODO: Remove this once package is ready for docs
// Suppress warnings in javadocs
tasks.withType<Javadoc>() {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:-html", "-quiet")
}

//tasks.withType<JavaCompile> {
//    options.compilerArgs = listOf("-Xlint:unchecked")
//}

// Include an Automatic-Module-Name in all JARs.
afterEvaluate {
    val moduleName: String by extra
    tasks.withType<Jar> {
        metaInf.with(licenseSpec)
        inputs.property("moduleName", moduleName)
        manifest {
            attributes(mapOf("Automatic-Module-Name" to moduleName))
        }
    }

    // Generate a version marker resource for this module.
    val generateVersionProvider = tasks.register<GenerateVersionProviderTask>("generateVersionProvider") {
        this.moduleName = moduleName
        this.moduleVersion = smithyJavaVersion
    }
    sourceSets["main"].resources.srcDir(generateVersionProvider.map { it.outputDir.resolve("resources") })
    tasks.named("processResources") { dependsOn(generateVersionProvider) }
}

// Always run javadoc after build.
tasks["build"].dependsOn(tasks["javadoc"])

/*
 * Code coverage
 * ====================================================
 *
 * Create code coverage reports after running tests.
 */
// Always run the jacoco test report after testing.
tasks["test"].finalizedBy(tasks["jacocoTestReport"])

// Configure jacoco to generate an HTML report.
tasks.jacocoTestReport {
    reports {
        xml.required.set(false)
        csv.required.set(false)
        html.outputLocation.set(file("${layout.buildDirectory.get()}/reports/jacoco"))
    }
}

// Ensure integ tests are executed as part of test suite
tasks["test"].finalizedBy("integ")
