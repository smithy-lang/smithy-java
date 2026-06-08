$version: "2"

namespace com.example

use aws.protocols#restJson1
use smithy.protocols#rpcv2Cbor
use smithy.protocols#rpcv2Json

/// Allows users to retrieve a menu, create a coffee order, and
/// and to view the status of their orders.
///
/// Declares three protocols. restJson1 routes by @http traits;
/// rpcv2Cbor and rpcv2Json route by /service/CoffeeShop/operation/<Op>
/// + smithy-protocol header. Per the Smithy 2.0 Wire-protocol-selection
/// guide and the protocol-test-harness pluggable-hosts compliance
/// tests, the server iterates protocols in precision order
/// (rpcv2Cbor=1, rpcv2Json=2, restJson1=7) per request.
@title("Coffee Shop Service")
@restJson1
@rpcv2Cbor
@rpcv2Json
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
