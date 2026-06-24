## Examples: Standalone Types
Package that generates Java types for a model without a service.

### Prerequisites
This example depends on the `software.amazon.smithy.java.gradle.smithy-java` Gradle plugin, which must be
available in a repository declared in `settings.gradle.kts`. If building from a local checkout
before release, publish the plugin to Maven Local first:

```console
./gradlew -p gradle-plugin publishToMavenLocal
```

### Usage
To use this example as a template, run the following command with the [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/index.html):

```console
smithy init -t standalone-types --url https://github.com/smithy-lang/smithy-java
```

or 

```console
smithy init -t standalone-types --url git@github.com:smithy-lang/smithy-java.git
```
