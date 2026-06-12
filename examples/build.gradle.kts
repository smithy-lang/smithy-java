
subprojects {
    group = "software.amazon.smithy.java.example"

    plugins.withId("java") {
        the<JavaPluginExtension>().toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}
