$version: "2.0"

namespace com.example

use smithy.example#OrderStatus
use smithy.example#Uuid

/// An Order resource, which has an id and describes an order by the type of coffee
/// and the order's status
resource Order {
    identifiers: {
        id: Uuid
    }
    properties: {
        coffeeType: CoffeeType
        status: OrderStatus
    }
    read: GetOrder
    create: CreateOrder
}

/// Create an order
@idempotent
@http(method: "PUT", uri: "/order")
operation CreateOrder {
    input := for Order {
        @required
        $coffeeType
    }

    output := for Order {
        @required
        $id

        @required
        $coffeeType

        @required
        $status
    }
}

/// Retrieve an order
@readonly
@http(method: "GET", uri: "/order/{id}")
operation GetOrder {
    input := for Order {
        @httpLabel
        @required
        $id
    }

    output := for Order {
        @required
        $id

        @required
        $coffeeType

        @required
        $status
    }

    errors: [
        OrderNotFound
    ]
}

/// An error indicating an order could not be found
@httpError(404)
@error("client")
structure OrderNotFound {
    message: String
    orderId: Uuid
}
