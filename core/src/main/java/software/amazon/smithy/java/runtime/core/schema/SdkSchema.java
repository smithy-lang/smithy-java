/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
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
    private final List<Trait> traits;
    private final List<SdkSchema> members;
    private final String memberName;
    private final SdkSchema memberTarget;
    private final int memberIndex;

    private SdkSchema(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id is null");
        this.type = Objects.requireNonNull(builder.type, "type is null");
        this.traits = builder.traits;
        this.members = builder.members;
        this.memberIndex = builder.memberIndex;
        this.memberName = builder.memberName;
        this.memberTarget = builder.memberTarget;
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
     * @return Returns the optionally found trait.
     * @param <T> Trait type to get.
     */
    @SuppressWarnings("unchecked")
    public <T extends Trait> Optional<T> getTrait(Class<T> trait) {
        for (Trait t : traits) {
            if (trait == t.getClass()) {
                return Optional.of((T) t);
            }
        }

        return isMember() ? memberTarget().getTrait(trait) : Optional.empty();
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
        return getTrait(trait)
            .orElseThrow(() -> new NoSuchElementException("Expected trait not found: " + trait.getName()));
    }

    /**
     * Get the names of the members contained in the shape.
     *
     * @return Returns the contained member names.
     */
    public final Iterable<String> memberNames() {
        return () -> new Iterator<>() {
            private int position = 0;

            @Override
            public boolean hasNext() {
                return position < members.size();
            }

            @Override
            public String next() {
                return members.get(position++).memberName();
            }
        };
    }

    /**
     * Gets the members of the schema.
     *
     * @return Returns the members.
     */
    public final List<SdkSchema> members() {
        return Collections.unmodifiableList(members);
    }

    /**
     * Get a member by name.
     *
     * @param memberName Member by name to get.
     * @return Returns the found member or null if not found.
     */
    public final SdkSchema member(String memberName) {
        return member(memberName, null);
    }

    /**
     * Get a member from this shape by name, or generate a synthetic member that uses the given name, targets
     * {@link PreludeSchemas#DOCUMENT}, and is a member of DOCUMENT.
     *
     * @param memberName Member to get or synthesize.
     * @return the member.
     */
    public final SdkSchema documentMember(String memberName) {
        return documentMember(memberName, PreludeSchemas.DOCUMENT);
    }

    /**
     * Get a member from this shape by name, or generate a synthetic member that uses the given name, targets
     * {@link PreludeSchemas#DOCUMENT}, and is a member of DOCUMENT.
     *
     * @param memberName Member to get or synthesize.
     * @return the member.
     */
    public final SdkSchema documentMember(String memberName, SdkSchema targetSchema) {
        var result = member(memberName);
        if (result == null) {
            result = SdkSchema.memberBuilder(memberName, targetSchema).id(PreludeSchemas.DOCUMENT.id()).build();
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
        for (SdkSchema memberSchema : members) {
            if (memberSchema.memberName().equals(memberName)) {
                return memberSchema;
            }
        }
        // Delegate to the member target members if it's a member.
        if (memberTarget != null) {
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
        for (SdkSchema memberSchema : members) {
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
        for (SdkSchema member : members) {
            if (!memberPredicate.test(member)) {
                filtered.add(member);
            }
        }
        return toBuilder().members(filtered).build();
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
        return memberIndex == sdkSchema.memberIndex && Objects.equals(id, sdkSchema.id) && type == sdkSchema.type
            && Objects.equals(traits, sdkSchema.traits) && Objects.equals(members, sdkSchema.members)
            && Objects.equals(memberName, sdkSchema.memberName)
            && Objects.equals(memberTarget, sdkSchema.memberTarget);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, traits, members, memberName, memberTarget, memberIndex);
    }

    public static final class Builder implements SmithyBuilder<SdkSchema> {

        private ShapeId id;
        private ShapeType type;
        private List<Trait> traits = Collections.emptyList();
        private List<SdkSchema> members = Collections.emptyList();
        private String memberName;
        private SdkSchema memberTarget;
        private int memberIndex = -1;

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
        public Builder traits(Trait... traits) {
            return traits(Arrays.asList(traits));
        }

        /**
         * Set traits on the shape.
         *
         * @param traits Traits to set.
         * @return Returns the builder.
         */
        public Builder traits(List<Trait> traits) {
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
            this.members = members;
            return this;
        }
    }
}
