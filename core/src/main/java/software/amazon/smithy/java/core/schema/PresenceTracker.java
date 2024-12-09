/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.BitSet;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Tracks the presence of required fields
 */
@SmithyInternalApi
public abstract sealed class PresenceTracker {

    /**
     * Sets a member as present.
     *
     * @param memberSchema schema of member to set
     */
    public abstract void setMember(Schema memberSchema);

    /**
     * Checks if a member is present.
     *
     * @param memberSchema schema of member to check.
     * @return true if member is present.
     */
    public abstract boolean checkMember(Schema memberSchema);

    /**
     * Checks if all required members are set.
     *
     * @return false if any required members are missing.
     */
    public abstract boolean allSet();

    /**
     * Gets all missing, required members.
     *
     * @return set of missing, required members.
     */
    public abstract Set<String> getMissingMembers();

    /**
     * Checks if all required members are set and throws an exception if any are unset.
     *
     * @throws SerializationException if any required members are not set.
     */
    public void validate() {
        if (!allSet()) {
            throw new SerializationException("Missing required members: " + getMissingMembers());
        }
    }

    /**
     * Returns the {@link PresenceTracker} to use for validating the presence.
     * of required members.
     *
     * @return StructureValidator to use for validating required members.
     */
    public static PresenceTracker of(Schema schema) {
        if (schema.requiredMemberCount == 0) {
            return NoOpPresenceTracker.INSTANCE;
        } else if (schema.requiredMemberCount <= 64) {
            return new PresenceTracker.RequiredMemberPresenceTracker(schema);
        } else {
            return new PresenceTracker.BigRequiredMemberPresenceTracker(schema);
        }
    }

    /**
     * NoOp tracker that does not track the presence of any members.
     *
     * <p>Should be used for shapes with no required fields.
     */
    static final class NoOpPresenceTracker extends PresenceTracker {
        private static final NoOpPresenceTracker INSTANCE = new NoOpPresenceTracker();

        @Override
        public void setMember(Schema memberSchema) {
            // No required members to set.
        }

        @Override
        public boolean checkMember(Schema memberSchema) {
            return false;
        }

        @Override
        public boolean allSet() {
            return true;
        }

        @Override
        public Set<String> getMissingMembers() {
            return Collections.emptySet();
        }
    }

    /**
     * Tracker for structures with less than 65 required members
     */
    static final class RequiredMemberPresenceTracker extends PresenceTracker {
        private long setBitfields = 0L;
        private final Schema schema;

        RequiredMemberPresenceTracker(Schema schema) {
            this.schema = schema;
        }

        @Override
        public void setMember(Schema memberSchema) {
            setBitfields |= memberSchema.requiredByValidationBitmask;
        }

        @Override
        public boolean checkMember(Schema memberSchema) {
            return (setBitfields & memberSchema.requiredByValidationBitmask) != 0L;
        }

        @Override
        public boolean allSet() {
            return schema.requiredStructureMemberBitfield == setBitfields;
        }

        @Override
        public Set<String> getMissingMembers() {
            Set<String> result = new TreeSet<>();
            for (var member : schema.members()) {
                if (member.isRequiredByValidation && (setBitfields & member.requiredByValidationBitmask) == 0L) {
                    result.add(member.memberName());
                }
            }
            return result;
        }
    }

    /**
     * Tracker for structures with at least 65 required members.
     */
    static final class BigRequiredMemberPresenceTracker extends PresenceTracker {
        private final BitSet bitSet;
        private final Schema schema;

        BigRequiredMemberPresenceTracker(Schema schema) {
            this.schema = schema;
            this.bitSet = new BitSet(schema.members().size());
        }

        @Override
        public void setMember(Schema memberSchema) {
            if (memberSchema.isRequiredByValidation) {
                bitSet.set(memberSchema.memberIndex());
            }
        }

        @Override
        public boolean checkMember(Schema memberSchema) {
            return bitSet.get(memberSchema.memberIndex());
        }

        @Override
        public boolean allSet() {
            if (bitSet.cardinality() != schema.requiredMemberCount) {
                return false;
            }
            for (var member : schema.members()) {
                if (member.isRequiredByValidation && !bitSet.get(member.memberIndex())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Set<String> getMissingMembers() {
            Set<String> result = new TreeSet<>();
            for (var member : schema.members()) {
                if (member.isRequiredByValidation && !bitSet.get(member.memberIndex())) {
                    result.add(member.memberName());
                }
            }
            return result;
        }
    }
}
