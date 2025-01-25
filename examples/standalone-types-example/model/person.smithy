$version: "2"

namespace smithy.example

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
