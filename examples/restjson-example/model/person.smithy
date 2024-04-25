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

        /// This is a binary blob! Yay!
        /// It has quite a few documentation traits added to it.
        @notProperty
        @externalDocumentation(Homepage: "https://www.example.com/", "API Reference": "https://www.example.com/api-ref")
        @since("1.3.4.5.6")
        @deprecated(message: "This shape is no longer used.", since: "1.4.5.6")
        @unstable
        binary: Blob

        @notProperty
        notRequiredBool: Boolean

        @required
        @notProperty
        requiredBool: Boolean

        @notProperty
        @httpQueryParams
        queryParams: MapListString

        @notProperty
        @default(true)
        defaultBoolean: Boolean

        @notProperty
        @default([])
        defaultList: ListOfString

        @notProperty
        @default({})
        defaultMap: MapStringString

        @notProperty
        nestedMap: MapOfStringMap

        @notProperty
        nestedList: ListOfStringList
    }

    output := for Person {
        @required
        $name

        @httpHeader("X-Favorite-Color")
        $favoriteColor

        @jsonName("Age")
        $age = 1

        $birthday

        @notProperty
        @httpResponseCode
        status: Integer

        @notProperty
        list: ListOfString

        @notProperty
        set: SetOfString

        @notProperty
        @required
        requiredList: ListOfString

        @notProperty
        struct: Nested
    }
}
