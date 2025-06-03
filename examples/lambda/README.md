## Example: Lambda Endpoint

### Usage
To use this example as a template, run the following command with the [Smithy CLI](https://smithy.io/2.0/guides/smithy-cli/index.html):
```console
smithy init -t lambda-endpoint --url https://github.com/smithy-lang/smithy-java
```

Or

```console
smithy init -t lambda-endpoint --url git@github.com:smithy-lang/smithy-java.git
```

To generate the zipfile containing the lambda handler, run the following from the root of the project:

```console
gradle buildZip
```

This will generate a zipfile at `build/distributions/lambda-0.0.1.zip`. This artifact contains all the code 
that is necessary run your smithy-java endpoint on AWS Lambda. To set up a lambda function, you can follow the 
official AWS Lambda documentation for setting up a Java-based Lambda function
[here](https://docs.aws.amazon.com/lambda/latest/dg/java-package.html#java-package-console).

The handler method should **always** be set to:
`software.amazon.smithy.java.aws.integrations.lambda.LambdaEndpoint::handleRequest`.
Example input events for the  endpoint are given under [events](events/). These events can be used to test your 
smithy-java endpoint in the AWS Console or with the AWS CLI.




