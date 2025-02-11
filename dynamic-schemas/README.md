# dynamic-schemas

Can dynamically create a Smithy-Java `Schema` from a Smithy model `Shape` and
can wrap a document to have it appear to be a typed value using a schema.

## Example usage

```java 
// (1) Load up a model
var model = Model.assembler()
    .addImport("/path/to/model.json")
    .assemble()
    .unwrap();

// (2) Create a SchemaConverter
var schemaConverter = new SchemaConverter(model);

// (3) Convert a Shape to a Schema
var shape = model.expectShape(ShapeId.from("com.example#Foo"));
var schema = schemaConverter.getSchema(shape);
```
