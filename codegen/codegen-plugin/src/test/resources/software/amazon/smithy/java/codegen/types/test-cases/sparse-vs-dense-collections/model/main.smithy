$version: "2"

namespace smithy.test

list DenseList {
    member: String
}

@sparse
list SparseList {
    member: String
}

map DenseMap {
    key: String
    value: String
}

@sparse
map SparseMap {
    key: String
    value: String
}

structure CollectionsStruct {
    denseList: DenseList
    sparseList: SparseList
    denseMap: DenseMap
    sparseMap: SparseMap
}
