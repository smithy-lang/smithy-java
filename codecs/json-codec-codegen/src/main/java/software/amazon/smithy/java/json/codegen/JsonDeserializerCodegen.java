/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.codegen.rt.plan.FieldCategory;
import software.amazon.smithy.java.codegen.rt.plan.FieldPlan;
import software.amazon.smithy.java.codegen.rt.plan.StructCodePlan;
import software.amazon.smithy.java.core.schema.SmithyEnum;

/**
 * Generates specialized JSON deserializer source code for a struct or union shape.
 * The generated class implements {@code GeneratedStructDeserializer} and reads JSON
 * directly from a byte buffer with speculative field name matching.
 */
final class JsonDeserializerCodegen {

    private int varSeq;
    private final Map<String, String> nestedDeCaches = new LinkedHashMap<>();

    private String v(String base) {
        return base + varSeq;
    }

    String generate(StructCodePlan plan, String className, String packageName) {
        varSeq = 0;
        nestedDeCaches.clear();

        // Collect nested struct types
        collectNestedStructTypes(plan);

        SourceBuilder sb = new SourceBuilder();

        sb.line("package " + packageName + ";");
        sb.emptyLine();
        sb.line("import java.util.Arrays;");
        sb.line("import java.util.ArrayList;");
        sb.line("import java.util.LinkedHashMap;");
        sb.line("import java.util.List;");
        sb.line("import java.util.Map;");
        sb.line("import java.nio.ByteBuffer;");
        sb.line("import java.math.BigInteger;");
        sb.line("import java.math.BigDecimal;");
        sb.line("import java.time.Instant;");
        sb.line("import software.amazon.smithy.java.codegen.rt.GeneratedStructDeserializer;");
        sb.line("import software.amazon.smithy.java.core.schema.SerializableStruct;");
        sb.line("import software.amazon.smithy.java.core.schema.ShapeBuilder;");
        sb.line("import software.amazon.smithy.java.json.smithy.JsonReadUtils;");
        sb.line("import software.amazon.smithy.java.json.smithy.JsonParseState;");
        sb.line("import software.amazon.smithy.java.json.codegen.JsonReaderContext;");
        sb.line("import software.amazon.smithy.java.json.codegen.JsonCodegenHelpers;");
        sb.line("import software.amazon.smithy.java.core.serde.SerializationException;");
        sb.emptyLine();

        sb.beginBlock("public final class " + className + " implements GeneratedStructDeserializer");

        String fqcn = packageName + "." + plan.shapeClass().getSimpleName();
        List<FieldPlan> fields = plan.fields();

        // Field name byte constants
        for (int i = 0; i < fields.size(); i++) {
            FieldPlan field = fields.get(i);
            sb.line("private static final byte[] DN_" + i
                    + " = \"" + field.memberName() + "\".getBytes(java.nio.charset.StandardCharsets.UTF_8);");
        }

        // Cached deserializer + schema fields for nested struct types
        for (var entry : nestedDeCaches.entrySet()) {
            sb.line("private static final Object[] " + entry.getValue()
                    + " = new Object[2];");
        }
        sb.emptyLine();

        // deserialize method
        sb.line("@Override");
        sb.beginBlock("public SerializableStruct deserialize(Object ctxObj, ShapeBuilder builderObj)");

        sb.line("JsonReaderContext ctx = (JsonReaderContext) ctxObj;");
        sb.line(fqcn + ".Builder builder = (" + fqcn + ".Builder) builderObj;");
        sb.line("byte[] buf = ctx.buf;");
        sb.line("int pos = ctx.pos;");
        sb.line("int end = ctx.end;");
        sb.emptyLine();

        // Expect opening brace
        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.beginBlock("if (pos >= end || buf[pos] != '{')");
        sb.line("throw new SerializationException(\"Expected '{' at position \" + pos);");
        sb.endBlock();
        sb.line("pos++;");
        sb.emptyLine();

        sb.line("int expectedNext = 0;");
        sb.line("boolean first = true;");
        sb.emptyLine();

        sb.beginBlock("while (true)");

        // Check for closing brace
        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.beginBlock("if (pos < end && buf[pos] == '}')");
        sb.line("pos++;");
        sb.line("break;");
        sb.endBlock();

        // Check for comma between fields
        sb.beginBlock("if (!first)");
        sb.beginBlock("if (pos >= end || buf[pos] != ',')");
        sb.line("throw new SerializationException(\"Expected ',' at position \" + pos);");
        sb.endBlock();
        sb.line("pos++;");
        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.endBlock();
        sb.line("first = false;");
        sb.emptyLine();

        // Parse field name
        sb.line("// Parse field name");
        sb.beginBlock("if (pos >= end || buf[pos] != '\"')");
        sb.line("throw new SerializationException(\"Expected '\\\"' at position \" + pos);");
        sb.endBlock();
        sb.line("pos++;"); // skip opening quote
        sb.emptyLine();

        sb.line("int matched = -1;");
        sb.emptyLine();

        // Speculative matching: try expected field first
        sb.line("// Speculative matching: try expected field first");
        sb.beginBlock("switch (expectedNext)");
        for (int i = 0; i < fields.size(); i++) {
            sb.beginBlock("case " + i + ":");
            sb.line("if (pos + DN_" + i + ".length < end && buf[pos + DN_" + i + ".length] == '\"'");
            sb.line("        && Arrays.equals(buf, pos, pos + DN_" + i + ".length, DN_" + i + ", 0, DN_"
                    + i + ".length))");
            sb.beginBlock("");
            sb.line("matched = " + i + ";");
            sb.line("expectedNext = " + (i + 1) + ";");
            sb.line("pos += DN_" + i + ".length + 1;");
            sb.endBlock();
            sb.line("break;");
            sb.endBlock();
        }
        sb.endBlock(); // switch
        sb.emptyLine();

        // Slow path: hash-based field dispatch
        sb.line("// Slow path: FNV-1a hash dispatch");
        sb.beginBlock("if (matched == -1)");
        sb.line("int nameStart = pos;");
        sb.beginBlock("while (pos < end && buf[pos] != '\"')");
        sb.line("if (buf[pos] == '\\\\') pos++;");
        sb.line("pos++;");
        sb.endBlock();
        sb.beginBlock("if (pos >= end)");
        sb.line("throw new SerializationException(\"Unterminated field name\");");
        sb.endBlock();
        sb.line("int nameEnd = pos;");
        sb.line("pos++;"); // skip closing quote
        sb.line("int nameLen = nameEnd - nameStart;");
        sb.emptyLine();

        // Inline FNV-1a hash computation in generated code
        sb.line("int hash = 0x811c9dc5;");
        sb.beginBlock("for (int h = nameStart; h < nameEnd; h++)");
        sb.line("hash ^= buf[h] & 0xFF;");
        sb.line("hash *= 0x01000193;");
        sb.endBlock();
        sb.emptyLine();

        // Build hash -> field index(es) map for collision detection
        java.util.Map<Integer, java.util.List<Integer>> hashToFields = new java.util.LinkedHashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            int hash = computeFnv1a(fields.get(i).memberName());
            hashToFields.computeIfAbsent(hash, k -> new java.util.ArrayList<>()).add(i);
        }

        sb.beginBlock("switch (hash)");
        for (var entry : hashToFields.entrySet()) {
            sb.line("case " + formatHashLiteral(entry.getKey()) + ":");
            List<Integer> fieldIndices = entry.getValue();
            for (int fi : fieldIndices) {
                sb.beginBlock("if (nameLen == DN_" + fi + ".length"
                        + " && Arrays.equals(buf, nameStart, nameEnd, DN_" + fi + ", 0, DN_" + fi + ".length))");
                sb.line("matched = " + fi + ";");
                sb.line("expectedNext = " + (fi + 1) + ";");
                sb.endBlock();
            }
            sb.line("break;");
        }
        sb.endBlock(); // switch
        sb.endBlock(); // if matched == -1
        sb.emptyLine();

        // Skip colon
        sb.line("// Skip colon");
        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.beginBlock("if (pos >= end || buf[pos] != ':')");
        sb.line("throw new SerializationException(\"Expected ':' at position \" + pos);");
        sb.endBlock();
        sb.line("pos++;");
        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.emptyLine();

        // Check for null
        sb.line("// Check for null value");
        sb.beginBlock("if (pos + 4 <= end && buf[pos] == 'n' && buf[pos+1] == 'u' "
                + "&& buf[pos+2] == 'l' && buf[pos+3] == 'l')");
        sb.line("pos += 4;");
        sb.line("continue;");
        sb.endBlock();
        sb.emptyLine();

        // Dispatch on matched field
        sb.line("// Dispatch on matched field");
        sb.beginBlock("switch (matched)");
        for (int i = 0; i < fields.size(); i++) {
            FieldPlan field = fields.get(i);
            sb.beginBlock("case " + i + ":"); // field: memberName
            emitFieldDeserialization(sb, field, fqcn);
            sb.line("break;");
            sb.endBlock();
        }
        sb.beginBlock("default:");
        sb.line("pos = JsonReadUtils.skipValue(buf, pos, end, ctx);");
        sb.line("break;");
        sb.endBlock();
        sb.endBlock(); // switch
        sb.endBlock(); // while
        sb.emptyLine();

        sb.line("ctx.pos = pos;");
        sb.line("return (SerializableStruct) builder.errorCorrection().build();");

        sb.endBlock(); // method
        sb.endBlock(); // class

        return sb.toString();
    }

    private void emitFieldDeserialization(SourceBuilder sb, FieldPlan field, String fqcn) {
        varSeq++;
        String builderSetter = "builder." + field.memberName();
        switch (field.category()) {
            case BOOLEAN:
                String bval = v("_bval");
                sb.line("boolean " + bval + " = pos + 4 <= end && buf[pos] == 't';");
                sb.beginBlock("if (" + bval + ")");
                sb.line("pos += 4;");
                sb.endBlock();
                sb.beginBlock("else");
                sb.line("pos += 5;");
                sb.endBlock();
                sb.line(builderSetter + "(" + bval + ");");
                break;
            case BYTE:
                sb.line("JsonReadUtils.parseLong(buf, pos, end, ctx);");
                sb.line(builderSetter + "((byte) ctx.parsedLong);");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case SHORT:
                sb.line("JsonReadUtils.parseLong(buf, pos, end, ctx);");
                sb.line(builderSetter + "((short) ctx.parsedLong);");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case INTEGER:
                sb.line("JsonReadUtils.parseLong(buf, pos, end, ctx);");
                sb.line(builderSetter + "((int) ctx.parsedLong);");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case LONG:
                sb.line("JsonReadUtils.parseLong(buf, pos, end, ctx);");
                sb.line(builderSetter + "(ctx.parsedLong);");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case FLOAT:
                sb.line("JsonReadUtils.parseDouble(buf, pos, end, ctx);");
                sb.line(builderSetter + "((float) ctx.parsedDouble);");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case DOUBLE:
                sb.line("JsonReadUtils.parseDouble(buf, pos, end, ctx);");
                sb.line(builderSetter + "(ctx.parsedDouble);");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case STRING:
                sb.line("JsonReadUtils.parseString(buf, pos, end, ctx);");
                sb.line(builderSetter + "(ctx.parsedString);");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case BLOB:
                String decoded = v("_decoded");
                sb.line("byte[] " + decoded + " = JsonReadUtils.decodeBase64String(buf, pos, end, ctx);");
                sb.line(builderSetter + "(ByteBuffer.wrap(" + decoded + "));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case BIG_INTEGER:
                String biStart = v("_start");
                sb.line("int " + biStart + " = pos;");
                sb.line("pos = JsonReadUtils.findNumberEnd(buf, pos, end);");
                sb.line(builderSetter + "(new BigInteger(new String(buf, " + biStart + ", pos - " + biStart + ","
                        + " java.nio.charset.StandardCharsets.US_ASCII)));");
                break;
            case BIG_DECIMAL:
                String bdStart = v("_bdstart");
                sb.line("int " + bdStart + " = pos;");
                sb.line("pos = JsonReadUtils.findNumberEnd(buf, pos, end);");
                sb.line(builderSetter + "(new BigDecimal(new String(buf, " + bdStart + ", pos - " + bdStart + ","
                        + " java.nio.charset.StandardCharsets.US_ASCII)));");
                break;
            case TIMESTAMP:
                emitTimestampDeserialization(sb, field, builderSetter);
                break;
            case LIST:
                emitListDeserialization(sb, field, fqcn, builderSetter);
                break;
            case MAP:
                emitMapDeserialization(sb, field, fqcn, builderSetter);
                break;
            case ENUM_STRING:
                sb.line("JsonReadUtils.parseString(buf, pos, end, ctx);");
                sb.line(builderSetter + "("
                        + field.schema().memberTarget().shapeClass().getName()
                        + ".from(ctx.parsedString));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case INT_ENUM:
                sb.line("JsonReadUtils.parseLong(buf, pos, end, ctx);");
                sb.line(builderSetter + "("
                        + field.schema().memberTarget().shapeClass().getName()
                        + ".from((int) ctx.parsedLong));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case STRUCT:
            case UNION:
                Class<?> structTargetClass = field.schema().memberTarget().shapeClass();
                String structClassName = structTargetClass.getName();
                String nested = v("_nested");
                sb.line("ctx.pos = pos;");
                sb.line("Object " + nested + " = " + deserializeNestedCallExpr(structTargetClass) + ";");
                sb.line(builderSetter + "((" + structClassName + ") " + nested + ");");
                sb.line("pos = ctx.pos;");
                break;
            case DOCUMENT:
                sb.line("ctx.pos = pos;");
                sb.line(builderSetter + "((software.amazon.smithy.java.core.serde.document.Document)"
                        + " JsonCodegenHelpers.deserializeDocument(ctx));");
                sb.line("pos = ctx.pos;");
                break;
            default:
                sb.line("pos = JsonReadUtils.skipValue(buf, pos, end, ctx);");
                break;
        }
    }

    private void emitTimestampDeserialization(SourceBuilder sb, FieldPlan field, String builderSetter) {
        // varSeq already incremented by caller
        String ts = v("_ts");
        String format = field.timestampFormat();
        if (format == null || "EPOCH_SECONDS".equals(format)) {
            sb.line("Instant " + ts + " = JsonCodegenHelpers.parseEpochSeconds(buf, pos, end, ctx);");
            sb.line("pos = ctx.parsedEndPos;");
            sb.line(builderSetter + "(" + ts + ");");
        } else if ("HTTP_DATE".equals(format)) {
            // Check if value is a number (epoch seconds) first, which happens when
            // the serializer does not use the timestampFormat trait
            sb.beginBlock("if (pos < end && (buf[pos] == '-' || (buf[pos] >= '0' && buf[pos] <= '9')))");
            sb.line("Instant " + ts + " = JsonCodegenHelpers.parseEpochSeconds(buf, pos, end, ctx);");
            sb.line("pos = ctx.parsedEndPos;");
            sb.line(builderSetter + "(" + ts + ");");
            sb.endBlock();
            sb.beginBlock("else");
            sb.line("Instant " + ts + " = JsonReadUtils.parseHttpDate(buf, pos, end, ctx);");
            sb.beginBlock("if (" + ts + " != null)");
            sb.line("pos = ctx.parsedEndPos;");
            sb.endBlock();
            sb.beginBlock("else");
            sb.line("JsonReadUtils.parseString(buf, pos, end, ctx);");
            sb.line(ts + " = Instant.parse(ctx.parsedString);");
            sb.line("pos = ctx.parsedEndPos;");
            sb.endBlock();
            sb.line(builderSetter + "(" + ts + ");");
            sb.endBlock();
        } else {
            // date-time or other string formats: check for numeric (epoch seconds) first
            sb.beginBlock("if (pos < end && (buf[pos] == '-' || (buf[pos] >= '0' && buf[pos] <= '9')))");
            sb.line("Instant " + ts + " = JsonCodegenHelpers.parseEpochSeconds(buf, pos, end, ctx);");
            sb.line("pos = ctx.parsedEndPos;");
            sb.line(builderSetter + "(" + ts + ");");
            sb.endBlock();
            sb.beginBlock("else");
            sb.line("Instant " + ts + " = JsonReadUtils.parseIso8601(buf, pos, end, ctx);");
            sb.beginBlock("if (" + ts + " != null)");
            sb.line("pos = ctx.parsedEndPos;");
            sb.endBlock();
            sb.beginBlock("else");
            sb.line("JsonReadUtils.parseString(buf, pos, end, ctx);");
            sb.line(ts + " = Instant.parse(ctx.parsedString);");
            sb.line("pos = ctx.parsedEndPos;");
            sb.endBlock();
            sb.line(builderSetter + "(" + ts + ");");
            sb.endBlock();
        }
    }

    private void emitListDeserialization(
            SourceBuilder sb,
            FieldPlan field,
            String fqcn,
            String builderSetter
    ) {
        // varSeq already incremented by caller
        FieldCategory elemCategory = resolveElementCategory(field);
        String list = v("_list");

        sb.line("ArrayList " + list + " = new ArrayList(4);");
        sb.beginBlock("if (pos < end && buf[pos] == '[')");
        sb.line("pos++;");
        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.beginBlock("if (pos < end && buf[pos] != ']')");

        sb.beginBlock("while (true)");
        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");

        // Check for null element
        sb.beginBlock("if (pos + 4 <= end && buf[pos] == 'n' && buf[pos+1] == 'u'"
                + " && buf[pos+2] == 'l' && buf[pos+3] == 'l')");
        sb.line(list + ".add(null);");
        sb.line("pos += 4;");
        sb.endBlock();
        sb.beginBlock("else");
        emitElementDeserialization(sb, elemCategory, field, list);
        sb.endBlock();

        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.beginBlock("if (pos < end && buf[pos] == ']')");
        sb.line("break;");
        sb.endBlock();
        sb.line("pos++;"); // skip comma
        sb.endBlock(); // while
        sb.endBlock(); // if not ]
        sb.line("pos++;"); // skip ]
        sb.endBlock(); // if [
        sb.line(builderSetter + "(" + list + ");");
    }

    private void emitMapDeserialization(
            SourceBuilder sb,
            FieldPlan field,
            String fqcn,
            String builderSetter
    ) {
        // varSeq already incremented by caller
        FieldCategory valCategory = resolveMapValueCategory(field);
        String map = v("_map");
        String key = v("_mkey");

        sb.line("LinkedHashMap " + map + " = new LinkedHashMap(4);");
        sb.beginBlock("if (pos < end && buf[pos] == '{')");
        sb.line("pos++;");
        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.beginBlock("if (pos < end && buf[pos] != '}')");

        sb.beginBlock("while (true)");
        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        // Parse key (always string)
        sb.line("JsonReadUtils.parseString(buf, pos, end, ctx);");
        sb.line("String " + key + " = ctx.parsedString;");
        sb.line("pos = ctx.parsedEndPos;");
        // Skip colon
        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.line("pos++;"); // skip :
        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");

        // Check for null value
        sb.beginBlock("if (pos + 4 <= end && buf[pos] == 'n' && buf[pos+1] == 'u'"
                + " && buf[pos+2] == 'l' && buf[pos+3] == 'l')");
        sb.line(map + ".put(" + key + ", null);");
        sb.line("pos += 4;");
        sb.endBlock();
        sb.beginBlock("else");
        emitMapValueDeserialization(sb, valCategory, field, map, key);
        sb.endBlock();

        sb.line("if (pos < end && buf[pos] <= ' ') pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.beginBlock("if (pos < end && buf[pos] == '}')");
        sb.line("break;");
        sb.endBlock();
        sb.line("pos++;"); // skip comma
        sb.endBlock(); // while
        sb.endBlock(); // if not }
        sb.line("pos++;"); // skip }
        sb.endBlock(); // if {
        sb.line(builderSetter + "(" + map + ");");
    }

    private void emitElementDeserialization(
            SourceBuilder sb,
            FieldCategory category,
            FieldPlan field,
            String listVar
    ) {
        switch (category) {
            case BOOLEAN:
                varSeq++;
                String bv = v("_bv");
                sb.line("boolean " + bv + " = pos + 4 <= end && buf[pos] == 't';");
                sb.beginBlock("if (" + bv + ")");
                sb.line("pos += 4;");
                sb.endBlock();
                sb.beginBlock("else");
                sb.line("pos += 5;");
                sb.endBlock();
                sb.line(listVar + ".add(Boolean.valueOf(" + bv + "));");
                break;
            case BYTE:
                sb.line("JsonReadUtils.parseLong(buf, pos, end, ctx);");
                sb.line(listVar + ".add(Byte.valueOf((byte) ctx.parsedLong));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case SHORT:
                sb.line("JsonReadUtils.parseLong(buf, pos, end, ctx);");
                sb.line(listVar + ".add(Short.valueOf((short) ctx.parsedLong));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case INTEGER:
                sb.line("JsonReadUtils.parseLong(buf, pos, end, ctx);");
                sb.line(listVar + ".add(Integer.valueOf((int) ctx.parsedLong));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case LONG:
                sb.line("JsonReadUtils.parseLong(buf, pos, end, ctx);");
                sb.line(listVar + ".add(Long.valueOf(ctx.parsedLong));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case FLOAT:
                sb.line("JsonReadUtils.parseDouble(buf, pos, end, ctx);");
                sb.line(listVar + ".add(Float.valueOf((float) ctx.parsedDouble));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case DOUBLE:
                sb.line("JsonReadUtils.parseDouble(buf, pos, end, ctx);");
                sb.line(listVar + ".add(Double.valueOf(ctx.parsedDouble));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case STRING:
                sb.line("JsonReadUtils.parseString(buf, pos, end, ctx);");
                sb.line(listVar + ".add(ctx.parsedString);");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case ENUM_STRING:
                sb.line("JsonReadUtils.parseString(buf, pos, end, ctx);");
                sb.line(listVar + ".add(" + field.elementClass().getName() + ".from(ctx.parsedString));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            default:
                // For complex elements, delegate
                sb.line("ctx.pos = pos;");
                sb.line(listVar + ".add(" + deserializeNestedCallExpr(field.elementClass()) + ");");
                sb.line("pos = ctx.pos;");
                break;
        }
    }

    private void emitMapValueDeserialization(
            SourceBuilder sb,
            FieldCategory category,
            FieldPlan field,
            String mapVar,
            String keyVar
    ) {
        switch (category) {
            case BOOLEAN:
                varSeq++;
                String bv = v("_mbv");
                sb.line("boolean " + bv + " = pos + 4 <= end && buf[pos] == 't';");
                sb.beginBlock("if (" + bv + ")");
                sb.line("pos += 4;");
                sb.endBlock();
                sb.beginBlock("else");
                sb.line("pos += 5;");
                sb.endBlock();
                sb.line(mapVar + ".put(" + keyVar + ", Boolean.valueOf(" + bv + "));");
                break;
            case BYTE:
            case SHORT:
            case INTEGER:
                sb.line("JsonReadUtils.parseLong(buf, pos, end, ctx);");
                sb.line(mapVar + ".put(" + keyVar + ", Integer.valueOf((int) ctx.parsedLong));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case LONG:
                sb.line("JsonReadUtils.parseLong(buf, pos, end, ctx);");
                sb.line(mapVar + ".put(" + keyVar + ", Long.valueOf(ctx.parsedLong));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case FLOAT:
                sb.line("JsonReadUtils.parseDouble(buf, pos, end, ctx);");
                sb.line(mapVar + ".put(" + keyVar + ", Float.valueOf((float) ctx.parsedDouble));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case DOUBLE:
                sb.line("JsonReadUtils.parseDouble(buf, pos, end, ctx);");
                sb.line(mapVar + ".put(" + keyVar + ", Double.valueOf(ctx.parsedDouble));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case STRING:
                sb.line("JsonReadUtils.parseString(buf, pos, end, ctx);");
                sb.line(mapVar + ".put(" + keyVar + ", ctx.parsedString);");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case ENUM_STRING:
                sb.line("JsonReadUtils.parseString(buf, pos, end, ctx);");
                sb.line(mapVar + ".put(" + keyVar + ", " + field.mapValueClass().getName()
                        + ".from(ctx.parsedString));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            default:
                // For complex values, delegate
                sb.line("ctx.pos = pos;");
                sb.line(mapVar + ".put(" + keyVar + ", " + deserializeNestedCallExpr(field.mapValueClass()) + ");");
                sb.line("pos = ctx.pos;");
                break;
        }
    }

    private static FieldCategory resolveElementCategory(FieldPlan field) {
        Class<?> elemClass = field.elementClass();
        if (elemClass == null) {
            return FieldCategory.STRING;
        }
        return classToCategory(elemClass);
    }

    private static FieldCategory resolveMapValueCategory(FieldPlan field) {
        Class<?> valClass = field.mapValueClass();
        if (valClass == null) {
            return FieldCategory.STRING;
        }
        return classToCategory(valClass);
    }

    private static FieldCategory classToCategory(Class<?> clazz) {
        if (clazz == String.class) {
            return FieldCategory.STRING;
        }
        if (clazz == Integer.class || clazz == int.class) {
            return FieldCategory.INTEGER;
        }
        if (clazz == Long.class || clazz == long.class) {
            return FieldCategory.LONG;
        }
        if (clazz == Double.class || clazz == double.class) {
            return FieldCategory.DOUBLE;
        }
        if (clazz == Float.class || clazz == float.class) {
            return FieldCategory.FLOAT;
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return FieldCategory.BOOLEAN;
        }
        if (clazz == Short.class || clazz == short.class) {
            return FieldCategory.SHORT;
        }
        if (clazz == Byte.class || clazz == byte.class) {
            return FieldCategory.BYTE;
        }
        if (SmithyEnum.class.isAssignableFrom(clazz)) {
            return FieldCategory.ENUM_STRING;
        }
        return FieldCategory.STRUCT;
    }

    private void collectNestedStructTypes(StructCodePlan plan) {
        for (FieldPlan field : plan.fields()) {
            switch (field.category()) {
                case STRUCT:
                case UNION:
                    registerNestedDeCache(field.schema().memberTarget().shapeClass());
                    break;
                case LIST:
                    Class<?> elemClass = field.elementClass();
                    if (elemClass != null && !isSimpleType(elemClass)) {
                        registerNestedDeCache(elemClass);
                    }
                    break;
                case MAP:
                    Class<?> valClass = field.mapValueClass();
                    if (valClass != null && !isSimpleType(valClass)) {
                        registerNestedDeCache(valClass);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void registerNestedDeCache(Class<?> clazz) {
        if (clazz != null) {
            nestedDeCaches.computeIfAbsent(clazz.getName(), k -> "_DE_" + clazz.getSimpleName());
        }
    }

    private String getDeCacheField(Class<?> clazz) {
        return nestedDeCaches.get(clazz.getName());
    }

    private static boolean isSimpleType(Class<?> clazz) {
        return clazz == String.class || clazz == Integer.class
                || clazz == Long.class
                || clazz == Double.class
                || clazz == Float.class
                || clazz == Boolean.class
                || clazz == Short.class
                || clazz == Byte.class
                || clazz == int.class
                || clazz == long.class
                || clazz == double.class
                || clazz == float.class
                || clazz == boolean.class
                || clazz == short.class
                || clazz == byte.class
                || SmithyEnum.class.isAssignableFrom(clazz);
    }

    private String deserializeNestedCallExpr(Class<?> targetClass) {
        String cacheField = targetClass != null ? getDeCacheField(targetClass) : null;
        if (cacheField != null) {
            return "JsonCodegenHelpers.deserializeNestedStructDirect(ctx, "
                    + cacheField + ", " + targetClass.getName() + ".class)";
        } else {
            return "JsonCodegenHelpers.deserializeNestedStruct(ctx, "
                    + targetClass.getName() + ".class)";
        }
    }

    private static int computeFnv1a(String fieldName) {
        byte[] bytes = fieldName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int hash = 0x811c9dc5;
        for (byte b : bytes) {
            hash ^= b & 0xFF;
            hash *= 0x01000193;
        }
        return hash;
    }

    private static String formatHashLiteral(int hash) {
        return Integer.toString(hash);
    }
}
