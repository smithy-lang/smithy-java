/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public class EnumValueTest {

    sealed interface Color extends SmithyEnum, SerializableShape {
        Schema SCHEMA = Schema.createEnum(ShapeId.from("smithy.test#Color"), Set.of("red"));
        Color RED = new $Known("red");

        @Override
        default void serialize(ShapeSerializer serializer) {
            serializer.writeString(SCHEMA, getValue());
        }

        final class $Known extends EnumValue implements Color {
            private $Known(String value) {
                super(value);
            }
        }

        record $Unknown(String value) implements Color {
            public $Unknown {
                Objects.requireNonNull(value, "Value cannot be null");
            }

            @Override
            public String getValue() {
                return value;
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof $Unknown that && value.equals(that.value);
            }

            @Override
            public int hashCode() {
                return value.hashCode();
            }

            @Override
            public String toString() {
                return value;
            }
        }
    }

    sealed interface Size extends SmithyEnum, SerializableShape {
        Schema SCHEMA = Schema.createEnum(ShapeId.from("smithy.test#Size"), Set.of("small"));

        @Override
        default void serialize(ShapeSerializer serializer) {
            serializer.writeString(SCHEMA, getValue());
        }

        record $Unknown(String value) implements Size {
            @Override
            public String getValue() {
                return value;
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof $Unknown that && value.equals(that.value);
            }

            @Override
            public int hashCode() {
                return value.hashCode();
            }
        }
    }

    sealed interface Level extends SmithyIntEnum, SerializableShape {
        Schema SCHEMA = Schema.createIntEnum(ShapeId.from("smithy.test#Level"), Set.of(1));
        Level LOW = new $Known(1);

        @Override
        default void serialize(ShapeSerializer serializer) {
            serializer.writeInteger(SCHEMA, getValue());
        }

        final class $Known extends IntEnumValue implements Level {
            private $Known(int value) {
                super(value);
            }
        }

        record $Unknown(int value) implements Level {
            @Override
            public int getValue() {
                return value;
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof $Unknown that && value == that.value;
            }

            @Override
            public int hashCode() {
                return value;
            }

            @Override
            public String toString() {
                return Integer.toString(value);
            }
        }
    }

    @Test
    public void knownValuesExposeValueAndCompareByIdentity() {
        assertThat(Color.RED.getValue(), equalTo("red"));
        assertThat(Level.LOW.getValue(), equalTo(1));
        assertThat(Color.RED, sameInstance(Color.RED));
        assertThat(Color.RED.equals(new Color.$Unknown("red")), is(false));
        assertThat(new Color.$Unknown("red").equals(Color.RED), is(false));
        assertThat(Color.RED.toString(), containsString("red"));
        assertThat(Level.LOW.toString(), containsString("1"));
    }

    @Test
    public void unknownValuesCompareByValueWithinTheSameType() {
        var a = new Color.$Unknown("purple");
        var b = new Color.$Unknown("purple");
        assertThat(a, equalTo(b));
        assertThat(a.hashCode(), equalTo(b.hashCode()));
        assertThat(a.value(), equalTo("purple"));
        assertThat(a.getValue(), equalTo("purple"));
        assertThat(a, not(equalTo(new Color.$Unknown("green"))));
        assertThat(a.equals(new Size.$Unknown("purple")), is(false));
        assertThat(a.equals(null), is(false));
        assertThat(a.toString(), equalTo("purple"));

        var x = new Level.$Unknown(7);
        assertThat(x, equalTo(new Level.$Unknown(7)));
        assertThat(x.hashCode(), equalTo(7));
        assertThat(x.value(), equalTo(7));
        assertThat(x, not(equalTo(new Level.$Unknown(8))));
        assertThat(x.toString(), equalTo("7"));
    }

    @Test
    public void unknownValuesSupportRecordPatterns() {
        Color color = new Color.$Unknown("purple");
        String matched = switch (color) {
            case Color.$Known known -> "known:" + known.getValue();
            case Color.$Unknown(String value) -> "unknown:" + value;
        };
        assertThat(matched, equalTo("unknown:purple"));
    }

    @Test
    public void unknownStringValuesRejectNull() {
        assertThrows(NullPointerException.class, () -> new Color.$Unknown(null));
    }
}
