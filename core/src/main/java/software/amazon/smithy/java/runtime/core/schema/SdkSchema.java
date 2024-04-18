/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.SmithyBuilder;

/**
 * Describes a generated shape with important metadata from a Smithy model.
 */
public class SdkSchema {

    private final ShapeId id;
    private final ShapeType type;
    private final Map<Class<? extends Trait>, Trait> traits;
    private final Map<String, SdkSchema> members;
    private final Set<String> stringEnumValues;
    private final Set<Integer> intEnumValues;
    private final String memberName;
    private final SdkSchema memberTarget;
    private final int memberIndex;

    private SdkSchema(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id is null");
        this.type = Objects.requireNonNull(builder.type, "type is null");
        this.traits = builder.traits == null ? Collections.emptyMap() : builder.traits;
        this.members = builder.members == null ? Collections.emptyMap() : builder.members;
        this.memberIndex = builder.memberIndex;
        this.memberName = builder.memberName;
        this.memberTarget = builder.memberTarget;
        this.stringEnumValues = builder.stringEnumValues;
        this.intEnumValues = builder.intEnumValues;
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
     */
    public static Builder memberBuilder(String memberName, SdkSchema memberTarget) {
        return memberBuilder(-1, memberName, memberTarget);
    }

    /**
     * Create a builder for a member.
     *
     * @param index        Index of the code generated member.
     * @param memberName   Name of the member.
     * @param memberTarget Schema the member targets.
     * @return Returns the member builder.
     */
    public static Builder memberBuilder(int index, String memberName, SdkSchema memberTarget) {
        Builder builder = builder();
        builder.memberTarget = Objects.requireNonNull(memberTarget, "memberTarget is null");
        builder.memberName = Objects.requireNonNull(memberName, "memberName is null");
        builder.memberIndex = index;
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
    public boolean isMember() {
        return memberName != null;
    }

    /**
     * Get the name of the member if the schema is for a member.
     *
     * @return Returns the member name.
     * @throws UnsupportedOperationException if the schema is not a member.
     */
    public String memberName() {
        if (isMember()) {
            return memberName;
        }
        throw new UnsupportedOperationException(
            "Attempted to get the member name of a schema that is not a member: "
                + this
        );
    }

    public int memberIndex() {
        return memberIndex;
    }

    /**
     * Get the target of the member.
     *
     * @return Member target.
     */
    public SdkSchema memberTarget() {
        if (isMember()) {
            return memberTarget;
        }
        throw new UnsupportedOperationException("Schema is not for a member: " + this);
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
    public Collection<SdkSchema> members() {
        return members.values();
    }

    /**
     * Get a member by name or return a default value.
     *
     * @param memberName Member by name to get.
     * @return Returns the found member or null if not found.
     */
    public SdkSchema member(String memberName) {
        return members.get(memberName);
    }

    /**
     * Lookup the memberIndex of a member contained in this shape.
     *
     * @param member Member to lookup.
     * @return the memberIndex or -1 if a member index is unknown.
     */
    public int lookupMemberIndex(SdkSchema member) {
        if (member.memberIndex > -1) {
            return member.memberIndex;
        } else {
            // if the member itself has no index, check if the member can be found by name in this schema.
            var lookup = member(member.memberName);
            return lookup == null ? -1 : lookup.memberIndex;
        }
    }

    /**
     * Get a member from this shape by name, or generate a synthetic member that uses the given name, targets
     * {@link PreludeSchemas#DOCUMENT}, and is a member of DOCUMENT.
     *
     * @param memberName Member to get or synthesize.
     * @return the member.
     */
    public SdkSchema getOrCreateDocumentMember(String memberName) {
        return getOrCreateDocumentMember(memberName, PreludeSchemas.DOCUMENT);
    }

    /**
     * Get a member from this shape by name, or generate a synthetic member that uses the given name, targets
     * {@link PreludeSchemas#DOCUMENT}, and is a member of DOCUMENT.
     *
     * @param memberName Member to get or synthesize.
     * @return the member.
     */
    public SdkSchema getOrCreateDocumentMember(String memberName, SdkSchema targetSchema) {
        var result = member(memberName);
        if (result == null) {
            result = SdkSchema.memberBuilder(-1, memberName, targetSchema).id(PreludeSchemas.DOCUMENT.id()).build();
        }
        return result;
    }

    /**
     * Get a member by name or return a default value.
     *
     * @param memberName Member by name to get.
     * @param defaultValue Default to return when not found.
     * @return Returns the found member or null if not found.
     */
    public final SdkSchema member(String memberName, SdkSchema defaultValue) {
        var m = members.get(memberName);
        if (m != null) {
            return m;
        } else if (isMember()) {
            return memberTarget.member(memberName, defaultValue);
        } else {
            return defaultValue;
        }
    }

    /**
     * Find the first member that matches the given predicate.
     *
     * @param predicate Predicate to filter members.
     * @return Returns the found member or null if none matched.
     */
    public final SdkSchema findMember(Predicate<SdkSchema> predicate) {
        for (SdkSchema memberSchema : members.values()) {
            if (predicate.test(memberSchema)) {
                return memberSchema;
            }
        }
        return null;
    }

    /**
     * Convert the shape to a builder that's populated with the state of the shape.
     *
     * @return Returns the created builder.
     */
    public Builder toBuilder() {
        Builder b = builder().id(id).traits(traits).type(type);
        b.memberIndex = memberIndex;
        b.memberName = memberName;
        b.memberTarget = memberTarget;
        return b;
    }

    /**
     * Creates a new SdkSchema that only contains members that match the given predicate.
     *
     * @param memberPredicate Predicate that returns true to keep a member.
     * @return Returns the created shape.
     */
    public SdkSchema withFilteredMembers(Predicate<SdkSchema> memberPredicate) {
        List<SdkSchema> filtered = new ArrayList<>(members.size());
        for (SdkSchema member : members.values()) {
            if (!memberPredicate.test(member)) {
                filtered.add(member);
            }
        }
        return toBuilder().members(filtered).build();
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
        SdkSchema sdkSchema = (SdkSchema) o;
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
        return Objects.hash(
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
    }

    public static final class Builder implements SmithyBuilder<SdkSchema> {

        private ShapeId id;
        private ShapeType type;
        private Map<Class<? extends Trait>, Trait> traits = null;
        private Map<String, SdkSchema> members = null;
        private String memberName;
        private SdkSchema memberTarget;
        private int memberIndex = -1;
        private Set<String> stringEnumValues = Collections.emptySet();
        private Set<Integer> intEnumValues = Collections.emptySet();

        @Override
        public SdkSchema build() {
            return new SdkSchema(this);
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
                case MEMBER, SERVICE, RESOURCE -> throw new IllegalStateException("Cannot set schema type to " + type);
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
        public Builder traits(Map<Class<? extends Trait>, Trait> traits) {
            traits(traits.values());
            return this;
        }

        /**
         * Set traits on the shape.
         *
         * @param traits Traits to set.
         * @return Returns the builder.
         */
        public Builder traits(Trait... traits) {
            return traits(Arrays.asList(traits));
        }

        /**
         * Set traits on the shape.
         *
         * @param traits Traits to set.
         * @return Returns the builder.
         */
        public Builder traits(Iterable<Trait> traits) {
            if (this.traits == null) {
                this.traits = new HashMap<>();
            }
            for (Trait trait : traits) {
                this.traits.put(trait.getClass(), trait);
            }
            return this;
        }

        /**
         * Set members on the shape.
         *
         * @param members Members to set.
         * @return Returns the builder.
         * @throws IllegalStateException if the schema is for a member.
         */
        public Builder members(SdkSchema... members) {
            return members(Arrays.asList(members));
        }

        /**
         * Set members on the shape using builders.
         *
         * @param members Members to set, and the ID of the current builder is used.
         * @return Returns the builder.
         * @throws IllegalStateException if the schema is for a member.
         */
        public Builder members(SdkSchema.Builder... members) {
            List<SdkSchema> built = new ArrayList<>(members.length);
            for (Builder member : members) {
                built.add(member.id(id).build());
            }
            return members(built);
        }

        /**
         * Set members on the shape.
         *
         * @param members Members to set.
         * @return Returns the builder.
         * @throws IllegalStateException if the schema is for a member.
         */
        public Builder members(List<SdkSchema> members) {
            if (memberTarget != null) {
                throw new IllegalStateException("Cannot add members to a member");
            }
            if (this.members == null) {
                this.members = new LinkedHashMap<>();
            }
            for (SdkSchema member : members) {
                this.members.put(member.memberName(), member);
            }
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
         * @throws SdkException if type has not been set or is not equal to ENUM or STRING.
         */
        public Builder stringEnumValues(Set<String> stringEnumValues) {
            if (type != ShapeType.STRING && type != ShapeType.ENUM) {
                throw new SdkException("Can only set enum values for STRING or ENUM types");
            }
            this.stringEnumValues = Objects.requireNonNull(stringEnumValues);
            return this;
        }

        /**
         * Set the allowed string enum values of a STRING or ENUM shape.
         *
         * @param stringEnumValues Allowed string values.
         * @return the builder.
         * @throws SdkException if type has not been set or is not equal to ENUM or STRING.
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
         * @throws SdkException if type has not been set or is not equal to INT_ENUM.
         */
        public Builder intEnumValues(Set<Integer> intEnumValues) {
            if (type != ShapeType.INT_ENUM) {
                throw new SdkException("Can only set intEnum values for INT_ENUM types");
            }
            this.intEnumValues = Objects.requireNonNull(intEnumValues);
            return this;
        }

        /**
         * Set the allowed intEnum values of an INT_ENUM shape.
         *
         * @param intEnumValues Allowed int values.
         * @return the builder.
         * @throws SdkException if type has not been set or is not equal to INT_ENUM.
         */
        public Builder intEnumValues(Integer... intEnumValues) {
            Set<Integer> values = new LinkedHashSet<>(intEnumValues.length);
            Collections.addAll(values, intEnumValues);
            return intEnumValues(values);
        }
    }
}
