/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.math.BigDecimal;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.PatternTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.Trait;

/**
 * Builds a root-level shape that may contain members (list, map, union, structure).
 *
 * <p>Once a SchemaBuilder is built using {@link #build()}, it cannot be modified any further by adding members.
 * If a SchemaBuilder is handed to members to build up a recursive schema, then it may be built by other shapes when
 * those shapes are built.
 */
public final class SchemaBuilder {

    final ShapeId id;
    final ShapeType type;
    final TraitMap traits;
    final List<MemberSchemaBuilder> members;
    private Schema builtShape;

    SchemaBuilder(ShapeId id, ShapeType type, Trait... traits) {
        this.id = id;
        this.type = type;
        this.traits = TraitMap.create(traits);

        members = switch (type) {
            case LIST -> new ArrayList<>(1);
            case MAP -> new ArrayList<>(2);
            default -> new ArrayList<>();
        };
    }

    /**
     * Add a member to the builder.
     *
     * @param name   Name of the member.
     * @param target Shape targeted by the member.
     * @param traits Member traits.
     * @return the builder.
     * @throws IllegalStateException if the builder has already been built.
     */
    public SchemaBuilder putMember(String name, Schema target, Trait... traits) {
        return putMember(name, new MemberSchemaBuilder(id.withMember(name), target, traits));
    }

    /**
     * Used for building recursive shapes, add a member that targets a shape that hasn't been built.
     *
     * @param name   Name of the member.
     * @param target Schema builder targeted by the member.
     * @param traits Member traits.
     * @return the builder.
     * @throws IllegalStateException if the builder has already been built.
     */
    public SchemaBuilder putMember(String name, SchemaBuilder target, Trait... traits) {
        return putMember(name, new MemberSchemaBuilder(id.withMember(name), target, traits));
    }

    private SchemaBuilder putMember(String name, MemberSchemaBuilder builder) {
        if (builtShape != null) {
            throw new IllegalStateException("Cannot add a member to an already built builder");
        }

        switch (type) {
            case LIST -> {
                if (!name.equals("member")) {
                    throw new IllegalArgumentException("List shape member must be 'member': " + name);
                }
            }
            case MAP -> {
                if (!(name.equals("key") || name.equals("value"))) {
                    throw new IllegalArgumentException("Map shape member must be 'key' or 'value': " + name);
                }
            }
        }

        members.add(builder);
        return this;
    }

    /**
     * Build the shape.
     *
     * @return the built shape.
     * @throws IllegalStateException if the builder is missing any required members.
     */
    public Schema build() {
        if (builtShape != null) {
            return builtShape;
        }

        // Do some basic member validation.
        switch (type) {
            case LIST -> {
                if (members.isEmpty()) {
                    throw new IllegalStateException("List shape missing member: " + id);
                }
            }
            case MAP -> {
                if (members.size() != 2) {
                    throw new IllegalStateException("Map shape missing key or value member: " + id);
                }
            }
            case UNION -> {
                if (members.isEmpty()) {
                    throw new IllegalStateException("Union shapes require at least one member: " + id);
                }
            }
        }

        var hasRecursive = false;
        for (var builder : members) {
            if (builder.targetBuilder != null) {
                hasRecursive = true;
                break;
            }
        }

        if (hasRecursive) {
            builtShape = new DeferredRootSchema(
                type,
                id,
                traits,
                new ArrayList<>(members),
                Collections.emptySet(),
                Collections.emptySet()
            );
        } else {
            builtShape = new RootSchema(
                type,
                id,
                traits,
                new ArrayList<>(members),
                Collections.emptySet(),
                Collections.emptySet()
            );
        }

        return builtShape;
    }

    @SuppressWarnings("unchecked")
    static Map<Class<? extends Trait>, Trait> createTraitMap(Trait[] traits) {
        var size = traits == null ? 0 : traits.length;
        return switch (size) {
            case 0 -> Map.of();
            case 1 -> Map.of(traits[0].getClass(), traits[0]);
            case 2 -> new Map2<>(traits[0].getClass(), traits[0], traits[1].getClass(), traits[1]);
            default -> {
                Map.Entry<Class<? extends Trait>, Trait>[] entries = new Map.Entry[traits.length];
                for (var i = 0; i < traits.length; i++) {
                    var trait = traits[i];
                    entries[i] = Map.entry(trait.getClass(), trait);
                }
                yield Map.ofEntries(entries);
            }
        };
    }

    static int computeRequiredMemberCount(ShapeType type, Collection<MemberSchemaBuilder> members) {
        if (type != ShapeType.STRUCTURE) {
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

    static <T> long computeRequiredBitField(
        ShapeType type,
        int requiredMemberCount,
        Iterable<T> members,
        Function<T, Long> bitMaskGetter
    ) {
        if ((requiredMemberCount > 0) && (requiredMemberCount <= 64) && type == ShapeType.STRUCTURE) {
            long setFields = 0L;
            for (var member : members) {
                setFields |= bitMaskGetter.apply(member);
            }
            return setFields;
        } else {
            return 0;
        }
    }

    record ValidationState(
        long minLengthConstraint,
        long maxLengthConstraint,
        BigDecimal minRangeConstraint,
        BigDecimal maxRangeConstraint,
        long minLongConstraint,
        long maxLongConstraint,
        double minDoubleConstraint,
        double maxDoubleConstraint,
        ValidatorOfString stringValidation
    ) {
        static ValidationState of(ShapeType type, TraitMap traits, Set<String> stringEnum) {
            long minLengthConstraint;
            long maxLengthConstraint;
            BigDecimal minRangeConstraint;
            BigDecimal maxRangeConstraint;
            long minLongConstraint;
            long maxLongConstraint;
            double minDoubleConstraint;
            double maxDoubleConstraint;
            ValidatorOfString stringValidation;

            // Precompute an allowed range, setting Long.MIN and Long.MAX when missing.
            LengthTrait lengthTrait = traits.get(TraitKey.LENGTH_TRAIT);
            if (lengthTrait == null) {
                minLengthConstraint = Long.MIN_VALUE;
                maxLengthConstraint = Long.MAX_VALUE;
            } else {
                minLengthConstraint = lengthTrait.getMin().orElse(Long.MIN_VALUE);
                maxLengthConstraint = lengthTrait.getMax().orElse(Long.MAX_VALUE);
            }

            // If the shape is a string or enum, pre-compute necessary validation (or no-op if not a string/enum).
            if (type == ShapeType.STRING || type == ShapeType.ENUM) {
                stringValidation = createStringValidator(
                    stringEnum,
                    lengthTrait,
                    traits.get(TraitKey.PATTERN_TRAIT)
                );
            } else {
                stringValidation = ValidatorOfString.of(Collections.emptyList());
            }

            // Range traits use BigDecimal, so use null when missing rather than any kind of default.
            RangeTrait rangeTrait = traits.get(TraitKey.RANGE_TRAIT);
            if (rangeTrait != null) {
                minRangeConstraint = rangeTrait.getMin().orElse(null);
                maxRangeConstraint = rangeTrait.getMax().orElse(null);
            } else {
                minRangeConstraint = null;
                maxRangeConstraint = null;
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
                    minDoubleConstraint = minRangeConstraint == null
                        ? Float.MIN_VALUE
                        : minRangeConstraint.floatValue();
                    maxDoubleConstraint = maxRangeConstraint == null
                        ? Float.MAX_VALUE
                        : maxRangeConstraint.floatValue();
                }
                case DOUBLE -> {
                    minLongConstraint = Long.MIN_VALUE;
                    maxLongConstraint = Long.MAX_VALUE;
                    minDoubleConstraint = minRangeConstraint == null
                        ? Double.MIN_VALUE
                        : minRangeConstraint.doubleValue();
                    maxDoubleConstraint = maxRangeConstraint == null
                        ? Double.MAX_VALUE
                        : maxRangeConstraint.doubleValue();
                }
                default -> {
                    minLongConstraint = Long.MIN_VALUE;
                    maxLongConstraint = Long.MAX_VALUE;
                    minDoubleConstraint = Double.MIN_VALUE;
                    maxDoubleConstraint = Double.MAX_VALUE;
                }
            }

            return new ValidationState(
                minLengthConstraint,
                maxLengthConstraint,
                minRangeConstraint,
                maxRangeConstraint,
                minLongConstraint,
                maxLongConstraint,
                minDoubleConstraint,
                maxDoubleConstraint,
                stringValidation
            );
        }
    }

    static ValidatorOfString createStringValidator(
        Set<String> enumValues,
        LengthTrait lengthTrait,
        PatternTrait patternTrait
    ) {
        List<ValidatorOfString> stringValidators = new ArrayList<>();

        if (lengthTrait != null) {
            stringValidators.add(
                new ValidatorOfString.LengthStringValidator(
                    lengthTrait.getMin().orElse(Long.MIN_VALUE),
                    lengthTrait.getMax().orElse(Long.MAX_VALUE)
                )
            );
        }

        if (!enumValues.isEmpty()) {
            stringValidators.add(ValidatorOfString.EnumStringValidator.INSTANCE);
        }

        if (patternTrait != null) {
            stringValidators.add(new ValidatorOfString.PatternStringValidator(patternTrait.getPattern()));
        }

        return ValidatorOfString.of(stringValidators);
    }

    static void sortMembers(List<MemberSchemaBuilder> members) {
        if (members.size() > 1) {
            // Sort members to ensure that required members with no default come before other members.
            members.sort((a, b) -> {
                int aRequiredWithNoDefault = a.isRequiredByValidation ? 1 : 0;
                int bRequiredWithNoDefault = b.isRequiredByValidation ? 1 : 0;
                return bRequiredWithNoDefault - aRequiredWithNoDefault;
            });
        }
    }

    static void assignMemberIndex(List<MemberSchemaBuilder> members) {
        for (int i = 0; i < members.size(); i++) {
            members.get(i).setMemberIndex(i);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Schema> createMembers(List<Schema> members) {
        return switch (members.size()) {
            case 0 -> Collections.emptyMap();
            case 1 -> Collections.singletonMap(members.get(0).memberName(), members.get(0));
            case 2 -> new Map2<>(
                members.get(0).memberName(),
                members.get(0),
                members.get(1).memberName(),
                members.get(1)
            );
            default -> {
                Map.Entry<String, Schema>[] entries = new Map.Entry[members.size()];
                for (var i = 0; i < members.size(); i++) {
                    var member = members.get(i);
                    entries[i] = Map.entry(member.memberName(), member);
                }
                yield Map.ofEntries(entries);
            }
        };
    }

    // This map implementation is slightly more performant and uses less memory than Map.of.
    private static final class Map2<K, V> extends AbstractMap<K, V> {

        private final K firstKey;
        private final V firstValue;
        private final K secondKey;
        private final V secondValue;
        private final Set<Entry<K, V>> entries;

        Map2(K firstKey, V firstValue, K secondKey, V secondValue) {
            this.firstKey = firstKey;
            this.firstValue = firstValue;
            this.secondKey = secondKey;
            this.secondValue = secondValue;
            this.entries = Set.of(
                new SimpleImmutableEntry<>(firstKey, firstValue),
                new SimpleImmutableEntry<>(secondKey, secondValue)
            );
        }

        @Override
        public boolean containsKey(Object key) {
            return firstKey.equals(key) || secondKey.equals(key);
        }

        @Override
        public V get(Object key) {
            if (firstKey.equals(key)) {
                return firstValue;
            } else if (secondKey.equals(key)) {
                return secondValue;
            } else {
                return null;
            }
        }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return entries;
        }
    }
}
