plugins {
    id("smithy-java.examples-conventions")
}

dependencies {
    api(project(":core"))

    itImplementation(libs.hamcrest)
}
