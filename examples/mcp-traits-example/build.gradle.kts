plugins {
    `java-library`
    id("software.amazon.smithy.gradle.smithy-base")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    val smithyJavaVersion: String by project
    val smithyVersion: String by project

    // Include the mcp-traits module for the @prompts trait
    implementation(project(":smithy-ai-traits"))
    
    // Standard Smithy dependencies
    smithyBuild("software.amazon.smithy.java:plugins:$smithyJavaVersion")
    implementation("software.amazon.smithy:smithy-model:$smithyVersion")
}

// Helps IDE's discover smithy models
sourceSets {
    main {
        java {
            srcDir("model")
        }
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}
