$version: "2"

namespace smithy.test

/// Structure with required, optional, primitive, and sparse collection members
structure JSpecifyStruct {
    @required
    requiredString: String

    optionalString: String

    @required
    requiredPrimitive: Boolean

    sparseList: SparseStringList
}

/// Structure exercising all collection sparsity combinations
structure CollectionStruct {
    /// Non-sparse list: List<String>
    nonSparseList: NonSparseStringList

    /// Sparse list: List<@Nullable String>
    @required
    sparseList: SparseStringList

    /// Non-sparse map: Map<String, String>
    nonSparseMap: NonSparseStringMap

    /// Sparse map: Map<String, @Nullable String>
    @required
    sparseMap: SparseStringMap

    /// Non-sparse list of sparse map: List<Map<String, @Nullable String>>
    nonSparseListOfSparseMap: NonSparseListOfSparseMap

    /// Sparse list of sparse map: List<@Nullable Map<String, @Nullable String>>
    @required
    sparseListOfSparseMap: SparseListOfSparseMap

    /// Sparse map of non-sparse list: Map<String, @Nullable List<String>>
    sparseMapOfNonSparseList: SparseMapOfNonSparseList

    /// Non-sparse list of non-sparse map: List<Map<String, String>>
    @required
    nonSparseListOfNonSparseMap: NonSparseListOfNonSparseMap
}

list NonSparseStringList {
    member: String
}

@sparse
list SparseStringList {
    member: String
}

map NonSparseStringMap {
    key: String
    value: String
}

@sparse
map SparseStringMap {
    key: String
    value: String
}

/// List<Map<String, @Nullable String>>
list NonSparseListOfSparseMap {
    member: SparseStringMap
}

/// List<@Nullable Map<String, @Nullable String>>
@sparse
list SparseListOfSparseMap {
    member: SparseStringMap
}

/// Map<String, @Nullable List<String>>
@sparse
map SparseMapOfNonSparseList {
    key: String
    value: NonSparseStringList
}

/// List<Map<String, String>>
list NonSparseListOfNonSparseMap {
    member: NonSparseStringMap
}

/// Union where all variants are primitive types
union AllPrimitiveUnion {
    intVariant: Integer
    boolVariant: Boolean
    longVariant: Long
}

/// Union with a mix of primitive and non-primitive variants
union MixedUnion {
    intVariant: Integer
    stringVariant: String
    boolVariant: Boolean
}
