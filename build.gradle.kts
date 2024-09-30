
import org.apache.tools.ant.taskdefs.condition.Os

task("addPrePushHooks") {
    onlyIf("unix") {
        !Os.isFamily(Os.FAMILY_WINDOWS)
    }
    exec {
        commandLine("ln", "-s", "-f", "../../git-hooks/pre-push", ".git/hooks/pre-push")
    }
    println("Pre-push hooks added")
}

val smithyJavaVersion = project.file("VERSION").readText().replace(System.lineSeparator(), "")
allprojects {
    group = "software.amazon.smithy.java"
    version = smithyJavaVersion
}
println("Smithy-Java version: '${smithyJavaVersion}'")
