$version: "2"

namespace smithy.example

/// Fields common to all events
@mixin
structure Event {
    @required
    itemId: OrderId

    @required
    timestamp: Timestamp
}

/// Request for item availability through app or website
structure QueryEvent with [Event] {}

/// Event indicating a customer has ordered an item from a store or website
structure NewOrderEvent with [Event] {
    @required
    quantity: Integer
}

/// Event indicating a customer has returned the item to a store
structure ReturnEvent with [Event] {
    @required
    quantity: Integer

    reason: String
}

/// An unique identifier for orders
@length(min: 1, max: 128)
string OrderId
