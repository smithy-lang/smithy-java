/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import software.amazon.smithy.java.rulesengine.PropertyGetter;
import software.amazon.smithy.java.rulesengine.RulesFunction;
import software.amazon.smithy.rulesengine.aws.language.functions.AwsArn;
import software.amazon.smithy.rulesengine.aws.language.functions.AwsPartition;
import software.amazon.smithy.rulesengine.aws.language.functions.IsVirtualHostableS3Bucket;
import software.amazon.smithy.rulesengine.aws.language.functions.partition.Partition;

/**
 * Implements AWS rules engine functions.
 *
 * @link <a href="https://smithy.io/2.0/aws/rules-engine/library-functions.html">AWS rules engine functions</a>
 */
enum AwsRulesFunction implements RulesFunction {
    AWS_PARTITION("aws.partition", 1) {
        @Override
        public Object apply1(Object arg1) {
            String region = (String) arg1;
            var partition = AwsPartition.findPartition(region);
            if (partition == null) {
                return null;
            }
            return new PartitionMap(partition);
        }

        // Convert AwsPartition to the map structure used in the rules engine.
        // Most of the entries aren't needed for evaluating rules, so map entries are created lazily (something that
        // isn't needed when evaluating rules), and accessing map values is done using a switch (something that is
        // essentially compiled into a map lookup via a lookupswitch).
        private record PartitionMap(Partition partition) implements PropertyGetter {
            @Override
            public Object getProperty(String name) {
                return switch (name) {
                    case "name" -> partition.getId();
                    case "dnsSuffix" -> partition.getOutputs().getDnsSuffix();
                    case "dualStackDnsSuffix" -> partition.getOutputs().getDualStackDnsSuffix();
                    case "supportsFIPS" -> partition.getOutputs().supportsFips();
                    case "supportsDualStack" -> partition.getOutputs().supportsDualStack();
                    case "implicitGlobalRegion" -> partition.getOutputs().getImplicitGlobalRegion();
                    default -> null;
                };
            }
        }
    },

    AWS_PARSE_ARN("aws.parseArn", 1) {
        // Single volatile reference for thread-safe hot-key caching without ThreadLocal overhead.
        private transient volatile ArnMap cache;

        @Override
        public Object apply1(Object arg1) {
            String value = (String) arg1;

            var c = cache;
            if (c != null && value.equals(c.key)) {
                return c;
            }

            var awsArn = AwsArn.parse(value).orElse(null);
            if (awsArn == null) {
                return null;
            }

            var result = new ArnMap(value, awsArn);
            cache = result;
            return result;
        }

        // Lazy property access. Same pattern as PartitionMap above.
        private record ArnMap(String key, AwsArn arn) implements PropertyGetter {
            @Override
            public Object getProperty(String name) {
                return switch (name) {
                    case "partition" -> arn.getPartition();
                    case "service" -> arn.getService();
                    case "region" -> arn.getRegion();
                    case "accountId" -> arn.getAccountId();
                    case "resourceId" -> arn.getResource();
                    default -> null;
                };
            }
        }
    },

    AWS_IS_VIRTUAL_HOSTED_BUCKET("aws.isVirtualHostableS3Bucket", 2) {
        @Override
        public Object apply2(Object arg1, Object arg2) {
            var hostLabel = (String) arg1;
            var allowDots = arg2 != null && (boolean) arg2;
            return IsVirtualHostableS3Bucket.isVirtualHostableBucket(hostLabel, allowDots);
        }
    };

    private final String name;
    private final int operands;

    AwsRulesFunction(String name, int operands) {
        this.name = name;
        this.operands = operands;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int getArgumentCount() {
        return operands;
    }

    @Override
    public String getFunctionName() {
        return name;
    }
}
