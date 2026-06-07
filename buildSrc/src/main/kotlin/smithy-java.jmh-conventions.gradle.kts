import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("me.champeau.jmh")
}

val Project.libs get() = the<LibrariesForLibs>()

// Standardized JMH property namespace (all "jmh." prefix):
//   -Pjmh.fast                 Reduce iterations for quick local runs
//   -Pjmh.includes=<regex>     Filter which benchmarks to run (comma-separated)
//   -Pjmh.profilers=<spec>     ";;"-separated profiler specs (e.g. "gc;;async:output=flamegraph;dir=build")
//   -Pjmh.warmupIterations=N   Override warmup iteration count
//   -Pjmh.iterations=N         Override measurement iteration count
//   -Pjmh.fork=N               Override fork count
val fast = providers.gradleProperty("jmh.fast").isPresent

jmh {
    jmhVersion = libs.versions.jmhCore.get()
    benchmarkMode.addAll("avgt")
    timeUnit = "ns"
    fork = providers.gradleProperty("jmh.fork").orElse("1").get().toInt()

    warmupIterations =
        if (fast) 1
        else providers.gradleProperty("jmh.warmupIterations").orElse("3").get().toInt()
    warmup = if (fast) "1s" else "2s"
    iterations =
        if (fast) 2
        else providers.gradleProperty("jmh.iterations").orElse("5").get().toInt()
    timeOnIteration = if (fast) "3s" else "5s"

    includes.addAll(
        providers.gradleProperty("jmh.includes")
            .map { it.split(",").filter { s -> s.isNotBlank() } }
            .orElse(emptyList()),
    )

    profilers.addAll(
        providers.gradleProperty("jmh.profilers")
            .map { it.split(";;").filter { s -> s.isNotBlank() } }
            .orElse(emptyList()),
    )

    forceGC = true

    jvmArgs.addAll(
        providers.gradleProperty("jmh.jvmArgs")
            .map { it.split(" ").filter { s -> s.isNotBlank() } }
            .orElse(emptyList()),
    )

    duplicateClassesStrategy = DuplicatesStrategy.EXCLUDE
}
