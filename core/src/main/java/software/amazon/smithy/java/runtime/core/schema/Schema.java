/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.Trait;

/**
 * Describes a generated shape with important metadata from a Smithy model.
 *
 * <p>Various constraint traits are precomputed based on the presence of traits exposed by this class to aid in the
 * performance of validation to avoid computing constraints and hashmap lookups by trait class.
 *
 * <p>Note: when creating a structure schema, all required members must come before optional members.
 */
public abstract sealed class Schema permits RootSchema, MemberSchema, DeferredRootSchema,
    DeferredMemberSchema {

    private final ShapeType type;
    private final ShapeId id;

    private Schema[][] memberLookupTable;
    private boolean skipLengthLookups;

    /**
     * Schema traits. This is package-private to allow MemberSchemaBuilder to eagerly merge member and target traits.
     */
    final Map<Class<? extends Trait>, Trait> traits;

    private final String memberName;

    /**
     * The structure member count that are required by validation.
     */
    final int requiredMemberCount;

    /**
     * The bitmask to use for this member to compute a required member bitfield. This value will match the
     * memberIndex if the member is required and has no default value. It will be zero if
     * isRequiredByValidation == false.
     */
    final long requiredByValidationBitmask;

    /**
     * Member index used for member deserialization and validation. This value is unstable across model updates.
     */
    private final int memberIndex;

    /**
     * The result of creating a bitfield of the memberIndex of every required member with no default value.
     * This allows for an inexpensive comparison for required structure member validation.
     */
    final long requiredStructureMemberBitfield;

    /**
     * True if the shape is a member that has the required trait and a non-null default trait.
     */
    final boolean isRequiredByValidation;

    final long minLengthConstraint;
    final long maxLengthConstraint;
    final BigDecimal minRangeConstraint;
    final BigDecimal maxRangeConstraint;
    final long minLongConstraint;
    final long maxLongConstraint;
    final double minDoubleConstraint;
    final double maxDoubleConstraint;
    final ValidatorOfString stringValidation;

    private final int hashCode;

    Schema(
        ShapeType type,
        ShapeId id,
        Map<Class<? extends Trait>, Trait> traits,
        List<MemberSchemaBuilder> members,
        Set<String> stringEnumValues
    ) {
        this.type = type;
        this.id = id;
        this.traits = traits;
        this.memberName = null;

        // Structure shapes need to sort members so that required members come before optional members.
        if (type == ShapeType.STRUCTURE) {
            SchemaBuilder.sortMembers(members);
        }

        // Assign an appropriate memberIndex and validation bitfield to each member.
        SchemaBuilder.assignMemberIndex(members);

        // Root-level shapes can initialize these member-specific values to default zero values.
        this.memberIndex = 0;
        this.requiredByValidationBitmask = 0;
        this.isRequiredByValidation = false;

        // Even root-level shapes have computed validation information to allow for validating root strings directly.
        var validationState = SchemaBuilder.ValidationState.of(type, traits, stringEnumValues);
        this.minLengthConstraint = validationState.minLengthConstraint();
        this.maxLengthConstraint = validationState.maxLengthConstraint();
        this.minLongConstraint = validationState.minLongConstraint();
        this.maxLongConstraint = validationState.maxLongConstraint();
        this.minDoubleConstraint = validationState.minDoubleConstraint();
        this.maxDoubleConstraint = validationState.maxDoubleConstraint();
        this.minRangeConstraint = validationState.minRangeConstraint();
        this.maxRangeConstraint = validationState.maxRangeConstraint();
        this.stringValidation = validationState.stringValidation();

        // Only use the slow version of required member validation if there are > 64 required members.
        this.requiredMemberCount = SchemaBuilder.computeRequiredMemberCount(type, members);
        this.requiredStructureMemberBitfield = SchemaBuilder.computeRequiredBitField(
            type,
            requiredMemberCount,
            members,
            m -> m.requiredByValidationBitmask
        );

        this.hashCode = Objects.hash(type, id, traits, memberIndex);
    }

    Schema(MemberSchemaBuilder builder) {
        this.type = builder.type;
        this.id = builder.id;
        this.traits = builder.traits;
        this.memberName = builder.id.getMember().orElseThrow();
        this.memberIndex = builder.memberIndex;
        this.requiredByValidationBitmask = builder.requiredByValidationBitmask;
        this.requiredMemberCount = builder.requiredMemberCount;
        this.isRequiredByValidation = builder.isRequiredByValidation;

        this.minLengthConstraint = builder.validationState.minLengthConstraint();
        this.maxLengthConstraint = builder.validationState.maxLengthConstraint();
        this.minRangeConstraint = builder.validationState.minRangeConstraint();
        this.maxRangeConstraint = builder.validationState.maxRangeConstraint();
        this.stringValidation = builder.validationState.stringValidation();
        this.minLongConstraint = builder.validationState.minLongConstraint();
        this.maxLongConstraint = builder.validationState.maxLongConstraint();
        this.minDoubleConstraint = builder.validationState.minDoubleConstraint();
        this.maxDoubleConstraint = builder.validationState.maxDoubleConstraint();

        // Compute the expected bitfield, and adjust how it's computed based on if the target is a builder or not.
        if (builder.target != null) {
            this.requiredStructureMemberBitfield = SchemaBuilder.computeRequiredBitField(
                type,
                requiredMemberCount,
                builder.target.members(),
                m -> m.requiredByValidationBitmask
            );
        } else {
            this.requiredStructureMemberBitfield = SchemaBuilder.computeRequiredBitField(
                type,
                requiredMemberCount,
                builder.targetBuilder.members,
                m -> m.requiredByValidationBitmask
            );
        }

        this.hashCode = Objects.hash(type, id, traits, memberIndex);
    }

    /**
     * Efficiently find a member using a byte array, comparing against member names.
     *
     * @param array  Data to check.
     * @param head Start of data.
     * @param tail End of data.
     * @return the matched member schema, or null if not found.
     */
    public final Schema findMember(byte[] array, int head, int tail) {
        var lookupTable = getMemberLookupTable();
        var len = tail - head;

        if (skipLengthLookups) {
            outer:
            for (var member : members()) {
                var memberName = member.memberName;
                if (memberName.length() == len) {
                    for (var i = head; i < tail; i++) {
                        if (memberName.charAt(i - head) != array[i]) {
                            continue outer;
                        }
                    }
                    return member;
                }
            }
        } else {
            if (len > 0 && len < lookupTable.length) {
                var table = lookupTable[len];
                outer:
                for (var member : table) {
                    var memberName = member.memberName;
                    for (var i = head; i < head + len; i++) {
                        if (memberName.charAt(i - head) != array[i]) {
                            continue outer;
                        }
                    }
                    return member;
                }
            }
        }

        return null;
    }

    private Schema[][] getMemberLookupTable() {
        var table = memberLookupTable;

        if (table == null) {

            if (memberTarget() != null) {
                // Don't recompute on members.
                memberLookupTable = memberTarget().memberLookupTable;
                skipLengthLookups = memberTarget().skipLengthLookups;
                return memberLookupTable;
            } else if (members().size() <= 2) {
                // Don't make a table when the size wouldn't benefit.
                memberLookupTable = new Schema[0][];
                skipLengthLookups = true;
                return memberLookupTable;
            }

            // First compute the largest member name to allocate the buckets of length -> members.
            int maxSize = 0;
            for (var member : members()) {
                maxSize = Math.max(maxSize, member.memberName.length());
            }

            // Allocate the member name buckets and buckets used to track the position in the array to add members.
            table = new Schema[maxSize + 1][];
            int[] remaining = new int[maxSize + 1];

            var uniqueMemberNameLengths = 0;
            for (var member : members()) {
                var len = member.memberName.length();
                if (remaining[len] == 0) {
                    uniqueMemberNameLengths++;
                }
                remaining[len]++;
            }

            for (var member : members()) {
                var length = member.memberName.length();
                int currentIndex = remaining[length];
                // If this is the first member for this length, allocate the bucket with the known remaining size.
                if (table[length] == null) {
                    table[length] = new Schema[currentIndex];
                }
                table[length][currentIndex - 1] = member;
                // We're inserting from the end to the beginning so decrement the number.
                remaining[length]--;
            }

            memberLookupTable = table;

            // Skip length-based bucketing if all members have the same size. In this case, clear out the table.
            if (uniqueMemberNameLengths == 1) {
                skipLengthLookups = true;
                memberLookupTable = new Schema[0][];
            }
        }

        return table;
    }

    public static Schema createBoolean(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.BOOLEAN, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createByte(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.BYTE, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createShort(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.SHORT, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createInteger(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.INTEGER, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createIntEnum(ShapeId id, Set<Integer> values, Trait... traits) {
        return new RootSchema(
            ShapeType.INT_ENUM,
            id,
            SchemaBuilder.createTraitMap(traits),
            Collections.emptyList(),
            Collections.emptySet(),
            values
        );
    }

    public static Schema createLong(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.LONG, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createFloat(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.FLOAT, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createDouble(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.DOUBLE, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createBigInteger(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.BIG_INTEGER, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createBigDecimal(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.BIG_DECIMAL, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createString(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.STRING, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createEnum(ShapeId id, Set<String> values, Trait... traits) {
        return new RootSchema(
            ShapeType.ENUM,
            id,
            SchemaBuilder.createTraitMap(traits),
            Collections.emptyList(),
            values,
            Collections.emptySet()
        );
    }

    public static Schema createBlob(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.BLOB, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createDocument(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.DOCUMENT, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createTimestamp(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.TIMESTAMP, id, SchemaBuilder.createTraitMap(traits));
    }

    public static Schema createOperation(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.OPERATION, id, SchemaBuilder.createTraitMap(traits));
    }

    public static SchemaBuilder structureBuilder(ShapeId id, Trait... traits) {
        return new SchemaBuilder(id, ShapeType.STRUCTURE, traits);
    }

    public static SchemaBuilder unionBuilder(ShapeId id, Trait... traits) {
        return new SchemaBuilder(id, ShapeType.UNION, traits);
    }

    public static SchemaBuilder listBuilder(ShapeId id, Trait... traits) {
        return new SchemaBuilder(id, ShapeType.LIST, traits);
    }

    public static SchemaBuilder mapBuilder(ShapeId id, Trait... traits) {
        return new SchemaBuilder(id, ShapeType.MAP, traits);
    }

    @Override
    public final String toString() {
        return "Schema{id='" + id + '\'' + ", type=" + type() + '}';
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        var o = (Schema) obj;
        return hashCode == o.hashCode
            && type == o.type
            && id.equals(o.id)
            && traits.equals(o.traits)
            && members().equals(o.members())
            && memberIndex == o.memberIndex;
    }

    @Override
    public final int hashCode() {
        return hashCode;
    }

    /**
     * Get the shape ID of the shape.
     *
     * @return Return the shape ID.
     */
    public final ShapeId id() {
        return id;
    }

    /**
     * Get the schema shape type.
     * <p>
     * Note that this will never return MEMBER as a type. Schema members act as if they are the target type.
     *
     * @return Returns the schema shape type.
     */
    public final ShapeType type() {
        return type;
    }

    /**
     * Check if the schema is for a member.
     *
     * @return Returns true if this is a member.
     */
    public final boolean isMember() {
        return memberName != null;
    }

    /**
     * Get the name of the member if the schema is for a member, or null if not.
     *
     * @return Returns the member name or null if not a member.
     */
    public final String memberName() {
        return memberName;
    }

    /**
     * The position of the member in the containing shape's list of members <em>at this point in time</em>.
     *
     * <p>This index is not stable over time; it can change between versions of a published JAR for a shape.
     * Do not serialize the member index itself, rely on it for persistence, or assume it will be the same across
     * versions of a JAR. This value is primarily used for
     *
     * @return the member index of this schema starting from 1. 0 is used when a shape is not a member.
     */
    public final int memberIndex() {
        return memberIndex;
    }

    /**
     * Get the target of the member, or null if this schema is not a member.
     *
     * @return Member target.
     */
    public Schema memberTarget() {
        return null;
    }

    /**
     * Get a trait if present.
     *
     * @param trait Trait to get.
     * @return Returns the trait, or null if not found.
     * @param <T> Trait type to get.
     */
    @SuppressWarnings("unchecked")
    public final <T extends Trait> T getTrait(Class<T> trait) {
        return (T) traits.get(trait);
    }

    /**
     * Check if the schema has a trait.
     *
     * @param trait Trait to check for.
     * @return true if the trait is found.
     * @param <T> Trait type.
     */
    public final <T extends Trait> boolean hasTrait(Class<T> trait) {
        return traits.containsKey(trait);
    }

    /**
     * Requires that the given trait type is found and returns it.
     *
     * @param trait Trait type to get.
     * @return Returns the found value.
     * @param <T> Trait to get.
     * @throws NoSuchElementException if the value does not exist.
     */
    public final <T extends Trait> T expectTrait(Class<T> trait) {
        var t = getTrait(trait);
        if (t == null) {
            throw new NoSuchElementException("Expected trait not found: " + trait.getName());
        }
        return t;
    }

    /**
     * Gets the members of the schema.
     *
     * @return Returns the members.
     */
    public List<Schema> members() {
        return Collections.emptyList();
    }

    /**
     * Get a member by name or return a default value.
     *
     * @param memberName Member by name to get.
     * @return Returns the found member or null if not found.
     */
    public Schema member(String memberName) {
        return null;
    }

    /**
     * Get the allowed values of the string.
     *
     * @return allowed string values (only relevant if not empty).
     */
    public Set<String> stringEnumValues() {
        return Collections.emptySet();
    }

    /**
     * Get the allowed integer values of an integer.
     *
     * @return allowed integer values (only relevant if not empty).
     */
    public Set<Integer> intEnumValues() {
        return Collections.emptySet();
    }
}
