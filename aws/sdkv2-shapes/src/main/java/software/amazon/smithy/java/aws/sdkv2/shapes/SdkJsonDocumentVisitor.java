/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.sdkv2.shapes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.SdkNumber;
import software.amazon.awssdk.core.document.DocumentVisitor;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonDocuments;
import software.amazon.smithy.java.json.JsonSettings;

record SdkJsonDocumentVisitor(JsonSettings settings) implements DocumentVisitor<Document> {
    @Override
    public Document visitNull() {
        return null;
    }

    @Override
    public Document visitBoolean(Boolean aBoolean) {
        return JsonDocuments.of(aBoolean, settings);
    }

    @Override
    public Document visitString(String s) {
        return JsonDocuments.of(s, settings);
    }

    @Override
    public Document visitNumber(SdkNumber sdkNumber) {
        // TODO: get SdkNumber to be able to return the underlying number value.
        return JsonDocuments.of(sdkNumber.bigDecimalValue(), settings);
    }

    @Override
    public Document visitMap(Map<String, software.amazon.awssdk.core.document.Document> map) {
        Map<String, Document> mapping = new LinkedHashMap<>(
                map.size());
        for (var entry : map.entrySet()) {
            mapping.put(entry.getKey(), entry.getValue().accept(this));
        }
        return JsonDocuments.of(mapping, settings);
    }

    @Override
    public Document visitList(List<software.amazon.awssdk.core.document.Document> list) {
        List<Document> values = new ArrayList<>(list.size());
        for (var document : list) {
            values.add(document.accept(this));
        }
        return JsonDocuments.of(values, settings);
    }
}
