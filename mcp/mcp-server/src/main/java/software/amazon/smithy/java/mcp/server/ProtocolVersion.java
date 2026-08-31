/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Collections;
import java.util.List;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public abstract sealed class ProtocolVersion implements Comparable<ProtocolVersion>
        permits ProtocolVersion.UnknownVersion, ProtocolVersion.v2024_11_05, ProtocolVersion.v2025_03_26,
        ProtocolVersion.v2025_06_18, ProtocolVersion.v2025_11_25 {
    public static final class v2025_11_25 extends ProtocolVersion {
        public static final v2025_11_25 INSTANCE = new v2025_11_25();

        private v2025_11_25() {
            super("2025-11-25");
        }
    }

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

    /**
     * Holder defers initialization until first use so the version subclasses are fully loaded
     * first — a plain static field on this class would read a still-null INSTANCE whenever a
     * subclass is the first member of the hierarchy to be initialized.
     */
    private static final class SupportedVersions {
        private static final List<ProtocolVersion> ALL = List.of(
                v2024_11_05.INSTANCE,
                v2025_03_26.INSTANCE,
                v2025_06_18.INSTANCE,
                v2025_11_25.INSTANCE);
        private static final ProtocolVersion LATEST = Collections.max(ALL);
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
        if (identifier == null) {
            return defaultVersion();
        }
        for (var version : SupportedVersions.ALL) {
            if (version.identifier.equals(identifier)) {
                return version;
            }
        }
        return new UnknownVersion(identifier);
    }

    public static ProtocolVersion defaultVersion() {
        return v2025_03_26.INSTANCE;
    }

    /**
     * The most recent protocol version this server supports, derived from the supported-version
     * registry.
     */
    public static ProtocolVersion latestVersion() {
        return SupportedVersions.LATEST;
    }
}
