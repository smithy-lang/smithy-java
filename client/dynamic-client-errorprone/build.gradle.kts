plugins {
    id("smithy-java.module-conventions")
}

description = "Error Prone check that validates DynamicClient usage against the Smithy model at compile time"

extra["displayName"] = "Smithy :: Java :: Dynamic client Error Prone check"
extra["moduleName"] = "software.amazon.smithy.java.dynamicclient.compiler"

dependencies {
    // The Error Prone check API this BugChecker extends.
    compileOnly(libs.errorprone.check.api)
    compileOnly(libs.errorprone.annotation)

    // AutoService generates the META-INF/services/com.google.errorprone.bugpatterns.BugChecker registration.
    compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")

    // The check runs inside javac and assembles the resolved model with the same ModelAssembler the runtime uses.
    implementation(libs.smithy.model)

    testImplementation(libs.errorprone.test.helpers)
    testImplementation(libs.errorprone.core)
    testImplementation(project(":client:dynamic-client"))
}

// Error Prone's check API compiles against javac internals, which require these exports on JDK 16+.
// `--add-exports` of a system-module package is incompatible with `--release`, so this module compiles with
// plain source/target (21, matching its dependencies) instead of the `--release 21` the conventions set.
tasks.withType<JavaCompile>().configureEach {
    options.release.set(null as Int?)
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.compilerArgs.addAll(
        listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        ),
    )
}

// Javadoc also parses the compiler-internal references, so it needs the same exports.
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addMultilineStringsOption("-add-exports").value = listOf(
        "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
    )
}

// SpotBugs analyzes bytecode that references javac internals it cannot load; not meaningful for an Error Prone check.
tasks.named("spotbugsMain") { enabled = false }

// The Error Prone test helpers spin up an in-process javac that also needs the module opens/exports at runtime.
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        "--add-exports=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
    )
}
