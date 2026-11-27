$version: "2"

namespace smithy.java.codegen.test.unions

operation DiscriminatedUnions {
    input := {
        team: SportsTeam
        bare: BareTagged
    }
}

/// A discriminated union with a named discriminator whose variants share fields and nest another
/// discriminated union.
@discriminated("sport")
union SportsTeam {
    soccer: SoccerTeamData
    basketball: BasketballTeamData
}

structure SoccerTeamData {
    name: String

    @jsonName("League")
    league: String

    mascot: TeamMascot
}

structure BasketballTeamData {
    name: String
    arena: String
    mascot: TeamMascot
}

@discriminated("kind")
union TeamMascot {
    animal: AnimalMascotData
    character: CharacterMascotData
}

structure AnimalMascotData {
    species: String
    nickname: String
}

structure CharacterMascotData {
    name: String
    costume: String
}

/// A discriminated union that uses the default discriminator, "__type".
@discriminated
union BareTagged {
    circle: BareCircle
    square: BareSquare
}

structure BareCircle {
    radius: Integer
}

structure BareSquare {
    side: Integer
}
