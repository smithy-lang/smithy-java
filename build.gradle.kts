
import org.apache.tools.ant.taskdefs.condition.Os
import org.jreleaser.model.Active

plugins {
    base
    alias(libs.plugins.jreleaser)
    idea
}

tasks.register<Copy>("addGitHooks") {
    onlyIf("unix") {
        !Os.isFamily(Os.FAMILY_WINDOWS)
    }
    from(File(rootProject.rootDir, "git-hooks"))
    into(File(rootProject.rootDir, ".git/hooks"))
}

val smithyJavaVersion = project.file("VERSION").readText().replace(System.lineSeparator(), "")
allprojects {
    group = "software.amazon.smithy.java"
    version = smithyJavaVersion
}
println("Smithy-Java version: '${smithyJavaVersion}'")

/*
 * Jreleaser (https://jreleaser.org) config.
 */
jreleaser {
    dryrun = false

    // Used for creating a tagged release, uploading files and generating changelog.
    // In the future we can set this up to push release tags to GitHub, but for now it's
    // set up to do nothing.
    // https://jreleaser.org/guide/latest/reference/release/index.html
    release {
        generic {
            enabled = true
            skipRelease = true
        }
    }

    // Used to announce a release to configured announcers.
    // https://jreleaser.org/guide/latest/reference/announce/index.html
    announce {
        active = Active.NEVER
    }

    // Signing configuration.
    // https://jreleaser.org/guide/latest/reference/signing.html
    signing {
        active = Active.ALWAYS
        armored = true
    }

    // Configuration for deploying to Maven Central.
    // https://jreleaser.org/guide/latest/examples/maven/maven-central.html#_gradle
    deploy {
        maven {
            mavenCentral {
                create("maven-central") {
                    active = Active.ALWAYS
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository(rootProject.layout.buildDirectory.dir("staging").get().asFile.path)
                }
            }
        }
    }
}


subprojects {
    plugins.withId("java") {
        apply(plugin = "idea")
        afterEvaluate {
            val sourceSets = the<SourceSetContainer>()
            sourceSets.findByName("it")?.let {
                idea {
                    module {
                        testSources.from(sourceSets["it"].java.srcDirs)
                    }
                }
            }
        }
    }
}
