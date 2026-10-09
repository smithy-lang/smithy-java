$version: "2"

namespace com.amazonaws.sdk.benchmark

use aws.protocols#awsJson1_0
use aws.protocols#awsQuery
use aws.protocols#restJson1
use aws.protocols#restXml
use smithy.protocols#rpcv2Cbor
use smithy.test#httpRequestTests
use smithy.test#httpResponseTests

@documentation(
    """
    A wide, heterogeneous structure used to isolate schema and type dispatch
    overhead in serialization and deserialization.
    """
)
@http(method: "POST", uri: "/WideTypes/{label}", code: 200)
@httpRequestTests([
    {
        id: "awsJson1_0_WideTypesRequest_S"
        protocol: awsJson1_0
        method: "POST"
        uri: "/"
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "awsJson1_0_WideTypesRequest_M"
        protocol: awsJson1_0
        method: "POST"
        uri: "/"
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "awsJson1_0_WideTypesRequest_L"
        protocol: awsJson1_0
        method: "POST"
        uri: "/"
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
            stringC: "charlie"
            integerC: 303
            longC: 30000000003
            booleanC: true
            doubleC: 303.125
            floatC: 303.25
            byteC: 33
            shortC: 3003
            bigIntegerC: 323456789012345678903
            bigDecimalC: 3234567890.323456789
            blobC: "blob-charlie"
            timestampC: 1786763045
            modeC: "CHARLIE"
            stringsC: ["charlie-1", "charlie-2", "charlie-3"]
            integersC: { seven: 7, eight: 8, nine: 9 }
            nestedC: { label: "nested-charlie", count: 33, active: true, ratio: 3.125 }
            choiceC: {
                nested: { label: "choice-charlie", count: 303, active: false, ratio: 30.125 }
            }
            stringD: "delta"
            integerD: 404
            longD: 40000000004
            booleanD: false
            doubleD: 404.125
            floatD: 404.25
            byteD: 44
            shortD: 4004
            bigIntegerD: 423456789012345678904
            bigDecimalD: 4234567890.423456789
            blobD: "blob-delta"
            timestampD: 1786766706
            modeD: "DELTA"
            stringsD: ["delta-1", "delta-2", "delta-3"]
            integersD: { ten: 10, eleven: 11, twelve: 12 }
            nestedD: { label: "nested-delta", count: 44, active: false, ratio: 4.125 }
            choiceD: { text: "choice-delta" }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "restJson1_WideTypesRequest_S"
        protocol: restJson1
        method: "POST"
        uri: "/WideTypes/alpha?query=query-alpha"
        headers: { "X-Wide-Request": "header-alpha" }
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "restJson1_WideTypesRequest_M"
        protocol: restJson1
        method: "POST"
        uri: "/WideTypes/alpha?query=query-alpha"
        headers: { "X-Wide-Request": "header-alpha" }
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "restJson1_WideTypesRequest_L"
        protocol: restJson1
        method: "POST"
        uri: "/WideTypes/alpha?query=query-alpha"
        headers: { "X-Wide-Request": "header-alpha" }
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
            stringC: "charlie"
            integerC: 303
            longC: 30000000003
            booleanC: true
            doubleC: 303.125
            floatC: 303.25
            byteC: 33
            shortC: 3003
            bigIntegerC: 323456789012345678903
            bigDecimalC: 3234567890.323456789
            blobC: "blob-charlie"
            timestampC: 1786763045
            modeC: "CHARLIE"
            stringsC: ["charlie-1", "charlie-2", "charlie-3"]
            integersC: { seven: 7, eight: 8, nine: 9 }
            nestedC: { label: "nested-charlie", count: 33, active: true, ratio: 3.125 }
            choiceC: {
                nested: { label: "choice-charlie", count: 303, active: false, ratio: 30.125 }
            }
            stringD: "delta"
            integerD: 404
            longD: 40000000004
            booleanD: false
            doubleD: 404.125
            floatD: 404.25
            byteD: 44
            shortD: 4004
            bigIntegerD: 423456789012345678904
            bigDecimalD: 4234567890.423456789
            blobD: "blob-delta"
            timestampD: 1786766706
            modeD: "DELTA"
            stringsD: ["delta-1", "delta-2", "delta-3"]
            integersD: { ten: 10, eleven: 11, twelve: 12 }
            nestedD: { label: "nested-delta", count: 44, active: false, ratio: 4.125 }
            choiceD: { text: "choice-delta" }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "restXml_WideTypesRequest_S"
        protocol: restXml
        method: "POST"
        uri: "/WideTypes/alpha?query=query-alpha"
        headers: { "X-Wide-Request": "header-alpha" }
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "restXml_WideTypesRequest_M"
        protocol: restXml
        method: "POST"
        uri: "/WideTypes/alpha?query=query-alpha"
        headers: { "X-Wide-Request": "header-alpha" }
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "restXml_WideTypesRequest_L"
        protocol: restXml
        method: "POST"
        uri: "/WideTypes/alpha?query=query-alpha"
        headers: { "X-Wide-Request": "header-alpha" }
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
            stringC: "charlie"
            integerC: 303
            longC: 30000000003
            booleanC: true
            doubleC: 303.125
            floatC: 303.25
            byteC: 33
            shortC: 3003
            bigIntegerC: 323456789012345678903
            bigDecimalC: 3234567890.323456789
            blobC: "blob-charlie"
            timestampC: 1786763045
            modeC: "CHARLIE"
            stringsC: ["charlie-1", "charlie-2", "charlie-3"]
            integersC: { seven: 7, eight: 8, nine: 9 }
            nestedC: { label: "nested-charlie", count: 33, active: true, ratio: 3.125 }
            choiceC: {
                nested: { label: "choice-charlie", count: 303, active: false, ratio: 30.125 }
            }
            stringD: "delta"
            integerD: 404
            longD: 40000000004
            booleanD: false
            doubleD: 404.125
            floatD: 404.25
            byteD: 44
            shortD: 4004
            bigIntegerD: 423456789012345678904
            bigDecimalD: 4234567890.423456789
            blobD: "blob-delta"
            timestampD: 1786766706
            modeD: "DELTA"
            stringsD: ["delta-1", "delta-2", "delta-3"]
            integersD: { ten: 10, eleven: 11, twelve: 12 }
            nestedD: { label: "nested-delta", count: 44, active: false, ratio: 4.125 }
            choiceD: { text: "choice-delta" }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "awsQuery_WideTypesRequest_S"
        protocol: awsQuery
        method: "POST"
        uri: "/"
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "awsQuery_WideTypesRequest_M"
        protocol: awsQuery
        method: "POST"
        uri: "/"
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "awsQuery_WideTypesRequest_L"
        protocol: awsQuery
        method: "POST"
        uri: "/"
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
            stringC: "charlie"
            integerC: 303
            longC: 30000000003
            booleanC: true
            doubleC: 303.125
            floatC: 303.25
            byteC: 33
            shortC: 3003
            bigIntegerC: 323456789012345678903
            bigDecimalC: 3234567890.323456789
            blobC: "blob-charlie"
            timestampC: 1786763045
            modeC: "CHARLIE"
            stringsC: ["charlie-1", "charlie-2", "charlie-3"]
            integersC: { seven: 7, eight: 8, nine: 9 }
            nestedC: { label: "nested-charlie", count: 33, active: true, ratio: 3.125 }
            choiceC: {
                nested: { label: "choice-charlie", count: 303, active: false, ratio: 30.125 }
            }
            stringD: "delta"
            integerD: 404
            longD: 40000000004
            booleanD: false
            doubleD: 404.125
            floatD: 404.25
            byteD: 44
            shortD: 4004
            bigIntegerD: 423456789012345678904
            bigDecimalD: 4234567890.423456789
            blobD: "blob-delta"
            timestampD: 1786766706
            modeD: "DELTA"
            stringsD: ["delta-1", "delta-2", "delta-3"]
            integersD: { ten: 10, eleven: 11, twelve: 12 }
            nestedD: { label: "nested-delta", count: 44, active: false, ratio: 4.125 }
            choiceD: { text: "choice-delta" }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "rpcv2Cbor_WideTypesRequest_S"
        protocol: rpcv2Cbor
        method: "POST"
        uri: "/"
        headers: { "smithy-protocol": "rpc-v2-cbor" }
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "rpcv2Cbor_WideTypesRequest_M"
        protocol: rpcv2Cbor
        method: "POST"
        uri: "/"
        headers: { "smithy-protocol": "rpc-v2-cbor" }
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
        }
        tags: ["serde-benchmark"]
    }
    {
        id: "rpcv2Cbor_WideTypesRequest_L"
        protocol: rpcv2Cbor
        method: "POST"
        uri: "/"
        headers: { "smithy-protocol": "rpc-v2-cbor" }
        params: {
            label: "alpha"
            query: "query-alpha"
            requestHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
            stringC: "charlie"
            integerC: 303
            longC: 30000000003
            booleanC: true
            doubleC: 303.125
            floatC: 303.25
            byteC: 33
            shortC: 3003
            bigIntegerC: 323456789012345678903
            bigDecimalC: 3234567890.323456789
            blobC: "blob-charlie"
            timestampC: 1786763045
            modeC: "CHARLIE"
            stringsC: ["charlie-1", "charlie-2", "charlie-3"]
            integersC: { seven: 7, eight: 8, nine: 9 }
            nestedC: { label: "nested-charlie", count: 33, active: true, ratio: 3.125 }
            choiceC: {
                nested: { label: "choice-charlie", count: 303, active: false, ratio: 30.125 }
            }
            stringD: "delta"
            integerD: 404
            longD: 40000000004
            booleanD: false
            doubleD: 404.125
            floatD: 404.25
            byteD: 44
            shortD: 4004
            bigIntegerD: 423456789012345678904
            bigDecimalD: 4234567890.423456789
            blobD: "blob-delta"
            timestampD: 1786766706
            modeD: "DELTA"
            stringsD: ["delta-1", "delta-2", "delta-3"]
            integersD: { ten: 10, eleven: 11, twelve: 12 }
            nestedD: { label: "nested-delta", count: 44, active: false, ratio: 4.125 }
            choiceD: { text: "choice-delta" }
        }
        tags: ["serde-benchmark"]
    }
])
@httpResponseTests([
    {
        id: "awsJson1_0_WideTypesResponse_S"
        protocol: awsJson1_0
        code: 200
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
        }
        body: """
            {"stringA":"alpha","integerA":101,"longA":10000000001,"booleanA":true,"doubleA":101.125,"floatA":101.25,"byteA":11,"shortA":1001,"bigIntegerA":123456789012345678901,"bigDecimalA":1234567890.123456789,"blobA":"YmxvYi1hbHBoYQ==","timestampA":1786755723,"modeA":"ALPHA","stringsA":["alpha-1","alpha-2","alpha-3"],"integersA":{"one":1,"two":2,"three":3},"nestedA":{"label":"nested-alpha","count":11,"active":true,"ratio":1.125},"choiceA":{"text":"choice-alpha"},"responseHeader":"header-alpha"}
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "awsJson1_0_WideTypesResponse_M"
        protocol: awsJson1_0
        code: 200
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
        }
        body: """
            {"stringA":"alpha","integerA":101,"longA":10000000001,"booleanA":true,"doubleA":101.125,"floatA":101.25,"byteA":11,"shortA":1001,"bigIntegerA":123456789012345678901,"bigDecimalA":1234567890.123456789,"blobA":"YmxvYi1hbHBoYQ==","timestampA":1786755723,"modeA":"ALPHA","stringsA":["alpha-1","alpha-2","alpha-3"],"integersA":{"one":1,"two":2,"three":3},"nestedA":{"label":"nested-alpha","count":11,"active":true,"ratio":1.125},"choiceA":{"text":"choice-alpha"},"stringB":"bravo","integerB":202,"longB":20000000002,"booleanB":false,"doubleB":202.125,"floatB":202.25,"byteB":22,"shortB":2002,"bigIntegerB":223456789012345678902,"bigDecimalB":2234567890.223456789,"blobB":"YmxvYi1icmF2bw==","timestampB":1786759384,"modeB":"BRAVO","stringsB":["bravo-1","bravo-2","bravo-3"],"integersB":{"four":4,"five":5,"six":6},"nestedB":{"label":"nested-bravo","count":22,"active":false,"ratio":2.125},"choiceB":{"number":202},"responseHeader":"header-alpha"}
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "awsJson1_0_WideTypesResponse_L"
        protocol: awsJson1_0
        code: 200
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
            stringC: "charlie"
            integerC: 303
            longC: 30000000003
            booleanC: true
            doubleC: 303.125
            floatC: 303.25
            byteC: 33
            shortC: 3003
            bigIntegerC: 323456789012345678903
            bigDecimalC: 3234567890.323456789
            blobC: "blob-charlie"
            timestampC: 1786763045
            modeC: "CHARLIE"
            stringsC: ["charlie-1", "charlie-2", "charlie-3"]
            integersC: { seven: 7, eight: 8, nine: 9 }
            nestedC: { label: "nested-charlie", count: 33, active: true, ratio: 3.125 }
            choiceC: {
                nested: { label: "choice-charlie", count: 303, active: false, ratio: 30.125 }
            }
            stringD: "delta"
            integerD: 404
            longD: 40000000004
            booleanD: false
            doubleD: 404.125
            floatD: 404.25
            byteD: 44
            shortD: 4004
            bigIntegerD: 423456789012345678904
            bigDecimalD: 4234567890.423456789
            blobD: "blob-delta"
            timestampD: 1786766706
            modeD: "DELTA"
            stringsD: ["delta-1", "delta-2", "delta-3"]
            integersD: { ten: 10, eleven: 11, twelve: 12 }
            nestedD: { label: "nested-delta", count: 44, active: false, ratio: 4.125 }
            choiceD: { text: "choice-delta" }
        }
        body: """
            {"stringA":"alpha","integerA":101,"longA":10000000001,"booleanA":true,"doubleA":101.125,"floatA":101.25,"byteA":11,"shortA":1001,"bigIntegerA":123456789012345678901,"bigDecimalA":1234567890.123456789,"blobA":"YmxvYi1hbHBoYQ==","timestampA":1786755723,"modeA":"ALPHA","stringsA":["alpha-1","alpha-2","alpha-3"],"integersA":{"one":1,"two":2,"three":3},"nestedA":{"label":"nested-alpha","count":11,"active":true,"ratio":1.125},"choiceA":{"text":"choice-alpha"},"stringB":"bravo","integerB":202,"longB":20000000002,"booleanB":false,"doubleB":202.125,"floatB":202.25,"byteB":22,"shortB":2002,"bigIntegerB":223456789012345678902,"bigDecimalB":2234567890.223456789,"blobB":"YmxvYi1icmF2bw==","timestampB":1786759384,"modeB":"BRAVO","stringsB":["bravo-1","bravo-2","bravo-3"],"integersB":{"four":4,"five":5,"six":6},"nestedB":{"label":"nested-bravo","count":22,"active":false,"ratio":2.125},"choiceB":{"number":202},"stringC":"charlie","integerC":303,"longC":30000000003,"booleanC":true,"doubleC":303.125,"floatC":303.25,"byteC":33,"shortC":3003,"bigIntegerC":323456789012345678903,"bigDecimalC":3234567890.323456789,"blobC":"YmxvYi1jaGFybGll","timestampC":1786763045,"modeC":"CHARLIE","stringsC":["charlie-1","charlie-2","charlie-3"],"integersC":{"seven":7,"eight":8,"nine":9},"nestedC":{"label":"nested-charlie","count":33,"active":true,"ratio":3.125},"choiceC":{"nested":{"label":"choice-charlie","count":303,"active":false,"ratio":30.125}},"stringD":"delta","integerD":404,"longD":40000000004,"booleanD":false,"doubleD":404.125,"floatD":404.25,"byteD":44,"shortD":4004,"bigIntegerD":423456789012345678904,"bigDecimalD":4234567890.423456789,"blobD":"YmxvYi1kZWx0YQ==","timestampD":1786766706,"modeD":"DELTA","stringsD":["delta-1","delta-2","delta-3"],"integersD":{"ten":10,"eleven":11,"twelve":12},"nestedD":{"label":"nested-delta","count":44,"active":false,"ratio":4.125},"choiceD":{"text":"choice-delta"},"responseHeader":"header-alpha"}
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "restJson1_WideTypesResponse_S"
        protocol: restJson1
        code: 200
        headers: { "X-Wide-Response": "header-alpha" }
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
        }
        body: """
            {"stringA":"alpha","integerA":101,"longA":10000000001,"booleanA":true,"doubleA":101.125,"floatA":101.25,"byteA":11,"shortA":1001,"bigIntegerA":123456789012345678901,"bigDecimalA":1234567890.123456789,"blobA":"YmxvYi1hbHBoYQ==","timestampA":1786755723,"modeA":"ALPHA","stringsA":["alpha-1","alpha-2","alpha-3"],"integersA":{"one":1,"two":2,"three":3},"nestedA":{"label":"nested-alpha","count":11,"active":true,"ratio":1.125},"choiceA":{"text":"choice-alpha"}}
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "restJson1_WideTypesResponse_M"
        protocol: restJson1
        code: 200
        headers: { "X-Wide-Response": "header-alpha" }
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
        }
        body: """
            {"stringA":"alpha","integerA":101,"longA":10000000001,"booleanA":true,"doubleA":101.125,"floatA":101.25,"byteA":11,"shortA":1001,"bigIntegerA":123456789012345678901,"bigDecimalA":1234567890.123456789,"blobA":"YmxvYi1hbHBoYQ==","timestampA":1786755723,"modeA":"ALPHA","stringsA":["alpha-1","alpha-2","alpha-3"],"integersA":{"one":1,"two":2,"three":3},"nestedA":{"label":"nested-alpha","count":11,"active":true,"ratio":1.125},"choiceA":{"text":"choice-alpha"},"stringB":"bravo","integerB":202,"longB":20000000002,"booleanB":false,"doubleB":202.125,"floatB":202.25,"byteB":22,"shortB":2002,"bigIntegerB":223456789012345678902,"bigDecimalB":2234567890.223456789,"blobB":"YmxvYi1icmF2bw==","timestampB":1786759384,"modeB":"BRAVO","stringsB":["bravo-1","bravo-2","bravo-3"],"integersB":{"four":4,"five":5,"six":6},"nestedB":{"label":"nested-bravo","count":22,"active":false,"ratio":2.125},"choiceB":{"number":202}}
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "restJson1_WideTypesResponse_L"
        protocol: restJson1
        code: 200
        headers: { "X-Wide-Response": "header-alpha" }
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
            stringC: "charlie"
            integerC: 303
            longC: 30000000003
            booleanC: true
            doubleC: 303.125
            floatC: 303.25
            byteC: 33
            shortC: 3003
            bigIntegerC: 323456789012345678903
            bigDecimalC: 3234567890.323456789
            blobC: "blob-charlie"
            timestampC: 1786763045
            modeC: "CHARLIE"
            stringsC: ["charlie-1", "charlie-2", "charlie-3"]
            integersC: { seven: 7, eight: 8, nine: 9 }
            nestedC: { label: "nested-charlie", count: 33, active: true, ratio: 3.125 }
            choiceC: {
                nested: { label: "choice-charlie", count: 303, active: false, ratio: 30.125 }
            }
            stringD: "delta"
            integerD: 404
            longD: 40000000004
            booleanD: false
            doubleD: 404.125
            floatD: 404.25
            byteD: 44
            shortD: 4004
            bigIntegerD: 423456789012345678904
            bigDecimalD: 4234567890.423456789
            blobD: "blob-delta"
            timestampD: 1786766706
            modeD: "DELTA"
            stringsD: ["delta-1", "delta-2", "delta-3"]
            integersD: { ten: 10, eleven: 11, twelve: 12 }
            nestedD: { label: "nested-delta", count: 44, active: false, ratio: 4.125 }
            choiceD: { text: "choice-delta" }
        }
        body: """
            {"stringA":"alpha","integerA":101,"longA":10000000001,"booleanA":true,"doubleA":101.125,"floatA":101.25,"byteA":11,"shortA":1001,"bigIntegerA":123456789012345678901,"bigDecimalA":1234567890.123456789,"blobA":"YmxvYi1hbHBoYQ==","timestampA":1786755723,"modeA":"ALPHA","stringsA":["alpha-1","alpha-2","alpha-3"],"integersA":{"one":1,"two":2,"three":3},"nestedA":{"label":"nested-alpha","count":11,"active":true,"ratio":1.125},"choiceA":{"text":"choice-alpha"},"stringB":"bravo","integerB":202,"longB":20000000002,"booleanB":false,"doubleB":202.125,"floatB":202.25,"byteB":22,"shortB":2002,"bigIntegerB":223456789012345678902,"bigDecimalB":2234567890.223456789,"blobB":"YmxvYi1icmF2bw==","timestampB":1786759384,"modeB":"BRAVO","stringsB":["bravo-1","bravo-2","bravo-3"],"integersB":{"four":4,"five":5,"six":6},"nestedB":{"label":"nested-bravo","count":22,"active":false,"ratio":2.125},"choiceB":{"number":202},"stringC":"charlie","integerC":303,"longC":30000000003,"booleanC":true,"doubleC":303.125,"floatC":303.25,"byteC":33,"shortC":3003,"bigIntegerC":323456789012345678903,"bigDecimalC":3234567890.323456789,"blobC":"YmxvYi1jaGFybGll","timestampC":1786763045,"modeC":"CHARLIE","stringsC":["charlie-1","charlie-2","charlie-3"],"integersC":{"seven":7,"eight":8,"nine":9},"nestedC":{"label":"nested-charlie","count":33,"active":true,"ratio":3.125},"choiceC":{"nested":{"label":"choice-charlie","count":303,"active":false,"ratio":30.125}},"stringD":"delta","integerD":404,"longD":40000000004,"booleanD":false,"doubleD":404.125,"floatD":404.25,"byteD":44,"shortD":4004,"bigIntegerD":423456789012345678904,"bigDecimalD":4234567890.423456789,"blobD":"YmxvYi1kZWx0YQ==","timestampD":1786766706,"modeD":"DELTA","stringsD":["delta-1","delta-2","delta-3"],"integersD":{"ten":10,"eleven":11,"twelve":12},"nestedD":{"label":"nested-delta","count":44,"active":false,"ratio":4.125},"choiceD":{"text":"choice-delta"}}
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "restXml_WideTypesResponse_S"
        protocol: restXml
        code: 200
        headers: { "X-Wide-Response": "header-alpha" }
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
        }
        body: """
            <WideTypesOutput><stringA>alpha</stringA><integerA>101</integerA><longA>10000000001</longA><booleanA>true</booleanA><doubleA>101.125</doubleA><floatA>101.25</floatA><byteA>11</byteA><shortA>1001</shortA><bigIntegerA>123456789012345678901</bigIntegerA><bigDecimalA>1234567890.123456789</bigDecimalA><blobA>YmxvYi1hbHBoYQ==</blobA><timestampA>2026-08-15T01:02:03Z</timestampA><modeA>ALPHA</modeA><stringsA><member>alpha-1</member><member>alpha-2</member><member>alpha-3</member></stringsA><integersA><entry><key>one</key><value>1</value></entry><entry><key>two</key><value>2</value></entry><entry><key>three</key><value>3</value></entry></integersA><nestedA><label>nested-alpha</label><count>11</count><active>true</active><ratio>1.125</ratio></nestedA><choiceA><text>choice-alpha</text></choiceA></WideTypesOutput>
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "restXml_WideTypesResponse_M"
        protocol: restXml
        code: 200
        headers: { "X-Wide-Response": "header-alpha" }
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
        }
        body: """
            <WideTypesOutput><stringA>alpha</stringA><integerA>101</integerA><longA>10000000001</longA><booleanA>true</booleanA><doubleA>101.125</doubleA><floatA>101.25</floatA><byteA>11</byteA><shortA>1001</shortA><bigIntegerA>123456789012345678901</bigIntegerA><bigDecimalA>1234567890.123456789</bigDecimalA><blobA>YmxvYi1hbHBoYQ==</blobA><timestampA>2026-08-15T01:02:03Z</timestampA><modeA>ALPHA</modeA><stringsA><member>alpha-1</member><member>alpha-2</member><member>alpha-3</member></stringsA><integersA><entry><key>one</key><value>1</value></entry><entry><key>two</key><value>2</value></entry><entry><key>three</key><value>3</value></entry></integersA><nestedA><label>nested-alpha</label><count>11</count><active>true</active><ratio>1.125</ratio></nestedA><choiceA><text>choice-alpha</text></choiceA><stringB>bravo</stringB><integerB>202</integerB><longB>20000000002</longB><booleanB>false</booleanB><doubleB>202.125</doubleB><floatB>202.25</floatB><byteB>22</byteB><shortB>2002</shortB><bigIntegerB>223456789012345678902</bigIntegerB><bigDecimalB>2234567890.223456789</bigDecimalB><blobB>YmxvYi1icmF2bw==</blobB><timestampB>2026-08-15T02:03:04Z</timestampB><modeB>BRAVO</modeB><stringsB><member>bravo-1</member><member>bravo-2</member><member>bravo-3</member></stringsB><integersB><entry><key>four</key><value>4</value></entry><entry><key>five</key><value>5</value></entry><entry><key>six</key><value>6</value></entry></integersB><nestedB><label>nested-bravo</label><count>22</count><active>false</active><ratio>2.125</ratio></nestedB><choiceB><number>202</number></choiceB></WideTypesOutput>
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "restXml_WideTypesResponse_L"
        protocol: restXml
        code: 200
        headers: { "X-Wide-Response": "header-alpha" }
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
            stringC: "charlie"
            integerC: 303
            longC: 30000000003
            booleanC: true
            doubleC: 303.125
            floatC: 303.25
            byteC: 33
            shortC: 3003
            bigIntegerC: 323456789012345678903
            bigDecimalC: 3234567890.323456789
            blobC: "blob-charlie"
            timestampC: 1786763045
            modeC: "CHARLIE"
            stringsC: ["charlie-1", "charlie-2", "charlie-3"]
            integersC: { seven: 7, eight: 8, nine: 9 }
            nestedC: { label: "nested-charlie", count: 33, active: true, ratio: 3.125 }
            choiceC: {
                nested: { label: "choice-charlie", count: 303, active: false, ratio: 30.125 }
            }
            stringD: "delta"
            integerD: 404
            longD: 40000000004
            booleanD: false
            doubleD: 404.125
            floatD: 404.25
            byteD: 44
            shortD: 4004
            bigIntegerD: 423456789012345678904
            bigDecimalD: 4234567890.423456789
            blobD: "blob-delta"
            timestampD: 1786766706
            modeD: "DELTA"
            stringsD: ["delta-1", "delta-2", "delta-3"]
            integersD: { ten: 10, eleven: 11, twelve: 12 }
            nestedD: { label: "nested-delta", count: 44, active: false, ratio: 4.125 }
            choiceD: { text: "choice-delta" }
        }
        body: """
            <WideTypesOutput><stringA>alpha</stringA><integerA>101</integerA><longA>10000000001</longA><booleanA>true</booleanA><doubleA>101.125</doubleA><floatA>101.25</floatA><byteA>11</byteA><shortA>1001</shortA><bigIntegerA>123456789012345678901</bigIntegerA><bigDecimalA>1234567890.123456789</bigDecimalA><blobA>YmxvYi1hbHBoYQ==</blobA><timestampA>2026-08-15T01:02:03Z</timestampA><modeA>ALPHA</modeA><stringsA><member>alpha-1</member><member>alpha-2</member><member>alpha-3</member></stringsA><integersA><entry><key>one</key><value>1</value></entry><entry><key>two</key><value>2</value></entry><entry><key>three</key><value>3</value></entry></integersA><nestedA><label>nested-alpha</label><count>11</count><active>true</active><ratio>1.125</ratio></nestedA><choiceA><text>choice-alpha</text></choiceA><stringB>bravo</stringB><integerB>202</integerB><longB>20000000002</longB><booleanB>false</booleanB><doubleB>202.125</doubleB><floatB>202.25</floatB><byteB>22</byteB><shortB>2002</shortB><bigIntegerB>223456789012345678902</bigIntegerB><bigDecimalB>2234567890.223456789</bigDecimalB><blobB>YmxvYi1icmF2bw==</blobB><timestampB>2026-08-15T02:03:04Z</timestampB><modeB>BRAVO</modeB><stringsB><member>bravo-1</member><member>bravo-2</member><member>bravo-3</member></stringsB><integersB><entry><key>four</key><value>4</value></entry><entry><key>five</key><value>5</value></entry><entry><key>six</key><value>6</value></entry></integersB><nestedB><label>nested-bravo</label><count>22</count><active>false</active><ratio>2.125</ratio></nestedB><choiceB><number>202</number></choiceB><stringC>charlie</stringC><integerC>303</integerC><longC>30000000003</longC><booleanC>true</booleanC><doubleC>303.125</doubleC><floatC>303.25</floatC><byteC>33</byteC><shortC>3003</shortC><bigIntegerC>323456789012345678903</bigIntegerC><bigDecimalC>3234567890.323456789</bigDecimalC><blobC>YmxvYi1jaGFybGll</blobC><timestampC>2026-08-15T03:04:05Z</timestampC><modeC>CHARLIE</modeC><stringsC><member>charlie-1</member><member>charlie-2</member><member>charlie-3</member></stringsC><integersC><entry><key>seven</key><value>7</value></entry><entry><key>eight</key><value>8</value></entry><entry><key>nine</key><value>9</value></entry></integersC><nestedC><label>nested-charlie</label><count>33</count><active>true</active><ratio>3.125</ratio></nestedC><choiceC><nested><label>choice-charlie</label><count>303</count><active>false</active><ratio>30.125</ratio></nested></choiceC><stringD>delta</stringD><integerD>404</integerD><longD>40000000004</longD><booleanD>false</booleanD><doubleD>404.125</doubleD><floatD>404.25</floatD><byteD>44</byteD><shortD>4004</shortD><bigIntegerD>423456789012345678904</bigIntegerD><bigDecimalD>4234567890.423456789</bigDecimalD><blobD>YmxvYi1kZWx0YQ==</blobD><timestampD>2026-08-15T04:05:06Z</timestampD><modeD>DELTA</modeD><stringsD><member>delta-1</member><member>delta-2</member><member>delta-3</member></stringsD><integersD><entry><key>ten</key><value>10</value></entry><entry><key>eleven</key><value>11</value></entry><entry><key>twelve</key><value>12</value></entry></integersD><nestedD><label>nested-delta</label><count>44</count><active>false</active><ratio>4.125</ratio></nestedD><choiceD><text>choice-delta</text></choiceD></WideTypesOutput>
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "awsQuery_WideTypesResponse_S"
        protocol: awsQuery
        code: 200
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
        }
        body: """
            <WideTypesResponse><WideTypesResult><stringA>alpha</stringA><integerA>101</integerA><longA>10000000001</longA><booleanA>true</booleanA><doubleA>101.125</doubleA><floatA>101.25</floatA><byteA>11</byteA><shortA>1001</shortA><bigIntegerA>123456789012345678901</bigIntegerA><bigDecimalA>1234567890.123456789</bigDecimalA><blobA>YmxvYi1hbHBoYQ==</blobA><timestampA>2026-08-15T01:02:03Z</timestampA><modeA>ALPHA</modeA><stringsA><member>alpha-1</member><member>alpha-2</member><member>alpha-3</member></stringsA><integersA><entry><key>one</key><value>1</value></entry><entry><key>two</key><value>2</value></entry><entry><key>three</key><value>3</value></entry></integersA><nestedA><label>nested-alpha</label><count>11</count><active>true</active><ratio>1.125</ratio></nestedA><choiceA><text>choice-alpha</text></choiceA><responseHeader>header-alpha</responseHeader></WideTypesResult></WideTypesResponse>
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "awsQuery_WideTypesResponse_M"
        protocol: awsQuery
        code: 200
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
        }
        body: """
            <WideTypesResponse><WideTypesResult><stringA>alpha</stringA><integerA>101</integerA><longA>10000000001</longA><booleanA>true</booleanA><doubleA>101.125</doubleA><floatA>101.25</floatA><byteA>11</byteA><shortA>1001</shortA><bigIntegerA>123456789012345678901</bigIntegerA><bigDecimalA>1234567890.123456789</bigDecimalA><blobA>YmxvYi1hbHBoYQ==</blobA><timestampA>2026-08-15T01:02:03Z</timestampA><modeA>ALPHA</modeA><stringsA><member>alpha-1</member><member>alpha-2</member><member>alpha-3</member></stringsA><integersA><entry><key>one</key><value>1</value></entry><entry><key>two</key><value>2</value></entry><entry><key>three</key><value>3</value></entry></integersA><nestedA><label>nested-alpha</label><count>11</count><active>true</active><ratio>1.125</ratio></nestedA><choiceA><text>choice-alpha</text></choiceA><stringB>bravo</stringB><integerB>202</integerB><longB>20000000002</longB><booleanB>false</booleanB><doubleB>202.125</doubleB><floatB>202.25</floatB><byteB>22</byteB><shortB>2002</shortB><bigIntegerB>223456789012345678902</bigIntegerB><bigDecimalB>2234567890.223456789</bigDecimalB><blobB>YmxvYi1icmF2bw==</blobB><timestampB>2026-08-15T02:03:04Z</timestampB><modeB>BRAVO</modeB><stringsB><member>bravo-1</member><member>bravo-2</member><member>bravo-3</member></stringsB><integersB><entry><key>four</key><value>4</value></entry><entry><key>five</key><value>5</value></entry><entry><key>six</key><value>6</value></entry></integersB><nestedB><label>nested-bravo</label><count>22</count><active>false</active><ratio>2.125</ratio></nestedB><choiceB><number>202</number></choiceB><responseHeader>header-alpha</responseHeader></WideTypesResult></WideTypesResponse>
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "awsQuery_WideTypesResponse_L"
        protocol: awsQuery
        code: 200
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
            stringC: "charlie"
            integerC: 303
            longC: 30000000003
            booleanC: true
            doubleC: 303.125
            floatC: 303.25
            byteC: 33
            shortC: 3003
            bigIntegerC: 323456789012345678903
            bigDecimalC: 3234567890.323456789
            blobC: "blob-charlie"
            timestampC: 1786763045
            modeC: "CHARLIE"
            stringsC: ["charlie-1", "charlie-2", "charlie-3"]
            integersC: { seven: 7, eight: 8, nine: 9 }
            nestedC: { label: "nested-charlie", count: 33, active: true, ratio: 3.125 }
            choiceC: {
                nested: { label: "choice-charlie", count: 303, active: false, ratio: 30.125 }
            }
            stringD: "delta"
            integerD: 404
            longD: 40000000004
            booleanD: false
            doubleD: 404.125
            floatD: 404.25
            byteD: 44
            shortD: 4004
            bigIntegerD: 423456789012345678904
            bigDecimalD: 4234567890.423456789
            blobD: "blob-delta"
            timestampD: 1786766706
            modeD: "DELTA"
            stringsD: ["delta-1", "delta-2", "delta-3"]
            integersD: { ten: 10, eleven: 11, twelve: 12 }
            nestedD: { label: "nested-delta", count: 44, active: false, ratio: 4.125 }
            choiceD: { text: "choice-delta" }
        }
        body: """
            <WideTypesResponse><WideTypesResult><stringA>alpha</stringA><integerA>101</integerA><longA>10000000001</longA><booleanA>true</booleanA><doubleA>101.125</doubleA><floatA>101.25</floatA><byteA>11</byteA><shortA>1001</shortA><bigIntegerA>123456789012345678901</bigIntegerA><bigDecimalA>1234567890.123456789</bigDecimalA><blobA>YmxvYi1hbHBoYQ==</blobA><timestampA>2026-08-15T01:02:03Z</timestampA><modeA>ALPHA</modeA><stringsA><member>alpha-1</member><member>alpha-2</member><member>alpha-3</member></stringsA><integersA><entry><key>one</key><value>1</value></entry><entry><key>two</key><value>2</value></entry><entry><key>three</key><value>3</value></entry></integersA><nestedA><label>nested-alpha</label><count>11</count><active>true</active><ratio>1.125</ratio></nestedA><choiceA><text>choice-alpha</text></choiceA><stringB>bravo</stringB><integerB>202</integerB><longB>20000000002</longB><booleanB>false</booleanB><doubleB>202.125</doubleB><floatB>202.25</floatB><byteB>22</byteB><shortB>2002</shortB><bigIntegerB>223456789012345678902</bigIntegerB><bigDecimalB>2234567890.223456789</bigDecimalB><blobB>YmxvYi1icmF2bw==</blobB><timestampB>2026-08-15T02:03:04Z</timestampB><modeB>BRAVO</modeB><stringsB><member>bravo-1</member><member>bravo-2</member><member>bravo-3</member></stringsB><integersB><entry><key>four</key><value>4</value></entry><entry><key>five</key><value>5</value></entry><entry><key>six</key><value>6</value></entry></integersB><nestedB><label>nested-bravo</label><count>22</count><active>false</active><ratio>2.125</ratio></nestedB><choiceB><number>202</number></choiceB><stringC>charlie</stringC><integerC>303</integerC><longC>30000000003</longC><booleanC>true</booleanC><doubleC>303.125</doubleC><floatC>303.25</floatC><byteC>33</byteC><shortC>3003</shortC><bigIntegerC>323456789012345678903</bigIntegerC><bigDecimalC>3234567890.323456789</bigDecimalC><blobC>YmxvYi1jaGFybGll</blobC><timestampC>2026-08-15T03:04:05Z</timestampC><modeC>CHARLIE</modeC><stringsC><member>charlie-1</member><member>charlie-2</member><member>charlie-3</member></stringsC><integersC><entry><key>seven</key><value>7</value></entry><entry><key>eight</key><value>8</value></entry><entry><key>nine</key><value>9</value></entry></integersC><nestedC><label>nested-charlie</label><count>33</count><active>true</active><ratio>3.125</ratio></nestedC><choiceC><nested><label>choice-charlie</label><count>303</count><active>false</active><ratio>30.125</ratio></nested></choiceC><stringD>delta</stringD><integerD>404</integerD><longD>40000000004</longD><booleanD>false</booleanD><doubleD>404.125</doubleD><floatD>404.25</floatD><byteD>44</byteD><shortD>4004</shortD><bigIntegerD>423456789012345678904</bigIntegerD><bigDecimalD>4234567890.423456789</bigDecimalD><blobD>YmxvYi1kZWx0YQ==</blobD><timestampD>2026-08-15T04:05:06Z</timestampD><modeD>DELTA</modeD><stringsD><member>delta-1</member><member>delta-2</member><member>delta-3</member></stringsD><integersD><entry><key>ten</key><value>10</value></entry><entry><key>eleven</key><value>11</value></entry><entry><key>twelve</key><value>12</value></entry></integersD><nestedD><label>nested-delta</label><count>44</count><active>false</active><ratio>4.125</ratio></nestedD><choiceD><text>choice-delta</text></choiceD><responseHeader>header-alpha</responseHeader></WideTypesResult></WideTypesResponse>
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "rpcv2Cbor_WideTypesResponse_S"
        protocol: rpcv2Cbor
        code: 200
        headers: { "smithy-protocol": "rpc-v2-cbor" }
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
        }
        body: """
            v2dzdHJpbmdBZWFscGhhaGludGVnZXJBGGVlbG9uZ0EbAAAAAlQL5AFoYm9vbGVhbkH1Z2RvdWJsZUH7QFlIAAAAAABmZmxvYXRB
            +kLKgABlYnl0ZUELZnNob3J0QRkD6WtiaWdJbnRlZ2VyQcJJBrFOn4EvNmw1a2JpZ0RlY2ltYWxBxIIoGxEiEPR96YEVZWJsb2JB
            SmJsb2ItYWxwaGFqdGltZXN0YW1wQcEaan+6i2Vtb2RlQWVBTFBIQWhzdHJpbmdzQYNnYWxwaGEtMWdhbHBoYS0yZ2FscGhhLTNp
            aW50ZWdlcnNBo2NvbmUBY3R3bwJldGhyZWUDZ25lc3RlZEG/ZWxhYmVsbG5lc3RlZC1hbHBoYWVjb3VudAtmYWN0aXZl9WVyYXRp
            b/s/8gAAAAAAAP9nY2hvaWNlQb9kdGV4dGxjaG9pY2UtYWxwaGH/bnJlc3BvbnNlSGVhZGVybGhlYWRlci1hbHBoYf8=
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "rpcv2Cbor_WideTypesResponse_M"
        protocol: rpcv2Cbor
        code: 200
        headers: { "smithy-protocol": "rpc-v2-cbor" }
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
        }
        body: """
            v2dzdHJpbmdBZWFscGhhaGludGVnZXJBGGVlbG9uZ0EbAAAAAlQL5AFoYm9vbGVhbkH1Z2RvdWJsZUH7QFlIAAAAAABmZmxvYXRB
            +kLKgABlYnl0ZUELZnNob3J0QRkD6WtiaWdJbnRlZ2VyQcJJBrFOn4EvNmw1a2JpZ0RlY2ltYWxBxIIoGxEiEPR96YEVZWJsb2JB
            SmJsb2ItYWxwaGFqdGltZXN0YW1wQcEaan+6i2Vtb2RlQWVBTFBIQWhzdHJpbmdzQYNnYWxwaGEtMWdhbHBoYS0yZ2FscGhhLTNp
            aW50ZWdlcnNBo2NvbmUBY3R3bwJldGhyZWUDZ25lc3RlZEG/ZWxhYmVsbG5lc3RlZC1hbHBoYWVjb3VudAtmYWN0aXZl9WVyYXRp
            b/s/8gAAAAAAAP9nY2hvaWNlQb9kdGV4dGxjaG9pY2UtYWxwaGH/Z3N0cmluZ0JlYnJhdm9oaW50ZWdlckIYymVsb25nQhsAAAAE
            qBfIAmhib29sZWFuQvRnZG91YmxlQvtAaUQAAAAAAGZmbG9hdEL6Q0pAAGVieXRlQhZmc2hvcnRCGQfSa2JpZ0ludGVnZXJCwkkM
            HRX9rpJGbDZrYmlnRGVjaW1hbELEgigbHwLHqCtDYhVlYmxvYkJKYmxvYi1icmF2b2p0aW1lc3RhbXBCwRpqf8jYZW1vZGVCZUJS
            QVZPaHN0cmluZ3NCg2dicmF2by0xZ2JyYXZvLTJnYnJhdm8tM2lpbnRlZ2Vyc0KjZGZvdXIEZGZpdmUFY3NpeAZnbmVzdGVkQr9l
            bGFiZWxsbmVzdGVkLWJyYXZvZWNvdW50FmZhY3RpdmX0ZXJhdGlv+0ABAAAAAAAA/2djaG9pY2VCv2ZudW1iZXIYyv9ucmVzcG9u
            c2VIZWFkZXJsaGVhZGVyLWFscGhh/w==
            """
        tags: ["serde-benchmark"]
    }
    {
        id: "rpcv2Cbor_WideTypesResponse_L"
        protocol: rpcv2Cbor
        code: 200
        headers: { "smithy-protocol": "rpc-v2-cbor" }
        params: {
            responseHeader: "header-alpha"
            stringA: "alpha"
            integerA: 101
            longA: 10000000001
            booleanA: true
            doubleA: 101.125
            floatA: 101.25
            byteA: 11
            shortA: 1001
            bigIntegerA: 123456789012345678901
            bigDecimalA: 1234567890.123456789
            blobA: "blob-alpha"
            timestampA: 1786755723
            modeA: "ALPHA"
            stringsA: ["alpha-1", "alpha-2", "alpha-3"]
            integersA: { one: 1, two: 2, three: 3 }
            nestedA: { label: "nested-alpha", count: 11, active: true, ratio: 1.125 }
            choiceA: { text: "choice-alpha" }
            stringB: "bravo"
            integerB: 202
            longB: 20000000002
            booleanB: false
            doubleB: 202.125
            floatB: 202.25
            byteB: 22
            shortB: 2002
            bigIntegerB: 223456789012345678902
            bigDecimalB: 2234567890.223456789
            blobB: "blob-bravo"
            timestampB: 1786759384
            modeB: "BRAVO"
            stringsB: ["bravo-1", "bravo-2", "bravo-3"]
            integersB: { four: 4, five: 5, six: 6 }
            nestedB: { label: "nested-bravo", count: 22, active: false, ratio: 2.125 }
            choiceB: { number: 202 }
            stringC: "charlie"
            integerC: 303
            longC: 30000000003
            booleanC: true
            doubleC: 303.125
            floatC: 303.25
            byteC: 33
            shortC: 3003
            bigIntegerC: 323456789012345678903
            bigDecimalC: 3234567890.323456789
            blobC: "blob-charlie"
            timestampC: 1786763045
            modeC: "CHARLIE"
            stringsC: ["charlie-1", "charlie-2", "charlie-3"]
            integersC: { seven: 7, eight: 8, nine: 9 }
            nestedC: { label: "nested-charlie", count: 33, active: true, ratio: 3.125 }
            choiceC: {
                nested: { label: "choice-charlie", count: 303, active: false, ratio: 30.125 }
            }
            stringD: "delta"
            integerD: 404
            longD: 40000000004
            booleanD: false
            doubleD: 404.125
            floatD: 404.25
            byteD: 44
            shortD: 4004
            bigIntegerD: 423456789012345678904
            bigDecimalD: 4234567890.423456789
            blobD: "blob-delta"
            timestampD: 1786766706
            modeD: "DELTA"
            stringsD: ["delta-1", "delta-2", "delta-3"]
            integersD: { ten: 10, eleven: 11, twelve: 12 }
            nestedD: { label: "nested-delta", count: 44, active: false, ratio: 4.125 }
            choiceD: { text: "choice-delta" }
        }
        body: """
            v2dzdHJpbmdBZWFscGhhaGludGVnZXJBGGVlbG9uZ0EbAAAAAlQL5AFoYm9vbGVhbkH1Z2RvdWJsZUH7QFlIAAAAAABmZmxvYXRB
            +kLKgABlYnl0ZUELZnNob3J0QRkD6WtiaWdJbnRlZ2VyQcJJBrFOn4EvNmw1a2JpZ0RlY2ltYWxBxIIoGxEiEPR96YEVZWJsb2JB
            SmJsb2ItYWxwaGFqdGltZXN0YW1wQcEaan+6i2Vtb2RlQWVBTFBIQWhzdHJpbmdzQYNnYWxwaGEtMWdhbHBoYS0yZ2FscGhhLTNp
            aW50ZWdlcnNBo2NvbmUBY3R3bwJldGhyZWUDZ25lc3RlZEG/ZWxhYmVsbG5lc3RlZC1hbHBoYWVjb3VudAtmYWN0aXZl9WVyYXRp
            b/s/8gAAAAAAAP9nY2hvaWNlQb9kdGV4dGxjaG9pY2UtYWxwaGH/Z3N0cmluZ0JlYnJhdm9oaW50ZWdlckIYymVsb25nQhsAAAAE
            qBfIAmhib29sZWFuQvRnZG91YmxlQvtAaUQAAAAAAGZmbG9hdEL6Q0pAAGVieXRlQhZmc2hvcnRCGQfSa2JpZ0ludGVnZXJCwkkM
            HRX9rpJGbDZrYmlnRGVjaW1hbELEgigbHwLHqCtDYhVlYmxvYkJKYmxvYi1icmF2b2p0aW1lc3RhbXBCwRpqf8jYZW1vZGVCZUJS
            QVZPaHN0cmluZ3NCg2dicmF2by0xZ2JyYXZvLTJnYnJhdm8tM2lpbnRlZ2Vyc0KjZGZvdXIEZGZpdmUFY3NpeAZnbmVzdGVkQr9l
            bGFiZWxsbmVzdGVkLWJyYXZvZWNvdW50FmZhY3RpdmX0ZXJhdGlv+0ABAAAAAAAA/2djaG9pY2VCv2ZudW1iZXIYyv9nc3RyaW5n
            Q2djaGFybGllaGludGVnZXJDGQEvZWxvbmdDGwAAAAb8I6wDaGJvb2xlYW5D9Wdkb3VibGVD+0By8gAAAAAAZmZsb2F0Q/pDl6AA
            ZWJ5dGVDGCFmc2hvcnRDGQu7a2JpZ0ludGVnZXJDwkkRiN1b2/VWbDdrYmlnRGVjaW1hbEPEgigbLON+W9idQxVlYmxvYkNMYmxv
            Yi1jaGFybGllanRpbWVzdGFtcEPBGmp/1yVlbW9kZUNnQ0hBUkxJRWhzdHJpbmdzQ4NpY2hhcmxpZS0xaWNoYXJsaWUtMmljaGFy
            bGllLTNpaW50ZWdlcnNDo2VzZXZlbgdlZWlnaHQIZG5pbmUJZ25lc3RlZEO/ZWxhYmVsbm5lc3RlZC1jaGFybGllZWNvdW50GCFm
            YWN0aXZl9WVyYXRpb/tACQAAAAAAAP9nY2hvaWNlQ79mbmVzdGVkv2VsYWJlbG5jaG9pY2UtY2hhcmxpZWVjb3VudBkBL2ZhY3Rp
            dmX0ZXJhdGlv+0A+IAAAAAAA//9nc3RyaW5nRGVkZWx0YWhpbnRlZ2VyRBkBlGVsb25nRBsAAAAJUC+QBGhib29sZWFuRPRnZG91
            YmxlRPtAeUIAAAAAAGZmbG9hdET6Q8ogAGVieXRlRBgsZnNob3J0RBkPpGtiaWdJbnRlZ2VyRMJJFvSkuglYZmw4a2JpZ0RlY2lt
            YWxExIIoGzrENQ+F9yQVZWJsb2JESmJsb2ItZGVsdGFqdGltZXN0YW1wRMEaan/lcmVtb2RlRGVERUxUQWhzdHJpbmdzRINnZGVs
            dGEtMWdkZWx0YS0yZ2RlbHRhLTNpaW50ZWdlcnNEo2N0ZW4KZmVsZXZlbgtmdHdlbHZlDGduZXN0ZWREv2VsYWJlbGxuZXN0ZWQt
            ZGVsdGFlY291bnQYLGZhY3RpdmX0ZXJhdGlv+0AQgAAAAAAA/2djaG9pY2VEv2R0ZXh0bGNob2ljZS1kZWx0Yf9ucmVzcG9uc2VI
            ZWFkZXJsaGVhZGVyLWFscGhh/w==
            """
        tags: ["serde-benchmark"]
    }
])
operation WideTypes {
    input: WideTypesInput
    output: WideTypesOutput
}

structure WideTypesInput with [WideTypesMembers] {
    @required
    @httpLabel
    label: String

    @httpQuery("query")
    query: String

    @httpHeader("X-Wide-Request")
    requestHeader: String
}

structure WideTypesOutput with [WideTypesMembers] {
    @httpHeader("X-Wide-Response")
    responseHeader: String
}

@mixin
structure WideTypesMembers {
    @required
    stringA: String

    @required
    integerA: Integer

    @required
    longA: Long

    @required
    booleanA: Boolean

    @required
    doubleA: Double

    @required
    floatA: Float

    @required
    byteA: Byte

    @required
    shortA: Short

    @required
    bigIntegerA: BigInteger

    @required
    bigDecimalA: BigDecimal

    @required
    blobA: Blob

    @required
    timestampA: Timestamp

    @required
    modeA: WideTypesMode

    @required
    stringsA: WideTypesStringList

    @required
    integersA: WideTypesIntegerMap

    @required
    nestedA: WideTypesNested

    @required
    choiceA: WideTypesChoice

    stringB: String

    integerB: Integer

    longB: Long

    booleanB: Boolean

    doubleB: Double

    floatB: Float

    byteB: Byte

    shortB: Short

    bigIntegerB: BigInteger

    bigDecimalB: BigDecimal

    blobB: Blob

    timestampB: Timestamp

    modeB: WideTypesMode

    stringsB: WideTypesStringList

    integersB: WideTypesIntegerMap

    nestedB: WideTypesNested

    choiceB: WideTypesChoice

    stringC: String

    integerC: Integer

    longC: Long

    booleanC: Boolean

    doubleC: Double

    floatC: Float

    byteC: Byte

    shortC: Short

    bigIntegerC: BigInteger

    bigDecimalC: BigDecimal

    blobC: Blob

    timestampC: Timestamp

    modeC: WideTypesMode

    stringsC: WideTypesStringList

    integersC: WideTypesIntegerMap

    nestedC: WideTypesNested

    choiceC: WideTypesChoice

    stringD: String

    integerD: Integer

    longD: Long

    booleanD: Boolean

    doubleD: Double

    floatD: Float

    byteD: Byte

    shortD: Short

    bigIntegerD: BigInteger

    bigDecimalD: BigDecimal

    blobD: Blob

    timestampD: Timestamp

    modeD: WideTypesMode

    stringsD: WideTypesStringList

    integersD: WideTypesIntegerMap

    nestedD: WideTypesNested

    choiceD: WideTypesChoice
}

structure WideTypesNested {
    label: String
    count: Integer
    active: Boolean
    ratio: Double
}

union WideTypesChoice {
    text: String
    number: Integer
    nested: WideTypesNested
}

list WideTypesStringList {
    member: String
}

map WideTypesIntegerMap {
    key: String
    value: Integer
}

enum WideTypesMode {
    ALPHA
    BRAVO
    CHARLIE
    DELTA
}
