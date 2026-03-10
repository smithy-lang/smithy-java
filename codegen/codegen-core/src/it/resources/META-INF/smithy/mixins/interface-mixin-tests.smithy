$version: "2"

namespace smithy.java.codegen.test.mixins

/// Interface mixin with basic members
@mixin(interface: true)
structure HasName {
    name: String
    @required
    id: Integer
}

/// Interface mixin extending another interface mixin
@mixin(interface: true)
structure HasFullName with [HasName] {
    lastName: String
}

/// Concrete structure using a single interface mixin
structure SimpleUser with [HasName] {
    email: String
}

/// Concrete structure using a chained interface mixin hierarchy
structure DetailedUser with [HasFullName] {
    age: Integer
}

/// Interface mixin with a list member to test has*() override
@mixin(interface: true)
structure HasTags {
    tags: TagList
}

list TagList {
    member: String
}

/// Concrete structure using the list-bearing interface mixin
structure TaggedResource with [HasTags] {
    resourceId: String
}

/// Concrete structure using multiple interface mixins
structure TaggedUser with [HasName, HasTags] {
    role: String
}

/// Error shape using an interface mixin
@error("client")
structure UserNotFound with [HasName] {
    detail: String
}

resource MixinTests {
    operations: [
        MixinTestOp
    ]
}

@http(method: "POST", uri: "/mixin-test")
operation MixinTestOp {
    input := {
        simpleUser: SimpleUser
        detailedUser: DetailedUser
        taggedResource: TaggedResource
        taggedUser: TaggedUser
    }
    errors: [
        UserNotFound
    ]
}
