$version: "2.0"


namespace smithy.java.codegen.server.test

use aws.auth#sigv4
use aws.protocols#restJson1

@sigv4(name: "restjson")
@restJson1
service TestService {
    version: "today"
    operations: [GetBeer, Echo, HashFile, ZipFile, FizzBuzz]
}

@http(method: "POST", uri: "/get-beer")
operation GetBeer {
    input:= {
        @httpHeader("X-Beer-Input-Id")
        @required
        id: Long
    }
    output:= {
        @required
        @httpPayload
        value: Beer

        @required
        @httpHeader("X-Beer-Output-Id")
        beerId: Long
    }
}

@http(method: "POST", uri: "/echo")
operation Echo {
    input: EchoInput
    output: EchoOutput
}

@http(method: "POST", uri: "/hash")
operation HashFile {
    input:= {
        @httpPayload
        @required
        payload: FileStream
    }

    output:= {
        hashcode: String
    }
}

@http(method: "POST", uri: "/gzip")
operation ZipFile {
    input:= {
        @httpPayload
        @required
        payload: FileStream
    }

    output:= {
        @httpPayload
        @required
        payload: FileStream
    }
}

@http(method: "POST", uri: "/fizzBuzz")
operation FizzBuzz {
    input:= {
        @httpPayload
        stream: ValueStream
    }
    output:= {
        @httpPayload
        stream: FizzBuzzStream
    }
}

@streaming
union ValueStream {
    Value: Value
}

structure Value {
    value: Long
}

@streaming
union FizzBuzzStream {
    fizz: FizzEvent
    buzz: BuzzEvent
}

structure FizzEvent {
    value: Long
}

structure BuzzEvent {
    value: Long
}

@streaming
blob FileStream

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
}

list BeerList {
    member: Beer
}


