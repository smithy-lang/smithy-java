import com.github.spotbugs.snom.Effort
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    `java-library`
    id("com.adarshr.test-logger")
    id("com.github.spotbugs")
    id("com.diffplug.spotless")
    id("com.autonomousapps.dependency-analysis")
    id("smithy-java.utilities")
}

// Workaround per: https://github.com/gradle/gradle/issues/15383
val Project.libs get() = the<LibrariesForLibs>()

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

/*
 * Common test configuration
 * ===============================
 */
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.hamcrest)
    testImplementation(libs.assertj.core)
    compileOnly("com.github.spotbugs:spotbugs-annotations:${spotbugs.toolVersion.get()}")
    testCompileOnly("com.github.spotbugs:spotbugs-annotations:${spotbugs.toolVersion.get()}")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

testlogger {
    showExceptions = true
    showStackTraces = true
    showFullStackTraces = false
    showCauses = true
    showSummary = false
    showPassed = false
    showSkipped = false
    showFailed = true
    showOnlySlow = false
    showStandardStreams = true
    showPassedStandardStreams = false
    showSkippedStandardStreams = false
    showFailedStandardStreams = true
    logLevel = LogLevel.WARN
}

/*
 * Formatting
 * ==================
 * see: https://github.com/diffplug/spotless/blob/main/plugin-gradle/README.md#java
 */
spotless {
    java {
        // Enforce a common license header on all files
        licenseHeaderFile("${project.rootDir}/config/spotless/license-header.txt")
            .onlyIfContentMatches("^((?!SKIPLICENSECHECK)[\\s\\S])*\$")
        leadingTabsToSpaces()
        endWithNewline()

        eclipse().configFile("${project.rootDir}/config/spotless/formatting.xml")

        // Static first, then everything else alphabetically
        removeUnusedImports()
        importOrder("\\#", "")
        // Ignore generated generated code for formatter check
        targetExclude("**/build/**/*.java", "**/build/generated-src*/*.*")
    }

    // Formatting for build.gradle.kts files
    kotlinGradle {
        ktlint()
        leadingTabsToSpaces()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

/*
 * Spotbugs
 * ====================================================
 *
 * Run spotbugs against source files and configure suppressions.
 */
// Configure the spotbugs extension.
spotbugs {
    effort = Effort.MAX
    excludeFilter = file("${project.rootDir}/config/spotbugs/filter.xml")
}

// Disable spotbugs tasks to avoid build failures with incompatible JDK versions.
tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    enabled = false
}


// We don't need to lint tests.
tasks.named("spotbugsTest") {
    enabled = false
}

/*
 * Repositories
 * ================================
 */
repositories {
    mavenLocal()
    mavenCentral()
}
