$version: "2"

namespace smithy.example

use aws.protocols#restJson1
use smithy.protocols#rpcv2Cbor

@restJson1
@rpcv2Cbor
service BeerService {
    operations: [
        GetBeer
        AddBeer
    ]
}

@http(method: "POST", uri: "/get-beer")
operation GetBeer {
    input := {
        id: String
    }
    output := {
        beer: Beer
    }
}

structure Beer {
    name: String
    quantity: Long
}

@http(method: "POST", uri: "/add-beer")
operation AddBeer {
    input := {
        beer: Beer
    }
    output := {
        id: String
    }
}
