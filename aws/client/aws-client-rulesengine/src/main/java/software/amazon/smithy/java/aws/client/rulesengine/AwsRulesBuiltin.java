/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.rulesengine;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.client.core.settings.AccountIdSetting;
import software.amazon.smithy.java.aws.client.core.settings.EndpointSettings;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.core.settings.S3EndpointSettings;
import software.amazon.smithy.java.aws.client.core.settings.StsEndpointSettings;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.context.Context;

/**
 * AWS built-ins.
 *
 * @link <a href="https://smithy.io/2.0/aws/rules-engine/built-ins.html">AWS built-ins</a>
 */
enum AwsRulesBuiltin implements Function<Context, Object> {
    REGION("AWS::Region") {
        @Override
        public Object apply(Context ctx) {
            return ctx.get(RegionSetting.REGION);
        }
    },

    USE_DUAL_STACK("AWS::UseDualStack") {
        @Override
        public Object apply(Context ctx) {
            return ctx.get(EndpointSettings.USE_DUAL_STACK);
        }
    },

    USE_FIPS("AWS::UseFIPS") {
        @Override
        public Object apply(Context ctx) {
            return ctx.get(EndpointSettings.USE_FIPS);
        }
    },

    AWS_AUTH_ACCOUNT_ID("AWS::Auth::AccountId") {
        @Override
        public Object apply(Context ctx) {
            var result = ctx.get(AccountIdSetting.AWS_ACCOUNT_ID);
            if (result != null) {
                return result;
            }
            if (ctx.get(CallContext.IDENTITY) instanceof AwsCredentialsIdentity awsIdentity) {
                return awsIdentity.accountId();
            }
            return null;
        }
    },

    AWS_AUTH_ACCOUNT_ID_ENDPOINT_MODE("AWS::Auth::AccountIdEndpointMode") {
        @Override
        public Object apply(Context ctx) {
            return ctx.get(EndpointSettings.ACCOUNT_ID_ENDPOINT_MODE);
        }
    },

    AWS_AUTH_CREDENTIAL_SCOPE("AWS::Auth::CredentialScope") {
        @Override
        public Object apply(Context ctx) {
            // TODO
            throw new UnsupportedOperationException("Not yet implemented: " + this);
        }
    },

    AWS_S3_ACCELERATE("AWS::S3::Accelerate") {
        @Override
        public Object apply(Context context) {
            return context.get(S3EndpointSettings.S3_ACCELERATE);
        }
    },

    AWS_S3_DISABLE_MULTI_REGION_ACCESS_POINTS("AWS::S3::DisableMultiRegionAccessPoints") {
        @Override
        public Object apply(Context context) {
            return context.get(S3EndpointSettings.S3_DISABLE_MULTI_REGION_ACCESS_POINTS);
        }
    },

    AWS_S3_FORCE_PATH_STYLE("AWS::S3::ForcePathStyle") {
        @Override
        public Object apply(Context context) {
            return context.get(S3EndpointSettings.S3_FORCE_PATH_STYLE);
        }
    },

    AWS_S3_USE_ARN_REGION("AWS::S3::UseArnRegion") {
        @Override
        public Object apply(Context context) {
            return context.get(S3EndpointSettings.S3_USE_ARN_REGION);
        }
    },

    AWS_S3_USE_GLOBAL_ENDPOINT("AWS::S3::UseGlobalEndpoint") {
        @Override
        public Object apply(Context context) {
            return context.get(S3EndpointSettings.S3_USE_GLOBAL_ENDPOINT);
        }
    },

    AWS_S3_CONTROL_USE_ARN_REGION("AWS::S3Control::UseArnRegion") {
        @Override
        public Object apply(Context context) {
            return context.get(S3EndpointSettings.S3_CONTROL_USE_ARN_REGION);
        }
    },

    AWS_STS_USE_GLOBAL_ENDPOINT("AWS::STS::UseGlobalEndpoint") {
        @Override
        public Object apply(Context context) {
            return context.get(StsEndpointSettings.STS_USE_GLOBAL_ENDPOINT);
        }
    };

    static final BiFunction<String, Context, Object> BUILTIN_PROVIDER = new BuiltinProvider();

    private static final class BuiltinProvider implements BiFunction<String, Context, Object> {
        private final Map<String, Function<Context, Object>> providers = new HashMap<>();

        private BuiltinProvider() {
            for (var e : values()) {
                providers.put(e.name, e);
            }
        }

        @Override
        public Object apply(String name, Context context) {
            var match = providers.get(name);
            return match == null ? null : match.apply(context);
        }
    }

    private final String name;

    AwsRulesBuiltin(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
