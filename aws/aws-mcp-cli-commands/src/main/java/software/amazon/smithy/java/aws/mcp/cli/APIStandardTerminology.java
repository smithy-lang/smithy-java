package software.amazon.smithy.java.aws.mcp.cli;

import java.util.HashSet;
import java.util.Set;

public final class APIStandardTerminology {
    static final Set<String> commonPrefixes = Set.of("", "Batch");
    public static Set<String> readOnlyApiPrefixes = new HashSet<>();
    public static Set<String> writeApiPrefixes = new HashSet<>();

    static {
        for (String prefix: commonPrefixes) {
            for (String term: Set.of("Get", "Encrypt", "Decrypt", "List", "Search", "Describe")) {
                readOnlyApiPrefixes.add(prefix + term);
            }
        }
        for (String prefix: commonPrefixes) {
            for (String term: Set.of("Accept", "Associate", "Cancel", "Copy", "Create", "Delete", "Deregister",
                    "Disassociate", "Export", "Import", "Notify", "Post", "Put", "Reboot", "Register", "Reject",
                    "Reset", "Restore", "Send", "Start", "Stop", "Tag", "Untag", "Update")) {
                writeApiPrefixes.add(prefix + term);
            }
        }
    }
}
