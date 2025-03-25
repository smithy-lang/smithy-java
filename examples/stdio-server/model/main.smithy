$version: "2"

namespace smithy.example

use smithy.protocols#jsonRpc2
use smithy.protocols#jsonRpc2Error
use smithy.protocols#jsonRpc2Method

@jsonRpc2
service BeerService {
    operations: [
        GetBeer
        AddBeer
    ]
}

@jsonRpc2Method(method: "get-beer")
operation GetBeer {
    input := {
        id: Long
    }
    output := {
        beer: Beer
    }
    errors: [
        NoSuchBeerException
    ]
}

structure Beer {
    @length(min: 3)
    name: String

    quantity: Long
}

@jsonRpc2Method(method: "add-beer")
operation AddBeer {
    input := {
        @required
        beer: Beer
    }

    output := {
        id: Long
    }
}

@error("client")
@jsonRpc2Error(code: 19229)
structure NoSuchBeerException {
    message: String
}
