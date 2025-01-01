$version: "2"

namespace smithy.example

use aws.protocols#restJson1

@restJson1
service BeerService {
    operations: [
        GetBeer
        AddBeer
    ]
}

@http(method: "POST", uri: "/get-beer")
operation GetBeer {
    input := {
        id: Long
    }
    output := {
        beer: Beer
    }
}

structure Beer {
    @required
    name: String

    @required
    quantity: Long

    @required
    brand: Brand
}

@http(method: "POST", uri: "/add-beer")
operation AddBeer {
    input := {
        @required
        beer: Beer

        @required
        @httpHeader("x-id")
        @range(min: 5)
        id: Long

        @required
        beers: BeerList
    }

    output := {
        id: Long
    }
}

structure Brand {
    @required
    @length(min: 5)
    name: String

    @required
    @length(min: 5)
    country: String
}

list BeerList {
    member: Beer
}
