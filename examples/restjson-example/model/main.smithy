$version: "2"

namespace smithy.example

service PersonDirectory {
    version: "01-01-2040"
    resources: [
        Person
    ]
    errors: [
        ValidationError
    ]
}
