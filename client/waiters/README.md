## waiters
Provides the core runtime functionality for [Waiters](https://smithy.io/2.0/additional-specs/waiters.html#waiters). 

Waiters are a client-side abstraction used to poll a resource until a desired state is reached, 
or until it is determined that the resource will never enter into the desired state.

### Usage
Waiter can be manually defined as:
```
// Create a waiter with a single failure acceptor
var waiter = Waiter.builder(client::getFoosSync)
    .failure(Matcher.output(o -> o.status().equals("FAILED"))))
    .build();
// Wait for up to 2 seconds for waiter to complete
waiter.wait(input, Duration.ofSeconds(2));
```

Waiters can also be code generated for your client based on the presence of the `smithy.waiters#waitable` trait.
To code generate waiters see: [waiter-integration](../../codegen/integrations/waiters-codegen)

