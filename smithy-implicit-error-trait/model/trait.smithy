$version: "2"

namespace smithy.framework

/// Meta-trait allowing traits targeting a service shape to add implicit errors to the service.
///
/// Traits that are marked with this trait that are applied to service shapes.
/// This trait is only respected by client and server code generators
// TODO: Finish docs
// TODO: add validator to ensure this is only ever applied to traits applied to services.
@trait(selector: "[trait|trait]")
list addsImplicitErrors {
    member: ErrorRef
}

@private
@idRef(selector: "[trait|error]")
string ErrorRef
