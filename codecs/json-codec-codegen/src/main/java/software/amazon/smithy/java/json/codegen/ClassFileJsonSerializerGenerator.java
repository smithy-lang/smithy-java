/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.codegen.rt.BytecodeCodecProfile.GenerationResult;
import software.amazon.smithy.java.codegen.rt.GeneratedStructSerializer;
import software.amazon.smithy.java.codegen.rt.plan.FieldCategory;
import software.amazon.smithy.java.codegen.rt.plan.FieldPlan;
import software.amazon.smithy.java.codegen.rt.plan.StructCodePlan;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.json.smithy.JsonWriteUtils;

/**
 * Generates serializer bytecode directly using the ClassFile API (JDK 25+).
 * Produces a class implementing {@link GeneratedStructSerializer}.
 */
final class ClassFileJsonSerializerGenerator {

    // Standard ClassDescs
    private static final ClassDesc CD_Object = ConstantDescs.CD_Object;
    private static final ClassDesc CD_String = ConstantDescs.CD_String;
    private static final ClassDesc CD_int = ConstantDescs.CD_int;
    private static final ClassDesc CD_long = ConstantDescs.CD_long;
    private static final ClassDesc CD_float = ConstantDescs.CD_float;
    private static final ClassDesc CD_double = ConstantDescs.CD_double;
    private static final ClassDesc CD_boolean = ConstantDescs.CD_boolean;
    private static final ClassDesc CD_byte = ConstantDescs.CD_byte;
    private static final ClassDesc CD_short = ConstantDescs.CD_short;
    private static final ClassDesc CD_void = ConstantDescs.CD_void;
    private static final ClassDesc CD_byte_array = ConstantDescs.CD_byte.arrayType();

    // Project ClassDescs
    private static final ClassDesc CD_WriterContext = ClassDesc.of(
            "software.amazon.smithy.java.codegen.rt.WriterContext");
    private static final ClassDesc CD_GeneratedStructSerializer = ClassDesc.of(
            "software.amazon.smithy.java.codegen.rt.GeneratedStructSerializer");
    private static final ClassDesc CD_JsonWriteUtils = ClassDesc.of(
            "software.amazon.smithy.java.json.smithy.JsonWriteUtils");
    private static final ClassDesc CD_JsonCodegenHelpers = ClassDesc.of(
            "software.amazon.smithy.java.json.codegen.JsonCodegenHelpers");
    private static final ClassDesc CD_BigInteger = ClassDesc.of("java.math.BigInteger");
    private static final ClassDesc CD_BigDecimal = ClassDesc.of("java.math.BigDecimal");
    private static final ClassDesc CD_Instant = ClassDesc.of("java.time.Instant");
    private static final ClassDesc CD_ByteBuffer = ClassDesc.of("java.nio.ByteBuffer");
    private static final ClassDesc CD_List = ClassDesc.of("java.util.List");
    private static final ClassDesc CD_Map = ClassDesc.of("java.util.Map");
    private static final ClassDesc CD_Iterator = ClassDesc.of("java.util.Iterator");
    private static final ClassDesc CD_Map_Entry = ClassDesc.of("java.util.Map$Entry");
    private static final ClassDesc CD_Boolean = ClassDesc.of("java.lang.Boolean");
    private static final ClassDesc CD_Number = ClassDesc.of("java.lang.Number");
    private static final ClassDesc CD_SmithyEnum = ClassDesc.of(
            "software.amazon.smithy.java.core.schema.SmithyEnum");
    private static final ClassDesc CD_System = ClassDesc.of("java.lang.System");

    // Slot assignments for serialize method
    // 0=this, 1=Object obj, 2=WriterContext ctx
    private static final int SLOT_TYPED = 3;
    private static final int SLOT_BUF = 4;
    private static final int SLOT_POS = 5;
    private static final int SLOT_FIRST_TEMP = 6;

    private int nextTempSlot;
    private final Map<String, Integer> nestedSerCacheFieldIndices = new LinkedHashMap<>();
    private int nextCacheIndex;

    GenerationResult generate(StructCodePlan plan, String className, String packageName) {
        nextTempSlot = SLOT_FIRST_TEMP;
        nestedSerCacheFieldIndices.clear();
        nextCacheIndex = 0;

        collectNestedStructTypes(plan);

        ClassDesc thisClass = ClassDesc.of(packageName + "." + className);
        ClassDesc shapeClass = ClassDesc.of(plan.shapeClass().getName());

        // Prepare field name bytes as class data
        List<byte[]> fieldNameBytesList = prepareFieldNameBytes(plan);

        byte[] bytecode = ClassFile.of().build(thisClass, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            cb.withInterfaceSymbols(CD_GeneratedStructSerializer);

            // Static fields for field name bytes (loaded from class data in clinit)
            for (int i = 0; i < fieldNameBytesList.size(); i++) {
                cb.withField("FN_" + i,
                        CD_byte_array,
                        ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            }

            // Static fields for nested serializer caches
            ClassDesc serArrayDesc = CD_GeneratedStructSerializer.arrayType();
            for (var entry : nestedSerCacheFieldIndices.entrySet()) {
                cb.withField("_SER_" + entry.getValue(),
                        serArrayDesc,
                        ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            }

            // Class initializer to load field name bytes from class data
            emitClassInit(cb, thisClass, fieldNameBytesList);

            // Default constructor
            emitConstructor(cb, thisClass);

            // serialize method
            if (plan.isUnion()) {
                emitUnionSerializeMethod(cb, thisClass, shapeClass, plan, fieldNameBytesList);
            } else {
                emitSerializeMethod(cb, thisClass, shapeClass, plan, fieldNameBytesList);
            }
        });

        // Class data: list of byte arrays for field names
        return new GenerationResult(bytecode, fieldNameBytesList);
    }

    private List<byte[]> prepareFieldNameBytes(StructCodePlan plan) {
        List<byte[]> result = new ArrayList<>();
        if (plan.isUnion()) {
            for (FieldPlan field : plan.fields()) {
                result.add(JsonWriteUtils.precomputeFieldNameBytes(field.memberName()));
            }
        } else {
            List<FieldPlan> serOrder = plan.serializationOrder();
            boolean first = true;
            for (FieldPlan field : serOrder) {
                byte[] nameBytes = JsonWriteUtils.precomputeFieldNameBytes(field.memberName());
                if (!first) {
                    byte[] withComma = new byte[nameBytes.length + 1];
                    withComma[0] = ',';
                    System.arraycopy(nameBytes, 0, withComma, 1, nameBytes.length);
                    nameBytes = withComma;
                }
                result.add(nameBytes);
                first = false;
            }
        }
        return result;
    }

    private void emitClassInit(
            java.lang.classfile.ClassBuilder cb,
            ClassDesc thisClass,
            List<byte[]> fieldNameBytesList
    ) {
        ClassDesc CD_MethodHandles = ClassDesc.of("java.lang.invoke.MethodHandles");
        ClassDesc CD_List_cls = ClassDesc.of("java.util.List");
        ClassDesc serArrayDesc = CD_GeneratedStructSerializer.arrayType();

        cb.withMethodBody(ConstantDescs.CLASS_INIT_NAME,
                MethodTypeDesc.of(CD_void),
                ClassFile.ACC_STATIC,
                code -> {
                    // Load class data: MethodHandles.classData(MethodHandles.lookup(), "", List.class)
                    code.invokestatic(CD_MethodHandles,
                            "lookup",
                            MethodTypeDesc.of(ClassDesc.of("java.lang.invoke.MethodHandles$Lookup")));
                    code.ldc("_");
                    code.ldc(CD_List_cls);
                    code.invokestatic(CD_MethodHandles,
                            "classData",
                            MethodTypeDesc.of(CD_Object,
                                    ClassDesc.of("java.lang.invoke.MethodHandles$Lookup"),
                                    CD_String,
                                    ClassDesc.of("java.lang.Class")));
                    code.checkcast(CD_List_cls);
                    int listSlot = 0;
                    code.astore(listSlot);

                    // Extract each byte[] from list into static fields
                    for (int i = 0; i < fieldNameBytesList.size(); i++) {
                        code.aload(listSlot);
                        code.ldc(i);
                        code.invokeinterface(CD_List_cls,
                                "get",
                                MethodTypeDesc.of(CD_Object, CD_int));
                        code.checkcast(CD_byte_array);
                        code.putstatic(thisClass, "FN_" + i, CD_byte_array);
                    }

                    // Init nested serializer cache arrays
                    for (var entry : nestedSerCacheFieldIndices.entrySet()) {
                        code.iconst_1();
                        code.anewarray(CD_GeneratedStructSerializer);
                        code.putstatic(thisClass, "_SER_" + entry.getValue(), serArrayDesc);
                    }

                    code.return_();
                });
    }

    private void emitConstructor(java.lang.classfile.ClassBuilder cb, ClassDesc thisClass) {
        cb.withMethodBody(ConstantDescs.INIT_NAME,
                MethodTypeDesc.of(CD_void),
                ClassFile.ACC_PUBLIC,
                code -> {
                    code.aload(0);
                    code.invokespecial(CD_Object,
                            ConstantDescs.INIT_NAME,
                            MethodTypeDesc.of(CD_void));
                    code.return_();
                });
    }

    private void emitSerializeMethod(
            java.lang.classfile.ClassBuilder cb,
            ClassDesc thisClass,
            ClassDesc shapeClass,
            StructCodePlan plan,
            List<byte[]> fieldNameBytesList
    ) {
        cb.withMethodBody("serialize",
                MethodTypeDesc.of(CD_void, CD_Object, CD_WriterContext),
                ClassFile.ACC_PUBLIC,
                code -> {
                    nextTempSlot = SLOT_FIRST_TEMP;

                    // Cast obj to typed
                    code.aload(1);
                    code.checkcast(shapeClass);
                    code.astore(SLOT_TYPED);

                    // Load buf and pos from ctx
                    code.aload(2);
                    code.getfield(CD_WriterContext, "buf", CD_byte_array);
                    code.astore(SLOT_BUF);
                    code.aload(2);
                    code.getfield(CD_WriterContext, "pos", CD_int);
                    code.istore(SLOT_POS);

                    List<FieldPlan> serOrder = plan.serializationOrder();

                    // Compute upfront capacity
                    int fixedCapacity = 2; // { and }
                    int fixedFieldEnd = 0;
                    for (int i = 0; i < serOrder.size(); i++) {
                        FieldPlan f = serOrder.get(i);
                        if (f.required() && f.category().isFixedSize()) {
                            byte[] nameBytes = fieldNameBytesList.get(i);
                            fixedCapacity += nameBytes.length + f.fixedSizeUpperBound();
                            fixedFieldEnd = i + 1;
                        }
                    }

                    boolean hasVariableFields = fixedFieldEnd < serOrder.size();

                    // ctx.ensure(pos, fixedCapacity)
                    emitEnsure(code, thisClass, fixedCapacity);

                    // buf[pos++] = '{'
                    emitWriteByte(code, '{');

                    // Required fixed-size fields
                    for (int i = 0; i < fixedFieldEnd; i++) {
                        FieldPlan f = serOrder.get(i);
                        emitFieldNameCopy(code, thisClass, i, fieldNameBytesList.get(i).length);
                        emitWriteFixedValue(code, shapeClass, f);
                    }

                    // Remaining fields
                    for (int i = fixedFieldEnd; i < serOrder.size(); i++) {
                        FieldPlan field = serOrder.get(i);
                        if (field.nullable()) {
                            emitOptionalField(code,
                                    thisClass,
                                    shapeClass,
                                    field,
                                    i,
                                    fieldNameBytesList.get(i).length,
                                    plan);
                        } else {
                            emitRequiredVariableField(code,
                                    thisClass,
                                    shapeClass,
                                    field,
                                    i,
                                    fieldNameBytesList.get(i).length,
                                    plan);
                        }
                    }

                    // Closing brace
                    if (hasVariableFields) {
                        emitEnsure(code, thisClass, 1);
                    }
                    emitWriteByte(code, '}');

                    // Sync back to ctx
                    emitSyncToCtx(code);
                    code.return_();
                });
    }

    private void emitUnionSerializeMethod(
            java.lang.classfile.ClassBuilder cb,
            ClassDesc thisClass,
            ClassDesc shapeClass,
            StructCodePlan plan,
            List<byte[]> fieldNameBytesList
    ) {
        cb.withMethodBody("serialize",
                MethodTypeDesc.of(CD_void, CD_Object, CD_WriterContext),
                ClassFile.ACC_PUBLIC,
                code -> {
                    nextTempSlot = SLOT_FIRST_TEMP;

                    // Cast obj to typed
                    code.aload(1);
                    code.checkcast(shapeClass);
                    code.astore(SLOT_TYPED);

                    // Load buf and pos from ctx
                    code.aload(2);
                    code.getfield(CD_WriterContext, "buf", CD_byte_array);
                    code.astore(SLOT_BUF);
                    code.aload(2);
                    code.getfield(CD_WriterContext, "pos", CD_int);
                    code.istore(SLOT_POS);

                    List<FieldPlan> fields = plan.fields();
                    Label endLabel = code.newLabel();

                    for (int i = 0; i < fields.size(); i++) {
                        FieldPlan field = fields.get(i);
                        String variantSimple = capitalize(field.memberName()) + "Member";
                        ClassDesc variantClass = ClassDesc.of(
                                plan.shapeClass().getName() + "$" + variantSimple);

                        Label nextVariant = code.newLabel();
                        int variantSlot = allocTempSlot();

                        // if (typed instanceof VariantMember)
                        code.aload(SLOT_TYPED);
                        code.instanceOf(variantClass);
                        code.ifeq(nextVariant);

                        // VariantMember variant = (VariantMember) typed;
                        code.aload(SLOT_TYPED);
                        code.checkcast(variantClass);
                        code.astore(variantSlot);

                        // ensure capacity for { + field name + some value
                        emitEnsure(code, thisClass, 2);
                        emitWriteByte(code, '{');

                        // Write field name
                        emitFieldNameCopy(code, thisClass, i, fieldNameBytesList.get(i).length);

                        // Write value
                        emitWriteValueWithCapacity(code,
                                thisClass,
                                variantClass,
                                field,
                                variantSlot,
                                plan);

                        // Closing brace
                        emitEnsure(code, thisClass, 1);
                        emitWriteByte(code, '}');

                        code.goto_(endLabel);
                        code.labelBinding(nextVariant);
                    }

                    code.labelBinding(endLabel);

                    // Sync back to ctx
                    emitSyncToCtx(code);
                    code.return_();
                });
    }

    // ---- Bytecode emit helpers ----

    private void emitEnsure(CodeBuilder code, ClassDesc thisClass, int needed) {
        // buf = ctx.ensure(pos, needed); pos = ctx.pos;
        code.aload(2); // ctx
        code.iload(SLOT_POS);
        code.ldc(needed);
        code.invokevirtual(CD_WriterContext,
                "ensure",
                MethodTypeDesc.of(CD_byte_array, CD_int, CD_int));
        code.astore(SLOT_BUF);
        code.aload(2);
        code.getfield(CD_WriterContext, "pos", CD_int);
        code.istore(SLOT_POS);
    }

    private void emitWriteByte(CodeBuilder code, char b) {
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.bipush(b);
        code.bastore();
        code.iinc(SLOT_POS, 1);
    }

    private void emitFieldNameCopy(CodeBuilder code, ClassDesc thisClass, int fieldIndex, int length) {
        // System.arraycopy(FN_i, 0, buf, pos, FN_i.length); pos += length;
        code.getstatic(thisClass, "FN_" + fieldIndex, CD_byte_array);
        code.iconst_0();
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.ldc(length);
        code.invokestatic(CD_System,
                "arraycopy",
                MethodTypeDesc.of(CD_void, CD_Object, CD_int, CD_Object, CD_int, CD_int));
        code.iinc(SLOT_POS, length);
    }

    private void emitSyncToCtx(CodeBuilder code) {
        code.aload(2);
        code.aload(SLOT_BUF);
        code.putfield(CD_WriterContext, "buf", CD_byte_array);
        code.aload(2);
        code.iload(SLOT_POS);
        code.putfield(CD_WriterContext, "pos", CD_int);
    }

    private void emitSyncFromCtx(CodeBuilder code) {
        code.aload(2);
        code.getfield(CD_WriterContext, "buf", CD_byte_array);
        code.astore(SLOT_BUF);
        code.aload(2);
        code.getfield(CD_WriterContext, "pos", CD_int);
        code.istore(SLOT_POS);
    }

    private void emitSyncPosToCtx(CodeBuilder code) {
        code.aload(2);
        code.iload(SLOT_POS);
        code.putfield(CD_WriterContext, "pos", CD_int);
    }

    private void emitSyncBufPosToCtx(CodeBuilder code) {
        emitSyncToCtx(code);
    }

    private int allocTempSlot() {
        return nextTempSlot++;
    }

    // ---- Fixed-size field value writes ----

    private void emitWriteFixedValue(CodeBuilder code, ClassDesc shapeClass, FieldPlan field) {
        switch (field.category()) {
            case BOOLEAN -> {
                // pos = JsonWriteUtils.writeBoolean(buf, pos, typed.isXxx())
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(SLOT_TYPED);
                code.invokevirtual(shapeClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_boolean));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeBoolean",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_boolean));
                code.istore(SLOT_POS);
            }
            case BYTE -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(SLOT_TYPED);
                code.invokevirtual(shapeClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_byte));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            case SHORT -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(SLOT_TYPED);
                code.invokevirtual(shapeClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_short));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            case INTEGER -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(SLOT_TYPED);
                code.invokevirtual(shapeClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_int));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            case LONG -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(SLOT_TYPED);
                code.invokevirtual(shapeClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_long));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeLong",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_long));
                code.istore(SLOT_POS);
            }
            case FLOAT -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(SLOT_TYPED);
                code.invokevirtual(shapeClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_float));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeFloatReusable",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_float));
                code.istore(SLOT_POS);
            }
            case DOUBLE -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(SLOT_TYPED);
                code.invokevirtual(shapeClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_double));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeDoubleReusable",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_double));
                code.istore(SLOT_POS);
            }
            case INT_ENUM -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(SLOT_TYPED);
                ClassDesc enumClass = ClassDesc.of(field.schema().memberTarget().shapeClass().getName());
                code.invokevirtual(shapeClass,
                        field.getterName(),
                        MethodTypeDesc.of(enumClass));
                code.invokevirtual(enumClass,
                        "getValue",
                        MethodTypeDesc.of(CD_int));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            default -> throw new IllegalArgumentException(
                    "emitWriteFixedValue: unsupported category " + field.category());
        }
    }

    private void emitRequiredVariableField(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc shapeClass,
            FieldPlan field,
            int fieldIndex,
            int nameLength,
            StructCodePlan plan
    ) {
        // Ensure capacity for field name
        emitEnsure(code, thisClass, nameLength);
        emitFieldNameCopy(code, thisClass, fieldIndex, nameLength);

        // Write value with its own capacity management
        emitWriteValueWithCapacity(code, thisClass, shapeClass, field, SLOT_TYPED, plan);
    }

    private void emitOptionalField(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc shapeClass,
            FieldPlan field,
            int fieldIndex,
            int nameLength,
            StructCodePlan plan
    ) {
        Label skipLabel = code.newLabel();
        int valSlot = allocTempSlot();

        ClassDesc returnType = getGetterReturnType(field);
        boolean isPrimitive = field.category().isPrimitive() && !field.required();

        // Get value and store in temp
        code.aload(SLOT_TYPED);
        code.invokevirtual(shapeClass,
                field.getterName(),
                MethodTypeDesc.of(boxedClassDesc(field)));
        code.astore(valSlot);

        // if (val == null) skip
        code.aload(valSlot);
        code.ifnull(skipLabel);

        if (field.category().isFixedSize()) {
            int totalCapacity = nameLength + field.fixedSizeUpperBound();
            emitEnsure(code, thisClass, totalCapacity);
            emitFieldNameCopy(code, thisClass, fieldIndex, nameLength);
            emitWriteFixedValueFromBoxedLocal(code, field, valSlot);
        } else {
            emitEnsure(code, thisClass, nameLength);
            emitFieldNameCopy(code, thisClass, fieldIndex, nameLength);
            emitWriteValueWithCapacityFromLocal(code, thisClass, shapeClass, field, valSlot, plan);
        }

        code.labelBinding(skipLabel);
    }

    private void emitWriteFixedValueFromBoxedLocal(CodeBuilder code, FieldPlan field, int valSlot) {
        switch (field.category()) {
            case BOOLEAN -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(CD_Boolean);
                code.invokevirtual(CD_Boolean, "booleanValue", MethodTypeDesc.of(CD_boolean));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeBoolean",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_boolean));
                code.istore(SLOT_POS);
            }
            case BYTE, SHORT, INTEGER -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "intValue", MethodTypeDesc.of(CD_int));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            case LONG -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "longValue", MethodTypeDesc.of(CD_long));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeLong",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_long));
                code.istore(SLOT_POS);
            }
            case FLOAT -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "floatValue", MethodTypeDesc.of(CD_float));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeFloatReusable",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_float));
                code.istore(SLOT_POS);
            }
            case DOUBLE -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "doubleValue", MethodTypeDesc.of(CD_double));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeDoubleReusable",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_double));
                code.istore(SLOT_POS);
            }
            case INT_ENUM -> {
                ClassDesc enumClass = ClassDesc.of(field.schema().memberTarget().shapeClass().getName());
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(enumClass);
                code.invokevirtual(enumClass, "getValue", MethodTypeDesc.of(CD_int));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            default -> throw new IllegalArgumentException(
                    "emitWriteFixedValueFromBoxedLocal: unsupported " + field.category());
        }
    }

    private void emitWriteValueWithCapacity(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc ownerClass,
            FieldPlan field,
            int ownerSlot,
            StructCodePlan plan
    ) {
        switch (field.category()) {
            case STRING, ENUM_STRING -> emitWriteString(code, ownerClass, field, ownerSlot);
            case BLOB -> emitWriteBlob(code, thisClass, ownerClass, field, ownerSlot);
            case BIG_INTEGER -> emitWriteBigInteger(code, thisClass, ownerClass, field, ownerSlot);
            case BIG_DECIMAL -> emitWriteBigDecimal(code, thisClass, ownerClass, field, ownerSlot);
            case TIMESTAMP -> emitWriteTimestamp(code, thisClass, ownerClass, field, ownerSlot);
            case LIST -> emitWriteList(code, thisClass, ownerClass, field, ownerSlot, plan);
            case MAP -> emitWriteMap(code, thisClass, ownerClass, field, ownerSlot, plan);
            case STRUCT, UNION -> emitWriteStruct(code, thisClass, ownerClass, field, ownerSlot);
            case DOCUMENT -> emitWriteDocument(code, ownerClass, field, ownerSlot);
            case FLOAT, DOUBLE -> {
                emitEnsure(code, thisClass, 24);
                emitWriteFixedValueFromGetter(code, ownerClass, field, ownerSlot);
            }
            default -> {
                emitEnsure(code, thisClass, field.fixedSizeUpperBound());
                emitWriteFixedValueFromGetter(code, ownerClass, field, ownerSlot);
            }
        }
    }

    private void emitWriteValueWithCapacityFromLocal(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc shapeClass,
            FieldPlan field,
            int valSlot,
            StructCodePlan plan
    ) {
        switch (field.category()) {
            case STRING -> {
                emitSyncBufPosToCtx(code);
                code.aload(2); // ctx
                code.aload(valSlot);
                code.checkcast(CD_String);
                code.invokestatic(CD_JsonCodegenHelpers,
                        "writeQuotedStringFused",
                        MethodTypeDesc.of(CD_void, CD_WriterContext, CD_String));
                emitSyncFromCtx(code);
            }
            case ENUM_STRING -> {
                emitSyncBufPosToCtx(code);
                code.aload(2);
                code.aload(valSlot);
                code.checkcast(CD_SmithyEnum);
                code.invokevirtual(CD_SmithyEnum, "getValue", MethodTypeDesc.of(CD_String));
                code.invokestatic(CD_JsonCodegenHelpers,
                        "writeQuotedStringFused",
                        MethodTypeDesc.of(CD_void, CD_WriterContext, CD_String));
                emitSyncFromCtx(code);
            }
            case BLOB -> emitWriteBlobFromLocal(code, thisClass, valSlot);
            case BIG_INTEGER -> emitWriteBigIntegerFromLocal(code, thisClass, valSlot);
            case BIG_DECIMAL -> emitWriteBigDecimalFromLocal(code, thisClass, valSlot);
            case TIMESTAMP -> emitWriteTimestampFromLocal(code, thisClass, field, valSlot);
            case LIST -> emitWriteListFromLocal(code, thisClass, field, valSlot, plan);
            case MAP -> emitWriteMapFromLocal(code, thisClass, field, valSlot, plan);
            case STRUCT, UNION -> emitWriteStructFromLocal(code, thisClass, field, valSlot);
            case DOCUMENT -> {
                emitSyncBufPosToCtx(code);
                code.aload(valSlot);
                code.aload(2);
                code.invokestatic(CD_JsonCodegenHelpers,
                        "serializeDocument",
                        MethodTypeDesc.of(CD_void, CD_Object, CD_WriterContext));
                emitSyncFromCtx(code);
            }
            default -> {
                // Fixed-size from boxed local
                if (field.category().isFixedSize()) {
                    emitEnsure(code, thisClass, field.fixedSizeUpperBound());
                    emitWriteFixedValueFromBoxedLocal(code, field, valSlot);
                }
            }
        }
    }

    private void emitWriteFixedValueFromGetter(
            CodeBuilder code,
            ClassDesc ownerClass,
            FieldPlan field,
            int ownerSlot
    ) {
        switch (field.category()) {
            case BOOLEAN -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(ownerSlot);
                code.invokevirtual(ownerClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_boolean));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeBoolean",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_boolean));
                code.istore(SLOT_POS);
            }
            case BYTE -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(ownerSlot);
                code.invokevirtual(ownerClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_byte));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            case SHORT -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(ownerSlot);
                code.invokevirtual(ownerClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_short));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            case INTEGER -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(ownerSlot);
                code.invokevirtual(ownerClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_int));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            case LONG -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(ownerSlot);
                code.invokevirtual(ownerClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_long));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeLong",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_long));
                code.istore(SLOT_POS);
            }
            case FLOAT -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(ownerSlot);
                code.invokevirtual(ownerClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_float));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeFloatReusable",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_float));
                code.istore(SLOT_POS);
            }
            case DOUBLE -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(ownerSlot);
                code.invokevirtual(ownerClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_double));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeDoubleReusable",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_double));
                code.istore(SLOT_POS);
            }
            case INT_ENUM -> {
                ClassDesc enumClass = ClassDesc.of(field.schema().memberTarget().shapeClass().getName());
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(ownerSlot);
                code.invokevirtual(ownerClass,
                        field.getterName(),
                        MethodTypeDesc.of(enumClass));
                code.invokevirtual(enumClass, "getValue", MethodTypeDesc.of(CD_int));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            default -> throw new IllegalArgumentException(
                    "emitWriteFixedValueFromGetter: unsupported " + field.category());
        }
    }

    // ---- String ----

    private void emitWriteString(
            CodeBuilder code,
            ClassDesc ownerClass,
            FieldPlan field,
            int ownerSlot
    ) {
        emitSyncBufPosToCtx(code);
        code.aload(2); // ctx
        code.aload(ownerSlot);
        if (field.category() == FieldCategory.ENUM_STRING) {
            ClassDesc enumClass = ClassDesc.of(field.schema().memberTarget().shapeClass().getName());
            code.invokevirtual(ownerClass,
                    field.getterName(),
                    MethodTypeDesc.of(enumClass));
            code.invokevirtual(enumClass, "getValue", MethodTypeDesc.of(CD_String));
        } else {
            code.invokevirtual(ownerClass,
                    field.getterName(),
                    MethodTypeDesc.of(CD_String));
        }
        code.invokestatic(CD_JsonCodegenHelpers,
                "writeQuotedStringFused",
                MethodTypeDesc.of(CD_void, CD_WriterContext, CD_String));
        emitSyncFromCtx(code);
    }

    // ---- Blob ----

    private void emitWriteBlob(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc ownerClass,
            FieldPlan field,
            int ownerSlot
    ) {
        int bbSlot = allocTempSlot();
        int lenSlot = allocTempSlot();
        int dataSlot = allocTempSlot();
        int offSlot = allocTempSlot();

        code.aload(ownerSlot);
        code.invokevirtual(ownerClass,
                field.getterName(),
                MethodTypeDesc.of(CD_ByteBuffer));
        code.astore(bbSlot);

        code.aload(bbSlot);
        code.invokevirtual(CD_ByteBuffer, "remaining", MethodTypeDesc.of(CD_int));
        code.istore(lenSlot);

        Label noArrayLabel = code.newLabel();
        Label afterLabel = code.newLabel();

        code.aload(bbSlot);
        code.invokevirtual(CD_ByteBuffer, "hasArray", MethodTypeDesc.of(CD_boolean));
        code.ifeq(noArrayLabel);

        // Has backing array
        code.aload(bbSlot);
        code.invokevirtual(CD_ByteBuffer, "array", MethodTypeDesc.of(CD_byte_array));
        code.astore(dataSlot);
        code.aload(bbSlot);
        code.invokevirtual(CD_ByteBuffer, "arrayOffset", MethodTypeDesc.of(CD_int));
        code.aload(bbSlot);
        code.invokevirtual(CD_ByteBuffer, "position", MethodTypeDesc.of(CD_int));
        code.iadd();
        code.istore(offSlot);
        code.goto_(afterLabel);

        // No backing array
        code.labelBinding(noArrayLabel);
        code.iload(lenSlot);
        code.newarray(java.lang.classfile.TypeKind.BYTE);
        code.astore(dataSlot);
        code.aload(bbSlot);
        code.invokevirtual(CD_ByteBuffer, "duplicate", MethodTypeDesc.of(CD_ByteBuffer));
        code.aload(dataSlot);
        code.invokevirtual(CD_ByteBuffer, "get", MethodTypeDesc.of(CD_ByteBuffer, CD_byte_array));
        code.pop();
        code.iconst_0();
        code.istore(offSlot);

        code.labelBinding(afterLabel);

        // ensure capacity: maxBase64Bytes(len)
        code.aload(2); // ctx
        code.iload(SLOT_POS);
        code.iload(lenSlot);
        code.invokestatic(CD_JsonWriteUtils,
                "maxBase64Bytes",
                MethodTypeDesc.of(CD_int, CD_int));
        code.invokevirtual(CD_WriterContext,
                "ensure",
                MethodTypeDesc.of(CD_byte_array, CD_int, CD_int));
        code.astore(SLOT_BUF);
        code.aload(2);
        code.getfield(CD_WriterContext, "pos", CD_int);
        code.istore(SLOT_POS);

        // pos = JsonWriteUtils.writeBase64String(buf, pos, data, off, len)
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.aload(dataSlot);
        code.iload(offSlot);
        code.iload(lenSlot);
        code.invokestatic(CD_JsonWriteUtils,
                "writeBase64String",
                MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_byte_array, CD_int, CD_int));
        code.istore(SLOT_POS);
    }

    private void emitWriteBlobFromLocal(CodeBuilder code, ClassDesc thisClass, int valSlot) {
        int bbSlot = allocTempSlot();
        int lenSlot = allocTempSlot();
        int dataSlot = allocTempSlot();
        int offSlot = allocTempSlot();

        code.aload(valSlot);
        code.checkcast(CD_ByteBuffer);
        code.astore(bbSlot);

        code.aload(bbSlot);
        code.invokevirtual(CD_ByteBuffer, "remaining", MethodTypeDesc.of(CD_int));
        code.istore(lenSlot);

        Label noArrayLabel = code.newLabel();
        Label afterLabel = code.newLabel();

        code.aload(bbSlot);
        code.invokevirtual(CD_ByteBuffer, "hasArray", MethodTypeDesc.of(CD_boolean));
        code.ifeq(noArrayLabel);

        code.aload(bbSlot);
        code.invokevirtual(CD_ByteBuffer, "array", MethodTypeDesc.of(CD_byte_array));
        code.astore(dataSlot);
        code.aload(bbSlot);
        code.invokevirtual(CD_ByteBuffer, "arrayOffset", MethodTypeDesc.of(CD_int));
        code.aload(bbSlot);
        code.invokevirtual(CD_ByteBuffer, "position", MethodTypeDesc.of(CD_int));
        code.iadd();
        code.istore(offSlot);
        code.goto_(afterLabel);

        code.labelBinding(noArrayLabel);
        code.iload(lenSlot);
        code.newarray(java.lang.classfile.TypeKind.BYTE);
        code.astore(dataSlot);
        code.aload(bbSlot);
        code.invokevirtual(CD_ByteBuffer, "duplicate", MethodTypeDesc.of(CD_ByteBuffer));
        code.aload(dataSlot);
        code.invokevirtual(CD_ByteBuffer, "get", MethodTypeDesc.of(CD_ByteBuffer, CD_byte_array));
        code.pop();
        code.iconst_0();
        code.istore(offSlot);

        code.labelBinding(afterLabel);

        code.aload(2);
        code.iload(SLOT_POS);
        code.iload(lenSlot);
        code.invokestatic(CD_JsonWriteUtils,
                "maxBase64Bytes",
                MethodTypeDesc.of(CD_int, CD_int));
        code.invokevirtual(CD_WriterContext,
                "ensure",
                MethodTypeDesc.of(CD_byte_array, CD_int, CD_int));
        code.astore(SLOT_BUF);
        code.aload(2);
        code.getfield(CD_WriterContext, "pos", CD_int);
        code.istore(SLOT_POS);

        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.aload(dataSlot);
        code.iload(offSlot);
        code.iload(lenSlot);
        code.invokestatic(CD_JsonWriteUtils,
                "writeBase64String",
                MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_byte_array, CD_int, CD_int));
        code.istore(SLOT_POS);
    }

    // ---- BigInteger ----

    private void emitWriteBigInteger(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc ownerClass,
            FieldPlan field,
            int ownerSlot
    ) {
        emitEnsure(code, thisClass, 56);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.aload(ownerSlot);
        code.invokevirtual(ownerClass,
                field.getterName(),
                MethodTypeDesc.of(CD_BigInteger));
        code.invokestatic(CD_JsonWriteUtils,
                "writeBigInteger",
                MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_BigInteger));
        code.istore(SLOT_POS);
    }

    private void emitWriteBigIntegerFromLocal(CodeBuilder code, ClassDesc thisClass, int valSlot) {
        emitEnsure(code, thisClass, 56);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.aload(valSlot);
        code.checkcast(CD_BigInteger);
        code.invokestatic(CD_JsonWriteUtils,
                "writeBigInteger",
                MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_BigInteger));
        code.istore(SLOT_POS);
    }

    // ---- BigDecimal ----

    private void emitWriteBigDecimal(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc ownerClass,
            FieldPlan field,
            int ownerSlot
    ) {
        int bdSlot = allocTempSlot();
        code.aload(ownerSlot);
        code.invokevirtual(ownerClass,
                field.getterName(),
                MethodTypeDesc.of(CD_BigDecimal));
        code.astore(bdSlot);
        emitWriteBigDecimalFromSlot(code, thisClass, bdSlot);
    }

    private void emitWriteBigDecimalFromLocal(CodeBuilder code, ClassDesc thisClass, int valSlot) {
        int bdSlot = allocTempSlot();
        code.aload(valSlot);
        code.checkcast(CD_BigDecimal);
        code.astore(bdSlot);
        emitWriteBigDecimalFromSlot(code, thisClass, bdSlot);
    }

    private void emitWriteBigDecimalFromSlot(CodeBuilder code, ClassDesc thisClass, int bdSlot) {
        Label elseLabel = code.newLabel();
        Label endLabel = code.newLabel();

        // if (bd.scale() >= 0 && bd.unscaledValue().bitLength() < 64)
        code.aload(bdSlot);
        code.invokevirtual(CD_BigDecimal, "scale", MethodTypeDesc.of(CD_int));
        code.iflt(elseLabel);
        code.aload(bdSlot);
        code.invokevirtual(CD_BigDecimal, "unscaledValue", MethodTypeDesc.of(CD_BigInteger));
        code.invokevirtual(CD_BigInteger, "bitLength", MethodTypeDesc.of(CD_int));
        code.bipush(64);
        code.if_icmpge(elseLabel);

        // Fast path
        emitEnsure(code, thisClass, 22);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.aload(bdSlot);
        code.invokevirtual(CD_BigDecimal, "unscaledValue", MethodTypeDesc.of(CD_BigInteger));
        code.invokevirtual(CD_BigInteger, "longValue", MethodTypeDesc.of(CD_long));
        code.aload(bdSlot);
        code.invokevirtual(CD_BigDecimal, "scale", MethodTypeDesc.of(CD_int));
        code.invokestatic(CD_JsonWriteUtils,
                "writeBigDecimalFromLong",
                MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_long, CD_int));
        code.istore(SLOT_POS);
        code.goto_(endLabel);

        // Slow path: toPlainString
        code.labelBinding(elseLabel);
        int strSlot = allocTempSlot();
        code.aload(bdSlot);
        code.invokevirtual(CD_BigDecimal, "toPlainString", MethodTypeDesc.of(CD_String));
        code.astore(strSlot);
        // ensure(pos, str.length() + 2)
        code.aload(2);
        code.iload(SLOT_POS);
        code.aload(strSlot);
        code.invokevirtual(CD_String, "length", MethodTypeDesc.of(CD_int));
        code.iconst_2();
        code.iadd();
        code.invokevirtual(CD_WriterContext,
                "ensure",
                MethodTypeDesc.of(CD_byte_array, CD_int, CD_int));
        code.astore(SLOT_BUF);
        code.aload(2);
        code.getfield(CD_WriterContext, "pos", CD_int);
        code.istore(SLOT_POS);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.aload(strSlot);
        code.invokestatic(CD_JsonWriteUtils,
                "writeAsciiString",
                MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_String));
        code.istore(SLOT_POS);

        code.labelBinding(endLabel);
    }

    // ---- Timestamp ----

    private void emitWriteTimestamp(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc ownerClass,
            FieldPlan field,
            int ownerSlot
    ) {
        int tsSlot = allocTempSlot();
        code.aload(ownerSlot);
        code.invokevirtual(ownerClass,
                field.getterName(),
                MethodTypeDesc.of(CD_Instant));
        code.astore(tsSlot);
        emitWriteTimestampFromSlot(code, thisClass, field, tsSlot);
    }

    private void emitWriteTimestampFromLocal(
            CodeBuilder code,
            ClassDesc thisClass,
            FieldPlan field,
            int valSlot
    ) {
        int tsSlot = allocTempSlot();
        code.aload(valSlot);
        code.checkcast(CD_Instant);
        code.astore(tsSlot);
        emitWriteTimestampFromSlot(code, thisClass, field, tsSlot);
    }

    private void emitWriteTimestampFromSlot(
            CodeBuilder code,
            ClassDesc thisClass,
            FieldPlan field,
            int tsSlot
    ) {
        String format = field.timestampFormat();
        if (format == null || "EPOCH_SECONDS".equals(format)) {
            emitEnsure(code, thisClass, 24);
            code.aload(SLOT_BUF);
            code.iload(SLOT_POS);
            code.aload(tsSlot);
            code.invokevirtual(CD_Instant, "getEpochSecond", MethodTypeDesc.of(CD_long));
            code.aload(tsSlot);
            code.invokevirtual(CD_Instant, "getNano", MethodTypeDesc.of(CD_int));
            code.invokestatic(CD_JsonWriteUtils,
                    "writeEpochSeconds",
                    MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_long, CD_int));
            code.istore(SLOT_POS);
        } else if ("HTTP_DATE".equals(format)) {
            emitEnsure(code, thisClass, 40);
            code.aload(SLOT_BUF);
            code.iload(SLOT_POS);
            code.aload(tsSlot);
            code.invokestatic(CD_JsonWriteUtils,
                    "writeHttpDate",
                    MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_Instant));
            code.istore(SLOT_POS);
        } else {
            emitEnsure(code, thisClass, 40);
            code.aload(SLOT_BUF);
            code.iload(SLOT_POS);
            code.aload(tsSlot);
            code.invokestatic(CD_JsonWriteUtils,
                    "writeIso8601Timestamp",
                    MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_Instant));
            code.istore(SLOT_POS);
        }
    }

    // ---- List ----

    private void emitWriteList(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc ownerClass,
            FieldPlan field,
            int ownerSlot,
            StructCodePlan plan
    ) {
        int listSlot = allocTempSlot();
        int idxSlot = allocTempSlot();

        code.aload(ownerSlot);
        code.invokevirtual(ownerClass,
                field.getterName(),
                MethodTypeDesc.of(CD_List));
        code.astore(listSlot);

        emitWriteListBody(code, thisClass, field, listSlot, idxSlot, plan);
    }

    private void emitWriteListFromLocal(
            CodeBuilder code,
            ClassDesc thisClass,
            FieldPlan field,
            int valSlot,
            StructCodePlan plan
    ) {
        int listSlot = allocTempSlot();
        int idxSlot = allocTempSlot();

        code.aload(valSlot);
        code.checkcast(CD_List);
        code.astore(listSlot);

        emitWriteListBody(code, thisClass, field, listSlot, idxSlot, plan);
    }

    private void emitWriteListBody(
            CodeBuilder code,
            ClassDesc thisClass,
            FieldPlan field,
            int listSlot,
            int idxSlot,
            StructCodePlan plan
    ) {
        FieldCategory elemCategory = resolveElementCategory(field);

        if (elemCategory.isFixedSize() && !field.sparse()) {
            // Batch capacity: [ + ] + N * (element bound + comma)
            int elemBound = elemCategory.fixedSizeUpperBound();
            emitSyncPosToCtx(code);
            code.aload(2);
            code.iconst_2();
            code.aload(listSlot);
            code.invokeinterface(CD_List, "size", MethodTypeDesc.of(CD_int));
            code.ldc(elemBound + 1);
            code.imul();
            code.iadd();
            code.invokevirtual(CD_WriterContext,
                    "ensureCapacity",
                    MethodTypeDesc.of(CD_void, CD_int));
            emitSyncFromCtx(code);

            emitWriteByte(code, '[');

            Label loopStart = code.newLabel();
            Label loopEnd = code.newLabel();
            Label skipComma = code.newLabel();

            code.iconst_0();
            code.istore(idxSlot);
            code.labelBinding(loopStart);
            code.iload(idxSlot);
            code.aload(listSlot);
            code.invokeinterface(CD_List, "size", MethodTypeDesc.of(CD_int));
            code.if_icmpge(loopEnd);

            // if (i > 0) buf[pos++] = ','
            code.iload(idxSlot);
            code.ifeq(skipComma);
            emitWriteByte(code, ',');
            code.labelBinding(skipComma);

            // Get element and write inline
            code.aload(listSlot);
            code.iload(idxSlot);
            code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
            emitWriteElementInline(code, elemCategory);

            code.iinc(idxSlot, 1);
            code.goto_(loopStart);
            code.labelBinding(loopEnd);

            emitWriteByte(code, ']');
        } else {
            // Variable-size elements
            emitEnsure(code, thisClass, 1);
            emitWriteByte(code, '[');

            Label loopStart = code.newLabel();
            Label loopEnd = code.newLabel();

            code.iconst_0();
            code.istore(idxSlot);
            code.labelBinding(loopStart);
            code.iload(idxSlot);
            code.aload(listSlot);
            code.invokeinterface(CD_List, "size", MethodTypeDesc.of(CD_int));
            code.if_icmpge(loopEnd);

            if (field.sparse()) {
                Label notNullLabel = code.newLabel();
                Label afterNullLabel = code.newLabel();

                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.ifnonnull(notNullLabel);

                // Null element
                emitSyncPosToCtx(code);
                code.aload(2);
                code.iconst_5();
                code.invokevirtual(CD_WriterContext,
                        "ensureCapacity",
                        MethodTypeDesc.of(CD_void, CD_int));
                emitSyncFromCtx(code);
                emitWriteCommaIfNotFirst(code, idxSlot);
                // writeNull
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.invokestatic(CD_JsonCodegenHelpers,
                        "writeNull",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int));
                code.istore(SLOT_POS);
                code.goto_(afterNullLabel);

                code.labelBinding(notNullLabel);
                emitWriteVariableElementWithComma(code, thisClass, elemCategory, field, listSlot, idxSlot);
                code.labelBinding(afterNullLabel);
            } else {
                emitWriteVariableElementWithComma(code, thisClass, elemCategory, field, listSlot, idxSlot);
            }

            code.iinc(idxSlot, 1);
            code.goto_(loopStart);
            code.labelBinding(loopEnd);

            emitEnsure(code, thisClass, 1);
            emitWriteByte(code, ']');
        }
    }

    private void emitWriteCommaIfNotFirst(CodeBuilder code, int idxSlot) {
        Label skipComma = code.newLabel();
        code.iload(idxSlot);
        code.ifeq(skipComma);
        emitWriteByte(code, ',');
        code.labelBinding(skipComma);
    }

    private void emitWriteElementInline(CodeBuilder code, FieldCategory category) {
        // Stack has: element Object on top. Store it, then emit typed write.
        int elemSlot = allocTempSlot();
        code.astore(elemSlot);

        switch (category) {
            case BOOLEAN -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(elemSlot);
                code.checkcast(CD_Boolean);
                code.invokevirtual(CD_Boolean, "booleanValue", MethodTypeDesc.of(CD_boolean));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeBoolean",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_boolean));
                code.istore(SLOT_POS);
            }
            case BYTE, SHORT, INTEGER -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(elemSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "intValue", MethodTypeDesc.of(CD_int));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            case LONG -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(elemSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "longValue", MethodTypeDesc.of(CD_long));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeLong",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_long));
                code.istore(SLOT_POS);
            }
            case FLOAT -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(elemSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "floatValue", MethodTypeDesc.of(CD_float));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeFloatReusable",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_float));
                code.istore(SLOT_POS);
            }
            case DOUBLE -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(elemSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "doubleValue", MethodTypeDesc.of(CD_double));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeDoubleReusable",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_double));
                code.istore(SLOT_POS);
            }
            case INT_ENUM -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(elemSlot);
                code.checkcast(CD_SmithyEnum);
                code.invokevirtual(CD_SmithyEnum, "getValue", MethodTypeDesc.of(CD_int));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            default -> throw new IllegalArgumentException(
                    "emitWriteElementInline: unsupported " + category);
        }
    }

    private void emitWriteVariableElementWithComma(
            CodeBuilder code,
            ClassDesc thisClass,
            FieldCategory category,
            FieldPlan field,
            int listSlot,
            int idxSlot
    ) {
        switch (category) {
            case BOOLEAN -> {
                emitSyncPosToCtx(code);
                code.aload(2);
                code.bipush(6);
                code.invokevirtual(CD_WriterContext,
                        "ensureCapacity",
                        MethodTypeDesc.of(CD_void, CD_int));
                emitSyncFromCtx(code);
                emitWriteCommaIfNotFirst(code, idxSlot);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.checkcast(CD_Boolean);
                code.invokevirtual(CD_Boolean, "booleanValue", MethodTypeDesc.of(CD_boolean));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeBoolean",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_boolean));
                code.istore(SLOT_POS);
            }
            case BYTE, SHORT, INTEGER -> {
                emitSyncPosToCtx(code);
                code.aload(2);
                code.bipush(12);
                code.invokevirtual(CD_WriterContext,
                        "ensureCapacity",
                        MethodTypeDesc.of(CD_void, CD_int));
                emitSyncFromCtx(code);
                emitWriteCommaIfNotFirst(code, idxSlot);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "intValue", MethodTypeDesc.of(CD_int));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            case LONG -> {
                emitSyncPosToCtx(code);
                code.aload(2);
                code.bipush(21);
                code.invokevirtual(CD_WriterContext,
                        "ensureCapacity",
                        MethodTypeDesc.of(CD_void, CD_int));
                emitSyncFromCtx(code);
                emitWriteCommaIfNotFirst(code, idxSlot);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "longValue", MethodTypeDesc.of(CD_long));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeLong",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_long));
                code.istore(SLOT_POS);
            }
            case STRING -> {
                emitEnsure(code, thisClass, 1);
                emitWriteCommaIfNotFirst(code, idxSlot);
                emitSyncBufPosToCtx(code);
                code.aload(2);
                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.checkcast(CD_String);
                code.invokestatic(CD_JsonCodegenHelpers,
                        "writeQuotedStringFused",
                        MethodTypeDesc.of(CD_void, CD_WriterContext, CD_String));
                emitSyncFromCtx(code);
            }
            case ENUM_STRING -> {
                emitEnsure(code, thisClass, 1);
                emitWriteCommaIfNotFirst(code, idxSlot);
                emitSyncBufPosToCtx(code);
                code.aload(2);
                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.checkcast(CD_SmithyEnum);
                code.invokevirtual(CD_SmithyEnum, "getValue", MethodTypeDesc.of(CD_String));
                code.invokestatic(CD_JsonCodegenHelpers,
                        "writeQuotedStringFused",
                        MethodTypeDesc.of(CD_void, CD_WriterContext, CD_String));
                emitSyncFromCtx(code);
            }
            default -> {
                Label skipComma = code.newLabel();
                code.iload(idxSlot);
                code.ifeq(skipComma);
                emitEnsure(code, thisClass, 1);
                emitWriteByte(code, ',');
                code.labelBinding(skipComma);

                emitSyncBufPosToCtx(code);
                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.aload(2);
                Class<?> elemStructClass = field.elementClass();
                emitSerializeNestedCall(code, thisClass, elemStructClass);
                emitSyncFromCtx(code);
            }
        }
    }

    // ---- Map ----

    private void emitWriteMap(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc ownerClass,
            FieldPlan field,
            int ownerSlot,
            StructCodePlan plan
    ) {
        int mapSlot = allocTempSlot();
        code.aload(ownerSlot);
        code.invokevirtual(ownerClass,
                field.getterName(),
                MethodTypeDesc.of(CD_Map));
        code.astore(mapSlot);
        emitWriteMapBody(code, thisClass, field, mapSlot, plan);
    }

    private void emitWriteMapFromLocal(
            CodeBuilder code,
            ClassDesc thisClass,
            FieldPlan field,
            int valSlot,
            StructCodePlan plan
    ) {
        int mapSlot = allocTempSlot();
        code.aload(valSlot);
        code.checkcast(CD_Map);
        code.astore(mapSlot);
        emitWriteMapBody(code, thisClass, field, mapSlot, plan);
    }

    private void emitWriteMapBody(
            CodeBuilder code,
            ClassDesc thisClass,
            FieldPlan field,
            int mapSlot,
            StructCodePlan plan
    ) {
        int itSlot = allocTempSlot();
        int firstSlot = allocTempSlot();
        int entrySlot = allocTempSlot();
        int keySlot = allocTempSlot();

        emitEnsure(code, thisClass, 1);
        emitWriteByte(code, '{');

        // Iterator it = map.entrySet().iterator()
        code.aload(mapSlot);
        ClassDesc CD_Set = ClassDesc.of("java.util.Set");
        code.invokeinterface(CD_Map, "entrySet", MethodTypeDesc.of(CD_Set));
        code.invokeinterface(CD_Set, "iterator", MethodTypeDesc.of(CD_Iterator));
        code.astore(itSlot);

        code.iconst_1();
        code.istore(firstSlot);

        Label loopStart = code.newLabel();
        Label loopEnd = code.newLabel();

        code.labelBinding(loopStart);
        code.aload(itSlot);
        code.invokeinterface(CD_Iterator, "hasNext", MethodTypeDesc.of(CD_boolean));
        code.ifeq(loopEnd);

        // Map.Entry entry = (Map.Entry) it.next()
        code.aload(itSlot);
        code.invokeinterface(CD_Iterator, "next", MethodTypeDesc.of(CD_Object));
        code.checkcast(CD_Map_Entry);
        code.astore(entrySlot);

        // String key = (String) entry.getKey()
        code.aload(entrySlot);
        code.invokeinterface(CD_Map_Entry, "getKey", MethodTypeDesc.of(CD_Object));
        code.checkcast(CD_String);
        code.astore(keySlot);

        // Write comma if not first
        emitEnsure(code, thisClass, 1);
        Label skipComma = code.newLabel();
        code.iload(firstSlot);
        code.ifne(skipComma);
        emitWriteByte(code, ',');
        code.labelBinding(skipComma);
        code.iconst_0();
        code.istore(firstSlot);

        // Write key as quoted string
        emitSyncBufPosToCtx(code);
        code.aload(2);
        code.aload(keySlot);
        code.invokestatic(CD_JsonCodegenHelpers,
                "writeQuotedStringFused",
                MethodTypeDesc.of(CD_void, CD_WriterContext, CD_String));
        emitSyncFromCtx(code);

        // Write colon
        emitEnsure(code, thisClass, 1);
        emitWriteByte(code, ':');

        // Write value
        FieldCategory valCategory = resolveMapValueCategory(field);
        if (field.sparse()) {
            Label notNull = code.newLabel();
            Label afterNull = code.newLabel();

            code.aload(entrySlot);
            code.invokeinterface(CD_Map_Entry, "getValue", MethodTypeDesc.of(CD_Object));
            code.ifnonnull(notNull);

            // null value
            emitEnsure(code, thisClass, 4);
            code.aload(SLOT_BUF);
            code.iload(SLOT_POS);
            code.invokestatic(CD_JsonCodegenHelpers,
                    "writeNull",
                    MethodTypeDesc.of(CD_int, CD_byte_array, CD_int));
            code.istore(SLOT_POS);
            code.goto_(afterNull);

            code.labelBinding(notNull);
            emitWriteMapValue(code, thisClass, valCategory, field, entrySlot);
            code.labelBinding(afterNull);
        } else {
            emitWriteMapValue(code, thisClass, valCategory, field, entrySlot);
        }

        code.goto_(loopStart);
        code.labelBinding(loopEnd);

        emitEnsure(code, thisClass, 1);
        emitWriteByte(code, '}');
    }

    private void emitWriteMapValue(
            CodeBuilder code,
            ClassDesc thisClass,
            FieldCategory category,
            FieldPlan field,
            int entrySlot
    ) {
        int valSlot = allocTempSlot();
        code.aload(entrySlot);
        code.invokeinterface(CD_Map_Entry, "getValue", MethodTypeDesc.of(CD_Object));
        code.astore(valSlot);

        switch (category) {
            case BOOLEAN -> {
                emitEnsure(code, thisClass, 5);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(CD_Boolean);
                code.invokevirtual(CD_Boolean, "booleanValue", MethodTypeDesc.of(CD_boolean));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeBoolean",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_boolean));
                code.istore(SLOT_POS);
            }
            case BYTE, SHORT, INTEGER -> {
                emitEnsure(code, thisClass, 11);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "intValue", MethodTypeDesc.of(CD_int));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeInt",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
            }
            case LONG -> {
                emitEnsure(code, thisClass, 20);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "longValue", MethodTypeDesc.of(CD_long));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeLong",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_long));
                code.istore(SLOT_POS);
            }
            case FLOAT -> {
                emitEnsure(code, thisClass, 24);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "floatValue", MethodTypeDesc.of(CD_float));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeFloatReusable",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_float));
                code.istore(SLOT_POS);
            }
            case DOUBLE -> {
                emitEnsure(code, thisClass, 24);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "doubleValue", MethodTypeDesc.of(CD_double));
                code.invokestatic(CD_JsonWriteUtils,
                        "writeDoubleReusable",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_double));
                code.istore(SLOT_POS);
            }
            case STRING -> {
                emitSyncBufPosToCtx(code);
                code.aload(2);
                code.aload(valSlot);
                code.checkcast(CD_String);
                code.invokestatic(CD_JsonCodegenHelpers,
                        "writeQuotedStringFused",
                        MethodTypeDesc.of(CD_void, CD_WriterContext, CD_String));
                emitSyncFromCtx(code);
            }
            case ENUM_STRING -> {
                emitSyncBufPosToCtx(code);
                code.aload(2);
                code.aload(valSlot);
                code.checkcast(CD_SmithyEnum);
                code.invokevirtual(CD_SmithyEnum, "getValue", MethodTypeDesc.of(CD_String));
                code.invokestatic(CD_JsonCodegenHelpers,
                        "writeQuotedStringFused",
                        MethodTypeDesc.of(CD_void, CD_WriterContext, CD_String));
                emitSyncFromCtx(code);
            }
            default -> {
                // Complex: delegate to nested struct handler
                emitSyncBufPosToCtx(code);
                code.aload(valSlot);
                code.aload(2);
                Class<?> valClass = field.mapValueClass();
                emitSerializeNestedCall(code, thisClass, valClass);
                emitSyncFromCtx(code);
            }
        }
    }

    // ---- Struct / Union nested ----

    private void emitWriteStruct(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc ownerClass,
            FieldPlan field,
            int ownerSlot
    ) {
        emitSyncBufPosToCtx(code);
        code.aload(ownerSlot);
        code.invokevirtual(ownerClass,
                field.getterName(),
                MethodTypeDesc.of(CD_Object));
        code.aload(2);
        Class<?> targetClass = field.schema().memberTarget().shapeClass();
        emitSerializeNestedCall(code, thisClass, targetClass);
        emitSyncFromCtx(code);
    }

    private void emitWriteStructFromLocal(
            CodeBuilder code,
            ClassDesc thisClass,
            FieldPlan field,
            int valSlot
    ) {
        emitSyncBufPosToCtx(code);
        code.aload(valSlot);
        code.aload(2);
        Class<?> targetClass = field.schema().memberTarget().shapeClass();
        emitSerializeNestedCall(code, thisClass, targetClass);
        emitSyncFromCtx(code);
    }

    private void emitSerializeNestedCall(
            CodeBuilder code,
            ClassDesc thisClass,
            Class<?> targetClass
    ) {
        // Stack: ..., Object value, WriterContext ctx
        Integer cacheIdx = targetClass != null ? nestedSerCacheFieldIndices.get(targetClass.getName()) : null;
        ClassDesc serArrayDesc = CD_GeneratedStructSerializer.arrayType();

        if (cacheIdx != null) {
            // JsonCodegenHelpers.serializeNestedStructDirect(value, ctx, cache, class)
            code.getstatic(thisClass, "_SER_" + cacheIdx, serArrayDesc);
            code.ldc(ClassDesc.of(targetClass.getName()));
            code.invokestatic(CD_JsonCodegenHelpers,
                    "serializeNestedStructDirect",
                    MethodTypeDesc.of(CD_void,
                            CD_Object,
                            CD_WriterContext,
                            serArrayDesc,
                            ClassDesc.of("java.lang.Class")));
        } else {
            // JsonCodegenHelpers.serializeNestedStruct(value, ctx)
            code.invokestatic(CD_JsonCodegenHelpers,
                    "serializeNestedStruct",
                    MethodTypeDesc.of(CD_void, CD_Object, CD_WriterContext));
        }
    }

    // ---- Document ----

    private void emitWriteDocument(
            CodeBuilder code,
            ClassDesc ownerClass,
            FieldPlan field,
            int ownerSlot
    ) {
        emitSyncBufPosToCtx(code);
        code.aload(ownerSlot);
        code.invokevirtual(ownerClass,
                field.getterName(),
                MethodTypeDesc.of(CD_Object));
        code.aload(2);
        code.invokestatic(CD_JsonCodegenHelpers,
                "serializeDocument",
                MethodTypeDesc.of(CD_void, CD_Object, CD_WriterContext));
        emitSyncFromCtx(code);
    }

    // ---- Nested struct type collection ----

    private void collectNestedStructTypes(StructCodePlan plan) {
        for (FieldPlan field : plan.fields()) {
            switch (field.category()) {
                case STRUCT, UNION -> registerNestedSerCache(field.schema().memberTarget().shapeClass());
                case LIST -> {
                    Class<?> elemClass = field.elementClass();
                    if (elemClass != null && !isSimpleType(elemClass)) {
                        registerNestedSerCache(elemClass);
                    }
                }
                case MAP -> {
                    Class<?> valClass = field.mapValueClass();
                    if (valClass != null && !isSimpleType(valClass)) {
                        registerNestedSerCache(valClass);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void registerNestedSerCache(Class<?> clazz) {
        if (clazz != null) {
            nestedSerCacheFieldIndices.computeIfAbsent(clazz.getName(), k -> nextCacheIndex++);
        }
    }

    // ---- Utility methods ----

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

    private static ClassDesc boxedClassDesc(FieldPlan field) {
        return switch (field.category()) {
            case BOOLEAN -> ClassDesc.of("java.lang.Boolean");
            case BYTE -> ClassDesc.of("java.lang.Byte");
            case SHORT -> ClassDesc.of("java.lang.Short");
            case INTEGER -> ClassDesc.of("java.lang.Integer");
            case LONG -> ClassDesc.of("java.lang.Long");
            case FLOAT -> ClassDesc.of("java.lang.Float");
            case DOUBLE -> ClassDesc.of("java.lang.Double");
            case STRING -> CD_String;
            case BLOB -> CD_ByteBuffer;
            case TIMESTAMP -> CD_Instant;
            case BIG_INTEGER -> CD_BigInteger;
            case BIG_DECIMAL -> CD_BigDecimal;
            case LIST -> CD_List;
            case MAP -> CD_Map;
            case ENUM_STRING, INT_ENUM, STRUCT, UNION -> {
                Class<?> tc = field.schema().memberTarget().shapeClass();
                yield tc != null ? ClassDesc.of(tc.getName()) : CD_Object;
            }
            default -> CD_Object;
        };
    }

    private static ClassDesc getGetterReturnType(FieldPlan field) {
        if (field.category().isPrimitive() && !field.required()) {
            return boxedClassDesc(field);
        }
        return boxedClassDesc(field);
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
        if (clazz == String.class)
            return FieldCategory.STRING;
        if (clazz == Integer.class || clazz == int.class)
            return FieldCategory.INTEGER;
        if (clazz == Long.class || clazz == long.class)
            return FieldCategory.LONG;
        if (clazz == Double.class || clazz == double.class)
            return FieldCategory.DOUBLE;
        if (clazz == Float.class || clazz == float.class)
            return FieldCategory.FLOAT;
        if (clazz == Boolean.class || clazz == boolean.class)
            return FieldCategory.BOOLEAN;
        if (clazz == Short.class || clazz == short.class)
            return FieldCategory.SHORT;
        if (clazz == Byte.class || clazz == byte.class)
            return FieldCategory.BYTE;
        if (SmithyEnum.class.isAssignableFrom(clazz))
            return FieldCategory.ENUM_STRING;
        return FieldCategory.STRUCT;
    }

    private static String capitalize(String s) {
        if (s.isEmpty())
            return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
