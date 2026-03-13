## jmespath

Provides functionality to perform a [JMESPath](https://jmespath.org/) query 
against a Smithy-Java `Document` and `SerializableStruct`.

### Example Usage

Given a `Document` representing the JSON object `{"foo": [1,2,3,4]}`,
all items in member `foo` greater than 2 could be queried as:

```java 
Document result = JMESPathQueries.query("foo[?@ > `2`]", myDocument);
```
