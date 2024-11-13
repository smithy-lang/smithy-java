
import org.apache.tools.ant.taskdefs.condition.Os

task("addGitHooks") {
    onlyIf("unix") {
        !Os.isFamily(Os.FAMILY_WINDOWS)
    }
    exec {
        commandLine("ln", "-s", "-f", "../../git-hooks/pre-push", ".git/hooks/pre-push")
        commandLine("ln", "-s", "-f", "../../git-hooks/pre-commit", ".git/hooks/pre-commit")
    }
    println("Git hooks added")
}

val smithyJavaVersion = project.file("VERSION").readText().replace(System.lineSeparator(), "")
allprojects {
    group = "software.amazon.smithy.java"
    version = smithyJavaVersion
}
println("Smithy-Java version: '${smithyJavaVersion}'")
