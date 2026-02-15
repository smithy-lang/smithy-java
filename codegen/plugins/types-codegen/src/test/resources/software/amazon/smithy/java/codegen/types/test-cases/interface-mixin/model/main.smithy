$version: "2"

namespace smithy.test

/// A simple interface mixin with basic members
@mixin(interface: true)
structure HasName {
    name: String
    @required
    id: Integer
}

/// An interface mixin that extends another interface mixin
@mixin(interface: true)
structure HasFullName with [HasName] {
    lastName: String
}

/// A concrete structure implementing a single interface mixin
structure SimpleUser with [HasName] {
    email: String
}

/// A concrete structure implementing a chained interface mixin hierarchy
structure DetailedUser with [HasFullName] {
    age: Integer
}
