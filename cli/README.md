## smithy-call

This module contains the base-version of smithy-call: a CLI that uses ahead-of-time compilation and the dynamic client to make adhoc calls to services.


The functionality provided by this CLI includes:
1. Execute operations listed in a service model
2. List operations listed in a service model
3. SigV4 authentication
4. Multi-protocol support

### Example Call
1. Build the native binary for smithy-call: `./gradlew :cli:nativeCompile`
2. Start-up Cafe service from the end-to-end example: `./gradlew :examples:end-to-end:run`
3. Check the available operations: `./cli/build/native/nativeCompile/smithy-call com.example#CoffeeShop --list-operations -m /Users/fluu/workplace/smithy-java-cli/examples/end-to-end/model`
4. Send a test call to our Cafe service: `./cli/build/native/nativeCompile/smithy-call com.example#CoffeeShop GetMenu -m /Users/fluu/workplace/smithy-java-cli/examples/end-to-end/model --url http://localhost:8888`

