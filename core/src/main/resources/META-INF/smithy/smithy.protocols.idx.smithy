$version: "2.0"

namespace smithy.protocols

/// Member index for an indexed protocol.
/// idx values must start at 1 and increase monotonically by 1 with no gaps.
@trait(selector: ":is(structure, union) :not([trait|mixin]) > member")
@range(min: 1)
integer idx
