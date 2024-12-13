$version: "2"

namespace com.shared.types

/// An identifier to describe a unique order
@length(min: 1, max: 128)
@pattern("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")
string Uuid

/// An enum describing the status of an order
enum OrderStatus {
    IN_PROGRESS
    COMPLETED
}
