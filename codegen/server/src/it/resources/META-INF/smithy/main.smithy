$version: "2.0"

namespace smithy.java.codegen.server.test

service TestService {
    version: "today"
    operations: [
        GetBeer
        Echo,
        GetError
    ]
}

operation GetBeer {
    input: GetBeerInput
    output: GetBeerOutput
}

operation Echo {
    input: EchoInput
    output: EchoOutput
}

operation GetError {
    input := {
        @required
        exceptionClass: String,
        @required
        message: String
    },
    errors: [BeerNotFoundException, InvalidBeerIdException, BeerTooLargeException]
}

structure EchoInput {
    value: EchoPayload
}

structure EchoOutput {
    value: EchoPayload
}

structure EchoPayload {
    string: String
    @required
    @default(0)
    echoCount: Integer
}

structure Beer {
    name: String
    id: Long
}

list BeerList {
    member: Beer
}

structure GetBeerInput {
    @required
    id: Long
}

structure GetBeerOutput {
    @required
    value: BeerList
}

@error("client")
@httpError(403)
structure BeerNotFoundException {
    @required
    message: String
}

@error("client")
@httpError(403)
structure InvalidBeerIdException {
    @required
    message: String
}


@error("server")
@httpError(503)
structure BeerTooLargeException {
    @required
    message: String
}