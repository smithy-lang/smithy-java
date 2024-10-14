## Smithy-Java Quickstart 
This project provides a template to get started using smithy-java to create clients
and servers. 

### Usage
To use this example, first, clone the `smithy-java` repo and run: 
```console
./gradlew build publishToMavenLocal
```
From the root of the repo to build  and publish the project modules to the local maven cache. 

Then, to create a new project from this template, use the [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/index.html) 
`init` command as follows: 
```console
smithy init -t quickstart --url https://github.com/smithy-lang/smithy-java
```

### Running and testing server
To run and test the server, run `./gradlew run` from the root of a project created from this
template. That will start the server running on port 8888. 

Once the server is running you can call the server using curl or 
by executing the integration tests in `client` subproject (i.e. `./gradlew :client:integ`). 
