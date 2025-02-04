## protocol-tests
Protocol testing harness for Smithy Java protocol compliance tests.

Protocol compliance tests are encoded in the Smithy model using traits and 
ensure that implementations correctly implement a protocol specification.

HTTP protocols should use the [http protocol testing traits](https://smithy.io/2.0/additional-specs/http-protocol-compliance-tests.html#http-protocol-compliance-tests) 
provided by Smithy.

> [!NOTE]
> Protocol compliance tests are currently only supported for HTTP Protocols

### Usage
> [!WARNING]
> This test harness requires JUnit5

Protocol test require a generated client or server for execution. 
To generate a client or server for protocol tests use the `ProtocolTestGenerator` provided 
by this package. 

To execute protocol compliance tests for a protocol, first define a class representing the 
test suite and apply the `@ProtocolTest` annotation as follows:
```java
@ProtocolTest(
    service = "aws.protocoltests.restjson#RestJson",
    testType = TestType.CLIENT
)
public class RestJson1ClientProtocolTests {
    // Add specific handlers here
}
```
The `@ProtocolTest` annotation will trigger the generation of tests based on the 
protocol test traits found within the closure of the provided service. 

Now we will write a handler for each type of protocol test trait in our model. 

Client request test and Server response test handlers must provide logic to determine 
if the provided wire data matches the expected data. For example:
```java
    @HttpClientRequestTests
    public void requestTest(DataStream expected, DataStream actual) {
        // Add logic to compare equality here...
    }
    
    @HttpServerResponseTests
    public void responseTest(DataStream expected, DataStream actual) {
        // Add logic to compare equality here...
    }
```

Client response and server request test handler need only execute the provided runnable: 
```java
    @HttpClientResponseTests
    public void requestTest(Runnable test) {
        test.run();
    }

    @HttpServerRequestTests
    public void requestTest(Runnable test) {
        test.run();
    }
```

#### Filtering 
Protocol tests can be filtered out for the entire test suite or for a particular 
handler using the `@ProtocolTestFilter` annotation. This annotation can be used to: 
1. Skip test cases by IDs
2. Skip all test cases on an operation based on operation ID. 
3. Only execute tests with specific IDs. All other tests will be skipped.
4. Only execute tests on a specific set of Operations. All tests on other operations will be skipped.

Example Usage:
```java
// Skip for the whole test suite
@ProtocolTest(
    service = "aws.protocoltests.restjson#RestJson",
    testType = TestType.CLIENT
)
@ProtocolTestFilter(skipOperations = {"SkippedOperationID"})
public class RestJson1ClientProtocolTests {

    @HttpClientRequestTests
    @ProtocolTestFilter(skipTests = {"SkippedTestID"})
    public void requestTest(DataStream expected, DataStream actual) {
        // Add logic to compare equality here...
    }
    
    // Other handlers....
}
```
