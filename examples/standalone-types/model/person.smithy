$version: "2"

metadata shapeClosures = [
    // A closure that includes every shape in the example namespace. Referencing this by id from
    // smithy-build.json drives types generation from the model rather than from the plugin's
    // default selector, and keeps the closure definition alongside the model.
    {
        id: "smithy.example#allTypes"
        includeNamespaces: ["smithy.example"]
    }
]

namespace smithy.example

use smithy.framework#ValidationException

structure Human {
    children: HumanList

    parents: Parents

    @required
    address: Address

    @required
    name: String

    @required
    age: Long
}

list HumanList {
    member: Human
}

structure Parents {
    father: Human
    mother: Human
}

structure Address {
    city: String
}

structure HumanValidationException {
    cause: ValidationException
}
