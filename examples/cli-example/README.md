## Smithy Java CLI Example
Generates a CLI that works with the End-To-End example.

To build the native binary for the CLI run:

```console 
./gradlew :examples:cli-example:nativeCompile
```

Then, in a separate terminal start the [end-to-end](../end-to-end) example server.

```console
./gradlew :examples:end-to-end:run
```

Now, check the generated CLI works: 

```console
./examples/cli-example/build/native/nativeCompile/cafe --help
```

And then use the generated CLI to call our Cafe service: 

```console

```
