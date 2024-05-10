/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.BitSet;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Tracks the presence of required fields
 */
public abstract sealed class PresenceTracker {

    /**
     * Sets a member as present.
     *
     * @param memberSchema schema of member to set
     */
    abstract void setMember(SdkSchema memberSchema);

    /**
     * Checks if a member is present.
     *
     * @param memberSchema schema of member to check.
     * @return true if member is present.
     */
    abstract boolean checkMember(SdkSchema memberSchema);

    /**
     * Checks if all required members are set.
     *
     * @return true if all required members are present.
     */
    abstract boolean allSet();

    /**
     * Gets all missing, required members.
     *
     * @return set of missing, required members.
     */
    abstract Set<String> getMissingMembers();

    /**
     * Returns the {@link PresenceTracker} to use for validating the presence.
     * of required members.
     *
     * @return StructureValidator to use for validating required members.
     */
    static PresenceTracker of(SdkSchema schema) {
        if (schema.requiredMemberCount == 0) {
            return NoOpPresenceTracker.INSTANCE;
        } else if (schema.requiredMemberCount < 64) {
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
    private static final class NoOpPresenceTracker extends PresenceTracker {
        private static final NoOpPresenceTracker INSTANCE = new NoOpPresenceTracker();

        @Override
        public void setMember(SdkSchema memberSchema) {
            // No required members to set.
        }

        @Override
        public boolean checkMember(SdkSchema memberSchema) {
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
     * Tracker for structures with less than 64 members
     */
    private static final class RequiredMemberPresenceTracker extends PresenceTracker {
        private long setBitfields = 0L;
        private final SdkSchema schema;

        RequiredMemberPresenceTracker(SdkSchema schema) {
            this.schema = schema;
        }

        @Override
        public void setMember(SdkSchema memberSchema) {
            setBitfields |= memberSchema.requiredByValidationBitmask;
        }

        @Override
        public boolean checkMember(SdkSchema schema) {
            return (setBitfields << ~schema.memberIndex()) < 0;
        }

        @Override
        public boolean allSet() {
            return schema.requiredStructureMemberBitfield == setBitfields;
        }

        @Override
        public Set<String> getMissingMembers() {
            Set<String> result = new TreeSet<>();
            for (var member : schema.members()) {
                if (member.isRequiredByValidation() && (setBitfields & member.requiredByValidationBitmask) == 0L) {
                    result.add(member.memberName());
                }
            }
            return result;
        }
    }

    /**
     * Tracker for structures with greater than 64 required members.
     */
    private static final class BigRequiredMemberPresenceTracker extends PresenceTracker {
        private final BitSet bitSet;
        private final SdkSchema schema;

        BigRequiredMemberPresenceTracker(SdkSchema schema) {
            this.schema = schema;
            this.bitSet = new BitSet(schema.members().size());
        }

        @Override
        public void setMember(SdkSchema memberSchema) {
            if (memberSchema.isRequiredByValidation()) {
                bitSet.set(memberSchema.memberIndex());
            }
        }

        @Override
        public boolean checkMember(SdkSchema memberSchema) {
            return bitSet.get(memberSchema.memberIndex());
        }

        @Override
        public boolean allSet() {
            if (bitSet.length() != schema.requiredMemberCount) {
                return false;
            }
            for (var member : schema.members()) {
                if (member.isRequiredByValidation() && !bitSet.get(member.memberIndex())) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Set<String> getMissingMembers() {
            Set<String> result = new TreeSet<>();
            for (var member : schema.members()) {
                if (member.isRequiredByValidation() && !bitSet.get(member.memberIndex())) {
                    result.add(member.memberName());
                }
            }
            return result;
        }
    }
}
