/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.codegen;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.codegen.rt.CodecProfile.GenerationResult;
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
    private static final ClassDesc CD_JsonWriterContext = ClassDesc.of(
            "software.amazon.smithy.java.json.smithy.JsonWriterContext");
    private static final ClassDesc CD_JsonCodegenHelpers = ClassDesc.of(
            "software.amazon.smithy.java.json.codegen.JsonCodegenHelpers");
    private static final ClassDesc CD_JsonSettings = ClassDesc.of(
            "software.amazon.smithy.java.json.JsonSettings");
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
    private static final ClassDesc CD_SerializableStruct = ClassDesc.of(
            "software.amazon.smithy.java.core.schema.SerializableStruct");
    private static final ClassDesc CD_Schema = ClassDesc.of(
            "software.amazon.smithy.java.core.schema.Schema");

    // Slot assignments for serialize method
    // 0=this, 1=Object obj, 2=WriterContext ctx
    private static final int SLOT_TYPED = 3;
    private static final int SLOT_BUF = 4;
    private static final int SLOT_POS = 5;
    private static final int SLOT_NEEDS_COMMA = 6;
    private static final int SLOT_FIRST_TEMP = 7;

    // Max variable fields per chunk method to keep bytecode under C2's DesiredMethodLimit.
    // Each variable field generates ~150-250 bytes of bytecode; 7 fields ≈ ~1500 bytes
    // which leaves room for C2 to inline helpers like writeQuotedStringFast (151 bytes).
    private static final int CHUNK_SIZE = 7;

    private int nextTempSlot;
    private final Map<String, Integer> nestedSerFieldIndices = new LinkedHashMap<>();
    private int nextSerIndex;

    private Set<Integer> jsonNameFieldIndices;
    private List<byte[]> jsonFieldNameBytesListRef;
    private ClassDesc shapeClassDesc;
    private final Map<String, Integer> timestampSchemaIndices = new LinkedHashMap<>();
    private int nextTsSchemaIndex;

    GenerationResult generate(
            StructCodePlan plan,
            String className,
            String packageName,
            Map<String, GeneratedStructSerializer[]> serializerHolders
    ) {
        nextTempSlot = SLOT_FIRST_TEMP;
        nestedSerFieldIndices.clear();
        nextSerIndex = 0;
        jsonNameFieldIndices = new LinkedHashSet<>();
        timestampSchemaIndices.clear();
        nextTsSchemaIndex = 0;

        collectNestedStructTypes(plan, serializerHolders);

        ClassDesc thisClass = ClassDesc.of(packageName + "." + className);
        ClassDesc shapeClass = ClassDesc.of(plan.shapeClass().getName());
        this.shapeClassDesc = shapeClass;

        List<byte[]> fieldNameBytesList = prepareFieldNameBytes(plan, false);
        List<byte[]> jsonFieldNameBytesList = prepareFieldNameBytes(plan, true);
        this.jsonFieldNameBytesListRef = jsonFieldNameBytesList;

        List<FieldPlan> serOrder = plan.isUnion() ? plan.fields() : plan.serializationOrder();
        for (int i = 0; i < serOrder.size(); i++) {
            FieldPlan f = serOrder.get(i);
            if (f.jsonName() != null && !f.jsonName().equals(f.memberName())) {
                jsonNameFieldIndices.add(i);
            }
        }

        collectTimestampFields(plan);

        // Class data: field name bytes, jsonName bytes, serializer holder arrays, timestamp schemas
        List<Object> classData = new ArrayList<>(fieldNameBytesList);
        for (int idx : jsonNameFieldIndices) {
            classData.add(jsonFieldNameBytesList.get(idx));
        }
        int serHoldersClassDataOffset = classData.size();
        for (var entry : nestedSerFieldIndices.entrySet()) {
            classData.add(serializerHolders.get(entry.getKey()));
        }
        int tsSchemaClassDataOffset = classData.size();
        for (var entry : timestampSchemaIndices.entrySet()) {
            classData.add(findMemberSchema(plan, entry.getKey()));
        }

        byte[] bytecode = ClassFile.of().build(thisClass, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            cb.withInterfaceSymbols(CD_GeneratedStructSerializer);

            for (int i = 0; i < fieldNameBytesList.size(); i++) {
                cb.withField("FN_" + i,
                        CD_byte_array,
                        ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            }
            for (int idx : jsonNameFieldIndices) {
                cb.withField("FN_J_" + idx,
                        CD_byte_array,
                        ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            }

            ClassDesc serArrayDesc = CD_GeneratedStructSerializer.arrayType();
            for (var entry : nestedSerFieldIndices.entrySet()) {
                cb.withField("_SER_" + entry.getValue(),
                        serArrayDesc,
                        ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            }

            for (var entry : timestampSchemaIndices.entrySet()) {
                cb.withField("_TS_" + entry.getValue(),
                        CD_Schema,
                        ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            }

            emitClassInit(cb,
                    thisClass,
                    fieldNameBytesList,
                    jsonFieldNameBytesList,
                    serHoldersClassDataOffset,
                    tsSchemaClassDataOffset);
            emitConstructor(cb, thisClass);

            if (plan.isUnion()) {
                emitUnionSerializeMethod(cb,
                        thisClass,
                        shapeClass,
                        plan,
                        fieldNameBytesList,
                        jsonFieldNameBytesList);
            } else {
                emitSerializeMethod(cb,
                        thisClass,
                        shapeClass,
                        plan,
                        fieldNameBytesList,
                        jsonFieldNameBytesList);
                emitSerializeChunkMethods(cb,
                        thisClass,
                        shapeClass,
                        plan,
                        fieldNameBytesList,
                        jsonFieldNameBytesList);
            }
        });

        return new GenerationResult(bytecode, classData);
    }

    private List<byte[]> prepareFieldNameBytes(StructCodePlan plan, boolean useJsonName) {
        List<byte[]> result = new ArrayList<>();
        if (plan.isUnion()) {
            for (FieldPlan field : plan.fields()) {
                result.add(JsonWriteUtils.precomputeFieldNameBytes(field.wireName(useJsonName)));
            }
        } else {
            List<FieldPlan> serOrder = plan.serializationOrder();
            boolean first = true;
            for (FieldPlan field : serOrder) {
                byte[] nameBytes = JsonWriteUtils.precomputeFieldNameBytes(
                        field.wireName(useJsonName));
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
            List<byte[]> fieldNameBytesList,
            List<byte[]> jsonFieldNameBytesList,
            int serHoldersClassDataOffset,
            int tsSchemaClassDataOffset
    ) {
        ClassDesc CD_MethodHandles = ClassDesc.of("java.lang.invoke.MethodHandles");
        ClassDesc CD_List_cls = ClassDesc.of("java.util.List");
        ClassDesc serArrayDesc = CD_GeneratedStructSerializer.arrayType();

        cb.withMethodBody(ConstantDescs.CLASS_INIT_NAME,
                MethodTypeDesc.of(CD_void),
                ClassFile.ACC_STATIC,
                code -> {
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

                    for (int i = 0; i < fieldNameBytesList.size(); i++) {
                        code.aload(listSlot);
                        code.ldc(i);
                        code.invokeinterface(CD_List_cls,
                                "get",
                                MethodTypeDesc.of(CD_Object, CD_int));
                        code.checkcast(CD_byte_array);
                        code.putstatic(thisClass, "FN_" + i, CD_byte_array);
                    }

                    int classDataIdx = fieldNameBytesList.size();
                    for (int idx : jsonNameFieldIndices) {
                        code.aload(listSlot);
                        code.ldc(classDataIdx++);
                        code.invokeinterface(CD_List_cls,
                                "get",
                                MethodTypeDesc.of(CD_Object, CD_int));
                        code.checkcast(CD_byte_array);
                        code.putstatic(thisClass, "FN_J_" + idx, CD_byte_array);
                    }

                    int serIdx = serHoldersClassDataOffset;
                    for (var entry : nestedSerFieldIndices.entrySet()) {
                        code.aload(listSlot);
                        code.ldc(serIdx++);
                        code.invokeinterface(CD_List_cls,
                                "get",
                                MethodTypeDesc.of(CD_Object, CD_int));
                        code.checkcast(serArrayDesc);
                        code.putstatic(thisClass, "_SER_" + entry.getValue(), serArrayDesc);
                    }

                    int tsIdx = tsSchemaClassDataOffset;
                    for (var entry : timestampSchemaIndices.entrySet()) {
                        code.aload(listSlot);
                        code.ldc(tsIdx++);
                        code.invokeinterface(CD_List_cls,
                                "get",
                                MethodTypeDesc.of(CD_Object, CD_int));
                        code.checkcast(CD_Schema);
                        code.putstatic(thisClass, "_TS_" + entry.getValue(), CD_Schema);
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
            List<byte[]> fieldNameBytesList,
            List<byte[]> jsonFieldNameBytesList
    ) {
        cb.withMethodBody("serialize",
                MethodTypeDesc.of(CD_void, CD_SerializableStruct, CD_WriterContext),
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

                    int totalCapacity = 2; // { and }
                    int fixedFieldEnd = 0;
                    for (int i = 0; i < serOrder.size(); i++) {
                        FieldPlan f = serOrder.get(i);
                        int nameLen = Math.max(
                                fieldNameBytesList.get(i).length,
                                jsonFieldNameBytesList.get(i).length);
                        totalCapacity += nameLen;
                        if (f.required() && f.category().isFixedSize()) {
                            totalCapacity += f.fixedSizeUpperBound();
                            fixedFieldEnd = i + 1;
                        }
                    }

                    emitEnsure(code, thisClass, totalCapacity);

                    // buf[pos++] = '{'
                    emitWriteByte(code, '{');

                    // needsComma tracks whether a field has been written (for optional field comma logic)
                    code.ldc(fixedFieldEnd > 0 ? 1 : 0);
                    code.istore(SLOT_NEEDS_COMMA);

                    // Required fixed-size fields (always present; comma baked into field name bytes)
                    for (int i = 0; i < fixedFieldEnd; i++) {
                        FieldPlan f = serOrder.get(i);
                        emitFieldNameCopy(code, thisClass, i, fieldNameBytesList.get(i).length);
                        emitWriteFixedValue(code, shapeClass, f);
                    }

                    // Variable fields: emit inline if few, split into chunk methods if many
                    int varFieldCount = serOrder.size() - fixedFieldEnd;
                    if (varFieldCount <= CHUNK_SIZE) {
                        // Small struct: emit all variable fields inline
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
                                code.iconst_1();
                                code.istore(SLOT_NEEDS_COMMA);
                            }
                        }
                    } else {
                        // Large struct: sync state to ctx, call chunk methods
                        emitSyncToCtx(code);
                        code.aload(2);
                        code.iload(SLOT_NEEDS_COMMA);
                        code.putfield(CD_WriterContext, "needsComma", CD_int);

                        int chunkIdx = 0;
                        for (
                                int start = fixedFieldEnd;
                                start < serOrder.size();
                                start += CHUNK_SIZE) {
                            code.aload(0); // this
                            code.aload(SLOT_TYPED);
                            code.aload(2); // ctx
                            code.invokevirtual(thisClass,
                                    "serializeVars$" + chunkIdx,
                                    MethodTypeDesc.of(CD_void, shapeClass, CD_WriterContext));
                            chunkIdx++;
                        }

                        // Reload buf, pos, needsComma from ctx
                        emitSyncFromCtx(code);
                        code.aload(2);
                        code.getfield(CD_WriterContext, "needsComma", CD_int);
                        code.istore(SLOT_NEEDS_COMMA);
                    }

                    // Closing brace
                    emitEnsure(code, thisClass, 1);
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
            List<byte[]> fieldNameBytesList,
            List<byte[]> jsonFieldNameBytesList
    ) {
        cb.withMethodBody("serialize",
                MethodTypeDesc.of(CD_void, CD_SerializableStruct, CD_WriterContext),
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

                        // ensure capacity for { + field name
                        int nameLen = fieldNameBytesList.get(i).length;
                        emitEnsure(code, thisClass, 1 + nameLen);
                        emitWriteByte(code, '{');

                        // Write field name
                        emitFieldNameCopy(code, thisClass, i, nameLen);

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

    private void emitSerializeChunkMethods(
            java.lang.classfile.ClassBuilder cb,
            ClassDesc thisClass,
            ClassDesc shapeClass,
            StructCodePlan plan,
            List<byte[]> fieldNameBytesList,
            List<byte[]> jsonFieldNameBytesList
    ) {
        List<FieldPlan> serOrder = plan.serializationOrder();
        int fixedFieldEnd = 0;
        for (int i = 0; i < serOrder.size(); i++) {
            FieldPlan f = serOrder.get(i);
            if (f.required() && f.category().isFixedSize()) {
                fixedFieldEnd = i + 1;
            }
        }

        int varFieldCount = serOrder.size() - fixedFieldEnd;
        if (varFieldCount <= CHUNK_SIZE) {
            return;
        }

        int chunkIdx = 0;
        for (int start = fixedFieldEnd; start < serOrder.size(); start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, serOrder.size());
            int capturedChunkIdx = chunkIdx;
            int capturedStart = start;
            int capturedEnd = end;

            // Signature: void serializeVars$N(ShapeClass typed, WriterContext ctx)
            // Slot layout: 0=this, 1=typed, 2=ctx, 3=buf, 4=pos, 5=needsComma, 6+=temps
            // SLOT_TYPED=3 in the main method, but here typed is slot 1 and ctx is slot 2.
            // We need to match slot assignments: load into the standard slots.
            cb.withMethodBody("serializeVars$" + capturedChunkIdx,
                    MethodTypeDesc.of(CD_void, shapeClass, CD_WriterContext),
                    ClassFile.ACC_PRIVATE,
                    code -> {
                        nextTempSlot = SLOT_FIRST_TEMP;

                        // Set up standard slot layout from parameters
                        // 0=this, 1=shapeClass typed, 2=WriterContext ctx
                        // Load typed into SLOT_TYPED (3) - but it's already at slot 1
                        // We need SLOT_TYPED=3, SLOT_BUF=4, SLOT_POS=5, SLOT_NEEDS_COMMA=6
                        // Load from parameters and ctx fields
                        code.aload(1); // typed param
                        code.astore(SLOT_TYPED);
                        code.aload(2); // ctx
                        code.getfield(CD_WriterContext, "buf", CD_byte_array);
                        code.astore(SLOT_BUF);
                        code.aload(2);
                        code.getfield(CD_WriterContext, "pos", CD_int);
                        code.istore(SLOT_POS);
                        code.aload(2);
                        code.getfield(CD_WriterContext, "needsComma", CD_int);
                        code.istore(SLOT_NEEDS_COMMA);

                        for (int i = capturedStart; i < capturedEnd; i++) {
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
                                code.iconst_1();
                                code.istore(SLOT_NEEDS_COMMA);
                            }
                        }

                        // Write state back to ctx
                        emitSyncToCtx(code);
                        code.aload(2);
                        code.iload(SLOT_NEEDS_COMMA);
                        code.putfield(CD_WriterContext, "needsComma", CD_int);
                        code.return_();
                    });
            chunkIdx++;
        }
    }

    // ---- Bytecode emit helpers ----

    private void emitEnsure(CodeBuilder code, ClassDesc thisClass, int needed) {
        // Inline the fast path: if (pos + needed > buf.length) buf = ctx.ensure(pos, needed)
        Label ok = code.newLabel();
        code.iload(SLOT_POS);
        code.ldc(needed);
        code.iadd();
        code.aload(SLOT_BUF);
        code.arraylength();
        code.if_icmple(ok);
        // Slow path: grow buffer
        code.aload(2); // ctx
        code.iload(SLOT_POS);
        code.ldc(needed);
        code.invokevirtual(CD_WriterContext,
                "ensure",
                MethodTypeDesc.of(CD_byte_array, CD_int, CD_int));
        code.astore(SLOT_BUF);
        code.labelBinding(ok);
    }

    private void emitWriteByte(CodeBuilder code, char b) {
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.bipush(b);
        code.bastore();
        code.iinc(SLOT_POS, 1);
    }

    private void emitFieldNameCopy(CodeBuilder code, ClassDesc thisClass, int fieldIndex, int length) {
        if (jsonNameFieldIndices.contains(fieldIndex)) {
            int jsonNameLength = jsonFieldNameBytesListRef.get(fieldIndex).length;
            // Branch: use FN_J_i when ctx.useJsonName, otherwise FN_i
            Label useMember = code.newLabel();
            Label afterCopy = code.newLabel();
            code.aload(2);
            code.checkcast(CD_JsonWriterContext);
            code.getfield(CD_JsonWriterContext, "jsonSettings", CD_JsonSettings);
            code.invokevirtual(CD_JsonSettings, "useJsonName", MethodTypeDesc.of(CD_boolean));
            code.ifeq(useMember);

            // jsonName path
            emitRawFieldNameCopy(code, thisClass, "FN_J_" + fieldIndex, jsonNameLength);
            code.goto_(afterCopy);

            // memberName path
            code.labelBinding(useMember);
            emitRawFieldNameCopy(code, thisClass, "FN_" + fieldIndex, length);

            code.labelBinding(afterCopy);
        } else {
            emitRawFieldNameCopy(code, thisClass, "FN_" + fieldIndex, length);
        }
    }

    private void emitOptionalFieldNameCopy(
            CodeBuilder code,
            ClassDesc thisClass,
            int fieldIndex,
            int nameLength
    ) {
        // The precomputed bytes for fieldIndex > 0 have a comma prefix: ",\"name\":"
        // If needsComma == true, copy all bytes (offset 0, length nameLength).
        // If needsComma == false, skip the comma (offset 1, length nameLength - 1).
        if (jsonNameFieldIndices.contains(fieldIndex)) {
            // Has jsonName variant — need to handle both FN_ and FN_J_ with comma logic
            int jsonNameLength = jsonFieldNameBytesListRef.get(fieldIndex).length;
            Label useMember = code.newLabel();
            Label afterCopy = code.newLabel();
            code.aload(2);
            code.checkcast(CD_JsonWriterContext);
            code.getfield(CD_JsonWriterContext, "jsonSettings", CD_JsonSettings);
            code.invokevirtual(CD_JsonSettings, "useJsonName", MethodTypeDesc.of(CD_boolean));
            code.ifeq(useMember);
            emitRangedFieldNameCopy(code, thisClass, "FN_J_" + fieldIndex, jsonNameLength);
            code.goto_(afterCopy);
            code.labelBinding(useMember);
            emitRangedFieldNameCopy(code, thisClass, "FN_" + fieldIndex, nameLength);
            code.labelBinding(afterCopy);
        } else {
            emitRangedFieldNameCopy(code, thisClass, "FN_" + fieldIndex, nameLength);
        }
    }

    private void emitRangedFieldNameCopy(
            CodeBuilder code,
            ClassDesc thisClass,
            String staticField,
            int fullLength
    ) {
        Label withComma = code.newLabel();
        Label afterCopy = code.newLabel();
        code.iload(SLOT_NEEDS_COMMA);
        code.ifne(withComma);
        // No comma: copy from offset 1, length-1
        emitRawFieldNameCopy(code, thisClass, staticField, 1, fullLength - 1);
        code.goto_(afterCopy);
        code.labelBinding(withComma);
        // With comma: copy from offset 0, full length
        emitRawFieldNameCopy(code, thisClass, staticField, 0, fullLength);
        code.labelBinding(afterCopy);
    }

    private void emitRawFieldNameCopy(CodeBuilder code, ClassDesc thisClass, String fieldName, int length) {
        emitRawFieldNameCopy(code, thisClass, fieldName, 0, length);
    }

    private void emitRawFieldNameCopy(
            CodeBuilder code,
            ClassDesc thisClass,
            String fieldName,
            int srcOffset,
            int length
    ) {
        code.getstatic(thisClass, fieldName, CD_byte_array);
        code.ldc(srcOffset);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.ldc(length);
        code.invokestatic(CD_System,
                "arraycopy",
                MethodTypeDesc.of(CD_void, CD_Object, CD_int, CD_Object, CD_int, CD_int));
        code.iinc(SLOT_POS, length);
    }

    private void collectTimestampFields(StructCodePlan plan) {
        List<FieldPlan> fields = plan.isUnion() ? plan.fields() : plan.serializationOrder();
        for (FieldPlan f : fields) {
            boolean needsSchema = false;
            if (f.category() == FieldCategory.TIMESTAMP) {
                needsSchema = true;
            } else if (f.category() == FieldCategory.LIST
                    && f.elementClass() == java.time.Instant.class) {
                needsSchema = true;
            } else if (f.category() == FieldCategory.MAP
                    && f.mapValueClass() == java.time.Instant.class) {
                needsSchema = true;
            }
            if (needsSchema) {
                String key = f.memberName();
                if (!timestampSchemaIndices.containsKey(key)) {
                    timestampSchemaIndices.put(key, nextTsSchemaIndex++);
                }
            }
        }
    }

    private static Object findMemberSchema(StructCodePlan plan, String memberName) {
        for (var member : plan.schema().members()) {
            if (member.memberName().equals(memberName)) {
                return member;
            }
        }
        throw new IllegalArgumentException("No member named " + memberName);
    }

    private void emitSyncToCtx(CodeBuilder code) {
        code.aload(2);
        code.dup();
        code.aload(SLOT_BUF);
        code.putfield(CD_WriterContext, "buf", CD_byte_array);
        code.iload(SLOT_POS);
        code.putfield(CD_WriterContext, "pos", CD_int);
    }

    private void emitSyncFromCtx(CodeBuilder code) {
        code.aload(2);
        code.dup();
        code.getfield(CD_WriterContext, "buf", CD_byte_array);
        code.astore(SLOT_BUF);
        code.getfield(CD_WriterContext, "pos", CD_int);
        code.istore(SLOT_POS);
    }

    private void emitSyncPosToCtx(CodeBuilder code) {
        code.aload(2);
        code.iload(SLOT_POS);
        code.putfield(CD_WriterContext, "pos", CD_int);
    }

    private void emitSyncBufPosToCtx(CodeBuilder code) {
        // ctx.buf is always in sync (ensure() updates this.buf directly), only pos needs syncing
        emitSyncPosToCtx(code);
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
                code.aload(2);
                code.checkcast(CD_JsonWriterContext);
                code.invokestatic(CD_JsonWriteUtils,
                        "writeFloatNanCheck",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_float, CD_JsonWriterContext));
                code.istore(SLOT_POS);
            }
            case DOUBLE -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(SLOT_TYPED);
                code.invokevirtual(shapeClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_double));
                code.aload(2);
                code.checkcast(CD_JsonWriterContext);
                code.invokestatic(CD_JsonWriteUtils,
                        "writeDoubleNanCheck",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_double, CD_JsonWriterContext));
                code.istore(SLOT_POS);
            }
            case INT_ENUM -> {
                Class<?> enumJavaClass = field.schema().memberTarget().shapeClass();
                ClassDesc enumClass = ClassDesc.of(enumJavaClass.getName());
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(SLOT_TYPED);
                code.invokevirtual(shapeClass,
                        field.getterName(),
                        MethodTypeDesc.of(enumClass));
                if (enumJavaClass.isInterface()) {
                    code.invokeinterface(enumClass, "getValue", MethodTypeDesc.of(CD_int));
                } else {
                    code.invokevirtual(enumClass, "getValue", MethodTypeDesc.of(CD_int));
                }
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

    private void emitRequiredVariableFieldNoNameEnsure(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc shapeClass,
            FieldPlan field,
            int fieldIndex,
            int nameLength,
            StructCodePlan plan
    ) {
        // Name capacity already ensured upfront, just copy
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

        // For optional collections, use hasX() to check if set (getters return empty for null fields)
        if (!field.required()
                && (field.category() == FieldCategory.LIST || field.category() == FieldCategory.MAP)) {
            String hasMethod = "has" + capitalize(field.memberName());
            code.aload(SLOT_TYPED);
            code.invokevirtual(shapeClass, hasMethod, MethodTypeDesc.of(CD_boolean));
            code.ifeq(skipLabel);
        }

        // Get value and store in temp
        code.aload(SLOT_TYPED);
        code.invokevirtual(shapeClass,
                field.getterName(),
                MethodTypeDesc.of(boxedClassDesc(field)));
        code.astore(valSlot);

        // if (val == null) skip
        code.aload(valSlot);
        code.ifnull(skipLabel);

        // Ensure capacity for field name + value in one call when possible
        int ensureLen = nameLength;
        if (jsonNameFieldIndices.contains(fieldIndex)) {
            ensureLen = Math.max(ensureLen, jsonFieldNameBytesListRef.get(fieldIndex).length);
        }
        if (field.category().isFixedSize()) {
            ensureLen += field.fixedSizeUpperBound();
        }
        emitEnsure(code, thisClass, ensureLen);

        // Field name copy with conditional comma handling:
        // fieldIndex > 0 means the precomputed bytes have a comma prefix.
        // If needsComma is false (no prior field written), skip the comma byte.
        if (fieldIndex > 0) {
            emitOptionalFieldNameCopy(code, thisClass, fieldIndex, nameLength);
        } else {
            emitFieldNameCopy(code, thisClass, fieldIndex, nameLength);
        }
        // Mark that a field has been written
        code.iconst_1();
        code.istore(SLOT_NEEDS_COMMA);

        if (field.category().isFixedSize()) {
            emitWriteFixedValueFromBoxedLocal(code, field, valSlot);
        } else {
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
                code.aload(2);
                code.checkcast(CD_JsonWriterContext);
                code.invokestatic(CD_JsonWriteUtils,
                        "writeFloatNanCheck",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_float, CD_JsonWriterContext));
                code.istore(SLOT_POS);
            }
            case DOUBLE -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "doubleValue", MethodTypeDesc.of(CD_double));
                code.aload(2);
                code.checkcast(CD_JsonWriterContext);
                code.invokestatic(CD_JsonWriteUtils,
                        "writeDoubleNanCheck",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_double, CD_JsonWriterContext));
                code.istore(SLOT_POS);
            }
            case INT_ENUM -> {
                ClassDesc enumClass = ClassDesc.of(field.schema().memberTarget().shapeClass().getName());
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(enumClass);
                if (field.schema().memberTarget().shapeClass().isInterface()) {
                    code.invokeinterface(enumClass, "getValue", MethodTypeDesc.of(CD_int));
                } else {
                    code.invokevirtual(enumClass, "getValue", MethodTypeDesc.of(CD_int));
                }
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
                code.aload(valSlot);
                code.checkcast(CD_String);
                emitWriteStringFastCall(code);
            }
            case ENUM_STRING -> {
                code.aload(valSlot);
                code.checkcast(CD_SmithyEnum);
                code.invokeinterface(CD_SmithyEnum, "getValue", MethodTypeDesc.of(CD_String));
                emitWriteStringFastCall(code);
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
                code.aload(2);
                code.checkcast(CD_JsonWriterContext);
                code.invokestatic(CD_JsonWriteUtils,
                        "writeFloatNanCheck",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_float, CD_JsonWriterContext));
                code.istore(SLOT_POS);
            }
            case DOUBLE -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(ownerSlot);
                code.invokevirtual(ownerClass,
                        field.getterName(),
                        MethodTypeDesc.of(CD_double));
                code.aload(2);
                code.checkcast(CD_JsonWriterContext);
                code.invokestatic(CD_JsonWriteUtils,
                        "writeDoubleNanCheck",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_double, CD_JsonWriterContext));
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
                if (field.schema().memberTarget().shapeClass().isInterface()) {
                    code.invokeinterface(enumClass, "getValue", MethodTypeDesc.of(CD_int));
                } else {
                    code.invokevirtual(enumClass, "getValue", MethodTypeDesc.of(CD_int));
                }
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

    /**
     * Emits a writeQuotedStringFast call. Stack before: string value is ready.
     * Expects string value on the top of stack. Handles reload from ctx on slow path.
     */
    private void emitWriteStringFastCall(CodeBuilder code) {
        // Stack: ..., String value
        // Need: buf, pos, value, ctx -> writeQuotedStringFast -> int
        int strSlot = allocTempSlot();
        code.astore(strSlot);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.aload(strSlot);
        code.aload(2); // ctx
        code.invokestatic(CD_JsonCodegenHelpers,
                "writeQuotedStringFast",
                MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_String, CD_WriterContext));

        // Check result: MIN_VALUE means slow path was taken
        int resultSlot = allocTempSlot();
        code.istore(resultSlot);
        Label reloadLabel = code.newLabel();
        Label afterLabel = code.newLabel();
        code.iload(resultSlot);
        code.ldc(Integer.MIN_VALUE);
        code.if_icmpeq(reloadLabel);
        // Fast path: result is new pos
        code.iload(resultSlot);
        code.istore(SLOT_POS);
        code.goto_(afterLabel);
        // Slow path: reload buf and pos from ctx
        code.labelBinding(reloadLabel);
        emitSyncFromCtx(code);
        code.labelBinding(afterLabel);
    }

    private void emitWriteString(
            CodeBuilder code,
            ClassDesc ownerClass,
            FieldPlan field,
            int ownerSlot
    ) {
        code.aload(ownerSlot);
        if (field.category() == FieldCategory.ENUM_STRING) {
            Class<?> enumJavaClass = field.schema().memberTarget().shapeClass();
            ClassDesc enumClass = ClassDesc.of(enumJavaClass.getName());
            code.invokevirtual(ownerClass,
                    field.getterName(),
                    MethodTypeDesc.of(enumClass));
            if (enumJavaClass.isInterface()) {
                code.invokeinterface(enumClass, "getValue", MethodTypeDesc.of(CD_String));
            } else {
                code.invokevirtual(enumClass, "getValue", MethodTypeDesc.of(CD_String));
            }
        } else {
            code.invokevirtual(ownerClass,
                    field.getterName(),
                    MethodTypeDesc.of(CD_String));
        }
        emitWriteStringFastCall(code);
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
        int scaleSlot = allocTempSlot();
        int unscaledSlot = allocTempSlot();

        // Cache scale() and unscaledValue() to avoid calling them twice
        code.aload(bdSlot);
        code.invokevirtual(CD_BigDecimal, "scale", MethodTypeDesc.of(CD_int));
        code.istore(scaleSlot);
        code.iload(scaleSlot);
        code.iflt(elseLabel);
        code.aload(bdSlot);
        code.invokevirtual(CD_BigDecimal, "unscaledValue", MethodTypeDesc.of(CD_BigInteger));
        code.astore(unscaledSlot);
        code.aload(unscaledSlot);
        code.invokevirtual(CD_BigInteger, "bitLength", MethodTypeDesc.of(CD_int));
        code.bipush(64);
        code.if_icmpge(elseLabel);

        // Fast path: reuse cached values
        emitEnsure(code, thisClass, 22);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.aload(unscaledSlot);
        code.invokevirtual(CD_BigInteger, "longValue", MethodTypeDesc.of(CD_long));
        code.iload(scaleSlot);
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
        Integer tsIdx = timestampSchemaIndices.get(field.memberName());
        emitEnsure(code, thisClass, 42);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.aload(tsSlot);
        code.getstatic(thisClass, "_TS_" + tsIdx, CD_Schema);
        code.aload(2);
        code.checkcast(CD_JsonWriterContext);
        code.getfield(CD_JsonWriterContext, "jsonSettings", CD_JsonSettings);
        code.invokestatic(CD_JsonCodegenHelpers,
                "writeTimestamp",
                MethodTypeDesc.of(CD_int,
                        CD_byte_array,
                        CD_int,
                        CD_Instant,
                        CD_Schema,
                        CD_JsonSettings));
        code.istore(SLOT_POS);
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
        int sizeSlot = allocTempSlot();

        // Cache list.size() before the loop to avoid repeated interface dispatch
        // through Collections.unmodifiableList wrapper
        code.aload(listSlot);
        code.invokeinterface(CD_List, "size", MethodTypeDesc.of(CD_int));
        code.istore(sizeSlot);

        if (elemCategory.isFixedSize() && !field.sparse()) {
            // Batch capacity: [ + ] + N * (element bound + comma)
            int elemBound = elemCategory.fixedSizeUpperBound();
            // Use ensure(pos, needed) to avoid ctx.pos round-trip
            code.aload(2); // ctx
            code.iload(SLOT_POS);
            code.iconst_2();
            code.iload(sizeSlot);
            code.ldc(elemBound + 1);
            code.imul();
            code.iadd(); // needed = 2 + size * (elemBound + 1)
            code.invokevirtual(CD_WriterContext,
                    "ensure",
                    MethodTypeDesc.of(CD_byte_array, CD_int, CD_int));
            code.astore(SLOT_BUF); // buf may have changed

            emitWriteByte(code, '[');

            Label loopStart = code.newLabel();
            Label loopEnd = code.newLabel();
            Label skipComma = code.newLabel();

            code.iconst_0();
            code.istore(idxSlot);
            code.labelBinding(loopStart);
            code.iload(idxSlot);
            code.iload(sizeSlot);
            code.if_icmpge(loopEnd);

            // if (i > 0) buf[pos++] = ','
            code.iload(idxSlot);
            code.ifeq(skipComma);
            emitWriteByte(code, ',');
            code.labelBinding(skipComma);

            // Get element and write inline (save/restore temp slots for loop reuse)
            int savedTempSlot = nextTempSlot;
            code.aload(listSlot);
            code.iload(idxSlot);
            code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
            emitWriteElementInline(code, elemCategory);
            nextTempSlot = savedTempSlot;

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
            code.iload(sizeSlot);
            code.if_icmpge(loopEnd);

            int savedTempSlot2 = nextTempSlot;
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
            nextTempSlot = savedTempSlot2;

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
                code.aload(2);
                code.checkcast(CD_JsonWriterContext);
                code.invokestatic(CD_JsonWriteUtils,
                        "writeFloatNanCheck",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_float, CD_JsonWriterContext));
                code.istore(SLOT_POS);
            }
            case DOUBLE -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(elemSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "doubleValue", MethodTypeDesc.of(CD_double));
                code.aload(2);
                code.checkcast(CD_JsonWriterContext);
                code.invokestatic(CD_JsonWriteUtils,
                        "writeDoubleNanCheck",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_double, CD_JsonWriterContext));
                code.istore(SLOT_POS);
            }
            case INT_ENUM -> throw new UnsupportedOperationException(
                    "INT_ENUM list elements not yet supported in ClassFile codegen");
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
                emitEnsure(code, thisClass, 6);
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
                emitEnsure(code, thisClass, 12);
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
                emitEnsure(code, thisClass, 21);
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
                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.checkcast(CD_String);
                emitWriteStringFastCall(code);
            }
            case ENUM_STRING -> {
                emitEnsure(code, thisClass, 1);
                emitWriteCommaIfNotFirst(code, idxSlot);
                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.checkcast(CD_SmithyEnum);
                code.invokeinterface(CD_SmithyEnum, "getValue", MethodTypeDesc.of(CD_String));
                emitWriteStringFastCall(code);
            }
            case BIG_INTEGER -> {
                emitEnsure(code, thisClass, 57);
                emitWriteCommaIfNotFirst(code, idxSlot);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.checkcast(CD_BigInteger);
                code.invokestatic(CD_JsonWriteUtils,
                        "writeBigInteger",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_BigInteger));
                code.istore(SLOT_POS);
            }
            case BIG_DECIMAL -> {
                int elemSlot = allocTempSlot();
                Label skipComma = code.newLabel();
                code.iload(idxSlot);
                code.ifeq(skipComma);
                emitEnsure(code, thisClass, 1);
                emitWriteByte(code, ',');
                code.labelBinding(skipComma);
                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.astore(elemSlot);
                emitWriteBigDecimalFromLocal(code, thisClass, elemSlot);
            }
            case BLOB -> {
                int elemSlot = allocTempSlot();
                Label skipComma = code.newLabel();
                code.iload(idxSlot);
                code.ifeq(skipComma);
                emitEnsure(code, thisClass, 1);
                emitWriteByte(code, ',');
                code.labelBinding(skipComma);
                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.astore(elemSlot);
                emitWriteBlobFromLocal(code, thisClass, elemSlot);
            }
            case TIMESTAMP -> {
                int elemSlot = allocTempSlot();
                Label skipComma = code.newLabel();
                code.iload(idxSlot);
                code.ifeq(skipComma);
                emitEnsure(code, thisClass, 1);
                emitWriteByte(code, ',');
                code.labelBinding(skipComma);
                code.aload(listSlot);
                code.iload(idxSlot);
                code.invokeinterface(CD_List, "get", MethodTypeDesc.of(CD_Object, CD_int));
                code.checkcast(CD_Instant);
                code.astore(elemSlot);
                emitWriteTimestampFromSlot(code, thisClass, field, elemSlot);
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
        code.aload(keySlot);
        emitWriteStringFastCall(code);

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
                code.aload(2);
                code.checkcast(CD_JsonWriterContext);
                code.invokestatic(CD_JsonWriteUtils,
                        "writeFloatNanCheck",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_float, CD_JsonWriterContext));
                code.istore(SLOT_POS);
            }
            case DOUBLE -> {
                emitEnsure(code, thisClass, 24);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.aload(valSlot);
                code.checkcast(CD_Number);
                code.invokevirtual(CD_Number, "doubleValue", MethodTypeDesc.of(CD_double));
                code.aload(2);
                code.checkcast(CD_JsonWriterContext);
                code.invokestatic(CD_JsonWriteUtils,
                        "writeDoubleNanCheck",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_double, CD_JsonWriterContext));
                code.istore(SLOT_POS);
            }
            case STRING -> {
                code.aload(valSlot);
                code.checkcast(CD_String);
                emitWriteStringFastCall(code);
            }
            case ENUM_STRING -> {
                code.aload(valSlot);
                code.checkcast(CD_SmithyEnum);
                code.invokeinterface(CD_SmithyEnum, "getValue", MethodTypeDesc.of(CD_String));
                emitWriteStringFastCall(code);
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
        Class<?> targetClass = field.schema().memberTarget().shapeClass();
        ClassDesc targetDesc = targetClass != null ? ClassDesc.of(targetClass.getName()) : CD_Object;
        code.aload(ownerSlot);
        code.invokevirtual(ownerClass,
                field.getterName(),
                MethodTypeDesc.of(targetDesc));
        code.aload(2);
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
        String name = targetClass != null ? targetClass.getName() : null;
        Integer idx = name != null ? nestedSerFieldIndices.get(name) : null;

        if (idx != null) {
            ClassDesc serArrayDesc = CD_GeneratedStructSerializer.arrayType();
            int ctxSlot = nextTempSlot++;
            int valSlot = nextTempSlot++;
            code.astore(ctxSlot);
            code.astore(valSlot);

            code.aload(valSlot);
            code.ifThenElse(Opcode.IFNONNULL,
                    tb -> {
                        tb.getstatic(thisClass, "_SER_" + idx, serArrayDesc);
                        tb.iconst_0();
                        tb.aaload();
                        tb.aload(valSlot);
                        tb.checkcast(CD_SerializableStruct);
                        tb.aload(ctxSlot);
                        tb.invokeinterface(CD_GeneratedStructSerializer,
                                "serialize",
                                MethodTypeDesc.of(CD_void,
                                        CD_SerializableStruct,
                                        CD_WriterContext));
                    },
                    fb -> {
                        fb.aload(ctxSlot);
                        fb.ldc(4);
                        fb.invokevirtual(CD_WriterContext,
                                "ensureCapacity",
                                MethodTypeDesc.of(CD_void, CD_int));
                        fb.aload(ctxSlot);
                        fb.getfield(CD_WriterContext, "buf", CD_byte_array);
                        fb.aload(ctxSlot);
                        fb.getfield(CD_WriterContext, "pos", CD_int);
                        fb.invokestatic(CD_JsonCodegenHelpers,
                                "writeNull",
                                MethodTypeDesc.of(CD_int, CD_byte_array, CD_int));
                        fb.aload(ctxSlot);
                        fb.swap();
                        fb.putfield(CD_WriterContext, "pos", CD_int);
                    });
            nextTempSlot -= 2;
        } else {
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
        ClassDesc CD_Document = ClassDesc.of("software.amazon.smithy.java.core.serde.document.Document");
        emitSyncBufPosToCtx(code);
        code.aload(ownerSlot);
        code.invokevirtual(ownerClass,
                field.getterName(),
                MethodTypeDesc.of(CD_Document));
        code.aload(2);
        code.invokestatic(CD_JsonCodegenHelpers,
                "serializeDocument",
                MethodTypeDesc.of(CD_void, CD_Object, CD_WriterContext));
        emitSyncFromCtx(code);
    }

    // ---- Nested struct type collection ----

    private void collectNestedStructTypes(
            StructCodePlan plan,
            Map<String, GeneratedStructSerializer[]> serializerHolders
    ) {
        for (FieldPlan field : plan.fields()) {
            Class<?> clazz = null;
            switch (field.category()) {
                case STRUCT, UNION -> clazz = field.schema().memberTarget().shapeClass();
                case LIST -> {
                    Class<?> elemClass = field.elementClass();
                    if (elemClass != null && !isSimpleType(elemClass)) {
                        clazz = elemClass;
                    }
                }
                case MAP -> {
                    Class<?> valClass = field.mapValueClass();
                    if (valClass != null && !isSimpleType(valClass)) {
                        clazz = valClass;
                    }
                }
                default -> {
                }
            }
            if (clazz != null) {
                String name = clazz.getName();
                if (serializerHolders.containsKey(name)) {
                    nestedSerFieldIndices.computeIfAbsent(name, k -> nextSerIndex++);
                }
            }
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
            case DOCUMENT -> ClassDesc.of("software.amazon.smithy.java.core.serde.document.Document");
            default -> CD_Object;
        };
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
        if (clazz == java.math.BigInteger.class)
            return FieldCategory.BIG_INTEGER;
        if (clazz == java.math.BigDecimal.class)
            return FieldCategory.BIG_DECIMAL;
        if (clazz == java.nio.ByteBuffer.class)
            return FieldCategory.BLOB;
        if (clazz == java.time.Instant.class)
            return FieldCategory.TIMESTAMP;
        if (software.amazon.smithy.java.core.serde.document.Document.class.isAssignableFrom(clazz))
            return FieldCategory.DOCUMENT;
        return FieldCategory.STRUCT;
    }

    private static String capitalize(String s) {
        if (s.isEmpty())
            return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
