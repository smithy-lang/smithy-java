/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.awsquery;

import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.emitDefaultConstructor;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.emitThrow;
import static software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodegenBytecode.invoke;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecBackend;
import software.amazon.smithy.java.codecs.commons.internal.codegen.RuntimeCodecPlan;
import software.amazon.smithy.java.codecs.commons.internal.codegen.UnsupportedSchemaException;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.ClassWriter;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Label;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.MethodVisitor;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Opcodes;
import software.amazon.smithy.java.codecs.commons.internal.codegen.classfile.Type;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * Emits a form-urlencoded Query serializer for one shape graph.
 *
 * <p>The interpreted serializer rebuilds every parameter name as it walks: one schema lookup and one
 * prefix push per segment, then a copy of the assembled prefix for each parameter. Almost all of that
 * is static. {@code &Foo.Bar.Baz=} depends only on the shape, so this backend accumulates the static
 * part of the path as it descends and emits it as a single constant {@code byte[]}. Only collection
 * indices and map keys reach the runtime prefix stack.
 *
 * <p>Folding a path into the method means a method is specialized per {@code (shape, path)} pair
 * rather than per shape. A self-recursive structure has no finite set of paths, so specialization is
 * capped; past the cap the caller pushes the accumulated path and calls the unfolded method for that
 * shape, which produces the same bytes by a slower route.
 *
 * <p>Output must be byte-identical to {@link QueryFormSerializer}, including the two places where
 * that serializer's output is arguably wrong: an ISO-8601 timestamp keeps its literal {@code ':'},
 * and a list-member or map key/value name from {@code @xmlName} is not percent-encoded. The
 * compliance tests URL-decode both sides and so cannot see either, but changing the bytes here would
 * be a wire-format change smuggled in as an optimization.
 */
final class QueryRuntimeCodegenBackend implements RuntimeCodecBackend<GeneratedQueryCodec>, Opcodes {
    private static final String CODEC = Type.getInternalName(GeneratedQueryCodec.class);
    private static final String WRITER = Type.getInternalName(QueryFormWriter.class);
    private static final String SERIALIZABLE_SHAPE = Type.getInternalName(SerializableShape.class);
    private static final String SMITHY_ENUM = Type.getInternalName(SmithyEnum.class);
    private static final String SMITHY_INT_ENUM = Type.getInternalName(SmithyIntEnum.class);
    private static final String BYTE_BUFFER = Type.getInternalName(ByteBuffer.class);
    private static final String INSTANT = Type.getInternalName(Instant.class);
    private static final String STRING = "java/lang/String";
    private static final String LIST = "java/util/List";
    private static final String MAP = "java/util/Map";
    private static final String MAP_ENTRY = "java/util/Map$Entry";
    private static final String SET = "java/util/Set";
    private static final String ITERATOR = "java/util/Iterator";
    private static final String CHARSET_DESCRIPTOR = "Ljava/nio/charset/Charset;";

    private static final String LIST_DESCRIPTOR = "(L" + LIST + ";L" + WRITER + ";)V";
    private static final String MAP_DESCRIPTOR = "(L" + MAP + ";L" + WRITER + ";)V";

    /**
     * Resolves names the same way the interpreted path does.
     *
     * <p>Called directly rather than through {@code Schema#getExtension} so that generation does not
     * depend on the provider having been discovered, and so that both paths derive their bytes from
     * one implementation.
     */
    private static final AwsQuerySchemaExtensions EXTENSIONS = new AwsQuerySchemaExtensions();

    /** Ceiling on specialized methods, and on how deep a static path may grow before it unfolds. */
    private static final int MAX_SPECIALIZATIONS = 96;
    private static final int MAX_PATH_DEPTH = 8;

    private final QueryFormSerializer.QueryVariant variant;

    QueryRuntimeCodegenBackend(QueryFormSerializer.QueryVariant variant) {
        this.variant = variant;
    }

    @Override
    public String id() {
        return "awsquery";
    }

    @Override
    public String variant() {
        return variant == QueryFormSerializer.QueryVariant.EC2_QUERY ? "Ec2" : "Aws";
    }

    @Override
    public Class<GeneratedQueryCodec> codecType() {
        return GeneratedQueryCodec.class;
    }

    @Override
    public Class<?> lookupHost() {
        return GeneratedQueryCodec.class;
    }

    @Override
    public Budgets budgets() {
        return new Budgets(220, 300, 8, 8);
    }

    @Override
    public Mode mode() {
        // Query requests are form-urlencoded but Query responses are XML, so there is nothing to read
        // here. WRITE_ONLY also stops the plan rejecting a shape whose builder setters cannot be
        // resolved reflectively, which this backend would never have called.
        return Mode.WRITE_ONLY;
    }

    @Override
    public MemberSelector memberSelector() {
        return MemberSelector.all();
    }

    @Override
    public Emission emit(RuntimeCodecPlan plan, String generatedName) {
        return new Generator(plan, generatedName, variant).generate();
    }

    /** A method to emit, keyed in {@code specializations} by shape and folded path. */
    private interface Task {
        int id();
    }

    private record StructTask(int id, RuntimeCodecPlan.StructPlan structure, String path) implements Task {}

    private record ListTask(int id, Schema member, String path) implements Task {}

    private record MapTask(int id, Schema member, String path) implements Task {}

    /**
     * A call site's resolution: the method to invoke, and the path the caller must push first.
     *
     * <p>{@code push} is null in the folded case, which is all but the recursion fallback.
     */
    private record Target(int method, String push) {}

    @FunctionalInterface
    private interface TaskFactory {
        Task create(int id, String path);
    }

    private static final class Generator {
        private final RuntimeCodecPlan plan;
        private final String className;
        private final QueryFormSerializer.QueryVariant variant;
        private final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        private final Map<String, RuntimeCodecPlan.StructPlan> structuresBySchema = new HashMap<>();

        /** Interned constants, keyed by the latin-1 form of their bytes. */
        private final Map<String, Integer> constants = new LinkedHashMap<>();

        /** Emitted methods, keyed by shape and folded path, so a cyclic graph terminates. */
        private final Map<String, Integer> specializations = new LinkedHashMap<>();

        private final ArrayDeque<Task> pending = new ArrayDeque<>();
        private int nextMethodId;
        private int methodCount;

        Generator(RuntimeCodecPlan plan, String className, QueryFormSerializer.QueryVariant variant) {
            this.plan = plan;
            this.className = className;
            this.variant = variant;
            for (RuntimeCodecPlan.StructPlan structure : plan.structures()) {
                structuresBySchema.put(structure.schema().id().toString(), structure);
            }
        }

        Emission generate() {
            writer.visit(V17, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Object", new String[] {CODEC});

            RuntimeCodecPlan.StructPlan root = plan.rootStructure();
            int rootId = structTarget(root, "").method();
            while (!pending.isEmpty()) {
                Task task = pending.poll();
                if (task instanceof StructTask structTask) {
                    emitStructure(structTask);
                } else if (task instanceof ListTask listTask) {
                    emitList(listTask);
                } else {
                    emitMap((MapTask) task);
                }
            }

            emitEntryPoint(root, rootId);
            emitDefaultConstructor(writer);
            // Constants are discovered while emitting, so the fields and the initializer that fills
            // them can only be written once every method is done.
            emitConstantFields();
            emitClassInitializer();
            writer.visitEnd();
            return new Emission(writer.toByteArray(), methodCount);
        }

        // -- method resolution ------------------------------------------------------------------

        private Target structTarget(RuntimeCodecPlan.StructPlan structure, String path) {
            return target('S', structure.schema().id().toString(), path, (id, p) -> new StructTask(id, structure, p));
        }

        private Target listTarget(Schema member, String path) {
            return target('L', member.id().toString(), path, (id, p) -> new ListTask(id, member, p));
        }

        private Target mapTarget(Schema member, String path) {
            if (variant == QueryFormSerializer.QueryVariant.EC2_QUERY) {
                throw new UnsupportedSchemaException("EC2 Query does not support maps: " + member.id());
            }
            return target('M', member.id().toString(), path, (id, p) -> new MapTask(id, member, p));
        }

        /**
         * Resolves a call to the method for {@code shape} at {@code path}, emitting it if needed.
         *
         * <p>Falls back to the unfolded method once specialization runs out of budget, which is what
         * makes a self-recursive shape terminate. The unfolded form is always allowed, because it is
         * the fallback's target.
         */
        private Target target(char kind, String shape, String path, TaskFactory factory) {
            String key = kind + "|" + shape + "|" + path;
            Integer existing = specializations.get(key);
            if (existing != null) {
                return new Target(existing, null);
            }
            if (path.isEmpty() || (specializations.size() < MAX_SPECIALIZATIONS && depth(path) <= MAX_PATH_DEPTH)) {
                return new Target(enqueue(key, path, factory), null);
            }
            String unfoldedKey = kind + "|" + shape + "|";
            Integer unfolded = specializations.get(unfoldedKey);
            return new Target(unfolded != null ? unfolded : enqueue(unfoldedKey, "", factory), path);
        }

        private int enqueue(String key, String path, TaskFactory factory) {
            int id = nextMethodId++;
            specializations.put(key, id);
            pending.add(factory.create(id, path));
            return id;
        }

        private static int depth(String path) {
            int segments = 1;
            for (int i = 0; i < path.length(); i++) {
                if (path.charAt(i) == '.') {
                    segments++;
                }
            }
            return segments;
        }

        // -- structures and unions --------------------------------------------------------------

        private void emitStructure(StructTask task) {
            RuntimeCodecPlan.StructPlan structure = task.structure();
            if (structure.union()) {
                emitUnion(task);
                return;
            }

            String descriptor = structDescriptor(structure);
            List<RuntimeCodecPlan.MemberPlan> members = structure.members();
            List<RuntimeCodecPlan.MethodRange> chunks = structure.writerChunks();

            if (chunks.size() <= 1) {
                MethodVisitor method = writer.visitMethod(ACC_PRIVATE, methodName(task.id()), descriptor, null, null);
                method.visitCode();
                for (RuntimeCodecPlan.MemberPlan member : members) {
                    emitMember(method, member, task.path());
                }
                endMethod(method);
                return;
            }

            int[] chunkIds = new int[chunks.size()];
            for (int i = 0; i < chunkIds.length; i++) {
                chunkIds[i] = nextMethodId++;
            }

            MethodVisitor dispatch = writer.visitMethod(ACC_PRIVATE, methodName(task.id()), descriptor, null, null);
            dispatch.visitCode();
            for (int chunkId : chunkIds) {
                dispatch.visitVarInsn(ALOAD, 0);
                dispatch.visitVarInsn(ALOAD, 1);
                dispatch.visitVarInsn(ALOAD, 2);
                dispatch.visitMethodInsn(INVOKESPECIAL, className, methodName(chunkId), descriptor, false);
            }
            endMethod(dispatch);

            for (int i = 0; i < chunkIds.length; i++) {
                RuntimeCodecPlan.MethodRange range = chunks.get(i);
                MethodVisitor method = writer.visitMethod(ACC_PRIVATE, methodName(chunkIds[i]), descriptor, null, null);
                method.visitCode();
                for (int m = range.startInclusive(); m < range.endExclusive(); m++) {
                    emitMember(method, members.get(m), task.path());
                }
                endMethod(method);
            }
        }

        private void emitUnion(StructTask task) {
            RuntimeCodecPlan.StructPlan structure = task.structure();
            MethodVisitor method =
                    writer.visitMethod(ACC_PRIVATE, methodName(task.id()), structDescriptor(structure), null, null);
            method.visitCode();
            for (RuntimeCodecPlan.MemberPlan member : structure.members()) {
                Label next = new Label();
                String variantType = Type.getInternalName(member.unionVariant());
                method.visitVarInsn(ALOAD, 1);
                method.visitTypeInsn(INSTANCEOF, variantType);
                method.visitJumpInsn(IFEQ, next);

                Method accessor = member.unionAccessor();
                Class<?> valueType = accessor.getReturnType();
                String path = join(task.path(), queryName(member.schema()));
                if (valueType.isPrimitive()) {
                    emitParam(method, path);
                    method.visitVarInsn(ALOAD, 2);
                    method.visitVarInsn(ALOAD, 1);
                    method.visitTypeInsn(CHECKCAST, variantType);
                    invoke(method, accessor);
                    emitValue(method, member.schema(), valueType);
                } else {
                    method.visitVarInsn(ALOAD, 1);
                    method.visitTypeInsn(CHECKCAST, variantType);
                    invoke(method, accessor);
                    method.visitVarInsn(ASTORE, 3);
                    emitTarget(method, member.schema(), path, 3, valueType);
                }
                method.visitInsn(RETURN);
                method.visitLabel(next);
            }
            // Falls off the end of the instanceof chain only for a variant the plan did not know
            // about, so the throw is the method's terminator; a trailing return would be unreachable.
            emitThrow(method, "Unsupported or unknown union variant for " + structure.schema().id());
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        /**
         * Emits one structure member.
         *
         * <p>The guard order matters for the wire format. A collection getter returns an empty
         * collection when the field is unset, and an empty AWS Query list emits its own name with no
         * value, so writing one unconditionally would add a parameter the interpreted path omits.
         * {@code presence()} is the generated {@code hasX()}, which reports on the field rather than
         * the getter, and is exactly the guard {@code serializeMembers} uses.
         */
        private void emitMember(MethodVisitor method, RuntimeCodecPlan.MemberPlan member, String basePath) {
            Label skip = new Label();
            Method presence = member.presence();
            if (presence != null) {
                method.visitVarInsn(ALOAD, 1);
                invoke(method, presence);
                method.visitJumpInsn(IFEQ, skip);
            }

            String path = join(basePath, queryName(member.schema()));
            Class<?> valueType = member.getter().getReturnType();
            if (valueType.isPrimitive()) {
                emitParam(method, path);
                method.visitVarInsn(ALOAD, 2);
                method.visitVarInsn(ALOAD, 1);
                invoke(method, member.getter());
                emitValue(method, member.schema(), valueType);
            } else {
                method.visitVarInsn(ALOAD, 1);
                invoke(method, member.getter());
                method.visitVarInsn(ASTORE, 3);
                method.visitVarInsn(ALOAD, 3);
                method.visitJumpInsn(IFNULL, skip);
                emitTarget(method, member.schema(), path, 3, valueType);
            }
            method.visitLabel(skip);
        }

        /** Dispatches on shape type for a reference value already stored in {@code valueLocal}. */
        private void emitTarget(
                MethodVisitor method,
                Schema memberSchema,
                String path,
                int valueLocal,
                Class<?> valueType
        ) {
            Schema target = memberSchema.isMember() ? memberSchema.memberTarget() : memberSchema;
            switch (target.type()) {
                case STRUCTURE, UNION -> {
                    RuntimeCodecPlan.StructPlan nested = requireStructure(target);
                    Target resolved = structTarget(nested, path);
                    emitPush(method, resolved);
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, valueLocal);
                    method.visitTypeInsn(CHECKCAST, Type.getInternalName(nested.shapeClass()));
                    method.visitVarInsn(ALOAD, 2);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            methodName(resolved.method()),
                            structDescriptor(nested),
                            false);
                    emitPop(method, resolved);
                }
                case LIST, SET -> emitAggregateCall(method, listTarget(memberSchema, path), valueLocal, LIST);
                case MAP -> emitAggregateCall(method, mapTarget(memberSchema, path), valueLocal, MAP);
                default -> {
                    emitParam(method, path);
                    method.visitVarInsn(ALOAD, 2);
                    method.visitVarInsn(ALOAD, valueLocal);
                    emitValue(method, memberSchema, valueType);
                }
            }
        }

        private void emitAggregateCall(MethodVisitor method, Target resolved, int valueLocal, String collection) {
            emitPush(method, resolved);
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, valueLocal);
            method.visitTypeInsn(CHECKCAST, collection);
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(
                    INVOKESPECIAL,
                    className,
                    methodName(resolved.method()),
                    collection.equals(LIST) ? LIST_DESCRIPTOR : MAP_DESCRIPTOR,
                    false);
            emitPop(method, resolved);
        }

        // -- lists ------------------------------------------------------------------------------

        /**
         * Emits a list writer.
         *
         * <p>Locals: 1 list, 2 writer, 3 index, 4 size, 5 element. The {@code head} constant carries
         * every static segment up to the index, including the trailing separator, so an element
         * parameter costs one copy and one integer format.
         */
        private void emitList(ListTask task) {
            Schema memberSchema = task.member();
            Schema listSchema = memberSchema.isMember() ? memberSchema.memberTarget() : memberSchema;
            Schema element = listSchema.listMember();
            if (element == null) {
                throw new UnsupportedSchemaException("List has no member schema: " + memberSchema.id());
            }

            AwsQuerySchemaExtensions.QueryMemberBinding binding = binding(memberSchema);
            // EC2 Query has no non-flattened form: every list is written as if it carried
            // @xmlFlattened, and an empty one contributes nothing at all.
            boolean flattened = variant == QueryFormSerializer.QueryVariant.EC2_QUERY || binding.listFlattened();
            String base = task.path();
            String head = (base.isEmpty() ? "" : base + '.')
                    + (flattened ? "" : latin1(binding.listMemberNameBytes()) + '.');

            MethodVisitor method = writer.visitMethod(ACC_PRIVATE, methodName(task.id()), LIST_DESCRIPTOR, null, null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEINTERFACE, LIST, "size", "()I", true);
            method.visitVarInsn(ISTORE, 4);

            if (variant == QueryFormSerializer.QueryVariant.AWS_QUERY) {
                Label notEmpty = new Label();
                method.visitVarInsn(ILOAD, 4);
                method.visitJumpInsn(IFNE, notEmpty);
                method.visitVarInsn(ALOAD, 2);
                if (base.isEmpty()) {
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "paramEmpty", "()V", false);
                } else {
                    method.visitFieldInsn(GETSTATIC, className, constant(base + '='), "[B");
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "param", "([B)V", false);
                }
                method.visitInsn(RETURN);
                method.visitLabel(notEmpty);
            }

            method.visitInsn(ICONST_0);
            method.visitVarInsn(ISTORE, 3);
            Label loop = new Label();
            Label done = new Label();
            method.visitLabel(loop);
            method.visitVarInsn(ILOAD, 3);
            method.visitVarInsn(ILOAD, 4);
            method.visitJumpInsn(IF_ICMPGE, done);
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(ILOAD, 3);
            method.visitMethodInsn(INVOKEINTERFACE, LIST, "get", "(I)Ljava/lang/Object;", true);
            method.visitVarInsn(ASTORE, 5);

            Label next = new Label();
            method.visitVarInsn(ALOAD, 5);
            method.visitJumpInsn(IFNULL, next);
            emitElement(method, element, head, 3, 5);
            method.visitLabel(next);
            method.visitIincInsn(3, 1);
            method.visitJumpInsn(GOTO, loop);
            method.visitLabel(done);
            endMethod(method);
        }

        private void emitElement(MethodVisitor method, Schema element, String head, int indexLocal, int valueLocal) {
            Schema target = element.isMember() ? element.memberTarget() : element;
            switch (target.type()) {
                case STRUCTURE, UNION -> {
                    RuntimeCodecPlan.StructPlan nested = requireStructure(target);
                    // The index has to be pushed, so the nested path restarts from empty.
                    Target resolved = structTarget(nested, "");
                    emitPushAt(method, head, indexLocal);
                    method.visitVarInsn(ALOAD, 0);
                    method.visitVarInsn(ALOAD, valueLocal);
                    method.visitTypeInsn(CHECKCAST, Type.getInternalName(nested.shapeClass()));
                    method.visitVarInsn(ALOAD, 2);
                    method.visitMethodInsn(
                            INVOKESPECIAL,
                            className,
                            methodName(resolved.method()),
                            structDescriptor(nested),
                            false);
                    emitPopPath(method);
                }
                case LIST, SET -> {
                    // A nested collection is named after the outer list's member, matching the extra
                    // ".member" the interpreted path pushes when a list element is itself a list.
                    Target resolved = listTarget(element, queryName(element));
                    emitPushAt(method, head, indexLocal);
                    emitAggregateCall(method, resolved, valueLocal, LIST);
                    emitPopPath(method);
                }
                case MAP -> {
                    Target resolved = mapTarget(element, queryName(element));
                    emitPushAt(method, head, indexLocal);
                    emitAggregateCall(method, resolved, valueLocal, MAP);
                    emitPopPath(method);
                }
                default -> {
                    method.visitVarInsn(ALOAD, 2);
                    method.visitFieldInsn(GETSTATIC, className, constant(head), "[B");
                    method.visitVarInsn(ILOAD, indexLocal);
                    method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "paramAt", "([BI)V", false);
                    method.visitVarInsn(ALOAD, 2);
                    method.visitVarInsn(ALOAD, valueLocal);
                    emitValue(method, element, Object.class);
                }
            }
        }

        // -- maps -------------------------------------------------------------------------------

        /**
         * Emits a map writer.
         *
         * <p>Locals: 1 map, 2 writer, 3 iterator, 4 entry, 5 value, 6 entry index. The entry index
         * advances for every entry, including one whose value is null, because the interpreted path
         * still writes that entry's key.
         */
        private void emitMap(MapTask task) {
            Schema memberSchema = task.member();
            Schema mapSchema = memberSchema.isMember() ? memberSchema.memberTarget() : memberSchema;
            Schema keyMember = mapSchema.mapKeyMember();
            Schema valueMember = mapSchema.mapValueMember();
            if (keyMember == null || valueMember == null) {
                throw new UnsupportedSchemaException("Map has no key or value schema: " + memberSchema.id());
            }

            AwsQuerySchemaExtensions.QueryMemberBinding binding = binding(memberSchema);
            String base = task.path();
            String entryHead = (base.isEmpty() ? "" : base + '.')
                    + (binding.mapFlattened() ? "" : latin1(binding.mapEntryNameBytes()) + '.');
            String keyName = latin1(binding.mapKeyNameBytes());
            String valueName = latin1(binding.mapValueNameBytes());

            MethodVisitor method = writer.visitMethod(ACC_PRIVATE, methodName(task.id()), MAP_DESCRIPTOR, null, null);
            method.visitCode();
            method.visitInsn(ICONST_0);
            method.visitVarInsn(ISTORE, 6);
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEINTERFACE, MAP, "entrySet", "()L" + SET + ";", true);
            method.visitMethodInsn(INVOKEINTERFACE, SET, "iterator", "()L" + ITERATOR + ";", true);
            method.visitVarInsn(ASTORE, 3);

            Label loop = new Label();
            Label done = new Label();
            method.visitLabel(loop);
            method.visitVarInsn(ALOAD, 3);
            method.visitMethodInsn(INVOKEINTERFACE, ITERATOR, "hasNext", "()Z", true);
            method.visitJumpInsn(IFEQ, done);
            method.visitVarInsn(ALOAD, 3);
            method.visitMethodInsn(INVOKEINTERFACE, ITERATOR, "next", "()Ljava/lang/Object;", true);
            method.visitTypeInsn(CHECKCAST, MAP_ENTRY);
            method.visitVarInsn(ASTORE, 4);

            emitPushAt(method, entryHead, 6);
            method.visitIincInsn(6, 1);

            emitParam(method, keyName);
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ALOAD, 4);
            method.visitMethodInsn(INVOKEINTERFACE, MAP_ENTRY, "getKey", "()Ljava/lang/Object;", true);
            if (keyMember.memberTarget().type() == ShapeType.ENUM) {
                method.visitTypeInsn(CHECKCAST, SMITHY_ENUM);
                method.visitMethodInsn(INVOKEINTERFACE, SMITHY_ENUM, "getValue", "()L" + STRING + ";", true);
            } else {
                method.visitTypeInsn(CHECKCAST, STRING);
            }
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "valueString", "(L" + STRING + ";)V", false);

            method.visitVarInsn(ALOAD, 4);
            method.visitMethodInsn(INVOKEINTERFACE, MAP_ENTRY, "getValue", "()Ljava/lang/Object;", true);
            method.visitVarInsn(ASTORE, 5);
            Label next = new Label();
            method.visitVarInsn(ALOAD, 5);
            method.visitJumpInsn(IFNULL, next);
            emitTarget(method, valueMember, valueName, 5, Object.class);
            method.visitLabel(next);

            emitPopPath(method);
            method.visitJumpInsn(GOTO, loop);
            method.visitLabel(done);
            endMethod(method);
        }

        // -- values -----------------------------------------------------------------------------

        /** Consumes a writer and a value from the stack and appends the value's encoded form. */
        private void emitValue(MethodVisitor method, Schema schema, Class<?> javaType) {
            Schema target = schema.isMember() ? schema.memberTarget() : schema;
            switch (target.type()) {
                case BOOLEAN -> {
                    unbox(method, javaType, Boolean.class, "booleanValue", "()Z");
                    call(method, "valueBoolean", "(Z)V");
                }
                case BYTE -> {
                    unbox(method, javaType, Byte.class, "byteValue", "()B");
                    call(method, "valueInt", "(I)V");
                }
                case SHORT -> {
                    unbox(method, javaType, Short.class, "shortValue", "()S");
                    call(method, "valueInt", "(I)V");
                }
                case INTEGER -> {
                    unbox(method, javaType, Integer.class, "intValue", "()I");
                    call(method, "valueInt", "(I)V");
                }
                case LONG -> {
                    unbox(method, javaType, Long.class, "longValue", "()J");
                    call(method, "valueLong", "(J)V");
                }
                case FLOAT -> {
                    unbox(method, javaType, Float.class, "floatValue", "()F");
                    call(method, "valueFloat", "(F)V");
                }
                case DOUBLE -> {
                    unbox(method, javaType, Double.class, "doubleValue", "()D");
                    call(method, "valueDouble", "(D)V");
                }
                case BIG_INTEGER -> {
                    method.visitTypeInsn(CHECKCAST, Type.getInternalName(BigInteger.class));
                    call(method, "valueBigInteger", "(L" + Type.getInternalName(BigInteger.class) + ";)V");
                }
                case BIG_DECIMAL -> {
                    method.visitTypeInsn(CHECKCAST, Type.getInternalName(BigDecimal.class));
                    call(method, "valueBigDecimal", "(L" + Type.getInternalName(BigDecimal.class) + ";)V");
                }
                case STRING -> {
                    requireAssignable(schema, javaType, CharSequence.class);
                    method.visitTypeInsn(CHECKCAST, STRING);
                    call(method, "valueString", "(L" + STRING + ";)V");
                }
                case ENUM -> {
                    if (javaType == String.class) {
                        call(method, "valueString", "(L" + STRING + ";)V");
                    } else {
                        method.visitTypeInsn(CHECKCAST, SMITHY_ENUM);
                        method.visitMethodInsn(INVOKEINTERFACE, SMITHY_ENUM, "getValue", "()L" + STRING + ";", true);
                        call(method, "valueString", "(L" + STRING + ";)V");
                    }
                }
                case INT_ENUM -> {
                    if (javaType == int.class) {
                        call(method, "valueInt", "(I)V");
                    } else if (javaType == Integer.class) {
                        unbox(method, javaType, Integer.class, "intValue", "()I");
                        call(method, "valueInt", "(I)V");
                    } else {
                        method.visitTypeInsn(CHECKCAST, SMITHY_INT_ENUM);
                        method.visitMethodInsn(INVOKEINTERFACE, SMITHY_INT_ENUM, "getValue", "()I", true);
                        call(method, "valueInt", "(I)V");
                    }
                }
                case BLOB -> {
                    // A streaming blob is a DataStream, not a ByteBuffer; the interpreted path routes
                    // it through writeDataStream, which this backend does not lower.
                    requireAssignable(schema, javaType, ByteBuffer.class);
                    method.visitTypeInsn(CHECKCAST, BYTE_BUFFER);
                    call(method, "valueBlob", "(L" + BYTE_BUFFER + ";)V");
                }
                case TIMESTAMP -> {
                    requireAssignable(schema, javaType, Instant.class);
                    method.visitTypeInsn(CHECKCAST, INSTANT);
                    call(method, timestampWriter(schema), "(L" + INSTANT + ";)V");
                }
                // Everything else, DOCUMENT included, has no Query representation this backend can
                // emit. An unsupported schema falls back to the interpreted path rather than
                // reporting an emitter bug.
                default -> throw new UnsupportedSchemaException(
                        "Query runtime codegen cannot write " + target.type() + " at " + schema.id());
            }
        }

        private String timestampWriter(Schema schema) {
            TimestampFormatTrait.Format format = QueryFormSerializer.resolveTimestampFormat(schema);
            return switch (format) {
                case DATE_TIME -> "valueIso8601";
                case EPOCH_SECONDS -> "valueEpochSeconds";
                case HTTP_DATE -> "valueHttpDate";
                // The interpreted path falls back to a TimestampFormatter built from the schema, which
                // cannot be reproduced from a constant pool.
                default -> throw new UnsupportedSchemaException(
                        "Query runtime codegen cannot write timestamp format " + format + " at " + schema.id());
            };
        }

        private static void requireAssignable(Schema schema, Class<?> javaType, Class<?> expected) {
            if (javaType != Object.class && !expected.isAssignableFrom(javaType)) {
                throw new UnsupportedSchemaException(
                        "Query runtime codegen expected " + expected.getSimpleName() + " but found "
                                + javaType.getName() + " at " + schema.id());
            }
        }

        private static void unbox(
                MethodVisitor method,
                Class<?> actual,
                Class<?> boxed,
                String name,
                String descriptor
        ) {
            if (!actual.isPrimitive()) {
                method.visitTypeInsn(CHECKCAST, Type.getInternalName(boxed));
                method.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(boxed), name, descriptor, false);
            }
        }

        private static void call(MethodVisitor method, String name, String descriptor) {
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, name, descriptor, false);
        }

        // -- shared emit helpers ----------------------------------------------------------------

        private void emitParam(MethodVisitor method, String path) {
            method.visitVarInsn(ALOAD, 2);
            method.visitFieldInsn(GETSTATIC, className, constant(path + '='), "[B");
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "param", "([B)V", false);
        }

        private void emitPushAt(MethodVisitor method, String head, int indexLocal) {
            method.visitVarInsn(ALOAD, 2);
            method.visitFieldInsn(GETSTATIC, className, constant(head), "[B");
            method.visitVarInsn(ILOAD, indexLocal);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "pushAt", "([BI)V", false);
        }

        private void emitPush(MethodVisitor method, Target resolved) {
            if (resolved.push() != null) {
                method.visitVarInsn(ALOAD, 2);
                method.visitFieldInsn(GETSTATIC, className, constant(resolved.push()), "[B");
                method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "pushPath", "([B)V", false);
            }
        }

        private void emitPop(MethodVisitor method, Target resolved) {
            if (resolved.push() != null) {
                emitPopPath(method);
            }
        }

        private void emitPopPath(MethodVisitor method) {
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKEVIRTUAL, WRITER, "popPath", "()V", false);
        }

        private void emitEntryPoint(RuntimeCodecPlan.StructPlan root, int rootId) {
            MethodVisitor method = writer.visitMethod(
                    ACC_PUBLIC,
                    "write",
                    "(L" + SERIALIZABLE_SHAPE + ";L" + WRITER + ";)V",
                    null,
                    null);
            method.visitCode();
            method.visitVarInsn(ALOAD, 0);
            method.visitVarInsn(ALOAD, 1);
            method.visitTypeInsn(CHECKCAST, Type.getInternalName(root.shapeClass()));
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKESPECIAL, className, methodName(rootId), structDescriptor(root), false);
            endMethod(method);
        }

        private void endMethod(MethodVisitor method) {
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
            methodCount++;
        }

        // -- constants --------------------------------------------------------------------------

        private void emitConstantFields() {
            for (Integer id : constants.values()) {
                writer.visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "C" + id, "[B", null, null);
            }
        }

        private void emitClassInitializer() {
            if (constants.isEmpty()) {
                return;
            }
            MethodVisitor method = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            method.visitCode();
            for (Map.Entry<String, Integer> entry : constants.entrySet()) {
                method.visitLdcInsn(entry.getKey());
                method.visitFieldInsn(
                        GETSTATIC,
                        Type.getInternalName(StandardCharsets.class),
                        "ISO_8859_1",
                        CHARSET_DESCRIPTOR);
                method.visitMethodInsn(
                        INVOKEVIRTUAL,
                        STRING,
                        "getBytes",
                        "(" + CHARSET_DESCRIPTOR + ")[B",
                        false);
                method.visitFieldInsn(PUTSTATIC, className, "C" + entry.getValue(), "[B");
            }
            method.visitInsn(RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
        }

        /**
         * Interns a constant and returns its field name.
         *
         * <p>Paths are carried as latin-1 strings, one char per byte, so a name that percent-encoded
         * or that came through {@code @xmlName} as raw UTF-8 round-trips through the constant pool
         * unchanged. Encoding them as UTF-8 strings instead would re-encode any byte above 0x7F.
         */
        private String constant(String latin1) {
            return "C" + constants.computeIfAbsent(latin1, ignored -> constants.size());
        }

        // -- naming -----------------------------------------------------------------------------

        private String queryName(Schema memberSchema) {
            AwsQuerySchemaExtensions.QueryMemberBinding binding = binding(memberSchema);
            return latin1(variant == QueryFormSerializer.QueryVariant.EC2_QUERY
                    ? binding.ec2QueryNameBytes()
                    : binding.awsQueryNameBytes());
        }

        private static AwsQuerySchemaExtensions.QueryMemberBinding binding(Schema memberSchema) {
            AwsQuerySchemaExtensions.QueryMemberBinding binding = EXTENSIONS.provide(memberSchema);
            if (binding == null) {
                throw new UnsupportedSchemaException(
                        "Query runtime codegen needs a member schema: " + memberSchema.id());
            }
            return binding;
        }

        private RuntimeCodecPlan.StructPlan requireStructure(Schema target) {
            RuntimeCodecPlan.StructPlan structure = structuresBySchema.get(target.id().toString());
            if (structure == null) {
                throw new UnsupportedSchemaException("Structure missing from plan: " + target.id());
            }
            return structure;
        }

        private static String latin1(byte[] bytes) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }

        private static String join(String path, String name) {
            return path.isEmpty() ? name : path + '.' + name;
        }

        private static String methodName(int id) {
            return "w" + id;
        }

        private static String structDescriptor(RuntimeCodecPlan.StructPlan structure) {
            return "(L" + Type.getInternalName(structure.shapeClass()) + ";L" + WRITER + ";)V";
        }
    }
}
