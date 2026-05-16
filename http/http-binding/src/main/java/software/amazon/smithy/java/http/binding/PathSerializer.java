/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.io.uri.URLEncoding;
import software.amazon.smithy.model.traits.HttpTrait;

/**
 * Compiles an HTTP URI template into a compact, tape for serializing the path of a single operation.
 *
 * <p>The tape is a {@code byte[]} of {@code (opcode, operand)} pairs:
 * <ul>
 *   <li>{@code op=1} — emit literal at {@code literals[operand]}</li>
 *   <li>{@code op=2} — emit regular label via {@code schema.member(members[operand])}</li>
 *   <li>{@code op=3} — emit greedy label via {@code schema.member(operand)}. Operand is the schema member index
 *   directly: a URI has at most one greedy label, so there's no benefit to indirecting through a side array.</li>
 * </ul>
 *
 * <p>Operands are read as unsigned bytes ({@code & 0xFF}). Supports up to 256 members in the input struct,
 * which more than exceeds anything we've seen in the wild.
 */
final class PathSerializer {

    private final Schema schema;
    // Tape of (opcode, operand) pairs. See class doc for opcode meanings.
    private final byte[] tape;
    // Regular-label member indices in input schema, in URI order. Excludes greedy.
    private final byte[] members;
    // Literal segments in URI order.
    private final String[] literals;
    // Non-null if the path has no labels and is static.
    private final String fastPath;

    /**
     * Compile a {@code PathSerializer} for one operation.
     *
     * @param httpTrait   operation's @http trait, source of the URI template
     * @param inputSchema input-struct Schema providing member resolution
     */
    PathSerializer(HttpTrait httpTrait, Schema inputSchema) {
        this.schema = inputSchema;
        var segments = httpTrait.getUri().getSegments();

        List<Byte> memberList = new ArrayList<>();
        List<String> literalList = new ArrayList<>();
        List<Byte> tapeList = new ArrayList<>();

        // Smithy URI templates start with "/" and segments are slash-separated.
        // We accumulate literal text into `current` until we hit a label, then
        // flush as one literal entry. Adjacent literals collapse for free.
        StringBuilder current = new StringBuilder("/");
        boolean firstSegment = true;
        for (var seg : segments) {
            if (!firstSegment) {
                current.append('/');
            }
            firstSegment = false;
            if (seg.isLabel() || seg.isGreedyLabel()) {
                if (!current.isEmpty()) {
                    tapeList.add((byte) 1);
                    tapeList.add((byte) literalList.size());
                    literalList.add(current.toString());
                    current.setLength(0);
                }
                String name = seg.getContent();
                Schema labelMember = inputSchema.member(name);
                if (labelMember == null) {
                    throw new IllegalStateException("URI label `" + name + "` not set for " + inputSchema.id());
                }
                int memberIdx = labelMember.memberIndex();
                if (seg.isGreedyLabel()) {
                    tapeList.add((byte) 3);
                    tapeList.add((byte) memberIdx);
                } else {
                    tapeList.add((byte) 2);
                    tapeList.add((byte) memberList.size());
                    memberList.add((byte) memberIdx);
                }
            } else {
                current.append(seg.getContent());
            }
        }

        // Trailing literal: only emit if non-empty so paths ending in a label don't pay an empty append at runtime.
        if (!current.isEmpty()) {
            tapeList.add((byte) 1);
            tapeList.add((byte) literalList.size());
            literalList.add(current.toString());
        }

        this.members = toByteArray(memberList);
        this.literals = literalList.toArray(new String[0]);
        this.tape = toByteArray(tapeList);
        // Fast path: a single literal opcode with no labels means the path is fully static.
        this.fastPath = (tape.length == 2 && tape[0] == 1) ? literals[0] : null;
    }

    String serialize(SerializableStruct struct) {
        if (fastPath != null) {
            return fastPath;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tape.length; i += 2) {
            int op = tape[i];
            int operand = tape[i + 1] & 0xFF;
            switch (op) {
                case 1 -> sb.append(literals[operand]);
                case 2 -> {
                    var labelSchema = schema.member(members[operand] & 0xFF);
                    var value = formatLabelValue(struct, labelSchema);
                    URLEncoding.encodeUnreserved(value, sb, false);
                }
                case 3 -> {
                    var labelSchema = schema.member(operand);
                    var value = formatLabelValue(struct, labelSchema);
                    URLEncoding.encodeUnreserved(value, sb, true);
                }
                default -> throw new IllegalStateException("Unknown path-tape opcode: " + op);
            }
        }

        return sb.toString();
    }

    private static String formatLabelValue(SerializableStruct struct, Schema labelSchema) {
        Object value = struct.getMemberValue(labelSchema);
        if (value == null) {
            throw emptyLabel(labelSchema);
        }

        return switch (labelSchema.type()) {
            case STRING, ENUM -> {
                var s = (String) value;
                if (s.isEmpty()) {
                    throw emptyLabel(labelSchema);
                }
                yield s;
            }
            case BOOLEAN -> Boolean.toString((boolean) value);
            case BYTE -> Byte.toString((byte) value);
            case SHORT -> Short.toString((short) value);
            case INTEGER, INT_ENUM -> Integer.toString((int) value);
            case LONG -> Long.toString((long) value);
            case FLOAT -> Float.toString((float) value);
            case DOUBLE -> Double.toString((double) value);
            case BIG_INTEGER, BIG_DECIMAL -> value.toString();
            case TIMESTAMP -> HttpBindingSchemaExtensions.memberBindingOf(labelSchema)
                    .timestampFormatter()
                    .writeString((Instant) value);
            default -> throw new SerializationException(
                    "Unsupported HTTP label type " + labelSchema.type() + " for `" + labelSchema.id() + "`");
        };
    }

    private static SerializationException emptyLabel(Schema labelSchema) {
        throw new SerializationException("HTTP label for `" + labelSchema.id() + "` cannot be empty");
    }

    private static byte[] toByteArray(List<Byte> list) {
        byte[] out = new byte[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
