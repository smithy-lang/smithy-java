/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.retries.api.RetrySafety;

final class ComparisonUtils {

    private ComparisonUtils() {}

    static RecursiveComparisonConfiguration getComparisonConfig() {
        return RecursiveComparisonConfiguration.builder()
            // Compare data streams by contained data
            .withComparatorForType(
                Comparator.comparing(d -> new StringBuildingSubscriber(d).getResult()),
                DataStream.class
            )
            .withComparatorForType(
                Comparator.comparing(ByteBufferUtils::getBytes, Arrays::compare),
                ByteBuffer.class
            )
            // Compare doubles and floats as longs so NaN's will be equatable
            .withComparatorForType(nanPermittingDoubleComparator(), Double.class)
            .withComparatorForType(nanPermittingFloatComparator(), Float.class)
            .withComparatorForType((a, b) -> Document.equals(a, b) ? 0 : 1, Document.class)
            .withIgnoredFieldsOfTypes(RetrySafety.class)
            .build();
    }

    private static Comparator<Double> nanPermittingDoubleComparator() {
        return (d1, d2) -> (Double.isNaN(d1) && Double.isNaN(d2)) ? 0 : Double.compare(d1, d2);
    }

    private static Comparator<Float> nanPermittingFloatComparator() {
        return (f1, f2) -> (Float.isNaN(f1) && Float.isNaN(f2)) ? 0 : Float.compare(f1, f2);
    }
}
