$version: "2.0"

namespace smithy.example.eventstreaming

use aws.protocols#restJson1

@restJson1
service FizzBuzzService {
    operations: [
        FizzBuzz
    ]
}

@http(method: "POST", uri: "/fizzBuzz", code: 200)
operation FizzBuzz {
    input: FizzBuzzInput
    output: FizzBuzzOutput
}

structure FizzBuzzInput {
    @httpPayload
    stream: ValueStream
}

structure FizzBuzzOutput {
    @httpPayload
    stream: FizzBuzzStream
}

@streaming
union ValueStream {
    Value: Value
}

structure Value {
    @required
    value: Long = 0
}

@streaming
union FizzBuzzStream {
    fizz: FizzEvent
    buzz: BuzzEvent
    negativeNumberException: NegativeNumberException
    malformedInputException: MalformedInputException
    internalException: InternalException
}

structure FizzEvent {
    @required
    value: Long = 0
}

structure BuzzEvent {
    @required
    value: Long = 0
}

@error("client")
structure NegativeNumberException {
    message: ErrorMessage
}

@error("client")
structure MalformedInputException {
    message: ErrorMessage
}

@error("server")
structure InternalException {
    message: ErrorMessage
}

string ErrorMessage

@default(0)
long Long
