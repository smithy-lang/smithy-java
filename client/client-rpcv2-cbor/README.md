## client-rpcv2-cbor
Client implementation of the [rpcv2-cbor](https://smithy.io/2.0/additional-specs/protocols/smithy-rpc-v2.html#smithy-rpc-v2-cbor-protocol) protocol.

### Usage
To use this protocol as the default in a generated client, first add the `@rpcv2Cbor` trait
to your service.

```diff
$version: "2"

namespace smithy.example

+ use smithy.protocols#rpcv2Cbor

+@rpcv2Cbor
service MyService {
    version: "2020-07-02"
}
```

Then add this module as a runtime dependency of your project 
```diff
dependencies {
+    implementation("software.amazon.smithy.java:client-rpcv2-cbor")
}
```

Finally, configure the client codegen plugin to use this protocol as the
default 
```diff
{
  "version": "1.0",
  "plugins": {
    "java-client-codegen": {
      "service": "com.example#CoffeeShop",
      "namespace": "com.example.cafe",
+     "protocol": "smithy.protocols#rpcv2Cbor"
    }
  }
}
```
