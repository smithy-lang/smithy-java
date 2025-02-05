## Example: End-to-end
This example generates both a client and a server, sharing common types.

The integration tests in this project run the generated client against the server.

### Usage
To use this example as a template, run the following command with the [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/index.html):

```console
smithy init -t end-to-end --url https://github.com/smithy-lang/smithy-java
```

or

```console
smithy init -t end-to-end --url git@github.com:smithy-lang/smithy-java.git
```

To run the server, run the following from the root of the project:

```console 
gradle run
```

This will start the server running on port `8888`.

The integration tests for this project test the generated client against the 
server. To run these tests:

```console
gradle integ
```

**Note**: The integration tests start the server automatically.
