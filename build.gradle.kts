
import org.apache.tools.ant.taskdefs.condition.Os

task("addPreCommitHooks") {
    onlyIf("unix") {
        !Os.isFamily(Os.FAMILY_WINDOWS)
    }
    exec {
        commandLine("ln", "-s", "-f", "../../git-hooks/pre-commit", ".git/hooks/pre-commit")
    }
    println("Pre-commit hooks added")
}
