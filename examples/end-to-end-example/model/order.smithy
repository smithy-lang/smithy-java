$version: "2.0"

namespace com.example

use com.shared.types#OrderStatus
use com.shared.types#Uuid
use smithy.waiters#waitable

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
@waitable(
    OrderExists: {
        documentation: "Wait until the order exists"
        acceptors: [
            {
                state: "success"
                matcher: { success: true }
            }
            {
                state: "success"
                matcher: { errorType: OtherError }
            }
            {
                state: "retry"
                matcher: { errorType: OrderNotFound }
            }
        ]
    }
)
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
        OtherError
    ]
}

/// An error indicating an order could not be found
@httpError(404)
@error("client")
@title("Thrown when no existing order is found for an ID.")
structure OrderNotFound {
    message: String
    orderId: Uuid
}

@httpError(405)
@error("client")
structure OtherError {
    message: String
}
