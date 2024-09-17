
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
