$version: "2"

namespace com.example

use aws.protocols#restJson1
use smithy.example.middleware#customAuth

// Adds arn as runtime trait binding
@authDefinition(
    traits: [aws.api#arn]
)
@trait(selector: "service")
structure arnAuth {}

/// Allows users to retrieve a menu, create a coffee order, and
/// and to view the status of their orders
@title("Coffee Shop Service")
@restJson1
@customAuth
@arnAuth
service CoffeeShop {
    version: "2024-08-23"
    operations: [
        GetMenu
    ]
    resources: [
        Order
    ]
}

/// Retrieve the menu
@http(method: "GET", uri: "/menu")
@readonly
operation GetMenu {
    output := {
        items: CoffeeItems
    }
}
