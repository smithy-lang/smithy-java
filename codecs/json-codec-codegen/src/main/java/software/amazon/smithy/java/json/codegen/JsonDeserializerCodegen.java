/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import java.util.List;
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

    private String v(String base) {
        return base + varSeq;
    }

    String generate(StructCodePlan plan, String className, String packageName) {
        varSeq = 0;
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
        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
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
        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
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
        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
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

        // Slow path: scan for closing quote and match linearly
        sb.line("// Slow path: scan for closing quote and linear match");
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
        sb.emptyLine();

        sb.beginBlock("for (int i = 0; i < " + fields.size() + "; i++)");
        sb.line("byte[] dn;");
        sb.beginBlock("switch (i)");
        for (int i = 0; i < fields.size(); i++) {
            sb.line("case " + i + ": dn = DN_" + i + "; break;");
        }
        sb.line("default: dn = null;");
        sb.endBlock(); // switch
        sb.beginBlock("if (dn != null && dn.length == nameEnd - nameStart"
                + " && Arrays.equals(buf, nameStart, nameEnd, dn, 0, dn.length))");
        sb.line("matched = i;");
        sb.line("expectedNext = i + 1;");
        sb.line("break;");
        sb.endBlock();
        sb.endBlock(); // for
        sb.endBlock(); // if matched == -1
        sb.emptyLine();

        // Skip colon
        sb.line("// Skip colon");
        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.beginBlock("if (pos >= end || buf[pos] != ':')");
        sb.line("throw new SerializationException(\"Expected ':' at position \" + pos);");
        sb.endBlock();
        sb.line("pos++;");
        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
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
                sb.line(builderSetter + "(("
                        + field.schema().memberTarget().shapeClass().getName()
                        + ") JsonCodegenHelpers.resolveEnum(ctx.parsedString, "
                        + field.schema().memberTarget().shapeClass().getName() + ".class));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case INT_ENUM:
                sb.line("JsonReadUtils.parseLong(buf, pos, end, ctx);");
                sb.line(builderSetter + "(("
                        + field.schema().memberTarget().shapeClass().getName()
                        + ") JsonCodegenHelpers.resolveIntEnum((int) ctx.parsedLong, "
                        + field.schema().memberTarget().shapeClass().getName() + ".class));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            case STRUCT:
            case UNION:
                String structClass = field.schema().memberTarget().shapeClass().getName();
                String nested = v("_nested");
                sb.line("ctx.pos = pos;");
                sb.line("Object " + nested + " = JsonCodegenHelpers.deserializeNestedStruct(ctx, "
                        + structClass + ".class);");
                sb.line(builderSetter + "((" + structClass + ") " + nested + ");");
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

        sb.line("ArrayList " + list + " = new ArrayList();");
        sb.beginBlock("if (pos < end && buf[pos] == '[')");
        sb.line("pos++;");
        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.beginBlock("if (pos < end && buf[pos] != ']')");

        sb.beginBlock("while (true)");
        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");

        // Check for null element
        sb.beginBlock("if (pos + 4 <= end && buf[pos] == 'n' && buf[pos+1] == 'u'"
                + " && buf[pos+2] == 'l' && buf[pos+3] == 'l')");
        sb.line(list + ".add(null);");
        sb.line("pos += 4;");
        sb.endBlock();
        sb.beginBlock("else");
        emitElementDeserialization(sb, elemCategory, field, list);
        sb.endBlock();

        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
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

        sb.line("LinkedHashMap " + map + " = new LinkedHashMap();");
        sb.beginBlock("if (pos < end && buf[pos] == '{')");
        sb.line("pos++;");
        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.beginBlock("if (pos < end && buf[pos] != '}')");

        sb.beginBlock("while (true)");
        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        // Parse key (always string)
        sb.line("JsonReadUtils.parseString(buf, pos, end, ctx);");
        sb.line("String " + key + " = ctx.parsedString;");
        sb.line("pos = ctx.parsedEndPos;");
        // Skip colon
        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
        sb.line("pos++;"); // skip :
        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");

        // Check for null value
        sb.beginBlock("if (pos + 4 <= end && buf[pos] == 'n' && buf[pos+1] == 'u'"
                + " && buf[pos+2] == 'l' && buf[pos+3] == 'l')");
        sb.line(map + ".put(" + key + ", null);");
        sb.line("pos += 4;");
        sb.endBlock();
        sb.beginBlock("else");
        emitMapValueDeserialization(sb, valCategory, field, map, key);
        sb.endBlock();

        sb.line("pos = JsonReadUtils.skipWhitespace(buf, pos, end);");
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
                sb.line(listVar + ".add(JsonCodegenHelpers.resolveEnum(ctx.parsedString, "
                        + field.elementClass().getName() + ".class));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            default:
                // For complex elements, delegate
                sb.line("ctx.pos = pos;");
                sb.line(listVar + ".add(JsonCodegenHelpers.deserializeNestedStruct(ctx, "
                        + field.elementClass().getName() + ".class));");
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
                sb.line(mapVar + ".put(" + keyVar + ", JsonCodegenHelpers.resolveEnum(ctx.parsedString, "
                        + field.mapValueClass().getName() + ".class));");
                sb.line("pos = ctx.parsedEndPos;");
                break;
            default:
                // For complex values, delegate
                sb.line("ctx.pos = pos;");
                sb.line(mapVar + ".put(" + keyVar + ", JsonCodegenHelpers.deserializeNestedStruct(ctx, "
                        + field.mapValueClass().getName() + ".class));");
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
}
