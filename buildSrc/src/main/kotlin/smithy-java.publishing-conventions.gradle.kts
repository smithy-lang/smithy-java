import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.provideDelegate

plugins {
    `maven-publish`
}

/*
 * Staging repository
 * ====================================================
 *
 * Configure publication to staging repo for jreleaser
 */
publishing {
    repositories {
        maven {
            name = "stagingRepository"
            url = uri("${rootProject.layout.buildDirectory}/staging")
        }
    }
    // Add license spec to all maven publications
    publications {
        afterEvaluate {
            create<MavenPublication>("mavenJava") {
                from(components["java"])

                val displayName: String by extra
                pom {
                    name.set(displayName)
                    description.set(project.description)
                    url.set("https://github.com/smithy-lang/smithy-java")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("smithy")
                            name.set("Smithy")
                            organization.set("Amazon Web Services")
                            organizationUrl.set("https://aws.amazon.com")
                            roles.add("developer")
                        }
                    }
                    scm {
                        url.set("https://github.com/smithy-lang/smithy-java.git")
                    }
                }
            }
        }
    }
}



