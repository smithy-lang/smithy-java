/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.client.rulesengine.RulesFunction;
import software.amazon.smithy.rulesengine.aws.language.functions.AwsArn;
import software.amazon.smithy.rulesengine.aws.language.functions.AwsPartition;
import software.amazon.smithy.rulesengine.aws.language.functions.IsVirtualHostableS3Bucket;
import software.amazon.smithy.rulesengine.aws.language.functions.ParseArn;
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
        private static final class PartitionMap extends AbstractMap<String, Object> {
            private final Partition partition;
            private Set<Entry<String, Object>> entrySet;

            private PartitionMap(Partition partition) {
                this.partition = partition;
            }

            @Override
            public int size() {
                return 6;
            }

            @Override
            public Set<Entry<String, Object>> entrySet() {
                var result = entrySet;
                if (result == null) {
                    result = Set.of(
                            Map.entry("name", partition.getId()),
                            Map.entry("dnsSuffix", partition.getOutputs().getDnsSuffix()),
                            Map.entry("dualStackDnsSuffix", partition.getOutputs().getDualStackDnsSuffix()),
                            Map.entry("supportsFIPS", partition.getOutputs().supportsFips()),
                            Map.entry("supportsDualStack", partition.getOutputs().supportsDualStack()),
                            Map.entry("implicitGlobalRegion", partition.getOutputs().getImplicitGlobalRegion()));
                    entrySet = result;
                }
                return result;
            }

            @Override
            public Object get(Object key) {
                if (key instanceof String s) {
                    return switch (s) {
                        case "name" -> partition.getId();
                        case "dnsSuffix" -> partition.getOutputs().getDnsSuffix();
                        case "dualStackDnsSuffix" -> partition.getOutputs().getDualStackDnsSuffix();
                        case "supportsFIPS" -> partition.getOutputs().supportsFips();
                        case "supportsDualStack" -> partition.getOutputs().supportsDualStack();
                        case "implicitGlobalRegion" -> partition.getOutputs().getImplicitGlobalRegion();
                        default -> null;
                    };
                }
                return null;
            }
        }
    },

    AWS_PARSE_ARN("aws.parseArn", 1) {
        @Override
        public Object apply1(Object arg1) {
            String value = (String) arg1;
            var awsArn = AwsArn.parse(value).orElse(null);
            if (awsArn == null) {
                return null;
            }
            return Map.of(
                    ParseArn.PARTITION.toString(),
                    awsArn.getPartition(),
                    ParseArn.SERVICE.toString(),
                    awsArn.getService(),
                    ParseArn.REGION.toString(),
                    awsArn.getRegion(),
                    ParseArn.ACCOUNT_ID.toString(),
                    awsArn.getAccountId(),
                    "resourceId", // TODO: make this one public too in Smithy
                    awsArn.getResource());
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
    public int getOperandCount() {
        return operands;
    }

    @Override
    public String getFunctionName() {
        return name;
    }
}
