/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.MemberSubsetCodec;

enum BodyMemberSubset implements MemberSubsetCodec.MemberSubset {
    REQUEST {
        @Override
        public boolean includes(Schema struct, Schema member) {
            return HttpBindingSchemaExtensions.structBindingsOf(struct)
                    .request().bindings[member.memberIndex()] == HttpBindingSchemaExtensions.Binding.BODY;
        }
    },
    RESPONSE {
        @Override
        public boolean includes(Schema struct, Schema member) {
            return HttpBindingSchemaExtensions.structBindingsOf(struct)
                    .response().bindings[member.memberIndex()] == HttpBindingSchemaExtensions.Binding.BODY;
        }
    };

    static BodyMemberSubset of(boolean isResponse) {
        return isResponse ? RESPONSE : REQUEST;
    }
}
