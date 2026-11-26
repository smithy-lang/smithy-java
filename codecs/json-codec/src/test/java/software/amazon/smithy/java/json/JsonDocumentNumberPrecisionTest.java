/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Verifies that numbers read into a {@link Document} do not lose precision.
 * 
 * <p>A fractional JSON number (anything containing {@code .}, {@code e} or {@code E}) is read as a
 * {@link BigDecimal} holding the exact value, not a double that would silently lose precision.
 * Integers keep their narrowest type.
 */

public class JsonDocumentNumberPrecisionTest extends ProviderTestBase {

    // More significant digits than a double can hold, so any trip through double changes the value.
    // This is especially dangerous for operations involving money.
    private static final String BEYOND_DOUBLE = "1.00499999999999999";

    private static Document read(JsonSerdeProvider provider, String json) {
        return codec(provider).createDeserializer(json.getBytes(StandardCharsets.UTF_8)).readDocument();
    }

    static List<Arguments> providersAndFractionalNumbers() {
        var numbers = new String[] {
                BEYOND_DOUBLE,
                "-" + BEYOND_DOUBLE,
                "123456789.123456789123456789",
                "100.50",
                "100.0",
                "3.14",
                "1e2",
                "1E-2",
                "1.5e3",
                "1.5e400", //overflows double
                "1e-400", //underflows double
        };
        var args = new ArrayList<Arguments>();
        for (var provider : providerInstances()) {
            for (var number : numbers) {
                args.add(Arguments.of(Named.of(provider.getName(), provider), number));
            }
        }

        return args;
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("providersAndFractionalNumbers")
    public void fractionalNumberKeepsExactValue(JsonSerdeProvider provider, String json) {
        var document = read(provider, json);
        assertThat(document.type(), is(ShapeType.BIG_DECIMAL));
        assertThat(document.asBigDecimal(), comparesEqualTo(new BigDecimal(json)));
    }

    @PerProvider
    public void fractionalNumberStillConvertsToNarrowerTypes(JsonSerdeProvider provider) {
        var document = read(provider, "1.5");

        assertThat(document.asFloat(), is(1.5f));
        assertThat(document.asDouble(), is(1.5));
        assertThat(document.asBigDecimal(), comparesEqualTo(new BigDecimal("1.5")));
    }

    @PerProvider
    public void fractionalNumberInObjectKeepsExactValue(JsonSerdeProvider provider) {
        var document = read(provider, "{\"bigDecimal\": " + BEYOND_DOUBLE + "}");

        assertThat(document.getMember("bigDecimal").asBigDecimal(), comparesEqualTo(new BigDecimal(BEYOND_DOUBLE)));
    }

    @PerProvider
    public void fractionalNumberInListKeepsExactValue(JsonSerdeProvider provider) {
        var document = read(provider, "[" + BEYOND_DOUBLE + ", 100.50]");

        assertThat(document.asList().get(0).asBigDecimal(), comparesEqualTo(new BigDecimal(BEYOND_DOUBLE)));
        assertThat(document.asList().get(1).asBigDecimal(), comparesEqualTo(new BigDecimal("100.50")));
    }

    @PerProvider
    public void integersKeepTheirNarrowestType(JsonSerdeProvider provider) {
        assertThat(read(provider, "100").type(), is(ShapeType.INTEGER));
        assertThat(read(provider, "-100").type(), is(ShapeType.INTEGER));
        assertThat(read(provider, "2147483648").type(), is(ShapeType.LONG));
        assertThat(read(provider, "9223372036854775807").type(), is(ShapeType.LONG));
        assertThat(read(provider, "9223372036854775808").type(), is(ShapeType.BIG_INTEGER));
        assertThat(read(provider, "99999999999999999999").asBigInteger(),
                equalTo(new BigInteger("99999999999999999999")));
    }
}
