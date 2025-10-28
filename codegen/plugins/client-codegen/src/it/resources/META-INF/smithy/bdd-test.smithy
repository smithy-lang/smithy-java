$version: "2.0"

namespace smithy.java.codegen.server.test

use aws.protocols#restJson1
use smithy.rules#clientContextParams
use smithy.rules#endpointBdd
use smithy.rules#endpointRuleSet

@clientContextParams(
    Region: {type: "string", documentation: "docs"}
    UseFips: {type: "boolean", documentation: "docs"}
)
@endpointBdd({
    version: "1.1"
    "parameters": {
        "Region": {
            "required": true,
            "documentation": "The AWS region",
            "type": "string"
        },
        "UseFips": {
            "required": true,
            "default": false,
            "documentation": "Use FIPS endpoints",
            "type": "boolean"
        }
    },
    "conditions": [
        {
            "fn": "booleanEquals",
            "argv": [
                {
                    "ref": "UseFips"
                },
                true
            ]
        }
    ],
    "results": [
        {
            "conditions": [],
            "endpoint": {
                "url": "https://service-fips.{Region}.amazonaws.com",
                "properties": {},
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://service.{Region}.amazonaws.com",
                "properties": {},
                "headers": {}
            },
            "type": "endpoint"
        }
    ],
    "root": 2,
    "nodeCount": 2,
    "nodes": "/////wAAAAH/////AAAAAAX14QEF9eEC"
})
@restJson1
service ServiceWithEndpointBdd {
    version: "2022-01-01"
    operations:[
        Echo
    ]
}

@clientContextParams(
    Region: {type: "string", documentation: "docs"}
)
@endpointRuleSet({
    "version": "1.1",
    "parameters": {
        "Region": {
            "required": true,
            "type": "String",
            "documentation": "docs"
        }
    },
    "rules": [
        {
            "conditions": [],
            "documentation": "base rule",
            "endpoint": {
                "url": "https://{Region}.amazonaws.com",
                "properties": {},
                "headers": {}
            },
            "type": "endpoint"
        }
    ]
})
service ServiceWithEndpointRuleSet {}
