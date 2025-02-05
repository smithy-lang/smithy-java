## Example: Basic Service
Demonstrates how to build a simple HTTP server from a service model. 

In this example, a Smithy model for a `BeerService` is used to generate a server and 
operation stub interfaces. These stub interfaces (`AddBeerOperation` and `GetBeerOperationAsync`) are implemented 
and registered with the Generated client to create a basic server.

This example server supports the `restJson1` protocol.

### Usage
To use this example as a template, run the following command with the [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/index.html): 
```console
smithy init -t basic-server --url https://github.com/smithy-lang/smithy-java
```

To run the server, run the following from the root of the project: 
```console 
gradle run
```
This will start the server running on port `8080`.

You can use curl to make a call to the service, for example you could add a beer as follows: 
```console
curl -H "content-type: application/json" -d '{"beer":{"name": "wit", "quantity":2} }' -X POST localhost:8080/add-beer
```
And get the beer using: 
```console
curl -H "content-type: application/json" -d '{"id": 1 }' -X POST localhost:8080/get-beer
```
