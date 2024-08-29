$version: "2"

namespace smithy.example

use aws.protocols#restJson1

@restJson1
service PersonDirectory {
    version: "01-01-2040"
    resources: [
        Person
    ]
    errors: [
        ValidationError
    ]
}
