
// Substitute any maven module dependencies with and project dependencies
subprojects {
    group = "software.amazon.smithy.java.examples"

    configurations.all {
        resolutionStrategy.dependencySubstitution {
            rootProject.allprojects.forEach {
                substitute(module("${it.group}:${it.name}")).using(project(it.path))
            }
        }
    }
}
