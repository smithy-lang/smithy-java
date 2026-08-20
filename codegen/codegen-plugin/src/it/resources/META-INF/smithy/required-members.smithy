$version: "2.0"

namespace smithy.java.codegen.types.test

/// Three required members with optional ones interleaved between them. Bits are assigned by
/// position among the *required* members only, so the optional members must not consume any.
structure InterleavedRequiredMembers {
    optionalA: String

    @required
    requiredA: String

    optionalB: String

    @required
    requiredB: String

    optionalC: String

    @required
    requiredC: String
}

/// A required member that has a default is not validated, so it must not take a bit either.
structure RequiredWithDefaultMember {
    @required
    @default("d")
    withDefault: String

    @required
    plain: String
}

/// A required member that is also clientOptional is assumed present from construction.
structure ClientOptionalRequiredMembers {
    @required
    @clientOptional
    lenient: String

    @required
    strict: String
}

/// Exactly 64 required members: the most that still fit in a long, and the only size that
/// exercises the highest bit and the all-ones mask.
structure RequiredMembers64 {
    @required
    m0: String

    @required
    m1: String

    @required
    m2: String

    @required
    m3: String

    @required
    m4: String

    @required
    m5: String

    @required
    m6: String

    @required
    m7: String

    @required
    m8: String

    @required
    m9: String

    @required
    m10: String

    @required
    m11: String

    @required
    m12: String

    @required
    m13: String

    @required
    m14: String

    @required
    m15: String

    @required
    m16: String

    @required
    m17: String

    @required
    m18: String

    @required
    m19: String

    @required
    m20: String

    @required
    m21: String

    @required
    m22: String

    @required
    m23: String

    @required
    m24: String

    @required
    m25: String

    @required
    m26: String

    @required
    m27: String

    @required
    m28: String

    @required
    m29: String

    @required
    m30: String

    @required
    m31: String

    @required
    m32: String

    @required
    m33: String

    @required
    m34: String

    @required
    m35: String

    @required
    m36: String

    @required
    m37: String

    @required
    m38: String

    @required
    m39: String

    @required
    m40: String

    @required
    m41: String

    @required
    m42: String

    @required
    m43: String

    @required
    m44: String

    @required
    m45: String

    @required
    m46: String

    @required
    m47: String

    @required
    m48: String

    @required
    m49: String

    @required
    m50: String

    @required
    m51: String

    @required
    m52: String

    @required
    m53: String

    @required
    m54: String

    @required
    m55: String

    @required
    m56: String

    @required
    m57: String

    @required
    m58: String

    @required
    m59: String

    @required
    m60: String

    @required
    m61: String

    @required
    m62: String

    @required
    m63: String
}

/// One required member more than fits in a long, so this falls back to PresenceTracker.
/// It exists to check that the fallback behaves exactly like the inline mask.
structure RequiredMembers65 {
    @required
    m0: String

    @required
    m1: String

    @required
    m2: String

    @required
    m3: String

    @required
    m4: String

    @required
    m5: String

    @required
    m6: String

    @required
    m7: String

    @required
    m8: String

    @required
    m9: String

    @required
    m10: String

    @required
    m11: String

    @required
    m12: String

    @required
    m13: String

    @required
    m14: String

    @required
    m15: String

    @required
    m16: String

    @required
    m17: String

    @required
    m18: String

    @required
    m19: String

    @required
    m20: String

    @required
    m21: String

    @required
    m22: String

    @required
    m23: String

    @required
    m24: String

    @required
    m25: String

    @required
    m26: String

    @required
    m27: String

    @required
    m28: String

    @required
    m29: String

    @required
    m30: String

    @required
    m31: String

    @required
    m32: String

    @required
    m33: String

    @required
    m34: String

    @required
    m35: String

    @required
    m36: String

    @required
    m37: String

    @required
    m38: String

    @required
    m39: String

    @required
    m40: String

    @required
    m41: String

    @required
    m42: String

    @required
    m43: String

    @required
    m44: String

    @required
    m45: String

    @required
    m46: String

    @required
    m47: String

    @required
    m48: String

    @required
    m49: String

    @required
    m50: String

    @required
    m51: String

    @required
    m52: String

    @required
    m53: String

    @required
    m54: String

    @required
    m55: String

    @required
    m56: String

    @required
    m57: String

    @required
    m58: String

    @required
    m59: String

    @required
    m60: String

    @required
    m61: String

    @required
    m62: String

    @required
    m63: String

    @required
    m64: String
}
