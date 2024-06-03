/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.SmithyBuilder;

/**
 * Describes a generated shape with important metadata from a Smithy model.
 *
 * <p>Various constraint traits are precomputed based on the presence of traits exposed by this class to aid in the
 * performance of validation to avoid computing constraints and hashmap lookups by trait class.
 *
 * <p>Note: when creating a structure schema, all required members must come before optional members.
 */
public final class Schema {

    private final ShapeId id;
    private final ShapeType type;
    private final Map<Class<? extends Trait>, Trait> traits;
    private final Map<String, Schema> members;
    private final List<Schema> memberList;
    private volatile int hashCode;

    private final String memberName;
    private final Schema memberTarget;

    /**
     * The position of the member in a containing shape's {@link #members()} return value.
     */
    private int memberIndex = -1;

    private final Set<String> stringEnumValues;
    private final Set<Integer> intEnumValues;

    // The following variables are used to speed up validation and prevent looking up constraints over and over.
    final int requiredMemberCount;
    final long minLengthConstraint;
    final long maxLengthConstraint;
    final BigDecimal minRangeConstraint;
    final BigDecimal maxRangeConstraint;
    final long minLongConstraint;
    final long maxLongConstraint;
    final double minDoubleConstraint;
    final double maxDoubleConstraint;
    final ValidatorOfString stringValidation;
    boolean isRequiredByValidation;

    /**
     * The bitmask to use for this member to compute a required member bitfield. This value will match the memberIndex
     * if the member is required and has no default value. It will be zero if isRequiredByValidation == false.
     */
    long requiredByValidationBitmask;

    /**
     * The result of creating a bitfield of the memberIndex of every required member with no default value.
     * This allows for an inexpensive comparison for required structure member validation.
     */
    final long requiredStructureMemberBitfield;

    private Schema(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id is null");
        this.type = Objects.requireNonNull(builder.type, "type is null");
        this.traits = createTraitMap(builder.traits);

        // Member setting.s
        this.memberName = builder.memberName;
        this.memberTarget = builder.memberTarget;
        this.members = MemberContainers.of(
            this.type,
            builder.members,
            this.memberTarget != null ? this.memberTarget.members : null
        );
        this.memberList = builder.members == null ? Collections.emptyList() : List.copyOf(members.values());
        // Validation requires the member is present if it's required and has no default value.
        this.isRequiredByValidation = memberName != null
            && !hasTrait(DefaultTrait.class)
            && hasTrait(RequiredTrait.class);

        this.stringEnumValues = builder.stringEnumValues;
        this.intEnumValues = builder.intEnumValues;

        // Precompute an allowed range, setting Long.MIN and Long.MAX when missing.
        var lengthTrait = getTrait(LengthTrait.class);
        if (lengthTrait == null) {
            minLengthConstraint = Long.MIN_VALUE;
            maxLengthConstraint = Long.MAX_VALUE;
        } else {
            minLengthConstraint = lengthTrait.getMin().orElse(Long.MIN_VALUE);
            maxLengthConstraint = lengthTrait.getMax().orElse(Long.MAX_VALUE);
        }

        // If the shape is a string or enum, pre-compute necessary validation (or no-op if not a string/enum).
        stringValidation = createStringValidator(this, lengthTrait);

        // Range traits use BigDecimal, so use null when missing rather than any kind of default.
        var rangeTrait = getTrait(RangeTrait.class);
        if (rangeTrait != null) {
            this.minRangeConstraint = rangeTrait.getMin().orElse(null);
            this.maxRangeConstraint = rangeTrait.getMax().orElse(null);
        } else {
            this.minRangeConstraint = null;
            this.maxRangeConstraint = null;
        }

        // Pre-compute allowable ranges so this doesn't have to be looked up during validation.
        // BigInteger and BigDecimal just use the rangeConstraint BigDecimal directly.
        switch (type) {
            case BYTE -> {
                minLongConstraint = minRangeConstraint == null ? Byte.MIN_VALUE : minRangeConstraint.byteValue();
                maxLongConstraint = maxRangeConstraint == null ? Byte.MAX_VALUE : maxRangeConstraint.byteValue();
                minDoubleConstraint = Double.MIN_VALUE;
                maxDoubleConstraint = Double.MAX_VALUE;
            }
            case SHORT -> {
                minLongConstraint = minRangeConstraint == null ? Short.MIN_VALUE : minRangeConstraint.shortValue();
                maxLongConstraint = maxRangeConstraint == null ? Short.MAX_VALUE : maxRangeConstraint.shortValue();
                minDoubleConstraint = Double.MIN_VALUE;
                maxDoubleConstraint = Double.MAX_VALUE;
            }
            case INTEGER -> {
                minLongConstraint = minRangeConstraint == null ? Integer.MIN_VALUE : minRangeConstraint.intValue();
                maxLongConstraint = maxRangeConstraint == null ? Integer.MAX_VALUE : maxRangeConstraint.intValue();
                minDoubleConstraint = Double.MIN_VALUE;
                maxDoubleConstraint = Double.MAX_VALUE;
            }
            case LONG -> {
                minLongConstraint = minRangeConstraint == null ? Long.MIN_VALUE : minRangeConstraint.longValue();
                maxLongConstraint = maxRangeConstraint == null ? Long.MAX_VALUE : maxRangeConstraint.longValue();
                minDoubleConstraint = Double.MIN_VALUE;
                maxDoubleConstraint = Double.MAX_VALUE;
            }
            case FLOAT -> {
                minLongConstraint = Long.MIN_VALUE;
                maxLongConstraint = Long.MAX_VALUE;
                minDoubleConstraint = minRangeConstraint == null ? Float.MIN_VALUE : minRangeConstraint.floatValue();
                maxDoubleConstraint = maxRangeConstraint == null ? Float.MAX_VALUE : maxRangeConstraint.floatValue();
            }
            case DOUBLE -> {
                minLongConstraint = Long.MIN_VALUE;
                maxLongConstraint = Long.MAX_VALUE;
                minDoubleConstraint = minRangeConstraint == null ? Double.MIN_VALUE : minRangeConstraint.doubleValue();
                maxDoubleConstraint = maxRangeConstraint == null ? Double.MAX_VALUE : maxRangeConstraint.doubleValue();
            }
            default -> {
                minLongConstraint = Long.MIN_VALUE;
                maxLongConstraint = Long.MAX_VALUE;
                minDoubleConstraint = Double.MIN_VALUE;
                maxDoubleConstraint = Double.MAX_VALUE;
            }
        }

        // Pre-compute where the shape contains any members marked as required.
        // We only need to use the slow version of required member validation if there are > 64 required members.
        this.requiredMemberCount = computeRequiredMemberCount(this.type, this.memberTarget, this.memberList);

        if ((requiredMemberCount > 0) && (requiredMemberCount <= 64) && type == ShapeType.STRUCTURE) {
            this.requiredStructureMemberBitfield = computeRequiredBitField(members());
        } else {
            this.requiredStructureMemberBitfield = 0;
        }
    }

    private static Map<Class<? extends Trait>, Trait> createTraitMap(Trait[] traits) {
        if (traits == null) {
            return Map.of();
        } else if (traits.length == 1) {
            return Map.of(traits[0].getClass(), traits[0]);
        } else {
            var result = new HashMap<Class<? extends Trait>, Trait>(traits.length);
            for (Trait trait : traits) {
                result.put(trait.getClass(), trait);
            }
            return Collections.unmodifiableMap(result);
        }
    }

    private static ValidatorOfString createStringValidator(Schema schema, LengthTrait lengthTrait) {
        List<ValidatorOfString> stringValidators = null;

        if (schema.type == ShapeType.STRING || schema.type == ShapeType.ENUM) {
            stringValidators = new ArrayList<>();

            if (lengthTrait != null) {
                stringValidators.add(
                    new ValidatorOfString.LengthStringValidator(
                        lengthTrait.getMin().orElse(Long.MIN_VALUE),
                        lengthTrait.getMax().orElse(Long.MAX_VALUE)
                    )
                );
            }

            if (!schema.stringEnumValues.isEmpty()) {
                stringValidators.add(ValidatorOfString.EnumStringValidator.INSTANCE);
            }

            var patternTrait = schema.getTrait(PatternTrait.class);
            if (patternTrait != null) {
                stringValidators.add(new ValidatorOfString.PatternStringValidator(patternTrait.getPattern()));
            }
        }

        return ValidatorOfString.of(stringValidators);
    }

    private static int computeRequiredMemberCount(ShapeType type, Schema memberTarget, List<Schema> members) {
        if (memberTarget != null) {
            return memberTarget.requiredMemberCount;
        } else if (type != ShapeType.STRUCTURE) {
            return 0;
        } else {
            int result = 0;
            for (var member : members) {
                if (member.isRequiredByValidation) {
                    result++;
                }
            }
            return result;
        }
    }

    private static long computeRequiredBitField(Collection<Schema> members) {
        long setFields = 0L;
        for (Schema member : members) {
            setFields |= member.requiredByValidationBitmask;
        }
        return setFields;
    }

    private void setMemberIndex(int memberIndex) {
        if (this.memberIndex != -1) {
            throw new IllegalStateException(
                "Member schema already has an assigned member index of "
                    + this.memberIndex + ". Members cannot be reused across shapes. Member: " + this
            );
        }

        hashCode = 0; // reset the hashcode
        this.memberIndex = memberIndex;
        requiredByValidationBitmask = isRequiredByValidation ? 1L << memberIndex : 0L;
    }

    /**
     * Creates a builder for a non-member.
     *
     * @return Returns the created builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a builder for a member.
     *
     * @param memberName   Name of the member.
     * @param memberTarget Schema the member targets.
     * @return Returns the member builder.
     * @throws IllegalArgumentException if {@code memberIndex} is less than 1.
     */
    public static Builder memberBuilder(String memberName, Schema memberTarget) {
        Builder builder = builder();
        builder.memberTarget = Objects.requireNonNull(memberTarget, "memberTarget is null");
        builder.memberName = Objects.requireNonNull(memberName, "memberName is null");
        builder.type = memberTarget.type;
        return builder;
    }

    @Override
    public String toString() {
        return "SdkSchema{id='" + id + '\'' + ", type=" + type + '}';
    }

    /**
     * Get the shape ID of the shape.
     *
     * @return Return the shape ID.
     */
    public ShapeId id() {
        return id;
    }

    /**
     * Get the schema shape type.
     * <p>
     * Note that this will never return MEMBER as a type. Schema members act as if they are the target type.
     *
     * @return Returns the schema shape type.
     */
    public ShapeType type() {
        return type;
    }

    /**
     * Check if the schema is for a member.
     *
     * @return Returns true if this is a member.
     */
    public boolean isMember() {
        return memberName != null;
    }

    /**
     * Get the name of the member if the schema is for a member, or null if not.
     *
     * @return Returns the member name or null if not a member.
     */
    public String memberName() {
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
    public int memberIndex() {
        return memberIndex;
    }

    /**
     * Get the target of the member, or null if the schema is not a member.
     *
     * @return Member target.
     */
    public Schema memberTarget() {
        return memberTarget;
    }

    /**
     * Get a trait if present.
     *
     * @param trait Trait to get.
     * @return Returns the trait, or null if not found.
     * @param <T> Trait type to get.
     */
    @SuppressWarnings("unchecked")
    public <T extends Trait> T getTrait(Class<T> trait) {
        var t = (T) traits.get(trait);
        if (t == null && isMember()) {
            return memberTarget.getTrait(trait);
        }
        return t;
    }

    /**
     * Check if the schema has a trait.
     *
     * @param trait Trait to check for.
     * @return true if the trait is found.
     * @param <T> Trait type.
     */
    public <T extends Trait> boolean hasTrait(Class<T> trait) {
        return traits.containsKey(trait) || (isMember() && memberTarget.hasTrait(trait));
    }

    /**
     * Requires that the given trait type is found and returns it.
     *
     * @param trait Trait type to get.
     * @return Returns the found value.
     * @param <T> Trait to get.
     * @throws NoSuchElementException if the value does not exist.
     */
    public <T extends Trait> T expectTrait(Class<T> trait) {
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
        return memberList;
    }

    /**
     * Get a member by name or return a default value.
     *
     * @param memberName Member by name to get.
     * @return Returns the found member or null if not found.
     */
    public Schema member(String memberName) {
        return members.get(memberName);
    }

    /**
     * Returns true if this is a required member with no default value.
     *
     * @return true if required.
     */
    boolean isRequiredByValidation() {
        return isRequiredByValidation;
    }

    /**
     * Get the allowed values of the string.
     *
     * @return allowed string values (only relevant if not empty).
     */
    public Set<String> stringEnumValues() {
        return stringEnumValues;
    }

    /**
     * Get the allowed integer values of an integer.
     *
     * @return allowed integer values (only relevant if not empty).
     */
    public Set<Integer> intEnumValues() {
        return intEnumValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Schema sdkSchema = (Schema) o;
        return memberIndex == sdkSchema.memberIndex && Objects.equals(id, sdkSchema.id)
            && type == sdkSchema.type
            && Objects.equals(traits, sdkSchema.traits)
            && Objects.equals(members, sdkSchema.members)
            && Objects.equals(stringEnumValues, sdkSchema.stringEnumValues)
            && Objects.equals(intEnumValues, sdkSchema.intEnumValues)
            && Objects.equals(memberName, sdkSchema.memberName)
            && Objects.equals(memberTarget, sdkSchema.memberTarget);
    }

    @Override
    public int hashCode() {
        var code = hashCode;
        if (code == 0) {
            code = Objects.hash(
                id,
                type,
                traits,
                members,
                stringEnumValues,
                intEnumValues,
                memberName,
                memberTarget,
                memberIndex
            );
            hashCode = code;
        }
        return code;
    }

    public static final class Builder implements SmithyBuilder<Schema> {

        private ShapeId id;
        private ShapeType type;
        private Trait[] traits;
        private List<Schema> members;
        private String memberName;
        private Schema memberTarget;

        private Set<String> stringEnumValues = Collections.emptySet();
        private Set<Integer> intEnumValues = Collections.emptySet();

        @Override
        public Schema build() {
            return new Schema(this);
        }

        /**
         * Set the shape ID.
         *
         * <p>For members, this is the container shape ID without the member name.
         *
         * @param id Shape ID to set.
         * @return Returns the builder.
         */
        public Builder id(String id) {
            return id(ShapeId.from(id));
        }

        /**
         * Set the shape ID.
         *
         * <p>For members, this is the container shape ID without the member name.
         *
         * @param id Shape ID to set.
         * @return Returns the builder.
         */
        public Builder id(ShapeId id) {
            this.id = id;
            if (memberName != null) {
                this.id = id.withMember(memberName);
            }
            return this;
        }

        /**
         * Set the shape type.
         *
         * @param type Type to set.
         * @return Returns the builder.
         * @throws IllegalArgumentException when member, service, or resource types are given.
         */
        public Builder type(ShapeType type) {
            switch (type) {
                case MEMBER, RESOURCE -> throw new IllegalStateException("Cannot set schema type to " + type);
            }
            this.type = type;
            return this;
        }

        /**
         * Set traits on the shape.
         *
         * @param traits Traits to set.
         * @return Returns the builder.
         */
        public Builder traits(Trait... traits) {
            this.traits = traits;
            return this;
        }

        /**
         * Set members on the shape.
         *
         * @param members Members to set.
         * @return Returns the builder.
         * @throws IllegalStateException if the schema is for a member.
         */
        public Builder members(Schema... members) {
            List<Schema> result = new ArrayList<>(members.length);
            Collections.addAll(result, members);
            return membersWithOwnedList(result);
        }

        /**
         * Set members on the shape using builders.
         *
         * @param members Members to set, and the ID of the current builder is used.
         * @return Returns the builder.
         * @throws IllegalStateException if the schema is for a member.
         */
        public Builder members(Builder... members) {
            List<Schema> built = new ArrayList<>(members.length);
            for (Builder member : members) {
                built.add(member.id(id).build());
            }
            return membersWithOwnedList(built);
        }

        /**
         * Set members on the shape.
         *
         * @param members Members to set.
         * @return Returns the builder.
         * @throws IllegalStateException if the schema is for a member, or if the member is owned by another shape.
         */
        public Builder members(List<Schema> members) {
            return membersWithOwnedList(new ArrayList<>(members));
        }

        // Given a list that the builder owns and is free to mutate and keep, sort the members and store them.
        private Builder membersWithOwnedList(List<Schema> members) {
            if (memberTarget != null) {
                throw new IllegalStateException("Cannot add members to a member");
            }

            // Sort members to ensure that required members with no default come before other members.
            members.sort((a, b) -> {
                if (a.isRequiredByValidation && !b.isRequiredByValidation) {
                    return -1;
                } else if (a.isRequiredByValidation) {
                    return 0;
                } else {
                    return 1;
                }
            });

            // Assign the member index for each, checking to ensure members aren't illegally shared across shapes.
            int index = 0;
            for (var member : members) {
                member.setMemberIndex(index++);
            }

            this.members = members;
            return this;
        }

        /**
         * Set the allowed string enum values of an ENUM shape.
         *
         * <p>Enum values are stored on the schema in this way rather than as separate members to unify the enum trait
         * and enum shapes, to simplify the Smithy data model and represent enums as strings, and to reduce the amount
         * of complexity involved in validating string values against enums (for example, no need to iterate over enum
         * members to determine if a member has a matching value).
         *
         * @param stringEnumValues Allowed string values.
         * @return the builder.
         * @throws ApiException if type has not been set or is not equal to ENUM or STRING.
         */
        public Builder stringEnumValues(Set<String> stringEnumValues) {
            if (type != ShapeType.STRING && type != ShapeType.ENUM) {
                throw new ApiException("Can only set enum values for STRING or ENUM types");
            }
            this.stringEnumValues = Objects.requireNonNull(stringEnumValues);
            return this;
        }

        /**
         * Set the allowed string enum values of a STRING or ENUM shape.
         *
         * @param stringEnumValues Allowed string values.
         * @return the builder.
         * @throws ApiException if type has not been set or is not equal to ENUM or STRING.
         */
        public Builder stringEnumValues(String... stringEnumValues) {
            Set<String> values = new LinkedHashSet<>(stringEnumValues.length);
            Collections.addAll(values, stringEnumValues);
            return stringEnumValues(values);
        }

        /**
         * Set the allowed intEnum values of an INT_ENUM shape.
         *
         * <p>IntEnum values are stored on the schema in this way rather than as separate members to simplify the
         * Smithy data model and represent intEnums as integers, and to reduce the amount of complexity involved in
         * validating number values against int enum values (for example, no need to iterate over members to determine
         * if a member has a matching value).
         *
         * @param intEnumValues Allowed int values.
         * @return the builder.
         * @throws ApiException if type has not been set or is not equal to INT_ENUM.
         */
        public Builder intEnumValues(Set<Integer> intEnumValues) {
            if (type != ShapeType.INT_ENUM) {
                throw new ApiException("Can only set intEnum values for INT_ENUM types");
            }
            this.intEnumValues = Objects.requireNonNull(intEnumValues);
            return this;
        }

        /**
         * Set the allowed intEnum values of an INT_ENUM shape.
         *
         * @param intEnumValues Allowed int values.
         * @return the builder.
         * @throws ApiException if type has not been set or is not equal to INT_ENUM.
         */
        public Builder intEnumValues(Integer... intEnumValues) {
            Set<Integer> values = new LinkedHashSet<>(intEnumValues.length);
            Collections.addAll(values, intEnumValues);
            return intEnumValues(values);
        }
    }
}
