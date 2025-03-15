/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
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
public abstract sealed class Schema implements MemberLookup
        permits RootSchema, MemberSchema, DeferredRootSchema, DeferredMemberSchema, ResolvedRootSchema {

    private final ShapeType type;
    private final ShapeId id;

    /**
     * Schema traits. This is package-private to allow MemberSchemaBuilder to eagerly merge member and target traits.
     */
    final TraitMap traits;

    private final String memberName;

    /**
     * Member index used for member deserialization and validation. This value is unstable across model updates.
     */
    private final int memberIndex;

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
    final boolean uniqueItemsConstraint;
    final boolean hasRangeConstraint;

    private Schema listMember;
    private Schema mapKeyMember;
    private Schema mapValueMember;

    private final int hash;

    Schema(
            ShapeType type,
            ShapeId id,
            TraitMap traits,
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
        this.uniqueItemsConstraint = type == ShapeType.LIST && hasTrait(TraitKey.UNIQUE_ITEMS_TRAIT);
        this.hasRangeConstraint = validationState.hasRangeConstraint();

        // Only use the slow version of required member validation if there are > 64 required members.

        this.hash = computeHash(this);
    }

    Schema(MemberSchemaBuilder builder) {
        this.type = builder.type;
        this.id = builder.id;
        this.traits = builder.traits;
        this.memberName = builder.id.getMember().orElseThrow();
        this.memberIndex = builder.memberIndex;
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
        this.uniqueItemsConstraint = type == ShapeType.LIST && hasTrait(TraitKey.UNIQUE_ITEMS_TRAIT);
        this.hasRangeConstraint = builder.validationState.hasRangeConstraint();

        this.hash = computeHash(this);
    }

    private static int computeHash(Schema schema) {
        int result = 1;
        result = 17 * result + schema.type.hashCode();
        result = 31 * result + schema.id.hashCode();
        result = 31 * result + schema.traits.hashCode();
        result = 31 * result + schema.memberIndex;
        return result;
    }

    public static Schema createBoolean(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.BOOLEAN, id, TraitMap.create(traits));
    }

    public static Schema createByte(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.BYTE, id, TraitMap.create(traits));
    }

    public static Schema createShort(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.SHORT, id, TraitMap.create(traits));
    }

    public static Schema createInteger(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.INTEGER, id, TraitMap.create(traits));
    }

    public static Schema createIntEnum(ShapeId id, Set<Integer> values, Trait... traits) {
        return new RootSchema(
                ShapeType.INT_ENUM,
                id,
                TraitMap.create(traits),
                Collections.emptyList(),
                Collections.emptySet(),
                values);
    }

    public static Schema createLong(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.LONG, id, TraitMap.create(traits));
    }

    public static Schema createFloat(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.FLOAT, id, TraitMap.create(traits));
    }

    public static Schema createDouble(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.DOUBLE, id, TraitMap.create(traits));
    }

    public static Schema createBigInteger(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.BIG_INTEGER, id, TraitMap.create(traits));
    }

    public static Schema createBigDecimal(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.BIG_DECIMAL, id, TraitMap.create(traits));
    }

    public static Schema createString(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.STRING, id, TraitMap.create(traits));
    }

    public static Schema createEnum(ShapeId id, Set<String> values, Trait... traits) {
        return new RootSchema(
                ShapeType.ENUM,
                id,
                TraitMap.create(traits),
                Collections.emptyList(),
                values,
                Collections.emptySet());
    }

    public static Schema createBlob(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.BLOB, id, TraitMap.create(traits));
    }

    public static Schema createDocument(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.DOCUMENT, id, TraitMap.create(traits));
    }

    public static Schema createTimestamp(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.TIMESTAMP, id, TraitMap.create(traits));
    }

    public static Schema createOperation(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.OPERATION, id, TraitMap.create(traits));
    }

    public static Schema createResource(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.RESOURCE, id, TraitMap.create(traits));
    }

    public static Schema createService(ShapeId id, Trait... traits) {
        return new RootSchema(ShapeType.SERVICE, id, TraitMap.create(traits));
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
        return type == o.type
                && id.equals(o.id)
                && traits.equals(o.traits)
                && members().equals(o.members())
                && memberIndex == o.memberIndex;
    }

    @Override
    public final int hashCode() {
        return hash;
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
     * Ensures that this Schema is a member that targets the given schema.
     *
     * <p>This is mostly used during shape deserialization in {@link ShapeBuilder#deserializeMember}.
     *
     * @param target Schema that this Schema must target.
     * @return the current Schema.
     */
    public Schema assertMemberTargetIs(Schema target) {
        var memberTarget = memberTarget();
        if (target == memberTarget) {
            return this;
        }
        throw new IllegalStateException("Expected a member schema that targets " + target.id + ", found " + this);
    }

    /**
     * Get a trait if present.
     *
     * @param trait Trait to get.
     * @return Returns the trait, or null if not found.
     * @param <T> Trait type to get.
     */
    public final <T extends Trait> T getTrait(TraitKey<T> trait) {
        return traits.get(trait);
    }

    /**
     * Check if the schema has a trait.
     *
     * @param trait Trait to check for.
     * @return true if the trait is found.
     */
    public final boolean hasTrait(TraitKey<? extends Trait> trait) {
        return traits.contains(trait);
    }

    /**
     * Requires that the given trait type is found and returns it.
     *
     * @param trait Trait type to get.
     * @return Returns the found value.
     * @param <T> Trait to get.
     * @throws NoSuchElementException if the value does not exist.
     */
    public final <T extends Trait> T expectTrait(TraitKey<T> trait) {
        var t = getTrait(trait);
        if (t == null) {
            throw new NoSuchElementException("Expected trait not found: " + trait.getClass().getName());
        }
        return t;
    }

    /**
     * Gets a trait, but only if it was applied directly to the shape.
     *
     * <p>This can be used to check if a trait was applied to a member. Almost all trait access should go through
     * {@link #getTrait}, but that method returns traits applied to a member or to the target shape of a member.
     * Sometimes you need to know if a trait was applied directly to a member. This method returns the result of
     * {@link #getTrait} for non-member shapes.
     *
     * @param trait Trait to get.
     * @return the trait if found, or null.
     * @param <T> Trait to get.
     */
    public <T extends Trait> T getDirectTrait(TraitKey<T> trait) {
        return getTrait(trait);
    }

    /**
     * Gets the members of the schema.
     *
     * @return Returns the members.
     */
    public List<Schema> members() {
        return Collections.emptyList();
    }

    @Override
    public Schema member(String memberName) {
        return null;
    }

    /**
     * Get the member of a list.
     *
     * <p>This method eliminates any overhead associated with hashmap lookups based on member names.
     *
     * @return Returns the found member or null if not found.
     */
    public final Schema listMember() {
        var result = listMember;
        if (result == null) {
            listMember = result = members().get(0);
            if (!"member".equals(result.memberName)) {
                throw new IllegalStateException("Expected list member, got " + result);
            }
        }
        return result;
    }

    /**
     * Get the key member of a map.
     *
     * <p>This method eliminates any overhead associated with hashmap lookups based on member names.
     *
     * @return Returns the found member or null if not found.
     */
    public final Schema mapKeyMember() {
        var result = mapKeyMember;
        if (result == null) {
            mapKeyMember = result = members().get(0);
            if (!"key".equals(result.memberName)) {
                throw new IllegalStateException("Expected map key member at position 0, got " + result);
            }
        }
        return result;
    }

    /**
     * Get the value member of a map.
     *
     * <p>This method eliminates any overhead associated with hashmap lookups based on member names.
     *
     * @return Returns the found member or null if not found.
     */
    public final Schema mapValueMember() {
        var result = mapValueMember;
        if (result == null) {
            mapValueMember = result = members().get(1);
            if (!"value".equals(result.memberName)) {
                throw new IllegalStateException("Expected map value member at position 1, got " + result);
            }
        }
        return result;
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

    /**
     *
     * @return The structure member count that are required by validation.
     */
    abstract int requiredMemberCount();

    /**
     *
     * @return The bitmask to use for this member to compute a required member bitfield. This value will match the
     * memberIndex if the member is required and has no default value. It will be zero if
     * isRequiredByValidation == false.
     */
    abstract long requiredByValidationBitmask();

    /**
     *
     * @return The result of creating a bitfield of the memberIndex of every required member with no default value.
     * This allows for an inexpensive comparison for required structure member validation.
     */
    abstract long requiredStructureMemberBitfield();

    /**
     * Resolve this Schema and return a resolved instance. The resolved instance can be the same Schema
     * if the Schema doesn't need any resolution.
     * This is primarily useful for Schemas of recursive structures which need to be resolved after construction.
     *
     * @return A resolved Schema.
     */
    public Schema resolve() {
        return this;
    }
}
