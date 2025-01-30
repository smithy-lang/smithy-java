## mock-client-plugin
The MockPlugin is used to intercept client requests and return canned
responses, shapes, or exceptions. 

### Usage
```java
// (1) Create a response queue and add a set of canned responses that will be returned 
//     from client in the order in which they were added to the queue.
var mockQueue = new MockQueue();
mockQueue.enqueue(
    HttpResponse.builder()
        .statusCode(200)
        .body(DataStream.ofString("{\"id\":\"1\"}"))
    .build()
);
mockQueue.enqueue(
    HttpResponse.builder()
        .statusCode(429)
        .body(DataStream.ofString("{\"__type\":\"InvalidSprocketId\"}"))
    .build()
);

// (2) Create a MockPlugin instance using the request queue created above.
var mockPlugin = MockPlugin.builder().addQueue(mockQueue).build();

// (3) Create a client instance that uses the MockPlugin. 
var client = SprocketClient.builder()
        .addPlugin(mockPlugin)
        .build();

// (4) Call client to get the first canned response from the queue.
var response = client.createSprocket(CreateSprocketRequest.builder().id(2).build());

// (5) Inspect the HTTP requests that were sent to the client.
var requests = mock.getRequests();

```

