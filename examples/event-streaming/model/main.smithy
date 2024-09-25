$version: "2.0"

namespace smithy.example.eventstreaming

use aws.protocols#restJson1

@restJson1
service MessageService {
    operations: [
        ExchangeMessages
        PublishMessages
        ReceiveMessages
    ]
}

@http(method: "POST", uri: "/exchange")
operation ExchangeMessages {
    input := {
        @httpHeader("x-chat-room")
        room: String

        @httpPayload
        stream: InputMessageStream
    }

    output := {
        @httpPayload
        stream: OutputMessageStream
    }

    errors: [
        RoomNotFound
    ]
}

@http(method: "POST", uri: "/publish")
operation PublishMessages {
    input := {
        @httpHeader("x-chat-room")
        room: String

        @httpPayload
        stream: InputMessageStream
    }

    errors: [
        RoomNotFound
    ]
}

@http(method: "POST", uri: "/receive")
operation ReceiveMessages {
    output := {
        @httpHeader("x-chat-room")
        room: String

        @httpPayload
        stream: OutputMessageStream
    }
}

@streaming
union InputMessageStream {
    message: MessageEvent
    leave: LeaveEvent
}

@streaming
union OutputMessageStream {
    message: MessageEvent
    leave: LeaveEvent
    terminate: TerminateEvent
}

structure MessageEvent {
    @required
    @eventHeader
    timestamp: DateTime

    @required
    message: String
}

structure LeaveEvent {
    @required
    @eventHeader
    timestamp: DateTime
}

@timestampFormat("date-time")
timestamp DateTime

@error("client")
structure TerminateEvent {
    message: String
}

@error("client")
@httpError(404)
structure RoomNotFound {
    message: String
}
