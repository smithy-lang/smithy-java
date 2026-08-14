/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import smithy.java.codegen.types.test.model.ClientOptionalRequiredMembers;
import smithy.java.codegen.types.test.model.InterleavedRequiredMembers;
import smithy.java.codegen.types.test.model.RequiredMembers64;
import smithy.java.codegen.types.test.model.RequiredMembers65;
import smithy.java.codegen.types.test.model.RequiredWithDefaultMember;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.Codec;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.json.JsonCodec;

/**
 * Generated builders track which required members have been set in a {@code long} bitmask when a
 * shape has 64 or fewer of them, and fall back to {@code PresenceTracker} above that.
 *
 * <p>The bit a member gets is its position among the <em>required</em> members, which is what these
 * tests are really about: nothing in the generated source tells you whether two members ended up
 * sharing a bit, or whether an optional member consumed one. Both show up here as a build that
 * either accepts an incomplete shape or names the wrong member as missing.
 */
public class RequiredMemberValidationTest {

    private static final JsonCodec CODEC = JsonCodec.builder().build();

    /** Optional members sit between the required ones and must not take bits of their own. */
    @Test
    void buildsWhenEveryRequiredMemberIsSet() {
        var shape = InterleavedRequiredMembers.builder()
                .requiredA("a")
                .requiredB("b")
                .requiredC("c")
                .build();

        assertThat(shape.getRequiredA()).isEqualTo("a");
        assertThat(shape.getRequiredB()).isEqualTo("b");
        assertThat(shape.getRequiredC()).isEqualTo("c");
        assertThat(shape.getOptionalA()).isNull();
    }

    /** Setting the optional members alone leaves every required one missing. */
    @Test
    void namesEveryMissingRequiredMember() {
        var builder = InterleavedRequiredMembers.builder().optionalA("a").optionalB("b").optionalC("c");

        assertThatThrownBy(builder::build)
                .isInstanceOf(SerializationException.class)
                .hasMessage("Missing required members: [requiredA, requiredB, requiredC]");
    }

    /**
     * All but one required member set. If two members shared a bit this would build without
     * complaint; if the bits were assigned over all members rather than the required ones, the
     * mask would never be reached and the wrong name would come back.
     */
    @ParameterizedTest
    @ValueSource(strings = {"requiredA", "requiredB", "requiredC"})
    void namesOnlyTheMissingRequiredMember(String omitted) {
        var builder = InterleavedRequiredMembers.builder();
        if (!omitted.equals("requiredA")) {
            builder.requiredA("a");
        }
        if (!omitted.equals("requiredB")) {
            builder.requiredB("b");
        }
        if (!omitted.equals("requiredC")) {
            builder.requiredC("c");
        }

        assertThatThrownBy(builder::build)
                .isInstanceOf(SerializationException.class)
                .hasMessage("Missing required members: [" + omitted + "]");
    }

    /** Presence is a bit, not a count: setting a member twice is the same as setting it once. */
    @Test
    void toleratesSettingTheSameMemberTwice() {
        var shape = InterleavedRequiredMembers.builder()
                .requiredA("first")
                .requiredA("second")
                .requiredB("b")
                .requiredC("c")
                .build();

        assertThat(shape.getRequiredA()).isEqualTo("second");
    }

    /** A required member with a default is not validated, so it must not take a bit either. */
    @Test
    void requiredMemberWithDefaultIsNotValidated() {
        var shape = RequiredWithDefaultMember.builder().plain("p").build();

        assertThat(shape.getPlain()).isEqualTo("p");
        assertThat(shape.getWithDefault()).isEqualTo("d");
        assertThatThrownBy(() -> RequiredWithDefaultMember.builder().withDefault("x").build())
                .isInstanceOf(SerializationException.class)
                .hasMessage("Missing required members: [plain]");
    }

    /** A required clientOptional member is marked present by the builder's constructor. */
    @Test
    void clientOptionalRequiredMemberIsAssumedPresent() {
        var shape = ClientOptionalRequiredMembers.builder().strict("s").build();

        assertThat(shape.getStrict()).isEqualTo("s");
        assertThat(shape.getLenient()).isNull();
        assertThatThrownBy(() -> ClientOptionalRequiredMembers.builder().build())
                .isInstanceOf(SerializationException.class)
                .hasMessage("Missing required members: [strict]");
    }

    /**
     * 64 required members is the largest count that still uses the inline mask, so it is the only
     * one that exercises the highest bit and the all-ones mask.
     */
    @Test
    void buildsWithAll64RequiredMembersSet() {
        var shape = strictBuild(RequiredMembers64.builder(), json(64, -1));

        assertThat(shape.getM0()).isEqualTo("v0");
        assertThat(shape.getM63()).isEqualTo("v63");
    }

    /**
     * Every member of the 64-member shape, omitted in turn. This is the sweep that would catch a
     * duplicated bit anywhere in the mask, including bit 63.
     */
    @ParameterizedTest
    @MethodSource("indexesOf64")
    void namesTheOneMissingMemberOf64(int omitted) {
        assertThatThrownBy(() -> strictBuild(RequiredMembers64.builder(), json(64, omitted)))
                .isInstanceOf(SerializationException.class)
                .hasMessage("Missing required members: [m" + omitted + "]");
    }

    /**
     * The same sweep through {@code errorCorrection()}, which reads the bits one at a time rather
     * than comparing the whole mask. Only the omitted member may be filled in: a wrong bit there
     * either leaves it unset (and {@code build()} throws) or overwrites a member that was present.
     */
    @ParameterizedTest
    @MethodSource("indexesOf64")
    void errorCorrectionFillsInOnlyTheMissingMemberOf64(int omitted) {
        var shape = CODEC.deserializeShape(json(64, omitted), RequiredMembers64.builder());

        assertEachMemberIs(shape, 64, omitted);
    }

    static Stream<Integer> indexesOf64() {
        return IntStream.range(0, 64).boxed();
    }

    /** One member past the inline limit falls back to the tracker, which must behave the same. */
    @Test
    void buildsWithAll65RequiredMembersSet() {
        var shape = strictBuild(RequiredMembers65.builder(), json(65, -1));

        assertThat(shape.getM0()).isEqualTo("v0");
        assertThat(shape.getM64()).isEqualTo("v64");
    }

    /** Every member holds its parsed value except {@code omitted}, which error correction blanked. */
    private static void assertEachMemberIs(SerializableStruct shape, int count, int omitted) {
        for (int i = 0; i < count; i++) {
            String value = shape.getMemberValue(shape.schema().member("m" + i));
            assertThat(value).as("m%d", i).isEqualTo(i == omitted ? "" : "v" + i);
        }
    }

    /** Builds without the error correction {@link Codec#deserializeShape} applies. */
    private static <T extends SerializableShape> T strictBuild(ShapeBuilder<T> builder, byte[] json) {
        return builder.deserialize(CODEC.createDeserializer(json)).build();
    }

    /** JSON for {@code count} members named {@code m0..} with the one at {@code omitted} left out. */
    private static byte[] json(int count, int omitted) {
        var sb = new StringBuilder("{");
        for (int i = 0; i < count; i++) {
            if (i == omitted) {
                continue;
            }
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append("\"m").append(i).append("\":\"v").append(i).append('"');
        }
        return sb.append('}').toString().getBytes(StandardCharsets.UTF_8);
    }
}
