$version: "2"

namespace smithy.example

resource PersonImage {
    identifiers: {
        name: String
    }
    read: GetPersonImage
    put: PutPersonImage
}

@readonly
@http(method: "GET", uri: "/persons/{name}/image", code: 200)
operation GetPersonImage {
    input := for PersonImage {
        @required
        @httpLabel
        $name
    }

    output := for PersonImage {
        @required
        @httpHeader("Person-Name")
        $name

        @required
        @httpPayload
        image: Stream
    }
}

@idempotent
@http(method: "PUT", uri: "/persons/{name}/images", code: 200)
operation PutPersonImage {
    input := for PersonImage {
        @required
        @httpLabel
        $name

        @httpHeader("Tags")
        tags: ListOfString

        @httpQuery("MoreTags")
        moreTags: ListOfString

        @required
        @httpPayload
        image: Stream
    }
}
