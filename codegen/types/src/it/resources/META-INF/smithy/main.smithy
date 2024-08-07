$version: "2.0"

namespace smithy.java.codegen.types.test

structure BeerCollection {
    name: String
    list: BeerList
}

structure Beer {
    @required
    name: String

    id: Long
}

list BeerList {
    member: Beer
}
