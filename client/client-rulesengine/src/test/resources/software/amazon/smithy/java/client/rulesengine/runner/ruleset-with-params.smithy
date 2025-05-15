$version: "1.0"

namespace example

use smithy.rules#contextParam
use smithy.rules#endpointRuleSet
use smithy.rules#staticContextParams
use smithy.rules#endpointTests
use smithy.rules#operationContextParams

@endpointRuleSet({
    "version": "1.3",
    "parameters": {
        "ParameterFoo": {
            "type": "string",
            "documentation": "docs"
        },
        "ParameterBar": {
            "type": "string",
            "documentation": "docs"
        }
        "ParameterBaz": {
            "type": "string",
            "documentation": "docs"
        }
    },
    "rules": [
        {
            "conditions": [
                {
                    "fn": "isSet",
                    "argv": [
                        {
                            "ref": "ParameterFoo"
                        }
                    ]
                }
            ],
            "type": "tree",
            "rules": [
                {
                    "conditions": [
                        {
                            "fn": "isSet",
                            "argv": [
                                {
                                    "ref": "ParameterBaz" // provided via jmespath
                                }
                            ]
                        }
                    ],
                    "endpoint": {
                        "url": "https://{ParameterBaz}.baz.amazonaws.com"
                    },
                    "type": "endpoint"
                }
                {
                    "conditions": [
                        {
                            "fn": "isSet",
                            "argv": [
                                {
                                    "ref": "ParameterBar"
                                }
                            ]
                        }
                    ],
                    "endpoint": {
                        "url": "https://{ParameterBar}.amazonaws.com"
                    },
                    "type": "endpoint"
                },
                {
                    "conditions": []
                    "endpoint": {
                        "url": "https://{ParameterFoo}.amazonaws.com"
                    },
                    "type": "endpoint"
                }
            ]
        }
        {
            "type": "error",
            "conditions": [],
            "error": "No rule matched"
        }
    ]
})
@endpointTests(
    version: "1.0",
    testCases: [
        {
            "documentation": "context param"
            "params": {
                "ParameterFoo": "foo"
            },
            "expect": {
                "endpoint": {
                    "url": "https://foo.amazonaws.com"
                }
            }
            "operationInputs": [
                {
                    "operationName": "GetResource"
                    "operationParams": {}
                }
            ]
        }
        {
            "documentation": "grabs operation context param"
            "params": {
                "ParameterFoo": "foo"
                "ParameterBar": "hello"
            },
            "expect": {
                "endpoint": {
                    "url": "https://hello.amazonaws.com"
                }
            }
            "operationInputs": [
                {
                    "operationName": "GetResource"
                    "operationParams": {
                        "bar": "hello"
                    }
                }
            ]
        }
        {
            "documentation": "falls back to last condition when no params were set"
            "params": {
                "ParameterFoo": "foo"
            },
            "expect": {
                "endpoint": {
                    "url": "https://foo.amazonaws.com"
                }
            }
            "operationInputs": [
                {
                    "operationName": "GetResource"
                    "operationParams": {}
                }
            ]
        }
        {
            "documentation": "uses jmespath context params"
            "params": {
                "ParameterFoo": "foo" // static context param
                "ParameterBaz": "bbaz" // jmespath extraction
            },
            "expect": {
                "endpoint": {
                    "url": "https://bbaz.baz.amazonaws.com"
                }
            }
            "operationInputs": [
                {
                    "operationName": "GetResource"
                    "operationParams": {
                        baz: {
                            bar: "bbaz"
                        }
                    }
                }
            ]
        }
    ]
)
service FizzBuzz {
    operations: [GetResource]
}

@staticContextParams("ParameterFoo": {value: "foo"})
@operationContextParams(
    ParameterBaz: {
        path: "baz.bar"
    }
)
operation GetResource {
    input: GetResourceInput
}

structure GetResourceInput {
    @contextParam(name: "ParameterBar")
    bar: String

    baz: Baz
}

structure Baz {
    bar: String
}
