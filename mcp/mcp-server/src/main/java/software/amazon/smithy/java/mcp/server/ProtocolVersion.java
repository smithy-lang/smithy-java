/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

public abstract sealed class ProtocolVersion implements Comparable<ProtocolVersion>
        permits ProtocolVersion.UnknownVersion, ProtocolVersion.v2024_11_05, ProtocolVersion.v2025_03_26,
        ProtocolVersion.v2025_06_18 {
    public static final class v2025_06_18 extends ProtocolVersion {
        public static final v2025_06_18 INSTANCE = new v2025_06_18();

        private v2025_06_18() {
            super("2025-06-18");
        }
    }

    public static final class v2025_03_26 extends ProtocolVersion {
        public static final v2025_03_26 INSTANCE = new v2025_03_26();

        private v2025_03_26() {
            super("2025-03-26");
        }
    }

    public static final class v2024_11_05 extends ProtocolVersion {
        public static final v2024_11_05 INSTANCE = new v2024_11_05();

        private v2024_11_05() {
            super("2024-11-05");
        }
    }

    public static final class UnknownVersion extends ProtocolVersion {
        private UnknownVersion(String identifier) {
            super(identifier);
        }
    }

    private final String identifier;

    private ProtocolVersion(String identifier) {
        this.identifier = identifier;
    }

    public String identifier() {
        return identifier;
    }

    @Override
    public final int compareTo(ProtocolVersion o) {
        if (o instanceof UnknownVersion) {
            if (this instanceof UnknownVersion) {
                return 0;
            }
            return 1;
        }

        return identifier.compareTo(o.identifier);
    }

    public static ProtocolVersion version(String identifier) {
        return switch (identifier) {
            case "2024-11-05" -> v2024_11_05.INSTANCE;
            case "2025-03-26" -> v2025_03_26.INSTANCE;
            case "2025-06-18" -> v2025_06_18.INSTANCE;
            default -> new UnknownVersion(identifier);
        };
    }
}
