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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.codegen.rt.BytecodeCodecProfile.GenerationResult;
import software.amazon.smithy.java.codegen.rt.GeneratedStructDeserializer;
import software.amazon.smithy.java.codegen.rt.plan.FieldCategory;
import software.amazon.smithy.java.codegen.rt.plan.FieldPlan;
import software.amazon.smithy.java.codegen.rt.plan.StructCodePlan;
import software.amazon.smithy.java.core.schema.SmithyEnum;

/**
 * Generates deserializer bytecode using the ClassFile API (JDK 25+).
 * Produces a class implementing {@link GeneratedStructDeserializer}.
 */
final class ClassFileJsonDeserializerGenerator {

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

    private static final ClassDesc CD_JsonReaderContext = ClassDesc.of(
            "software.amazon.smithy.java.json.codegen.JsonReaderContext");
    private static final ClassDesc CD_GeneratedStructDeserializer = ClassDesc.of(
            "software.amazon.smithy.java.codegen.rt.GeneratedStructDeserializer");
    private static final ClassDesc CD_SerializableStruct = ClassDesc.of(
            "software.amazon.smithy.java.core.schema.SerializableStruct");
    private static final ClassDesc CD_ShapeBuilder = ClassDesc.of(
            "software.amazon.smithy.java.core.schema.ShapeBuilder");
    private static final ClassDesc CD_JsonReadUtils = ClassDesc.of(
            "software.amazon.smithy.java.json.smithy.JsonReadUtils");
    private static final ClassDesc CD_JsonParseState = ClassDesc.of(
            "software.amazon.smithy.java.json.smithy.JsonParseState");
    private static final ClassDesc CD_JsonCodegenHelpers = ClassDesc.of(
            "software.amazon.smithy.java.json.codegen.JsonCodegenHelpers");
    private static final ClassDesc CD_SerializationException = ClassDesc.of(
            "software.amazon.smithy.java.core.serde.SerializationException");
    private static final ClassDesc CD_BigInteger = ClassDesc.of("java.math.BigInteger");
    private static final ClassDesc CD_BigDecimal = ClassDesc.of("java.math.BigDecimal");
    private static final ClassDesc CD_Instant = ClassDesc.of("java.time.Instant");
    private static final ClassDesc CD_ByteBuffer = ClassDesc.of("java.nio.ByteBuffer");
    private static final ClassDesc CD_ArrayList = ClassDesc.of("java.util.ArrayList");
    private static final ClassDesc CD_LinkedHashMap = ClassDesc.of("java.util.LinkedHashMap");
    private static final ClassDesc CD_List = ClassDesc.of("java.util.List");
    private static final ClassDesc CD_Map = ClassDesc.of("java.util.Map");
    private static final ClassDesc CD_Arrays = ClassDesc.of("java.util.Arrays");
    private static final ClassDesc CD_Charset = ClassDesc.of("java.nio.charset.StandardCharsets");
    private static final ClassDesc CD_SmithyEnum = ClassDesc.of(
            "software.amazon.smithy.java.core.schema.SmithyEnum");
    private static final ClassDesc CD_Document = ClassDesc.of(
            "software.amazon.smithy.java.core.serde.document.Document");
    private static final ClassDesc CD_System = ClassDesc.of("java.lang.System");
    private static final ClassDesc CD_ErrorCorrection = ClassDesc.of(
            "software.amazon.smithy.java.core.schema.ShapeBuilder");

    // Slot assignments: 0=this, 1=Object ctxObj, 2=ShapeBuilder builderObj
    private static final int SLOT_CTX = 3;
    private static final int SLOT_BUILDER = 4;
    private static final int SLOT_BUF = 5;
    private static final int SLOT_POS = 6;
    private static final int SLOT_END = 7;
    private static final int SLOT_EXPECTED_NEXT = 8;
    private static final int SLOT_FIRST = 9;
    private static final int SLOT_MATCHED = 10;
    private static final int SLOT_FIRST_TEMP = 11;

    private int nextTempSlot;
    private final Map<String, Integer> nestedDeCacheIndices = new LinkedHashMap<>();
    private int nextCacheIndex;

    GenerationResult generate(StructCodePlan plan, String className, String packageName) {
        nextTempSlot = SLOT_FIRST_TEMP;
        nestedDeCacheIndices.clear();
        nextCacheIndex = 0;

        collectNestedStructTypes(plan);

        ClassDesc thisClass = ClassDesc.of(packageName + "." + className);
        ClassDesc shapeClass = ClassDesc.of(plan.shapeClass().getName());

        List<byte[]> fieldNameBytesList = new ArrayList<>();
        for (FieldPlan f : plan.fields()) {
            fieldNameBytesList.add(f.memberName().getBytes(StandardCharsets.UTF_8));
        }

        byte[] bytecode = ClassFile.of().build(thisClass, cb -> {
            cb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL);
            cb.withInterfaceSymbols(CD_GeneratedStructDeserializer);

            // Static fields for field name bytes
            for (int i = 0; i < fieldNameBytesList.size(); i++) {
                cb.withField("DN_" + i,
                        CD_byte_array,
                        ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            }

            // Nested deserializer cache fields
            ClassDesc objArrayDesc = CD_Object.arrayType();
            for (var entry : nestedDeCacheIndices.entrySet()) {
                cb.withField("_DE_" + entry.getValue(),
                        objArrayDesc,
                        ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
            }

            emitClassInit(cb, thisClass, fieldNameBytesList);
            emitConstructor(cb, thisClass);
            emitDeserializeMethod(cb, thisClass, shapeClass, plan, fieldNameBytesList);
        });

        return new GenerationResult(bytecode, fieldNameBytesList);
    }

    private void emitClassInit(
            java.lang.classfile.ClassBuilder cb,
            ClassDesc thisClass,
            List<byte[]> fieldNameBytesList
    ) {
        ClassDesc CD_MethodHandles = ClassDesc.of("java.lang.invoke.MethodHandles");
        ClassDesc CD_List_cls = ClassDesc.of("java.util.List");
        ClassDesc objArrayDesc = CD_Object.arrayType();

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
                        code.putstatic(thisClass, "DN_" + i, CD_byte_array);
                    }

                    for (var entry : nestedDeCacheIndices.entrySet()) {
                        code.iconst_2();
                        code.anewarray(CD_Object);
                        code.putstatic(thisClass, "_DE_" + entry.getValue(), objArrayDesc);
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

    private void emitDeserializeMethod(
            java.lang.classfile.ClassBuilder cb,
            ClassDesc thisClass,
            ClassDesc shapeClass,
            StructCodePlan plan,
            List<byte[]> fieldNameBytesList
    ) {
        ClassDesc builderClass = ClassDesc.of(plan.shapeClass().getName() + "$Builder");

        cb.withMethodBody("deserialize",
                MethodTypeDesc.of(CD_SerializableStruct, CD_Object, CD_ShapeBuilder),
                ClassFile.ACC_PUBLIC,
                code -> {
                    nextTempSlot = SLOT_FIRST_TEMP;

                    // Cast parameters
                    code.aload(1);
                    code.checkcast(CD_JsonReaderContext);
                    code.astore(SLOT_CTX);
                    code.aload(2);
                    code.checkcast(builderClass);
                    code.astore(SLOT_BUILDER);

                    // Load buf, pos, end from ctx
                    code.aload(SLOT_CTX);
                    code.getfield(CD_JsonReaderContext, "buf", CD_byte_array);
                    code.astore(SLOT_BUF);
                    code.aload(SLOT_CTX);
                    code.getfield(CD_JsonReaderContext, "pos", CD_int);
                    code.istore(SLOT_POS);
                    code.aload(SLOT_CTX);
                    code.getfield(CD_JsonReaderContext, "end", CD_int);
                    code.istore(SLOT_END);

                    // Skip whitespace before {
                    emitSkipWhitespace(code);

                    // Expect '{'
                    Label noOpenBrace = code.newLabel();
                    code.iload(SLOT_POS);
                    code.iload(SLOT_END);
                    code.if_icmpge(noOpenBrace);
                    code.aload(SLOT_BUF);
                    code.iload(SLOT_POS);
                    code.baload();
                    code.bipush('{');
                    Label openBraceOk = code.newLabel();
                    code.if_icmpeq(openBraceOk);
                    code.labelBinding(noOpenBrace);
                    emitThrowExpected(code, "Expected '{' at position ");
                    code.labelBinding(openBraceOk);
                    code.iinc(SLOT_POS, 1);

                    // int expectedNext = 0; boolean first = true;
                    code.iconst_0();
                    code.istore(SLOT_EXPECTED_NEXT);
                    code.iconst_1();
                    code.istore(SLOT_FIRST);

                    // Main loop
                    Label loopStart = code.newLabel();
                    Label loopEnd = code.newLabel();
                    code.labelBinding(loopStart);

                    // Skip whitespace
                    emitSkipWhitespace(code);

                    // Check for '}'
                    Label notCloseBrace = code.newLabel();
                    code.iload(SLOT_POS);
                    code.iload(SLOT_END);
                    code.if_icmpge(notCloseBrace);
                    code.aload(SLOT_BUF);
                    code.iload(SLOT_POS);
                    code.baload();
                    code.bipush('}');
                    code.if_icmpne(notCloseBrace);
                    code.iinc(SLOT_POS, 1);
                    code.goto_(loopEnd);
                    code.labelBinding(notCloseBrace);

                    // Handle comma between fields
                    Label afterComma = code.newLabel();
                    code.iload(SLOT_FIRST);
                    code.ifne(afterComma);
                    // Expect comma
                    Label commaOk = code.newLabel();
                    code.iload(SLOT_POS);
                    code.iload(SLOT_END);
                    code.if_icmpge(afterComma); // lenient
                    code.aload(SLOT_BUF);
                    code.iload(SLOT_POS);
                    code.baload();
                    code.bipush(',');
                    code.if_icmpne(afterComma); // lenient
                    code.iinc(SLOT_POS, 1);
                    emitSkipWhitespace(code);
                    code.labelBinding(afterComma);
                    code.iconst_0();
                    code.istore(SLOT_FIRST);

                    // Expect opening quote for field name
                    Label quoteOk = code.newLabel();
                    code.iload(SLOT_POS);
                    code.iload(SLOT_END);
                    code.if_icmpge(quoteOk); // will fail later
                    code.aload(SLOT_BUF);
                    code.iload(SLOT_POS);
                    code.baload();
                    code.bipush('"');
                    code.if_icmpne(quoteOk); // will fail later
                    code.labelBinding(quoteOk);
                    code.iinc(SLOT_POS, 1); // skip opening quote

                    // matched = -1
                    code.iconst_m1();
                    code.istore(SLOT_MATCHED);

                    List<FieldPlan> fields = plan.fields();

                    // Speculative matching via switch on expectedNext
                    Label afterSpeculative = code.newLabel();
                    emitSpeculativeMatch(code, thisClass, fields, fieldNameBytesList, afterSpeculative);
                    code.labelBinding(afterSpeculative);

                    // Slow path: FNV-1a hash
                    Label afterHash = code.newLabel();
                    code.iload(SLOT_MATCHED);
                    code.iconst_m1();
                    code.if_icmpne(afterHash);
                    emitHashDispatch(code, thisClass, fields, fieldNameBytesList);
                    code.labelBinding(afterHash);

                    // Skip colon
                    emitSkipWhitespace(code);
                    code.iinc(SLOT_POS, 1); // skip ':'
                    emitSkipWhitespace(code);

                    // Check for null
                    Label notNull = code.newLabel();
                    emitNullCheck(code, notNull);
                    code.iinc(SLOT_POS, 4);
                    code.goto_(loopStart);
                    code.labelBinding(notNull);

                    // Dispatch on matched field
                    emitFieldDispatch(code, thisClass, builderClass, fields, plan);

                    code.goto_(loopStart);
                    code.labelBinding(loopEnd);

                    // Write pos back to ctx
                    code.aload(SLOT_CTX);
                    code.iload(SLOT_POS);
                    code.putfield(CD_JsonReaderContext, "pos", CD_int);

                    // return (SerializableStruct) builder.errorCorrection().build()
                    ClassDesc CD_SerializableShape = ClassDesc.of(
                            "software.amazon.smithy.java.core.schema.SerializableShape");
                    code.aload(SLOT_BUILDER);
                    code.invokevirtual(builderClass,
                            "errorCorrection",
                            MethodTypeDesc.of(CD_ShapeBuilder));
                    code.invokeinterface(CD_ShapeBuilder,
                            "build",
                            MethodTypeDesc.of(CD_SerializableShape));
                    code.checkcast(CD_SerializableStruct);
                    code.areturn();
                });
    }

    private void emitSkipWhitespace(CodeBuilder code) {
        Label noWs = code.newLabel();
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.if_icmpge(noWs);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.baload();
        code.bipush(' ');
        code.if_icmpgt(noWs);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.invokestatic(CD_JsonReadUtils,
                "skipWhitespace",
                MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
        code.istore(SLOT_POS);
        code.labelBinding(noWs);
    }

    private void emitThrowExpected(CodeBuilder code, String msg) {
        code.new_(CD_SerializationException);
        code.dup();
        code.ldc(msg);
        code.invokestatic(ClassDesc.of("java.lang.String"),
                "valueOf",
                MethodTypeDesc.of(CD_String, CD_Object));
        code.invokespecial(CD_SerializationException,
                ConstantDescs.INIT_NAME,
                MethodTypeDesc.of(CD_void, CD_String));
        code.athrow();
    }

    private void emitSpeculativeMatch(
            CodeBuilder code,
            ClassDesc thisClass,
            List<FieldPlan> fields,
            List<byte[]> fieldNameBytesList,
            Label afterSpeculative
    ) {
        // switch(expectedNext) { case 0: try DN_0 ... }
        Label defaultLabel = code.newLabel();
        Label[] caseLabels = new Label[fields.size()];
        int[] caseValues = new int[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            caseLabels[i] = code.newLabel();
            caseValues[i] = i;
        }

        code.iload(SLOT_EXPECTED_NEXT);
        // Use lookupswitch for speculative match
        var cases = new java.util.ArrayList<java.lang.classfile.instruction.SwitchCase>();
        for (int i = 0; i < fields.size(); i++) {
            cases.add(java.lang.classfile.instruction.SwitchCase.of(i, caseLabels[i]));
        }
        code.lookupswitch(defaultLabel, cases);

        int matchResultSlot = nextTempSlot++;
        for (int i = 0; i < fields.size(); i++) {
            code.labelBinding(caseLabels[i]);

            // int result = JsonReadUtils.matchFieldName(buf, pos, end, DN_i)
            code.aload(SLOT_BUF);
            code.iload(SLOT_POS);
            code.iload(SLOT_END);
            code.getstatic(thisClass, "DN_" + i, CD_byte_array);
            code.invokestatic(CD_JsonReadUtils,
                    "matchFieldName",
                    MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int, CD_byte_array));
            code.istore(matchResultSlot);
            code.iload(matchResultSlot);
            code.iconst_m1();
            Label noMatch = code.newLabel();
            code.if_icmpeq(noMatch);

            // Match!
            code.ldc(i);
            code.istore(SLOT_MATCHED);
            code.ldc(i + 1);
            code.istore(SLOT_EXPECTED_NEXT);
            code.iload(matchResultSlot);
            code.istore(SLOT_POS);
            code.goto_(afterSpeculative);

            code.labelBinding(noMatch);
            code.goto_(defaultLabel);
        }

        code.labelBinding(defaultLabel);
        // Fall through: matched stays -1
    }

    private void emitHashDispatch(
            CodeBuilder code,
            ClassDesc thisClass,
            List<FieldPlan> fields,
            List<byte[]> fieldNameBytesList
    ) {
        int nameStartSlot = nextTempSlot++;
        int nameEndSlot = nextTempSlot++;
        int nameLenSlot = nextTempSlot++;
        int hashSlot = nextTempSlot++;

        // int nameStart = pos
        code.iload(SLOT_POS);
        code.istore(nameStartSlot);

        // Scan to closing quote
        Label scanLoop = code.newLabel();
        Label scanEnd = code.newLabel();
        code.labelBinding(scanLoop);
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.if_icmpge(scanEnd);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.baload();
        code.bipush('"');
        code.if_icmpeq(scanEnd);
        // Handle escape
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.baload();
        code.bipush('\\');
        Label notEscape = code.newLabel();
        code.if_icmpne(notEscape);
        code.iinc(SLOT_POS, 1); // skip escaped char
        code.labelBinding(notEscape);
        code.iinc(SLOT_POS, 1);
        code.goto_(scanLoop);
        code.labelBinding(scanEnd);

        // int nameEnd = pos; pos++ (skip closing quote)
        code.iload(SLOT_POS);
        code.istore(nameEndSlot);
        code.iinc(SLOT_POS, 1);

        // int nameLen = nameEnd - nameStart
        code.iload(nameEndSlot);
        code.iload(nameStartSlot);
        code.isub();
        code.istore(nameLenSlot);

        // Compute FNV-1a hash
        code.ldc(0x811c9dc5);
        code.istore(hashSlot);
        int hSlot = nextTempSlot++;
        code.iload(nameStartSlot);
        code.istore(hSlot);
        Label hashLoop = code.newLabel();
        Label hashEnd = code.newLabel();
        code.labelBinding(hashLoop);
        code.iload(hSlot);
        code.iload(nameEndSlot);
        code.if_icmpge(hashEnd);
        code.iload(hashSlot);
        code.aload(SLOT_BUF);
        code.iload(hSlot);
        code.baload();
        code.sipush(0xFF);
        code.iand();
        code.ixor();
        code.ldc(0x01000193);
        code.imul();
        code.istore(hashSlot);
        code.iinc(hSlot, 1);
        code.goto_(hashLoop);
        code.labelBinding(hashEnd);

        // Build hash -> field indices map
        Map<Integer, List<Integer>> hashToFields = new LinkedHashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            int hash = computeFnv1a(fieldNameBytesList.get(i));
            hashToFields.computeIfAbsent(hash, k -> new ArrayList<>()).add(i);
        }

        // switch(hash) { ... }
        Label switchDefault = code.newLabel();
        var cases = new ArrayList<java.lang.classfile.instruction.SwitchCase>();
        Map<Integer, Label> hashLabels = new LinkedHashMap<>();
        for (int hash : hashToFields.keySet()) {
            Label l = code.newLabel();
            hashLabels.put(hash, l);
            cases.add(java.lang.classfile.instruction.SwitchCase.of(hash, l));
        }

        code.iload(hashSlot);
        code.lookupswitch(switchDefault, cases);

        for (var entry : hashToFields.entrySet()) {
            code.labelBinding(hashLabels.get(entry.getKey()));
            for (int fi : entry.getValue()) {
                // if (nameLen == DN_fi.length && Arrays.equals(...))
                Label noMatch = code.newLabel();
                code.iload(nameLenSlot);
                code.getstatic(thisClass, "DN_" + fi, CD_byte_array);
                code.arraylength();
                code.if_icmpne(noMatch);

                code.aload(SLOT_BUF);
                code.iload(nameStartSlot);
                code.iload(nameEndSlot);
                code.getstatic(thisClass, "DN_" + fi, CD_byte_array);
                code.iconst_0();
                code.getstatic(thisClass, "DN_" + fi, CD_byte_array);
                code.arraylength();
                code.invokestatic(CD_Arrays,
                        "equals",
                        MethodTypeDesc.of(CD_boolean,
                                CD_byte_array,
                                CD_int,
                                CD_int,
                                CD_byte_array,
                                CD_int,
                                CD_int));
                code.ifeq(noMatch);

                code.ldc(fi);
                code.istore(SLOT_MATCHED);
                code.ldc(fi + 1);
                code.istore(SLOT_EXPECTED_NEXT);
                code.labelBinding(noMatch);
            }
            code.goto_(switchDefault);
        }
        code.labelBinding(switchDefault);
    }

    private void emitNullCheck(CodeBuilder code, Label notNullLabel) {
        // if (pos+4 <= end && buf[pos]=='n' && buf[pos+1]=='u' && buf[pos+2]=='l' && buf[pos+3]=='l')
        code.iload(SLOT_POS);
        code.iconst_4();
        code.iadd();
        code.iload(SLOT_END);
        code.if_icmpgt(notNullLabel);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.baload();
        code.bipush('n');
        code.if_icmpne(notNullLabel);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.iconst_1();
        code.iadd();
        code.baload();
        code.bipush('u');
        code.if_icmpne(notNullLabel);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.iconst_2();
        code.iadd();
        code.baload();
        code.bipush('l');
        code.if_icmpne(notNullLabel);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.iconst_3();
        code.iadd();
        code.baload();
        code.bipush('l');
        code.if_icmpne(notNullLabel);
    }

    private void emitFieldDispatch(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc builderClass,
            List<FieldPlan> fields,
            StructCodePlan plan
    ) {
        Class<?> actualBuilderClass;
        try {
            actualBuilderClass = Class.forName(plan.shapeClass().getName() + "$Builder");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        Label defaultLabel = code.newLabel();
        var cases = new ArrayList<java.lang.classfile.instruction.SwitchCase>();
        Label[] fieldLabels = new Label[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            fieldLabels[i] = code.newLabel();
            cases.add(java.lang.classfile.instruction.SwitchCase.of(i, fieldLabels[i]));
        }

        Label afterSwitch = code.newLabel();
        code.iload(SLOT_MATCHED);
        code.lookupswitch(defaultLabel, cases);

        for (int i = 0; i < fields.size(); i++) {
            code.labelBinding(fieldLabels[i]);
            emitFieldDeserialization(code, thisClass, builderClass, fields.get(i), plan, actualBuilderClass);
            code.goto_(afterSwitch);
        }

        code.labelBinding(defaultLabel);
        // Skip unknown field value
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.aload(SLOT_CTX);
        code.invokestatic(CD_JsonReadUtils,
                "skipValue",
                MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int, CD_JsonParseState));
        code.istore(SLOT_POS);

        code.labelBinding(afterSwitch);
    }

    private void emitFieldDeserialization(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc builderClass,
            FieldPlan field,
            StructCodePlan plan,
            Class<?> actualBuilderClass
    ) {
        String setter = field.memberName();
        ClassDesc setterParam = resolveSetterParamType(actualBuilderClass, setter);
        switch (field.category()) {
            case BOOLEAN -> {
                int bSlot = nextTempSlot++;
                code.iload(SLOT_POS);
                code.iconst_4();
                code.iadd();
                code.iload(SLOT_END);
                Label notTrue = code.newLabel();
                Label afterBool = code.newLabel();
                code.if_icmpgt(notTrue);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.baload();
                code.bipush('t');
                code.if_icmpne(notTrue);
                code.iconst_1();
                code.istore(bSlot);
                code.iinc(SLOT_POS, 4);
                code.goto_(afterBool);
                code.labelBinding(notTrue);
                code.iconst_0();
                code.istore(bSlot);
                code.iinc(SLOT_POS, 5);
                code.labelBinding(afterBool);
                code.aload(SLOT_BUILDER);
                code.iload(bSlot);
                emitBoxIfNeeded(code, FieldCategory.BOOLEAN, setterParam);
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, setterParam));
                code.pop();
            }
            case BYTE -> {
                emitParseLong(code);
                code.aload(SLOT_BUILDER);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedLong", CD_long);
                code.l2i();
                code.i2b();
                emitBoxIfNeeded(code, FieldCategory.BYTE, setterParam);
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, setterParam));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case SHORT -> {
                emitParseLong(code);
                code.aload(SLOT_BUILDER);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedLong", CD_long);
                code.l2i();
                code.i2s();
                emitBoxIfNeeded(code, FieldCategory.SHORT, setterParam);
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, setterParam));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case INTEGER -> {
                emitParseLong(code);
                code.aload(SLOT_BUILDER);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedLong", CD_long);
                code.l2i();
                emitBoxIfNeeded(code, FieldCategory.INTEGER, setterParam);
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, setterParam));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case LONG -> {
                emitParseLong(code);
                code.aload(SLOT_BUILDER);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedLong", CD_long);
                emitBoxIfNeeded(code, FieldCategory.LONG, setterParam);
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, setterParam));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case FLOAT -> {
                emitParseDouble(code);
                code.aload(SLOT_BUILDER);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedDouble", CD_double);
                code.d2f();
                emitBoxIfNeeded(code, FieldCategory.FLOAT, setterParam);
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, setterParam));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case DOUBLE -> {
                emitParseDouble(code);
                code.aload(SLOT_BUILDER);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedDouble", CD_double);
                emitBoxIfNeeded(code, FieldCategory.DOUBLE, setterParam);
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, setterParam));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case STRING -> {
                emitParseString(code);
                code.aload(SLOT_BUILDER);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedString", CD_String);
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, setterParam));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case BLOB -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.iload(SLOT_END);
                code.aload(SLOT_CTX);
                code.invokestatic(CD_JsonReadUtils,
                        "decodeBase64String",
                        MethodTypeDesc.of(CD_byte_array, CD_byte_array, CD_int, CD_int, CD_JsonParseState));
                int decodedSlot = nextTempSlot++;
                code.astore(decodedSlot);
                code.aload(SLOT_BUILDER);
                code.aload(decodedSlot);
                code.invokestatic(CD_ByteBuffer,
                        "wrap",
                        MethodTypeDesc.of(CD_ByteBuffer, CD_byte_array));
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, CD_ByteBuffer));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case BIG_INTEGER -> {
                int startSlot = nextTempSlot++;
                code.iload(SLOT_POS);
                code.istore(startSlot);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.iload(SLOT_END);
                code.invokestatic(CD_JsonReadUtils,
                        "findNumberEnd",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
                code.aload(SLOT_BUILDER);
                code.new_(CD_BigInteger);
                code.dup();
                code.new_(CD_String);
                code.dup();
                code.aload(SLOT_BUF);
                code.iload(startSlot);
                code.iload(SLOT_POS);
                code.iload(startSlot);
                code.isub();
                ClassDesc CD_Charset_cls = ClassDesc.of("java.nio.charset.Charset");
                code.getstatic(ClassDesc.of("java.nio.charset.StandardCharsets"), "US_ASCII", CD_Charset_cls);
                code.invokespecial(CD_String,
                        ConstantDescs.INIT_NAME,
                        MethodTypeDesc.of(CD_void, CD_byte_array, CD_int, CD_int, CD_Charset_cls));
                code.invokespecial(CD_BigInteger,
                        ConstantDescs.INIT_NAME,
                        MethodTypeDesc.of(CD_void, CD_String));
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, CD_BigInteger));
                code.pop();
            }
            case BIG_DECIMAL -> {
                int startSlot = nextTempSlot++;
                code.iload(SLOT_POS);
                code.istore(startSlot);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.iload(SLOT_END);
                code.invokestatic(CD_JsonReadUtils,
                        "findNumberEnd",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int));
                code.istore(SLOT_POS);
                code.aload(SLOT_BUILDER);
                code.new_(CD_BigDecimal);
                code.dup();
                code.new_(CD_String);
                code.dup();
                code.aload(SLOT_BUF);
                code.iload(startSlot);
                code.iload(SLOT_POS);
                code.iload(startSlot);
                code.isub();
                ClassDesc CD_Charset_cls2 = ClassDesc.of("java.nio.charset.Charset");
                code.getstatic(ClassDesc.of("java.nio.charset.StandardCharsets"), "US_ASCII", CD_Charset_cls2);
                code.invokespecial(CD_String,
                        ConstantDescs.INIT_NAME,
                        MethodTypeDesc.of(CD_void, CD_byte_array, CD_int, CD_int, CD_Charset_cls2));
                code.invokespecial(CD_BigDecimal,
                        ConstantDescs.INIT_NAME,
                        MethodTypeDesc.of(CD_void, CD_String));
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, CD_BigDecimal));
                code.pop();
            }
            case TIMESTAMP -> emitTimestampDeserialization(code, thisClass, builderClass, field);
            case LIST -> emitListDeserialization(code, thisClass, builderClass, field, plan);
            case MAP -> emitMapDeserialization(code, thisClass, builderClass, field, plan);
            case ENUM_STRING -> {
                emitParseString(code);
                ClassDesc enumClass = ClassDesc.of(field.schema().memberTarget().shapeClass().getName());
                code.aload(SLOT_BUILDER);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedString", CD_String);
                code.invokestatic(enumClass,
                        "from",
                        MethodTypeDesc.of(enumClass, CD_String));
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, enumClass));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case INT_ENUM -> {
                emitParseLong(code);
                ClassDesc enumClass = ClassDesc.of(field.schema().memberTarget().shapeClass().getName());
                code.aload(SLOT_BUILDER);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedLong", CD_long);
                code.l2i();
                code.invokestatic(enumClass,
                        "from",
                        MethodTypeDesc.of(enumClass, CD_int));
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, enumClass));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case STRUCT, UNION -> {
                Class<?> targetClass = field.schema().memberTarget().shapeClass();
                ClassDesc targetDesc = ClassDesc.of(targetClass.getName());
                code.aload(SLOT_CTX);
                code.iload(SLOT_POS);
                code.putfield(CD_JsonReaderContext, "pos", CD_int);
                code.aload(SLOT_BUILDER);
                emitDeserializeNestedCall(code, thisClass, targetClass);
                code.checkcast(targetDesc);
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, targetDesc));
                code.pop();
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "pos", CD_int);
                code.istore(SLOT_POS);
            }
            case DOCUMENT -> {
                code.aload(SLOT_CTX);
                code.iload(SLOT_POS);
                code.putfield(CD_JsonReaderContext, "pos", CD_int);
                code.aload(SLOT_BUILDER);
                code.aload(SLOT_CTX);
                code.invokestatic(CD_JsonCodegenHelpers,
                        "deserializeDocument",
                        MethodTypeDesc.of(CD_Object, CD_JsonReaderContext));
                code.checkcast(CD_Document);
                code.invokevirtual(builderClass,
                        setter,
                        MethodTypeDesc.of(builderClass, CD_Document));
                code.pop();
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "pos", CD_int);
                code.istore(SLOT_POS);
            }
            default -> {
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.iload(SLOT_END);
                code.aload(SLOT_CTX);
                code.invokestatic(CD_JsonReadUtils,
                        "skipValue",
                        MethodTypeDesc.of(CD_int, CD_byte_array, CD_int, CD_int, CD_JsonParseState));
                code.istore(SLOT_POS);
            }
        }
    }

    private void emitParseLong(CodeBuilder code) {
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.aload(SLOT_CTX);
        code.invokestatic(CD_JsonReadUtils,
                "parseLong",
                MethodTypeDesc.of(CD_void, CD_byte_array, CD_int, CD_int, CD_JsonParseState));
    }

    private void emitParseDouble(CodeBuilder code) {
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.aload(SLOT_CTX);
        code.invokestatic(CD_JsonReadUtils,
                "parseDouble",
                MethodTypeDesc.of(CD_void, CD_byte_array, CD_int, CD_int, CD_JsonParseState));
    }

    private void emitParseString(CodeBuilder code) {
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.aload(SLOT_CTX);
        code.invokestatic(CD_JsonReadUtils,
                "parseString",
                MethodTypeDesc.of(CD_void, CD_byte_array, CD_int, CD_int, CD_JsonParseState));
    }

    private void emitUpdatePosFromParsedEndPos(CodeBuilder code) {
        code.aload(SLOT_CTX);
        code.getfield(CD_JsonReaderContext, "parsedEndPos", CD_int);
        code.istore(SLOT_POS);
    }

    private void emitTimestampDeserialization(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc builderClass,
            FieldPlan field
    ) {
        String setter = field.memberName();
        String format = field.timestampFormat();
        if (format == null || "EPOCH_SECONDS".equals(format)) {
            code.aload(SLOT_BUF);
            code.iload(SLOT_POS);
            code.iload(SLOT_END);
            code.aload(SLOT_CTX);
            code.invokestatic(CD_JsonCodegenHelpers,
                    "parseEpochSeconds",
                    MethodTypeDesc.of(CD_Instant, CD_byte_array, CD_int, CD_int, CD_JsonParseState));
            int tsSlot = nextTempSlot++;
            code.astore(tsSlot);
            emitUpdatePosFromParsedEndPos(code);
            code.aload(SLOT_BUILDER);
            code.aload(tsSlot);
            code.invokevirtual(builderClass,
                    setter,
                    MethodTypeDesc.of(builderClass, CD_Instant));
            code.pop();
        } else {
            // For HTTP_DATE and date-time: check if numeric first, fallback to string parse
            Label isString = code.newLabel();
            Label afterTs = code.newLabel();

            code.iload(SLOT_POS);
            code.iload(SLOT_END);
            code.if_icmpge(isString);
            code.aload(SLOT_BUF);
            code.iload(SLOT_POS);
            code.baload();
            int bSlot = nextTempSlot++;
            code.istore(bSlot);
            code.iload(bSlot);
            code.bipush('-');
            code.if_icmpeq(isString); // negative number
            code.iload(bSlot);
            code.bipush('0');
            Label checkDigitEnd = code.newLabel();
            code.if_icmplt(isString);
            code.iload(bSlot);
            code.bipush('9');
            code.if_icmpgt(isString);

            // Numeric: parse as epoch seconds
            code.aload(SLOT_BUF);
            code.iload(SLOT_POS);
            code.iload(SLOT_END);
            code.aload(SLOT_CTX);
            code.invokestatic(CD_JsonCodegenHelpers,
                    "parseEpochSeconds",
                    MethodTypeDesc.of(CD_Instant, CD_byte_array, CD_int, CD_int, CD_JsonParseState));
            int ts2Slot = nextTempSlot++;
            code.astore(ts2Slot);
            emitUpdatePosFromParsedEndPos(code);
            code.aload(SLOT_BUILDER);
            code.aload(ts2Slot);
            code.invokevirtual(builderClass,
                    setter,
                    MethodTypeDesc.of(builderClass, CD_Instant));
            code.pop();
            code.goto_(afterTs);

            code.labelBinding(isString);
            // String: parse as string, then Instant.parse
            emitParseString(code);
            emitUpdatePosFromParsedEndPos(code);
            code.aload(SLOT_BUILDER);
            code.aload(SLOT_CTX);
            code.getfield(CD_JsonReaderContext, "parsedString", CD_String);
            code.invokestatic(CD_Instant,
                    "parse",
                    MethodTypeDesc.of(CD_Instant, ClassDesc.of("java.lang.CharSequence")));
            code.invokevirtual(builderClass,
                    setter,
                    MethodTypeDesc.of(builderClass, CD_Instant));
            code.pop();

            code.labelBinding(afterTs);
        }
    }

    private void emitListDeserialization(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc builderClass,
            FieldPlan field,
            StructCodePlan plan
    ) {
        String setter = field.memberName();
        FieldCategory elemCategory = resolveElementCategory(field);
        int listSlot = nextTempSlot++;

        code.new_(CD_ArrayList);
        code.dup();
        code.iconst_4();
        code.invokespecial(CD_ArrayList,
                ConstantDescs.INIT_NAME,
                MethodTypeDesc.of(CD_void, CD_int));
        code.astore(listSlot);

        // Expect '['
        Label afterList = code.newLabel();
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.if_icmpge(afterList);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.baload();
        code.bipush('[');
        code.if_icmpne(afterList);
        code.iinc(SLOT_POS, 1);
        emitSkipWhitespace(code);

        // Check for empty array
        Label notEmpty = code.newLabel();
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.if_icmpge(notEmpty);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.baload();
        code.bipush(']');
        Label isEmpty = code.newLabel();
        code.if_icmpeq(isEmpty);
        code.labelBinding(notEmpty);

        // Element loop
        Label elemLoop = code.newLabel();
        Label elemEnd = code.newLabel();
        code.labelBinding(elemLoop);
        emitSkipWhitespace(code);

        // Check null
        Label elemNotNull = code.newLabel();
        emitNullCheck(code, elemNotNull);
        code.aload(listSlot);
        code.aconst_null();
        code.invokevirtual(CD_ArrayList, "add", MethodTypeDesc.of(CD_boolean, CD_Object));
        code.pop();
        code.iinc(SLOT_POS, 4);
        Label afterElem = code.newLabel();
        code.goto_(afterElem);

        code.labelBinding(elemNotNull);
        emitElementDeserialization(code, thisClass, elemCategory, field, listSlot);

        code.labelBinding(afterElem);
        emitSkipWhitespace(code);
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.if_icmpge(elemEnd);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.baload();
        code.bipush(']');
        code.if_icmpeq(elemEnd);
        code.iinc(SLOT_POS, 1); // skip comma
        code.goto_(elemLoop);

        code.labelBinding(elemEnd);
        code.labelBinding(isEmpty);
        code.iinc(SLOT_POS, 1); // skip ]

        code.labelBinding(afterList);
        code.aload(SLOT_BUILDER);
        code.aload(listSlot);
        code.invokevirtual(builderClass,
                setter,
                MethodTypeDesc.of(builderClass, CD_List));
        code.pop();
    }

    private void emitElementDeserialization(
            CodeBuilder code,
            ClassDesc thisClass,
            FieldCategory category,
            FieldPlan field,
            int listSlot
    ) {
        switch (category) {
            case BOOLEAN -> {
                int bSlot = nextTempSlot++;
                Label notTrue = code.newLabel();
                Label afterBool = code.newLabel();
                code.iload(SLOT_POS);
                code.iconst_4();
                code.iadd();
                code.iload(SLOT_END);
                code.if_icmpgt(notTrue);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.baload();
                code.bipush('t');
                code.if_icmpne(notTrue);
                code.iconst_1();
                code.istore(bSlot);
                code.iinc(SLOT_POS, 4);
                code.goto_(afterBool);
                code.labelBinding(notTrue);
                code.iconst_0();
                code.istore(bSlot);
                code.iinc(SLOT_POS, 5);
                code.labelBinding(afterBool);
                code.aload(listSlot);
                code.iload(bSlot);
                code.invokestatic(ClassDesc.of("java.lang.Boolean"),
                        "valueOf",
                        MethodTypeDesc.of(ClassDesc.of("java.lang.Boolean"), CD_boolean));
                code.invokevirtual(CD_ArrayList, "add", MethodTypeDesc.of(CD_boolean, CD_Object));
                code.pop();
            }
            case BYTE -> {
                emitParseLong(code);
                code.aload(listSlot);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedLong", CD_long);
                code.l2i();
                code.i2b();
                code.invokestatic(ClassDesc.of("java.lang.Byte"),
                        "valueOf",
                        MethodTypeDesc.of(ClassDesc.of("java.lang.Byte"), CD_byte));
                code.invokevirtual(CD_ArrayList, "add", MethodTypeDesc.of(CD_boolean, CD_Object));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case SHORT -> {
                emitParseLong(code);
                code.aload(listSlot);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedLong", CD_long);
                code.l2i();
                code.i2s();
                code.invokestatic(ClassDesc.of("java.lang.Short"),
                        "valueOf",
                        MethodTypeDesc.of(ClassDesc.of("java.lang.Short"), CD_short));
                code.invokevirtual(CD_ArrayList, "add", MethodTypeDesc.of(CD_boolean, CD_Object));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case INTEGER -> {
                emitParseLong(code);
                code.aload(listSlot);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedLong", CD_long);
                code.l2i();
                code.invokestatic(ClassDesc.of("java.lang.Integer"),
                        "valueOf",
                        MethodTypeDesc.of(ClassDesc.of("java.lang.Integer"), CD_int));
                code.invokevirtual(CD_ArrayList, "add", MethodTypeDesc.of(CD_boolean, CD_Object));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case LONG -> {
                emitParseLong(code);
                code.aload(listSlot);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedLong", CD_long);
                code.invokestatic(ClassDesc.of("java.lang.Long"),
                        "valueOf",
                        MethodTypeDesc.of(ClassDesc.of("java.lang.Long"), CD_long));
                code.invokevirtual(CD_ArrayList, "add", MethodTypeDesc.of(CD_boolean, CD_Object));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case FLOAT -> {
                emitParseDouble(code);
                code.aload(listSlot);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedDouble", CD_double);
                code.d2f();
                code.invokestatic(ClassDesc.of("java.lang.Float"),
                        "valueOf",
                        MethodTypeDesc.of(ClassDesc.of("java.lang.Float"), CD_float));
                code.invokevirtual(CD_ArrayList, "add", MethodTypeDesc.of(CD_boolean, CD_Object));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case DOUBLE -> {
                emitParseDouble(code);
                code.aload(listSlot);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedDouble", CD_double);
                code.invokestatic(ClassDesc.of("java.lang.Double"),
                        "valueOf",
                        MethodTypeDesc.of(ClassDesc.of("java.lang.Double"), CD_double));
                code.invokevirtual(CD_ArrayList, "add", MethodTypeDesc.of(CD_boolean, CD_Object));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case STRING -> {
                emitParseString(code);
                code.aload(listSlot);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedString", CD_String);
                code.invokevirtual(CD_ArrayList, "add", MethodTypeDesc.of(CD_boolean, CD_Object));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case ENUM_STRING -> {
                emitParseString(code);
                ClassDesc enumClass = ClassDesc.of(field.elementClass().getName());
                code.aload(listSlot);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedString", CD_String);
                code.invokestatic(enumClass,
                        "from",
                        MethodTypeDesc.of(enumClass, CD_String));
                code.invokevirtual(CD_ArrayList, "add", MethodTypeDesc.of(CD_boolean, CD_Object));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            default -> {
                // Complex: nested struct
                code.aload(SLOT_CTX);
                code.iload(SLOT_POS);
                code.putfield(CD_JsonReaderContext, "pos", CD_int);
                code.aload(listSlot);
                emitDeserializeNestedCall(code, thisClass, field.elementClass());
                code.invokevirtual(CD_ArrayList, "add", MethodTypeDesc.of(CD_boolean, CD_Object));
                code.pop();
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "pos", CD_int);
                code.istore(SLOT_POS);
            }
        }
    }

    private void emitMapDeserialization(
            CodeBuilder code,
            ClassDesc thisClass,
            ClassDesc builderClass,
            FieldPlan field,
            StructCodePlan plan
    ) {
        String setter = field.memberName();
        FieldCategory valCategory = resolveMapValueCategory(field);
        int mapSlot = nextTempSlot++;

        code.new_(CD_LinkedHashMap);
        code.dup();
        code.iconst_4();
        code.invokespecial(CD_LinkedHashMap,
                ConstantDescs.INIT_NAME,
                MethodTypeDesc.of(CD_void, CD_int));
        code.astore(mapSlot);

        Label afterMap = code.newLabel();
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.if_icmpge(afterMap);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.baload();
        code.bipush('{');
        code.if_icmpne(afterMap);
        code.iinc(SLOT_POS, 1);
        emitSkipWhitespace(code);

        Label notEmpty = code.newLabel();
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.if_icmpge(notEmpty);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.baload();
        code.bipush('}');
        Label mapEmpty = code.newLabel();
        code.if_icmpeq(mapEmpty);
        code.labelBinding(notEmpty);

        Label mapLoop = code.newLabel();
        Label mapEnd = code.newLabel();
        code.labelBinding(mapLoop);
        emitSkipWhitespace(code);

        // Parse key
        emitParseString(code);
        int keySlot = nextTempSlot++;
        code.aload(SLOT_CTX);
        code.getfield(CD_JsonReaderContext, "parsedString", CD_String);
        code.astore(keySlot);
        emitUpdatePosFromParsedEndPos(code);

        // Skip colon
        emitSkipWhitespace(code);
        code.iinc(SLOT_POS, 1);
        emitSkipWhitespace(code);

        // Check null
        Label valNotNull = code.newLabel();
        emitNullCheck(code, valNotNull);
        code.aload(mapSlot);
        code.aload(keySlot);
        code.aconst_null();
        code.invokevirtual(CD_LinkedHashMap,
                "put",
                MethodTypeDesc.of(CD_Object, CD_Object, CD_Object));
        code.pop();
        code.iinc(SLOT_POS, 4);
        Label afterVal = code.newLabel();
        code.goto_(afterVal);

        code.labelBinding(valNotNull);
        emitMapValueDeserialization(code, thisClass, valCategory, field, mapSlot, keySlot);

        code.labelBinding(afterVal);
        emitSkipWhitespace(code);
        code.iload(SLOT_POS);
        code.iload(SLOT_END);
        code.if_icmpge(mapEnd);
        code.aload(SLOT_BUF);
        code.iload(SLOT_POS);
        code.baload();
        code.bipush('}');
        code.if_icmpeq(mapEnd);
        code.iinc(SLOT_POS, 1); // skip comma
        code.goto_(mapLoop);

        code.labelBinding(mapEnd);
        code.labelBinding(mapEmpty);
        code.iinc(SLOT_POS, 1); // skip }

        code.labelBinding(afterMap);
        code.aload(SLOT_BUILDER);
        code.aload(mapSlot);
        code.invokevirtual(builderClass,
                setter,
                MethodTypeDesc.of(builderClass, CD_Map));
        code.pop();
    }

    private void emitMapValueDeserialization(
            CodeBuilder code,
            ClassDesc thisClass,
            FieldCategory category,
            FieldPlan field,
            int mapSlot,
            int keySlot
    ) {
        switch (category) {
            case STRING -> {
                emitParseString(code);
                code.aload(mapSlot);
                code.aload(keySlot);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedString", CD_String);
                code.invokevirtual(CD_LinkedHashMap,
                        "put",
                        MethodTypeDesc.of(CD_Object, CD_Object, CD_Object));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case INTEGER -> {
                emitParseLong(code);
                code.aload(mapSlot);
                code.aload(keySlot);
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "parsedLong", CD_long);
                code.l2i();
                code.invokestatic(ClassDesc.of("java.lang.Integer"),
                        "valueOf",
                        MethodTypeDesc.of(ClassDesc.of("java.lang.Integer"), CD_int));
                code.invokevirtual(CD_LinkedHashMap,
                        "put",
                        MethodTypeDesc.of(CD_Object, CD_Object, CD_Object));
                code.pop();
                emitUpdatePosFromParsedEndPos(code);
            }
            case BOOLEAN -> {
                int bSlot = nextTempSlot++;
                Label notTrue = code.newLabel();
                Label afterBool = code.newLabel();
                code.iload(SLOT_POS);
                code.iconst_4();
                code.iadd();
                code.iload(SLOT_END);
                code.if_icmpgt(notTrue);
                code.aload(SLOT_BUF);
                code.iload(SLOT_POS);
                code.baload();
                code.bipush('t');
                code.if_icmpne(notTrue);
                code.iconst_1();
                code.istore(bSlot);
                code.iinc(SLOT_POS, 4);
                code.goto_(afterBool);
                code.labelBinding(notTrue);
                code.iconst_0();
                code.istore(bSlot);
                code.iinc(SLOT_POS, 5);
                code.labelBinding(afterBool);
                code.aload(mapSlot);
                code.aload(keySlot);
                code.iload(bSlot);
                code.invokestatic(ClassDesc.of("java.lang.Boolean"),
                        "valueOf",
                        MethodTypeDesc.of(ClassDesc.of("java.lang.Boolean"), CD_boolean));
                code.invokevirtual(CD_LinkedHashMap,
                        "put",
                        MethodTypeDesc.of(CD_Object, CD_Object, CD_Object));
                code.pop();
            }
            default -> {
                // Complex value: nested struct
                code.aload(SLOT_CTX);
                code.iload(SLOT_POS);
                code.putfield(CD_JsonReaderContext, "pos", CD_int);
                code.aload(mapSlot);
                code.aload(keySlot);
                emitDeserializeNestedCall(code, thisClass, field.mapValueClass());
                code.invokevirtual(CD_LinkedHashMap,
                        "put",
                        MethodTypeDesc.of(CD_Object, CD_Object, CD_Object));
                code.pop();
                code.aload(SLOT_CTX);
                code.getfield(CD_JsonReaderContext, "pos", CD_int);
                code.istore(SLOT_POS);
            }
        }
    }

    private void emitDeserializeNestedCall(CodeBuilder code, ClassDesc thisClass, Class<?> targetClass) {
        Integer cacheIdx = targetClass != null ? nestedDeCacheIndices.get(targetClass.getName()) : null;
        ClassDesc objArrayDesc = CD_Object.arrayType();

        if (cacheIdx != null) {
            code.aload(SLOT_CTX);
            code.getstatic(thisClass, "_DE_" + cacheIdx, objArrayDesc);
            code.ldc(ClassDesc.of(targetClass.getName()));
            code.invokestatic(CD_JsonCodegenHelpers,
                    "deserializeNestedStructDirect",
                    MethodTypeDesc.of(CD_Object,
                            CD_JsonReaderContext,
                            objArrayDesc,
                            ClassDesc.of("java.lang.Class")));
        } else {
            code.aload(SLOT_CTX);
            code.ldc(ClassDesc.of(targetClass.getName()));
            code.invokestatic(CD_JsonCodegenHelpers,
                    "deserializeNestedStruct",
                    MethodTypeDesc.of(CD_Object,
                            CD_JsonReaderContext,
                            ClassDesc.of("java.lang.Class")));
        }
    }

    // ---- Helpers ----

    private void collectNestedStructTypes(StructCodePlan plan) {
        for (FieldPlan field : plan.fields()) {
            switch (field.category()) {
                case STRUCT, UNION -> registerNestedDeCache(field.schema().memberTarget().shapeClass());
                case LIST -> {
                    Class<?> elemClass = field.elementClass();
                    if (elemClass != null && !isSimpleType(elemClass)) {
                        registerNestedDeCache(elemClass);
                    }
                }
                case MAP -> {
                    Class<?> valClass = field.mapValueClass();
                    if (valClass != null && !isSimpleType(valClass)) {
                        registerNestedDeCache(valClass);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void registerNestedDeCache(Class<?> clazz) {
        if (clazz != null) {
            nestedDeCacheIndices.computeIfAbsent(clazz.getName(), k -> nextCacheIndex++);
        }
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

    private static FieldCategory resolveElementCategory(FieldPlan field) {
        Class<?> elemClass = field.elementClass();
        if (elemClass == null)
            return FieldCategory.STRING;
        return classToCategory(elemClass);
    }

    private static FieldCategory resolveMapValueCategory(FieldPlan field) {
        Class<?> valClass = field.mapValueClass();
        if (valClass == null)
            return FieldCategory.STRING;
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

    private static ClassDesc resolveSetterParamType(Class<?> builderClass, String setterName) {
        for (java.lang.reflect.Method m : builderClass.getMethods()) {
            if (m.getName().equals(setterName) && m.getParameterCount() == 1) {
                return ClassDesc.ofDescriptor(m.getParameterTypes()[0].descriptorString());
            }
        }
        return CD_Object;
    }

    private static boolean needsBoxing(ClassDesc setterParam) {
        return !setterParam.isPrimitive();
    }

    private static void emitBoxIfNeeded(CodeBuilder code, FieldCategory category, ClassDesc setterParam) {
        if (setterParam.isPrimitive()) {
            return;
        }
        switch (category) {
            case BOOLEAN -> code.invokestatic(ClassDesc.of("java.lang.Boolean"),
                    "valueOf",
                    MethodTypeDesc.of(ClassDesc.of("java.lang.Boolean"), CD_boolean));
            case BYTE -> code.invokestatic(ClassDesc.of("java.lang.Byte"),
                    "valueOf",
                    MethodTypeDesc.of(ClassDesc.of("java.lang.Byte"), CD_byte));
            case SHORT -> code.invokestatic(ClassDesc.of("java.lang.Short"),
                    "valueOf",
                    MethodTypeDesc.of(ClassDesc.of("java.lang.Short"), CD_short));
            case INTEGER -> code.invokestatic(ClassDesc.of("java.lang.Integer"),
                    "valueOf",
                    MethodTypeDesc.of(ClassDesc.of("java.lang.Integer"), CD_int));
            case LONG -> code.invokestatic(ClassDesc.of("java.lang.Long"),
                    "valueOf",
                    MethodTypeDesc.of(ClassDesc.of("java.lang.Long"), CD_long));
            case FLOAT -> code.invokestatic(ClassDesc.of("java.lang.Float"),
                    "valueOf",
                    MethodTypeDesc.of(ClassDesc.of("java.lang.Float"), CD_float));
            case DOUBLE -> code.invokestatic(ClassDesc.of("java.lang.Double"),
                    "valueOf",
                    MethodTypeDesc.of(ClassDesc.of("java.lang.Double"), CD_double));
            default -> {
            }
        }
    }

    private static int computeFnv1a(byte[] data) {
        int hash = 0x811c9dc5;
        for (byte b : data) {
            hash ^= b & 0xFF;
            hash *= 0x01000193;
        }
        return hash;
    }
}
