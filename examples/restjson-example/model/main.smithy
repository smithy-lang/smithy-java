$version: "2"

namespace smithy.example

use aws.auth#sigv4
use aws.protocols#restJson1

@restJson1
@sigv4(name: "service")
service PersonDirectory {
    version: "01-01-2040"
    resources: [
        Person
    ]
    errors: [
        ValidationError
    ]
}
