$version: "2"

namespace smithy.example

resource Person {
    identifiers: { name: String }
    properties: { favoriteColor: String, age: Integer, birthday: Birthday }
    put: PutPerson
    resources: [
        PersonImage
    ]
}

@idempotent
@http(method: "PUT", uri: "/persons/{name}", code: 200)
operation PutPerson {
    input := for Person {
        @httpLabel
        @required
        $name

        @httpQuery("favoriteColor")
        $favoriteColor

        @jsonName("Age")
        $age

        @default("1985-04-12T23:20:50.52Z")
        $birthday

        @notProperty
        binary: Blob

        @notProperty
        @httpQueryParams
        queryParams: MapListString
    }

    output := for Person {
        @required
        $name

        @httpHeader("X-Favorite-Color")
        $favoriteColor

        @jsonName("Age")
        $age = 1

        $birthday
    }
}
