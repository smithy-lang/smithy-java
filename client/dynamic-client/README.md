### dynamic-client
Provides a dynamic client that can be used without code generation. 

The dynamic client can load Smithy models at runtime, convert them to a schema-based client, 
and call a service using `Document` types as input and output. 

### Example usage 
```java 
// (1) Load up a model
var model = Model.assembler()
    .addImport("/path/to/model.json")
    .assemble()
    .unwrap();

// (2) Which service to call
var shapeId = ShapeId.from("com.example#CoffeeShop");

// (3) make the client and manually wire up the protocol, transport, etc.
var client = DynamicClient.builder()
    .service(shapeId)
    .model(model)
    .protocol(new RestJsonClientProtocol(shapeId))
    .transport(new JavaHttpClientTransport())
    .endpointResolver(EndpointResolver.staticEndpoint("https://api.cafe.example.com"))
    .build();

// (4) Input is defined using a document that mirrors what you'd see in the Smithy model.
var input = Document.createFromObject(Map.of("coffeeType", "COLD_BREW"));

// (5) "call" is used to send the input to an operation by name, and it returns a document too.
var result = client.call("CreateOrder", input).get();

System.out.println(result);
```
