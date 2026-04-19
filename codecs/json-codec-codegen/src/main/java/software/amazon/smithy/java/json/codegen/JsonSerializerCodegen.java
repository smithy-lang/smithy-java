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
import software.amazon.smithy.java.json.smithy.JsonWriteUtils;

/**
 * Generates specialized JSON serializer source code for a struct or union shape.
 * The generated class implements {@code GeneratedStructSerializer} and writes JSON
 * directly to a byte buffer, eliminating virtual dispatch overhead.
 */
final class JsonSerializerCodegen {

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
        sb.line("import java.math.BigInteger;");
        sb.line("import java.math.BigDecimal;");
        sb.line("import java.nio.ByteBuffer;");
        sb.line("import java.time.Instant;");
        sb.line("import java.util.Iterator;");
        sb.line("import java.util.List;");
        sb.line("import java.util.Map;");
        sb.line("import software.amazon.smithy.java.codegen.rt.GeneratedStructSerializer;");
        sb.line("import software.amazon.smithy.java.codegen.rt.WriterContext;");
        sb.line("import software.amazon.smithy.java.json.smithy.JsonWriteUtils;");
        sb.line("import software.amazon.smithy.java.json.codegen.JsonCodegenHelpers;");
        sb.emptyLine();

        sb.beginBlock("public final class " + className + " implements GeneratedStructSerializer");

        String fqcn = packageName + "." + plan.shapeClass().getSimpleName();

        if (plan.isUnion()) {
            generateUnionFieldConstants(sb, plan);
            sb.emptyLine();
            generateUnionSerializeMethod(sb, plan, fqcn);
        } else {
            generateFieldConstants(sb, plan);
            sb.emptyLine();
            generateSerializeMethod(sb, plan, fqcn);
        }

        sb.endBlock(); // class

        return sb.toString();
    }

    private void generateFieldConstants(SourceBuilder sb, StructCodePlan plan) {
        List<FieldPlan> serOrder = plan.serializationOrder();
        boolean first = true;
        for (int i = 0; i < serOrder.size(); i++) {
            FieldPlan field = serOrder.get(i);
            byte[] nameBytes = JsonWriteUtils.precomputeFieldNameBytes(field.memberName());
            if (!first) {
                // Prepend comma for non-first fields
                byte[] withComma = new byte[nameBytes.length + 1];
                withComma[0] = ',';
                System.arraycopy(nameBytes, 0, withComma, 1, nameBytes.length);
                nameBytes = withComma;
            }
            sb.line("private static final byte[] FN_" + i + " = " + byteArrayLiteral(nameBytes) + ";");
            first = false;
        }
    }

    private void generateUnionFieldConstants(SourceBuilder sb, StructCodePlan plan) {
        List<FieldPlan> fields = plan.fields();
        for (int i = 0; i < fields.size(); i++) {
            FieldPlan field = fields.get(i);
            byte[] nameBytes = JsonWriteUtils.precomputeFieldNameBytes(field.memberName());
            sb.line("private static final byte[] FN_" + i + " = " + byteArrayLiteral(nameBytes) + ";");
        }
    }

    private void generateSerializeMethod(SourceBuilder sb, StructCodePlan plan, String fqcn) {
        sb.line("@Override");
        sb.beginBlock("public void serialize(Object obj, WriterContext ctx)");

        sb.line(fqcn + " typed = (" + fqcn + ") obj;");
        sb.line("byte[] buf = ctx.buf;");
        sb.line("int pos = ctx.pos;");
        sb.emptyLine();

        // Opening brace
        sb.line("// Opening brace");
        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(1);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("buf[pos++] = '{';");
        sb.emptyLine();

        List<FieldPlan> serOrder = plan.serializationOrder();

        // Generate batched fixed-size required fields
        int i = 0;
        while (i < serOrder.size()) {
            FieldPlan field = serOrder.get(i);

            if (field.required() && field.category().isFixedSize()) {
                // Batch consecutive required fixed-size fields
                int batchStart = i;
                int totalCapacity = 0;
                while (i < serOrder.size()
                        && serOrder.get(i).required()
                        && serOrder.get(i).category().isFixedSize()) {
                    FieldPlan f = serOrder.get(i);
                    // field name bytes length + value upper bound
                    byte[] nameBytes = JsonWriteUtils.precomputeFieldNameBytes(f.memberName());
                    int nameBytesLen = nameBytes.length + (i > 0 ? 1 : 0); // +1 for comma if not first
                    totalCapacity += nameBytesLen + f.fixedSizeUpperBound();
                    i++;
                }

                sb.line("// Batch: " + (i - batchStart) + " required fixed-size field(s)");
                sb.line("ctx.pos = pos;");
                sb.line("ctx.ensureCapacity(" + totalCapacity + ");");
                sb.line("buf = ctx.buf; pos = ctx.pos;");

                for (int j = batchStart; j < i; j++) {
                    FieldPlan f = serOrder.get(j);
                    sb.line("System.arraycopy(FN_" + j + ", 0, buf, pos, FN_" + j + ".length);");
                    sb.line("pos += FN_" + j + ".length;");
                    emitWriteValue(sb, f, fqcn, "typed." + f.getterName() + "()", false);
                    sb.emptyLine();
                }
            } else if (field.nullable()) {
                emitOptionalField(sb, field, i, fqcn);
                i++;
            } else {
                emitRequiredVariableField(sb, field, i, fqcn);
                i++;
            }
        }

        // Closing brace
        sb.line("// Closing brace");
        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(1);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("buf[pos++] = '}';");
        sb.emptyLine();
        sb.line("ctx.buf = buf;");
        sb.line("ctx.pos = pos;");

        sb.endBlock(); // method
    }

    private void generateUnionSerializeMethod(SourceBuilder sb, StructCodePlan plan, String fqcn) {
        sb.line("@Override");
        sb.beginBlock("public void serialize(Object obj, WriterContext ctx)");

        sb.line(fqcn + " typed = (" + fqcn + ") obj;");
        sb.line("byte[] buf = ctx.buf;");
        sb.line("int pos = ctx.pos;");
        sb.emptyLine();

        List<FieldPlan> fields = plan.fields();
        for (int i = 0; i < fields.size(); i++) {
            FieldPlan field = fields.get(i);
            // For unions, each variant is a subclass; use instanceof to dispatch
            // Since Janino doesn't support pattern matching, use traditional instanceof + cast
            String variantClass = fqcn + "." + capitalize(field.memberName()) + "Member";
            String keyword = (i == 0) ? "if" : "else if";
            sb.beginBlock(keyword + " (typed instanceof " + variantClass + ")");

            sb.line(variantClass + " variant = (" + variantClass + ") typed;");

            // Ensure capacity for { + field name + value + }
            sb.line("ctx.pos = pos;");
            sb.line("ctx.ensureCapacity(2);");
            sb.line("buf = ctx.buf; pos = ctx.pos;");
            sb.line("buf[pos++] = '{';");

            sb.line("System.arraycopy(FN_" + i + ", 0, buf, pos, FN_" + i + ".length);");
            sb.line("pos += FN_" + i + ".length;");

            String getter = "variant." + field.getterName() + "()";
            emitWriteValueWithCapacity(sb, field, fqcn, getter);

            sb.line("ctx.pos = pos;");
            sb.line("ctx.ensureCapacity(1);");
            sb.line("buf = ctx.buf; pos = ctx.pos;");
            sb.line("buf[pos++] = '}';");

            sb.endBlock();
        }
        sb.emptyLine();
        sb.line("ctx.buf = buf;");
        sb.line("ctx.pos = pos;");

        sb.endBlock(); // method
    }

    private void emitRequiredVariableField(SourceBuilder sb, FieldPlan field, int fieldIndex, String fqcn) {
        sb.line("// Required field: " + field.memberName() + " (" + field.category() + ")");

        // Write field name - need capacity for field name bytes
        byte[] nameBytes = JsonWriteUtils.precomputeFieldNameBytes(field.memberName());
        int nameBytesLen = nameBytes.length + (fieldIndex > 0 ? 1 : 0);
        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(" + nameBytesLen + ");");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("System.arraycopy(FN_" + fieldIndex + ", 0, buf, pos, FN_" + fieldIndex + ".length);");
        sb.line("pos += FN_" + fieldIndex + ".length;");

        String getter = "typed." + field.getterName() + "()";
        emitWriteValueWithCapacity(sb, field, fqcn, getter);
        sb.emptyLine();
    }

    private void emitOptionalField(SourceBuilder sb, FieldPlan field, int fieldIndex, String fqcn) {
        sb.line("// Optional field: " + field.memberName() + " (" + field.category() + ")");
        String valueType = getBoxedType(field);
        String getter = "typed." + field.getterName() + "()";

        if (field.category().isPrimitive() && !field.required()) {
            // For optional primitives, need boxed getter
            sb.line(valueType + " _" + field.memberName() + " = " + getter + ";");
            sb.beginBlock("if (_" + field.memberName() + " != null)");
            String localVal = "_" + field.memberName();
            // Unbox for primitive write methods
            String unboxed = unboxValue(localVal, field.category());
            emitFieldNameAndValue(sb, field, fieldIndex, fqcn, unboxed, true);
            sb.endBlock();
        } else {
            sb.line(valueType + " _" + field.memberName() + " = " + getter + ";");
            sb.beginBlock("if (_" + field.memberName() + " != null)");
            emitFieldNameAndValue(sb, field, fieldIndex, fqcn, "_" + field.memberName(), false);
            sb.endBlock();
        }
        sb.emptyLine();
    }

    private void emitFieldNameAndValue(
            SourceBuilder sb,
            FieldPlan field,
            int fieldIndex,
            String fqcn,
            String valueExpr,
            boolean isPrimitiveUnboxed
    ) {
        byte[] nameBytes = JsonWriteUtils.precomputeFieldNameBytes(field.memberName());
        int nameBytesLen = nameBytes.length + (fieldIndex > 0 ? 1 : 0);

        if (field.category().isFixedSize()) {
            int totalCapacity = nameBytesLen + field.fixedSizeUpperBound();
            sb.line("ctx.pos = pos;");
            sb.line("ctx.ensureCapacity(" + totalCapacity + ");");
            sb.line("buf = ctx.buf; pos = ctx.pos;");
            sb.line("System.arraycopy(FN_" + fieldIndex + ", 0, buf, pos, FN_" + fieldIndex + ".length);");
            sb.line("pos += FN_" + fieldIndex + ".length;");
            emitWriteValue(sb, field, fqcn, valueExpr, isPrimitiveUnboxed);
        } else {
            sb.line("ctx.pos = pos;");
            sb.line("ctx.ensureCapacity(" + nameBytesLen + ");");
            sb.line("buf = ctx.buf; pos = ctx.pos;");
            sb.line("System.arraycopy(FN_" + fieldIndex + ", 0, buf, pos, FN_" + fieldIndex + ".length);");
            sb.line("pos += FN_" + fieldIndex + ".length;");
            emitWriteValueWithCapacity(sb, field, fqcn, valueExpr);
        }
    }

    private void emitWriteValueWithCapacity(SourceBuilder sb, FieldPlan field, String fqcn, String valueExpr) {
        switch (field.category()) {
            case STRING:
            case ENUM_STRING:
                emitWriteValueWithCapacity_String(sb, field, valueExpr);
                break;
            case BLOB:
                emitWriteValueWithCapacity_Blob(sb, valueExpr);
                break;
            case BIG_INTEGER:
                emitWriteValueWithCapacity_BigInteger(sb, valueExpr);
                break;
            case BIG_DECIMAL:
                emitWriteValueWithCapacity_BigDecimal(sb, valueExpr);
                break;
            case TIMESTAMP:
                emitWriteValueWithCapacity_Timestamp(sb, field, valueExpr);
                break;
            case LIST:
                emitWriteValueWithCapacity_List(sb, field, fqcn, valueExpr);
                break;
            case MAP:
                emitWriteValueWithCapacity_Map(sb, field, fqcn, valueExpr);
                break;
            case STRUCT:
            case UNION:
                emitWriteValueWithCapacity_Struct(sb, valueExpr);
                break;
            case DOCUMENT:
                emitWriteValueWithCapacity_Document(sb, valueExpr);
                break;
            case FLOAT:
            case DOUBLE:
                // These are fixed-size but need individual capacity
                sb.line("ctx.pos = pos;");
                sb.line("ctx.ensureCapacity(24);");
                sb.line("buf = ctx.buf; pos = ctx.pos;");
                emitWriteValue(sb, field, fqcn, valueExpr, false);
                break;
            default:
                // For other fixed-size types that somehow ended up here
                sb.line("ctx.pos = pos;");
                sb.line("ctx.ensureCapacity(" + field.fixedSizeUpperBound() + ");");
                sb.line("buf = ctx.buf; pos = ctx.pos;");
                emitWriteValue(sb, field, fqcn, valueExpr, false);
                break;
        }
    }

    private void emitWriteValue(
            SourceBuilder sb,
            FieldPlan field,
            String fqcn,
            String valueExpr,
            boolean alreadyUnboxed
    ) {
        switch (field.category()) {
            case BOOLEAN:
                sb.line("pos = JsonWriteUtils.writeBoolean(buf, pos, " + valueExpr + ");");
                break;
            case BYTE:
            case SHORT:
            case INTEGER:
                sb.line("pos = JsonWriteUtils.writeInt(buf, pos, " + valueExpr + ");");
                break;
            case LONG:
                sb.line("pos = JsonWriteUtils.writeLong(buf, pos, " + valueExpr + ");");
                break;
            case FLOAT:
                sb.line("pos = JsonWriteUtils.writeFloat(buf, pos, " + valueExpr + ");");
                break;
            case DOUBLE:
                sb.line("pos = JsonWriteUtils.writeDouble(buf, pos, " + valueExpr + ");");
                break;
            case INT_ENUM:
                sb.line("pos = JsonWriteUtils.writeInt(buf, pos, " + valueExpr + ".getValue());");
                break;
            default:
                throw new IllegalArgumentException("emitWriteValue called for non-fixed category: " + field.category());
        }
    }

    private void emitWriteValueWithCapacity_String(SourceBuilder sb, FieldPlan field, String valueExpr) {
        String strExpr = valueExpr;
        if (field.category() == FieldCategory.ENUM_STRING) {
            strExpr = valueExpr + ".getValue()";
        }
        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(" + strExpr + "));");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("pos = JsonWriteUtils.writeQuotedString(buf, pos, " + strExpr + ");");
    }

    private void emitWriteValueWithCapacity_Blob(SourceBuilder sb, String valueExpr) {
        varSeq++;
        String bb = v("_bb");
        String data = v("_data");
        sb.line("ByteBuffer " + bb + " = " + valueExpr + ";");
        sb.line("byte[] " + data + " = new byte[" + bb + ".remaining()];");
        sb.line(bb + ".duplicate().get(" + data + ");");
        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(JsonWriteUtils.maxBase64Bytes(" + data + ".length));");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("pos = JsonWriteUtils.writeBase64String(buf, pos, " + data + ", 0, " + data + ".length);");
    }

    private void emitWriteValueWithCapacity_BigInteger(SourceBuilder sb, String valueExpr) {
        varSeq++;
        String s = v("_bis");
        sb.line("String " + s + " = " + valueExpr + ".toString();");
        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(" + s + ".length() + 2);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("pos = JsonWriteUtils.writeAsciiString(buf, pos, " + s + ");");
    }

    private void emitWriteValueWithCapacity_BigDecimal(SourceBuilder sb, String valueExpr) {
        varSeq++;
        String s = v("_bds");
        sb.line("String " + s + " = " + valueExpr + ".toPlainString();");
        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(" + s + ".length() + 2);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("pos = JsonWriteUtils.writeAsciiString(buf, pos, " + s + ");");
    }

    private void emitWriteValueWithCapacity_Timestamp(SourceBuilder sb, FieldPlan field, String valueExpr) {
        String format = field.timestampFormat();
        if (format == null || "EPOCH_SECONDS".equals(format)) {
            varSeq++;
            String ts = v("_ts");
            sb.line("Instant " + ts + " = " + valueExpr + ";");
            sb.line("ctx.pos = pos;");
            sb.line("ctx.ensureCapacity(24);");
            sb.line("buf = ctx.buf; pos = ctx.pos;");
            sb.line("pos = JsonWriteUtils.writeEpochSeconds(buf, pos, " + ts + ".getEpochSecond(), " + ts
                    + ".getNano());");
        } else if ("HTTP_DATE".equals(format)) {
            sb.line("ctx.pos = pos;");
            sb.line("ctx.ensureCapacity(40);");
            sb.line("buf = ctx.buf; pos = ctx.pos;");
            sb.line("pos = JsonWriteUtils.writeHttpDate(buf, pos, " + valueExpr + ");");
        } else {
            // Unknown format, default to ISO 8601
            sb.line("ctx.pos = pos;");
            sb.line("ctx.ensureCapacity(40);");
            sb.line("buf = ctx.buf; pos = ctx.pos;");
            sb.line("pos = JsonWriteUtils.writeIso8601Timestamp(buf, pos, " + valueExpr + ");");
        }
    }

    private void emitWriteValueWithCapacity_List(
            SourceBuilder sb,
            FieldPlan field,
            String fqcn,
            String valueExpr
    ) {
        varSeq++;
        String list = v("_list");
        String idx = v("_i");

        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(1);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("buf[pos++] = '[';");

        String elementType = resolveElementType(field);
        sb.line("List " + list + " = " + valueExpr + ";");
        sb.beginBlock("for (int " + idx + " = 0; " + idx + " < " + list + ".size(); " + idx + "++)");
        sb.beginBlock("if (" + idx + " > 0)");
        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(1);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("buf[pos++] = ',';");
        sb.endBlock();

        FieldCategory elemCategory = resolveElementCategory(field);
        if (field.sparse()) {
            sb.beginBlock("if (" + list + ".get(" + idx + ") == null)");
            sb.line("ctx.pos = pos;");
            sb.line("ctx.ensureCapacity(4);");
            sb.line("buf = ctx.buf; pos = ctx.pos;");
            sb.line("pos = JsonCodegenHelpers.writeNull(buf, pos);");
            sb.endBlock();
            sb.beginBlock("else");
        }
        String elemExpr = "(" + elementType + ") " + list + ".get(" + idx + ")";
        emitWriteElement(sb, elemCategory, elemExpr, field, fqcn);
        if (field.sparse()) {
            sb.endBlock();
        }

        sb.endBlock(); // for loop

        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(1);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("buf[pos++] = ']';");
    }

    private void emitWriteValueWithCapacity_Map(
            SourceBuilder sb,
            FieldPlan field,
            String fqcn,
            String valueExpr
    ) {
        varSeq++;
        String it = v("_it");
        String first = v("_first");
        String entry = v("_entry");
        String key = v("_key");

        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(1);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("buf[pos++] = '{';");

        String valueType = resolveMapValueType(field);
        sb.line("Iterator " + it + " = " + valueExpr + ".entrySet().iterator();");
        sb.line("boolean " + first + " = true;");
        sb.beginBlock("while (" + it + ".hasNext())");
        sb.line("Map.Entry " + entry + " = (Map.Entry) " + it + ".next();");
        sb.beginBlock("if (!" + first + ")");
        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(1);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("buf[pos++] = ',';");
        sb.endBlock();
        sb.line(first + " = false;");

        // Write key (always a String)
        sb.line("String " + key + " = (String) " + entry + ".getKey();");
        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(" + key + ") + 1);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("pos = JsonWriteUtils.writeQuotedString(buf, pos, " + key + ");");
        sb.line("buf[pos++] = ':';");

        // Write value
        FieldCategory valCategory = resolveMapValueCategory(field);
        if (field.sparse()) {
            sb.beginBlock("if (" + entry + ".getValue() == null)");
            sb.line("ctx.pos = pos;");
            sb.line("ctx.ensureCapacity(4);");
            sb.line("buf = ctx.buf; pos = ctx.pos;");
            sb.line("pos = JsonCodegenHelpers.writeNull(buf, pos);");
            sb.endBlock();
            sb.beginBlock("else");
        }
        String valExpr = "(" + valueType + ") " + entry + ".getValue()";
        emitWriteElement(sb, valCategory, valExpr, field, fqcn);
        if (field.sparse()) {
            sb.endBlock();
        }

        sb.endBlock(); // while

        sb.line("ctx.pos = pos;");
        sb.line("ctx.ensureCapacity(1);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
        sb.line("buf[pos++] = '}';");
    }

    private void emitWriteElement(
            SourceBuilder sb,
            FieldCategory category,
            String elemExpr,
            FieldPlan field,
            String fqcn
    ) {
        switch (category) {
            case BOOLEAN:
                sb.line("ctx.pos = pos;");
                sb.line("ctx.ensureCapacity(5);");
                sb.line("buf = ctx.buf; pos = ctx.pos;");
                sb.line("pos = JsonWriteUtils.writeBoolean(buf, pos, ((Boolean) " + elemExpr + ").booleanValue());");
                break;
            case BYTE:
            case SHORT:
            case INTEGER:
                sb.line("ctx.pos = pos;");
                sb.line("ctx.ensureCapacity(11);");
                sb.line("buf = ctx.buf; pos = ctx.pos;");
                sb.line("pos = JsonWriteUtils.writeInt(buf, pos, ((Number) " + elemExpr + ").intValue());");
                break;
            case LONG:
                sb.line("ctx.pos = pos;");
                sb.line("ctx.ensureCapacity(20);");
                sb.line("buf = ctx.buf; pos = ctx.pos;");
                sb.line("pos = JsonWriteUtils.writeLong(buf, pos, ((Number) " + elemExpr + ").longValue());");
                break;
            case FLOAT:
                sb.line("ctx.pos = pos;");
                sb.line("ctx.ensureCapacity(24);");
                sb.line("buf = ctx.buf; pos = ctx.pos;");
                sb.line("pos = JsonWriteUtils.writeFloat(buf, pos, ((Number) " + elemExpr + ").floatValue());");
                break;
            case DOUBLE:
                sb.line("ctx.pos = pos;");
                sb.line("ctx.ensureCapacity(24);");
                sb.line("buf = ctx.buf; pos = ctx.pos;");
                sb.line("pos = JsonWriteUtils.writeDouble(buf, pos, ((Number) " + elemExpr + ").doubleValue());");
                break;
            case STRING:
                varSeq++;
                String elem = v("_elem");
                sb.line("String " + elem + " = (String) " + elemExpr + ";");
                sb.line("ctx.pos = pos;");
                sb.line("ctx.ensureCapacity(JsonWriteUtils.maxQuotedStringBytes(" + elem + "));");
                sb.line("buf = ctx.buf; pos = ctx.pos;");
                sb.line("pos = JsonWriteUtils.writeQuotedString(buf, pos, " + elem + ");");
                break;
            case ENUM_STRING:
                sb.line("ctx.pos = pos;");
                sb.line("ctx.ensureCapacity(JsonWriteUtils.maxQuotedStringBytes("
                        + "((software.amazon.smithy.java.core.schema.SmithyEnum) " + elemExpr + ").getValue()));");
                sb.line("buf = ctx.buf; pos = ctx.pos;");
                sb.line("pos = JsonWriteUtils.writeQuotedString(buf, pos, "
                        + "((software.amazon.smithy.java.core.schema.SmithyEnum) " + elemExpr + ").getValue());");
                break;
            default:
                // For complex elements (struct, list, etc), delegate
                sb.line("ctx.buf = buf; ctx.pos = pos;");
                sb.line("JsonCodegenHelpers.serializeNestedStruct(" + elemExpr + ", ctx);");
                sb.line("buf = ctx.buf; pos = ctx.pos;");
                break;
        }
    }

    private void emitWriteValueWithCapacity_Struct(SourceBuilder sb, String valueExpr) {
        sb.line("ctx.buf = buf; ctx.pos = pos;");
        sb.line("JsonCodegenHelpers.serializeNestedStruct(" + valueExpr + ", ctx);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
    }

    private void emitWriteValueWithCapacity_Document(SourceBuilder sb, String valueExpr) {
        sb.line("ctx.buf = buf; ctx.pos = pos;");
        sb.line("JsonCodegenHelpers.serializeDocument(" + valueExpr + ", ctx);");
        sb.line("buf = ctx.buf; pos = ctx.pos;");
    }

    // ---- Utility methods ----

    private static String byteArrayLiteral(byte[] bytes) {
        StringBuilder sb = new StringBuilder("new byte[]{");
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            byte b = bytes[i];
            if (b >= 0x20 && b < 0x7F && b != '\'' && b != '\\') {
                sb.append('\'').append((char) b).append('\'');
            } else {
                sb.append(b);
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String getBoxedType(FieldPlan field) {
        switch (field.category()) {
            case BOOLEAN:
                return "Boolean";
            case BYTE:
                return "Byte";
            case SHORT:
                return "Short";
            case INTEGER:
                return "Integer";
            case LONG:
                return "Long";
            case FLOAT:
                return "Float";
            case DOUBLE:
                return "Double";
            case STRING:
                return "String";
            case BLOB:
                return "ByteBuffer";
            case TIMESTAMP:
                return "Instant";
            case BIG_INTEGER:
                return "BigInteger";
            case BIG_DECIMAL:
                return "BigDecimal";
            case LIST:
                return "List";
            case MAP:
                return "Map";
            case ENUM_STRING:
            case INT_ENUM:
            case STRUCT:
            case UNION:
                Class<?> targetClass = field.schema().memberTarget().shapeClass();
                if (targetClass != null) {
                    return targetClass.getName();
                }
                return "Object";
            default:
                return "Object";
        }
    }

    private static String unboxValue(String varName, FieldCategory category) {
        switch (category) {
            case BOOLEAN:
                return varName + ".booleanValue()";
            case BYTE:
                return varName + ".byteValue()";
            case SHORT:
                return varName + ".shortValue()";
            case INTEGER:
                return varName + ".intValue()";
            case LONG:
                return varName + ".longValue()";
            case FLOAT:
                return varName + ".floatValue()";
            case DOUBLE:
                return varName + ".doubleValue()";
            default:
                return varName;
        }
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static FieldCategory resolveElementCategory(FieldPlan field) {
        Class<?> elemClass = field.elementClass();
        if (elemClass == null) {
            return FieldCategory.STRING;
        }
        return classToCategory(elemClass);
    }

    private static String resolveElementType(FieldPlan field) {
        Class<?> elemClass = field.elementClass();
        if (elemClass == null) {
            return "Object";
        }
        return toSimpleBoxedName(elemClass);
    }

    private static FieldCategory resolveMapValueCategory(FieldPlan field) {
        Class<?> valClass = field.mapValueClass();
        if (valClass == null) {
            return FieldCategory.STRING;
        }
        return classToCategory(valClass);
    }

    private static String resolveMapValueType(FieldPlan field) {
        Class<?> valClass = field.mapValueClass();
        if (valClass == null) {
            return "Object";
        }
        return toSimpleBoxedName(valClass);
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

    private static String toSimpleBoxedName(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            if (clazz == int.class) {
                return "Integer";
            }
            if (clazz == long.class) {
                return "Long";
            }
            if (clazz == double.class) {
                return "Double";
            }
            if (clazz == float.class) {
                return "Float";
            }
            if (clazz == boolean.class) {
                return "Boolean";
            }
            if (clazz == short.class) {
                return "Short";
            }
            if (clazz == byte.class) {
                return "Byte";
            }
        }
        return clazz.getSimpleName();
    }
}
