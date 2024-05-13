$version: "2"

namespace smithy.java.codegen.test.structures

use smithy.java.codegen.test.exceptions#ExceptionOperation
use smithy.java.codegen.test.structures.members#BigDecimals
use smithy.java.codegen.test.structures.members#BigIntegers
use smithy.java.codegen.test.structures.members#Blobs
use smithy.java.codegen.test.structures.members#Booleans
use smithy.java.codegen.test.structures.members#Bytes
use smithy.java.codegen.test.structures.members#ClientErrorCorrection
use smithy.java.codegen.test.structures.members#Doubles
use smithy.java.codegen.test.structures.members#Floats
use smithy.java.codegen.test.structures.members#Integers
use smithy.java.codegen.test.structures.members#ListWithStructs
use smithy.java.codegen.test.structures.members#Lists
use smithy.java.codegen.test.structures.members#Longs
use smithy.java.codegen.test.structures.members#Maps
use smithy.java.codegen.test.structures.members#NestedLists
use smithy.java.codegen.test.structures.members#NestedMaps
use smithy.java.codegen.test.structures.members#RecursiveLists
use smithy.java.codegen.test.structures.members#RecursiveMap
use smithy.java.codegen.test.structures.members#RecursiveMaps
use smithy.java.codegen.test.structures.members#Sets
use smithy.java.codegen.test.structures.members#Shorts
use smithy.java.codegen.test.structures.members#Strings
use smithy.java.codegen.test.structures.members#Structures
use smithy.java.codegen.test.structures.members#Timestamps

resource StructureShapes {
    operations: [
        // Blob members
        Blobs,
        // Boolean members
        Booleans,
        // List members
        Lists,
        Sets,
        ListWithStructs,
        NestedLists,
        // TODO: Get recursive working
        //RecursiveLists,
        // Maps
        Maps,
        NestedMaps,
        // TODO: Get recursive working
        // RecursiveMaps,
        // Number members
        BigDecimals,
        BigIntegers,
        Bytes,
        Doubles,
        Floats,
        Integers,
        Longs,
        Shorts,
        // String members
        Strings,
        // Structure members
        Structures,
        // Timestamp members
        Timestamps
        // Exceptions
        ExceptionOperation,
        // Error correction
        ClientErrorCorrection
    ]
}
