$version: "2"

namespace smithy.example

use trials#Trials

resource Person {
    identifiers: {
        name: String
    }
    properties: {
        favoriteColor: String
        age: Integer
        birthday: Birthday
    }
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
        @length(max: 7)
        $name

        @httpQuery("favoriteColor")
        $favoriteColor

        @jsonName("Age")
        @range(max: 150)
        $age = 0

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

        @notProperty
        trials: Trials
    }
}
