/**
 * A subscriber, which runs no code generator. It depends on the `types` project for the
 * generated event classes, which is the point of publishing them separately.
 *
 * The project is referenced relative to the parent so this works both standalone and
 * when the example is built inside the smithy-java repository.
 */
dependencies {
    val smithyJavaVersion: String by project

    implementation(project(parent!!.path + ":types"))
    implementation("software.amazon.smithy.java:cbor-codec:$smithyJavaVersion")
}
