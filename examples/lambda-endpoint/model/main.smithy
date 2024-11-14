$version: "2"

namespace smithy.example

use smithy.protocols#rpcv2Cbor

@rpcv2Cbor
service BeerService {
    operations: [
        GetBeer
        AddBeer
    ]
}

operation GetBeer {
    input := {
        id: Long
    }
    output := {
        beer: Beer
    }
}

structure Beer {
    name: String
    quantity: Long
}

operation AddBeer {
    input := {
        beer: Beer
    }
    output := {
        id: Long
    }
}
