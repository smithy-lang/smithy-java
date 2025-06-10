/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.servicebundle.bundler;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class ApiStandardTerminology {

    private static final Set<String> COMMON_PREFIXES = Set.of("Batch");
    private static final Set<String> READ_ONLY_TERMS =
            Set.of("Get", "Encrypt", "Decrypt", "List", "Search", "Describe");
    private static final Set<String> WRITE_TERMS = Set.of("Accept",
            "Associate",
            "Cancel",
            "Copy",
            "Create",
            "Delete",
            "Deregister",
            "Disassociate",
            "Export",
            "Import",
            "Notify",
            "Post",
            "Put",
            "Reboot",
            "Register",
            "Reject",
            "Reset",
            "Restore",
            "Send",
            "Start",
            "Stop",
            "Tag",
            "Untag",
            "Update");

    private static final Set<String> READ_ONLY_API_PREFIXES = withCommonsPrefixes(READ_ONLY_TERMS);
    private static final Set<String> WRITE_API_PREFIXES = withCommonsPrefixes(WRITE_TERMS);

    public static Set<String> getReadOnlyApiPrefixes() {
        return READ_ONLY_API_PREFIXES;
    }

    public static Set<String> getWriteApiPrefixes() {
        return WRITE_API_PREFIXES;
    }

    private ApiStandardTerminology() {}

    private static Set<String> withCommonsPrefixes(Collection<String> terms) {
        Set<String> prefixes = new HashSet<>(terms);
        for (String commonPrefix : COMMON_PREFIXES) {
            for (String term : terms) {
                prefixes.add(commonPrefix + term);
            }
        }
        return Collections.unmodifiableSet(prefixes);
    }
}
