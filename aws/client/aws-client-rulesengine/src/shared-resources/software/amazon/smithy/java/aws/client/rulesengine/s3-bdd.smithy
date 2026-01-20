$version: "2"

namespace com.amazonaws.s3

// Apply the BDD rules manually to the S3 model to avoid needing to do
// sifting for integration and unit tests.
apply AmazonS3 @smithy.rules#endpointBdd({
    "version": "1.1",
    "parameters": {
        "Bucket": {
            "required": false,
            "documentation": "The S3 bucket used to send the request. This is an optional parameter that will be set automatically for operations that are scoped to an S3 bucket.",
            "type": "string"
        },
        "Region": {
            "builtIn": "AWS::Region",
            "required": false,
            "documentation": "The AWS region used to dispatch the request.",
            "type": "string"
        },
        "UseFIPS": {
            "builtIn": "AWS::UseFIPS",
            "required": true,
            "default": false,
            "documentation": "When true, send this request to the FIPS-compliant regional endpoint. If the configured endpoint does not have a FIPS compliant endpoint, dispatching the request will return an error.",
            "type": "boolean"
        },
        "UseDualStack": {
            "builtIn": "AWS::UseDualStack",
            "required": true,
            "default": false,
            "documentation": "When true, use the dual-stack endpoint. If the configured endpoint does not support dual-stack, dispatching the request MAY return an error.",
            "type": "boolean"
        },
        "Endpoint": {
            "builtIn": "SDK::Endpoint",
            "required": false,
            "documentation": "Override the endpoint used to send this request",
            "type": "string"
        },
        "ForcePathStyle": {
            "builtIn": "AWS::S3::ForcePathStyle",
            "required": true,
            "default": false,
            "documentation": "When true, force a path-style endpoint to be used where the bucket name is part of the path.",
            "type": "boolean"
        },
        "Accelerate": {
            "builtIn": "AWS::S3::Accelerate",
            "required": true,
            "default": false,
            "documentation": "When true, use S3 Accelerate. NOTE: Not all regions support S3 accelerate.",
            "type": "boolean"
        },
        "UseGlobalEndpoint": {
            "builtIn": "AWS::S3::UseGlobalEndpoint",
            "required": true,
            "default": false,
            "documentation": "Whether the global endpoint should be used, rather then the regional endpoint for us-east-1.",
            "type": "boolean"
        },
        "UseObjectLambdaEndpoint": {
            "required": false,
            "documentation": "Internal parameter to use object lambda endpoint for an operation (eg: WriteGetObjectResponse)",
            "type": "boolean"
        },
        "Key": {
            "required": false,
            "documentation": "The S3 Key used to send the request. This is an optional parameter that will be set automatically for operations that are scoped to an S3 Key.",
            "type": "string"
        },
        "Prefix": {
            "required": false,
            "documentation": "The S3 Prefix used to send the request. This is an optional parameter that will be set automatically for operations that are scoped to an S3 Prefix.",
            "type": "string"
        },
        "CopySource": {
            "required": false,
            "documentation": "The Copy Source used for Copy Object request. This is an optional parameter that will be set automatically for operations that are scoped to Copy Source.",
            "type": "string"
        },
        "DisableAccessPoints": {
            "required": false,
            "documentation": "Internal parameter to disable Access Point Buckets",
            "type": "boolean"
        },
        "DisableMultiRegionAccessPoints": {
            "builtIn": "AWS::S3::DisableMultiRegionAccessPoints",
            "required": true,
            "default": false,
            "documentation": "Whether multi-region access points (MRAP) should be disabled.",
            "type": "boolean"
        },
        "UseArnRegion": {
            "builtIn": "AWS::S3::UseArnRegion",
            "required": false,
            "documentation": "When an Access Point ARN is provided and this flag is enabled, the SDK MUST use the ARN's region when constructing the endpoint instead of the client's configured region.",
            "type": "boolean"
        },
        "UseS3ExpressControlEndpoint": {
            "required": false,
            "documentation": "Internal parameter to indicate whether S3Express operation should use control plane, (ex. CreateBucket)",
            "type": "boolean"
        },
        "DisableS3ExpressSessionAuth": {
            "required": false,
            "documentation": "Parameter to indicate whether S3Express session auth should be disabled",
            "type": "boolean"
        }
    },
    "conditions": [
        {
            "fn": "isSet",
            "argv": [
                {
                    "ref": "Region"
                }
            ]
        },
        {
            "fn": "ite",
            "argv": [
                {
                    "fn": "stringEquals",
                    "argv": [
                        {
                            "ref": "Region"
                        },
                        "aws-global"
                    ]
                },
                "us-east-1",
                {
                    "ref": "Region"
                }
            ],
            "assign": "_effective_std_region"
        },
        {
            "fn": "booleanEquals",
            "argv": [
                {
                    "ref": "Accelerate"
                },
                true
            ]
        },
        {
            "fn": "booleanEquals",
            "argv": [
                {
                    "ref": "UseFIPS"
                },
                true
            ]
        },
        {
            "fn": "booleanEquals",
            "argv": [
                {
                    "ref": "UseDualStack"
                },
                true
            ]
        },
        {
            "fn": "isSet",
            "argv": [
                {
                    "ref": "Endpoint"
                }
            ]
        },
        {
            "fn": "isSet",
            "argv": [
                {
                    "ref": "Bucket"
                }
            ]
        },
        {
            "fn": "getAttr",
            "argv": [
                {
                    "fn": "split",
                    "argv": [
                        {
                            "ref": "Bucket"
                        },
                        "--",
                        0
                    ]
                },
                "[-1]"
            ],
            "assign": "bucketSuffix"
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "bucketSuffix"
                },
                "x-s3"
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "bucketSuffix"
                },
                "xa-s3"
            ]
        },
        {
            "fn": "aws.partition",
            "argv": [
                {
                    "ref": "Region"
                }
            ],
            "assign": "partitionResult"
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "bucketSuffix"
                },
                "op-s3"
            ]
        },
        {
            "fn": "substring",
            "argv": [
                {
                    "ref": "Bucket"
                },
                49,
                50,
                true
            ],
            "assign": "hardwareType"
        },
        {
            "fn": "substring",
            "argv": [
                {
                    "ref": "Bucket"
                },
                8,
                12,
                true
            ],
            "assign": "regionPrefix"
        },
        {
            "fn": "parseURL",
            "argv": [
                {
                    "ref": "Endpoint"
                }
            ],
            "assign": "url"
        },
        {
            "fn": "substring",
            "argv": [
                {
                    "ref": "Bucket"
                },
                32,
                49,
                true
            ],
            "assign": "outpostId_ssa_2"
        },
        {
            "fn": "booleanEquals",
            "argv": [
                {
                    "ref": "ForcePathStyle"
                },
                true
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "partitionResult"
                        },
                        "name"
                    ]
                },
                "aws-cn"
            ]
        },
        {
            "fn": "isValidHostLabel",
            "argv": [
                {
                    "ref": "outpostId_ssa_2"
                },
                false
            ]
        },
        {
            "fn": "ite",
            "argv": [
                {
                    "ref": "UseFIPS"
                },
                "-fips",
                ""
            ],
            "assign": "_s3e_fips"
        },
        {
            "fn": "ite",
            "argv": [
                {
                    "ref": "UseDualStack"
                },
                ".dualstack",
                ""
            ],
            "assign": "_s3e_ds"
        },
        {
            "fn": "ite",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "ref": "DisableS3ExpressSessionAuth"
                        },
                        false
                    ]
                },
                "sigv4",
                "sigv4-s3express"
            ],
            "assign": "_s3e_auth"
        },
        {
            "fn": "booleanEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "ref": "UseS3ExpressControlEndpoint"
                        },
                        false
                    ]
                },
                true
            ]
        },
        {
            "fn": "aws.isVirtualHostableS3Bucket",
            "argv": [
                {
                    "ref": "Bucket"
                },
                false
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "url"
                        },
                        "scheme"
                    ]
                },
                "http"
            ]
        },
        {
            "fn": "aws.isVirtualHostableS3Bucket",
            "argv": [
                {
                    "ref": "Bucket"
                },
                true
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "regionPrefix"
                },
                "beta"
            ]
        },
        {
            "fn": "aws.parseArn",
            "argv": [
                {
                    "ref": "Bucket"
                }
            ],
            "assign": "bucketArn"
        },
        {
            "fn": "ite",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "ref": "UseArnRegion"
                        },
                        true
                    ]
                },
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "bucketArn"
                        },
                        "region"
                    ]
                },
                {
                    "ref": "Region"
                }
            ],
            "assign": "_effective_arn_region"
        },
        {
            "fn": "getAttr",
            "argv": [
                {
                    "ref": "bucketArn"
                },
                "resourceId[0]"
            ],
            "assign": "arnType"
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "arnType"
                },
                ""
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "arnType"
                },
                "accesspoint"
            ]
        },
        {
            "fn": "getAttr",
            "argv": [
                {
                    "ref": "bucketArn"
                },
                "resourceId[1]"
            ],
            "assign": "accessPointName_ssa_1"
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "accessPointName_ssa_1"
                },
                ""
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "bucketArn"
                        },
                        "region"
                    ]
                },
                ""
            ]
        },
        {
            "fn": "getAttr",
            "argv": [
                {
                    "fn": "split",
                    "argv": [
                        {
                            "ref": "Bucket"
                        },
                        "--",
                        0
                    ]
                },
                "[-2]"
            ],
            "assign": "s3expressAvailabilityZoneId"
        },
        {
            "fn": "aws.partition",
            "argv": [
                {
                    "ref": "_effective_arn_region"
                }
            ],
            "assign": "bucketPartition"
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "fn": "substring",
                            "argv": [
                                {
                                    "ref": "Bucket"
                                },
                                27,
                                29,
                                true
                            ]
                        },
                        ""
                    ]
                },
                "--"
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "fn": "substring",
                            "argv": [
                                {
                                    "ref": "Bucket"
                                },
                                0,
                                4,
                                false
                            ]
                        },
                        ""
                    ]
                },
                "arn:"
            ]
        },
        {
            "fn": "isValidHostLabel",
            "argv": [
                {
                    "ref": "Region"
                },
                false
            ]
        },
        {
            "fn": "booleanEquals",
            "argv": [
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "url"
                        },
                        "isIp"
                    ]
                },
                true
            ]
        },
        {
            "fn": "uriEncode",
            "argv": [
                {
                    "ref": "Bucket"
                }
            ],
            "assign": "uri_encoded_bucket"
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "fn": "substring",
                            "argv": [
                                {
                                    "ref": "Bucket"
                                },
                                26,
                                28,
                                true
                            ]
                        },
                        ""
                    ]
                },
                "--"
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "hardwareType"
                },
                "e"
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "hardwareType"
                },
                "o"
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "fn": "substring",
                            "argv": [
                                {
                                    "ref": "Bucket"
                                },
                                19,
                                21,
                                true
                            ]
                        },
                        ""
                    ]
                },
                "--"
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "fn": "substring",
                            "argv": [
                                {
                                    "ref": "Bucket"
                                },
                                20,
                                22,
                                true
                            ]
                        },
                        ""
                    ]
                },
                "--"
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "bucketArn"
                        },
                        "service"
                    ]
                },
                "s3-object-lambda"
            ]
        },
        {
            "fn": "booleanEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "ref": "DisableAccessPoints"
                        },
                        false
                    ]
                },
                true
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "fn": "substring",
                            "argv": [
                                {
                                    "ref": "Bucket"
                                },
                                15,
                                17,
                                true
                            ]
                        },
                        ""
                    ]
                },
                "--"
            ]
        },
        {
            "fn": "isValidHostLabel",
            "argv": [
                {
                    "ref": "Region"
                },
                true
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "bucketArn"
                        },
                        "service"
                    ]
                },
                "s3-outposts"
            ]
        },
        {
            "fn": "getAttr",
            "argv": [
                {
                    "ref": "bucketArn"
                },
                "resourceId[4]"
            ]
        },
        {
            "fn": "booleanEquals",
            "argv": [
                {
                    "ref": "DisableMultiRegionAccessPoints"
                },
                true
            ]
        },
        {
            "fn": "getAttr",
            "argv": [
                {
                    "ref": "bucketArn"
                },
                "resourceId[1]"
            ],
            "assign": "outpostId_ssa_1"
        },
        {
            "fn": "booleanEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "ref": "UseArnRegion"
                        },
                        true
                    ]
                },
                true
            ]
        },
        {
            "fn": "isValidHostLabel",
            "argv": [
                {
                    "ref": "outpostId_ssa_1"
                },
                false
            ]
        },
        {
            "fn": "getAttr",
            "argv": [
                {
                    "ref": "bucketArn"
                },
                "resourceId[2]"
            ],
            "assign": "outpostType"
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "_effective_arn_region"
                },
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "bucketArn"
                        },
                        "region"
                    ]
                }
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "bucketPartition"
                        },
                        "name"
                    ]
                },
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "partitionResult"
                        },
                        "name"
                    ]
                }
            ]
        },
        {
            "fn": "isValidHostLabel",
            "argv": [
                {
                    "ref": "_effective_arn_region"
                },
                true
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "bucketArn"
                        },
                        "accountId"
                    ]
                },
                ""
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "bucketArn"
                        },
                        "service"
                    ]
                },
                "s3"
            ]
        },
        {
            "fn": "isValidHostLabel",
            "argv": [
                {
                    "ref": "accessPointName_ssa_1"
                },
                true
            ]
        },
        {
            "fn": "isValidHostLabel",
            "argv": [
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "bucketArn"
                        },
                        "accountId"
                    ]
                },
                false
            ]
        },
        {
            "fn": "isValidHostLabel",
            "argv": [
                {
                    "ref": "accessPointName_ssa_1"
                },
                false
            ]
        },
        {
            "fn": "getAttr",
            "argv": [
                {
                    "ref": "bucketArn"
                },
                "resourceId[3]"
            ],
            "assign": "accessPointName_ssa_2"
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "outpostType"
                },
                "accesspoint"
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "bucketArn"
                        },
                        "partition"
                    ]
                },
                {
                    "fn": "getAttr",
                    "argv": [
                        {
                            "ref": "partitionResult"
                        },
                        "name"
                    ]
                }
            ]
        },
        {
            "fn": "booleanEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "ref": "UseObjectLambdaEndpoint"
                        },
                        false
                    ]
                },
                true
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "Region"
                },
                "aws-global"
            ]
        },
        {
            "fn": "booleanEquals",
            "argv": [
                {
                    "ref": "UseGlobalEndpoint"
                },
                true
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "ref": "Region"
                },
                "us-east-1"
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "fn": "substring",
                            "argv": [
                                {
                                    "ref": "Bucket"
                                },
                                16,
                                18,
                                true
                            ]
                        },
                        ""
                    ]
                },
                "--"
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "fn": "substring",
                            "argv": [
                                {
                                    "ref": "Bucket"
                                },
                                21,
                                23,
                                true
                            ]
                        },
                        ""
                    ]
                },
                "--"
            ]
        },
        {
            "fn": "stringEquals",
            "argv": [
                {
                    "fn": "coalesce",
                    "argv": [
                        {
                            "fn": "substring",
                            "argv": [
                                {
                                    "ref": "Bucket"
                                },
                                14,
                                16,
                                true
                            ]
                        },
                        ""
                    ]
                },
                "--"
            ]
        }
    ],
    "results": [
        {
            "conditions": [],
            "error": "Accelerate cannot be used with FIPS",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Cannot set dual-stack in combination with a custom endpoint.",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "A custom endpoint cannot be combined with FIPS",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "A custom endpoint cannot be combined with S3 Accelerate",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Partition does not support FIPS",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "S3Express does not support S3 Accelerate.",
            "type": "error"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "{url#scheme}://{url#authority}/{uri_encoded_bucket}{url#path}",
                "properties": {
                    "backend": "S3Express",
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "{_s3e_auth}",
                            "signingName": "s3express",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "{url#scheme}://{Bucket}.{url#authority}{url#path}",
                "properties": {
                    "backend": "S3Express",
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "{_s3e_auth}",
                            "signingName": "s3express",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "error": "S3Express bucket name is not a valid virtual hostable name.",
            "type": "error"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3express-control{_s3e_fips}{_s3e_ds}.{_effective_std_region}.{partitionResult#dnsSuffix}/{uri_encoded_bucket}",
                "properties": {
                    "backend": "S3Express",
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3express",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.s3express{_s3e_fips}-{s3expressAvailabilityZoneId}{_s3e_ds}.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "backend": "S3Express",
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "{_s3e_auth}",
                            "signingName": "s3express",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "error": "Unrecognized S3Express bucket name format.",
            "type": "error"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "{url#scheme}://{url#authority}{url#path}",
                "properties": {
                    "backend": "S3Express",
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "{_s3e_auth}",
                            "signingName": "s3express",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3express-control{_s3e_fips}{_s3e_ds}.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "backend": "S3Express",
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3express",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "error": "Expected a endpoint to be specified but no endpoint was found",
            "type": "error"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.ec2.{url#authority}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4a",
                            "signingName": "s3-outposts",
                            "signingRegionSet": [
                                "*"
                            ]
                        },
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3-outposts",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.ec2.s3-outposts.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4a",
                            "signingName": "s3-outposts",
                            "signingRegionSet": [
                                "*"
                            ]
                        },
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3-outposts",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.op-{outpostId_ssa_2}.{url#authority}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4a",
                            "signingName": "s3-outposts",
                            "signingRegionSet": [
                                "*"
                            ]
                        },
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3-outposts",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.op-{outpostId_ssa_2}.s3-outposts.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4a",
                            "signingName": "s3-outposts",
                            "signingRegionSet": [
                                "*"
                            ]
                        },
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3-outposts",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "error": "Unrecognized hardware type: \"Expected hardware type o or e but got {hardwareType}\"",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid Outposts Bucket alias - it must be a valid bucket name.",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: The outpost Id must only contain a-z, A-Z, 0-9 and `-`.",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Custom endpoint `{Endpoint}` was not a valid URI",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "S3 Accelerate cannot be used in this region",
            "type": "error"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.s3-fips.dualstack.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.s3-fips.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.s3-accelerate.dualstack.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.s3-accelerate.dualstack.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.s3.dualstack.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "{url#scheme}://{url#authority}{url#normalizedPath}{Bucket}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "{url#scheme}://{Bucket}.{url#authority}{url#path}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.s3-accelerate.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.s3.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{Bucket}.s3.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "error": "Invalid region: region was not a valid DNS name.",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "S3 Object Lambda does not support Dual-stack",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "S3 Object Lambda does not support S3 Accelerate",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Access points are not supported for this operation",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid configuration: region from ARN `{bucketArn#region}` does not match client region `{Region}` and UseArnRegion is `false`",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: Missing account id",
            "type": "error"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "{url#scheme}://{accessPointName_ssa_1}-{bucketArn#accountId}.{url#authority}{url#path}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3-object-lambda",
                            "signingRegion": "{_effective_arn_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{accessPointName_ssa_1}-{bucketArn#accountId}.s3-object-lambda-fips.{_effective_arn_region}.{bucketPartition#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3-object-lambda",
                            "signingRegion": "{_effective_arn_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{accessPointName_ssa_1}-{bucketArn#accountId}.s3-object-lambda.{_effective_arn_region}.{bucketPartition#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3-object-lambda",
                            "signingRegion": "{_effective_arn_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: The access point name may only contain a-z, A-Z, 0-9 and `-`. Found: `{accessPointName_ssa_1}`",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: The account id may only contain a-z, A-Z, 0-9 and `-`. Found: `{bucketArn#accountId}`",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid region in ARN: `{bucketArn#region}` (invalid DNS name)",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Client was configured for partition `{partitionResult#name}` but ARN (`{Bucket}`) has `{bucketPartition#name}`",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: The ARN may only contain a single resource component after `accesspoint`.",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: bucket ARN is missing a region",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: Expected a resource of the format `accesspoint:<accesspoint name>` but no name was provided",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: Object Lambda ARNs only support `accesspoint` arn types, but found: `{arnType}`",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Access Points do not support S3 Accelerate",
            "type": "error"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{accessPointName_ssa_1}-{bucketArn#accountId}.s3-accesspoint-fips.dualstack.{_effective_arn_region}.{bucketPartition#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_arn_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{accessPointName_ssa_1}-{bucketArn#accountId}.s3-accesspoint-fips.{_effective_arn_region}.{bucketPartition#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_arn_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{accessPointName_ssa_1}-{bucketArn#accountId}.s3-accesspoint.dualstack.{_effective_arn_region}.{bucketPartition#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_arn_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "{url#scheme}://{accessPointName_ssa_1}-{bucketArn#accountId}.{url#authority}{url#path}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_arn_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{accessPointName_ssa_1}-{bucketArn#accountId}.s3-accesspoint.{_effective_arn_region}.{bucketPartition#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_arn_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: The ARN was not for the S3 service, found: {bucketArn#service}",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "S3 MRAP does not support dual-stack",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "S3 MRAP does not support FIPS",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "S3 MRAP does not support S3 Accelerate",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid configuration: Multi-Region Access Point ARNs are disabled.",
            "type": "error"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{accessPointName_ssa_1}.accesspoint.s3-global.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4a",
                            "signingName": "s3",
                            "signingRegionSet": [
                                "*"
                            ]
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "error": "Client was configured for partition `{partitionResult#name}` but bucket referred to partition `{bucketArn#partition}`",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid Access Point Name",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "S3 Outposts does not support Dual-stack",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "S3 Outposts does not support FIPS",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "S3 Outposts does not support S3 Accelerate",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid Arn: Outpost Access Point ARN contains sub resources",
            "type": "error"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{accessPointName_ssa_2}-{bucketArn#accountId}.{outpostId_ssa_1}.{url#authority}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4a",
                            "signingName": "s3-outposts",
                            "signingRegionSet": [
                                "*"
                            ]
                        },
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3-outposts",
                            "signingRegion": "{_effective_arn_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://{accessPointName_ssa_2}-{bucketArn#accountId}.{outpostId_ssa_1}.s3-outposts.{_effective_arn_region}.{bucketPartition#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4a",
                            "signingName": "s3-outposts",
                            "signingRegionSet": [
                                "*"
                            ]
                        },
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3-outposts",
                            "signingRegion": "{_effective_arn_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "error": "Expected an outpost type `accesspoint`, found {outpostType}",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: expected an access point name",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: Expected a 4-component resource",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: The outpost Id may only contain a-z, A-Z, 0-9 and `-`. Found: `{outpostId_ssa_1}`",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: The Outpost Id was not set",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: Unrecognized format: {Bucket} (type: {arnType})",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: No ARN type specified",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Invalid ARN: `{Bucket}` was not a valid ARN",
            "type": "error"
        },
        {
            "conditions": [],
            "error": "Path-style addressing cannot be used with ARN buckets",
            "type": "error"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3-fips.dualstack.{_effective_std_region}.{partitionResult#dnsSuffix}/{uri_encoded_bucket}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3-fips.{_effective_std_region}.{partitionResult#dnsSuffix}/{uri_encoded_bucket}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3.dualstack.{_effective_std_region}.{partitionResult#dnsSuffix}/{uri_encoded_bucket}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "{url#scheme}://{url#authority}{url#normalizedPath}{uri_encoded_bucket}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3.{partitionResult#dnsSuffix}/{uri_encoded_bucket}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3.{_effective_std_region}.{partitionResult#dnsSuffix}/{uri_encoded_bucket}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "error": "Path-style addressing cannot be used with S3 Accelerate",
            "type": "error"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "{url#scheme}://{url#authority}{url#path}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3-object-lambda",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3-object-lambda-fips.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3-object-lambda",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3-object-lambda.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3-object-lambda",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3-fips.dualstack.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3-fips.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3.dualstack.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "{url#scheme}://{url#authority}{url#path}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "endpoint": {
                "url": "https://s3.{_effective_std_region}.{partitionResult#dnsSuffix}",
                "properties": {
                    "authSchemes": [
                        {
                            "disableDoubleEncoding": true,
                            "name": "sigv4",
                            "signingName": "s3",
                            "signingRegion": "{_effective_std_region}"
                        }
                    ]
                },
                "headers": {}
            },
            "type": "endpoint"
        },
        {
            "conditions": [],
            "error": "A region must be set when sending requests to S3.",
            "type": "error"
        }
    ],
    "root": 542,
    "nodeCount": 542,
    "nodes": "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AAAAFQAAAJUAAACWAAAAFAAAAJcAAACWAAAAEwAAAJgAAACWAAAAEQX14QUAAACZAAAACgAAAJoF9eEJAAAASgX14QsF9eEMAAAASQX14QsAAACcAAAAMQX14QsAAACdAAAALgX14QsAAACeAAAAJQX14QsAAACfAAAAIwAAAKAF9eEMAAAAFwAAAKEF9eEJAAAAFQAAAKIAAACWAAAAFAAAAKMAAACWAAAAEwAAAKQAAACWAAAAEQX14QUAAAClAAAACgAAAKYF9eEJAAAAEQX14QUAAAAIAAAAKQX14VEAAAALAAAAJgX14U8AAACpAAAAGwX14VAAAACqAAAAEQX14QUAAACrAAAAJwX14RkF9eEjAAAAQQX14TUF9eEsAAAAQAAAAK4F9eEtAAAAPgAAAK8F9eE6AAAAPAAAALAF9eEuAAAAOwAAALEF9eEvAAAAOQX14TAAAACyAAAAOgAAALIF9eEnAAAAOQX14TAAAAC0AAAANwAAALMAAAC1AAAAMAX14SYAAAC2AAAALwX14SQAAAC3AAAAJAAAALgAAAAhAAAAIgAAABEAAAC5AAAAIQX14TIAAAC6AAAAIAAAALsF9eEyAAAAHwAAALwAAAAnAAAAHgX14U4AAAC9AAAAHQAAAL4F9eFOAAAAHAAAAL8AAACpAAAAGwAAAMAAAACqAAAAFwAAAK0AAADBAAAAEQX14QUAAADCAAAAEAAAAKwAAADDAAAADwAAAKgAAADEAAAADQAAAMUAAADEAAAADAAAAMYAAADEAAAACwAAAMcAAADEAAAACgAAAMgAAABBAAAACQAAAKcAAADJAAAACAAAAJsAAADKAAAACgAAAMQAAABBAAAABwAAAMsAAADMAAAARQX14SQF9eFbAAAAMgAAAM4F9eEjAAAAFgX14Q4AAADPAAAAFQAAANAAAADPAAAAFAAAANEAAADPAAAAEwAAANIAAADPAAAAEQX14QUAAADTAAAACgAAANQF9eFhAAAABgAAAM0AAADVAAAABQX14QIAAADWAAAARQX14VkF9eFhAAAAMgAAANgAAAAKAAAAKQX14VIAAADZAAAAJgX14U8AAADaAAAAGwX14VAAAADbAAAAEQX14QUAAADcAAAAJwX14RoF9eEjAAAAPwX14TwF9eFBAAAALwX14TEAAADfAAAAQQX14SoF9eEs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"
})
