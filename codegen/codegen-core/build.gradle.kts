plugins {
    id("smithy-java.module-conventions")
    id("smithy-java.publishing-conventions")
}

description = "Smithy Java code generation core infrastructure"

extra["displayName"] = "Smithy :: Java :: Codegen :: Core"
extra["moduleName"] = "software.amazon.smithy.java.codegen.core"

dependencies {
    api(libs.smithy.codegen)
    implementation(project(":core"))
    implementation(project(":logging"))
    implementation(libs.jspecify)
}

// Internal source set for the types-only SmithyBuildPlugin.
// This plugin is NOT part of the published artifact — it is only used
// at build time by modules that need types-only code generation (e.g., framework-errors).
sourceSets {
    create("internal") {
        compileClasspath += sourceSets["main"].output + configurations["compileClasspath"]
    }
}

val compileInternalJava = tasks.named<JavaCompile>("compileInternalJava")
val processInternalResources = tasks.named<ProcessResources>("processInternalResources")

// Consumable configuration that includes both main classes and the internal plugin
val internalElements by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
    extendsFrom(configurations["runtimeClasspath"])
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

artifacts {
    add("internalElements", tasks.jar)
    add("internalElements", compileInternalJava.map { it.destinationDirectory.get() }) {
        builtBy(compileInternalJava)
    }
    add("internalElements", processInternalResources.map { it.destinationDir }) {
        builtBy(processInternalResources)
    }
}
