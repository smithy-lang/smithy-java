/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A mutable user agent available for all requests across any protocol.
 *
 * <p>Protocols that support a user-agent (like HTTP) should use this user-agent to set a default user-agent header.
 *
 * <p>User agents resemble the following:
 *
 * <pre>{@code
 * smithy-java/0.1 ua/2.1 os/mac#10.1Lang/java#17.0.0 custom/value other-custom/value1,value2
 * }</pre>
 *
 * <p>Keys and values set on the user-agent are automatically sanitized such that any of the following characters
 * are replaced with "_": {@code [() ,/:;<=>?@[]{}\]}.
 */
public final class SmithyUserAgent {

    private static final String UA_VERSION = "2.1";
    // TODO: add a constant to core that gets updated on each release.
    private static final String SMITHY_VERSION = "0.1";
    private static final SmithyUserAgent DEFAULT;
    private static final String STATIC_SEGMENT;

    private static final String UA_DENYLIST_CHARS = "[() ,/:;<=>?@[]{}\\]";
    private static final char REPLACEMENT = '_';
    private static final int ASCII_LIMIT = 128; // For ASCII characters
    private static final boolean[] DENYLIST = new boolean[ASCII_LIMIT];

    static {
        for (char c : UA_DENYLIST_CHARS.toCharArray()) {
            DENYLIST[c] = true;
        }

        STATIC_SEGMENT = "smithy-java/" + SMITHY_VERSION
            + " ua/" + UA_VERSION
            + " os/" + getOsFamily() + "#" + sanitizeValue(System.getProperty("os.version"))
            + " lang/java#" + sanitizeValue(System.getProperty("java.version"))
            + ' ';
        DEFAULT = new SmithyUserAgent();
        DEFAULT.toString(); // create and cache the toString value.
    }

    private String toolName;
    private String toolVersion;
    private String executionEnv;
    private String cachedValue;

    private final Map<String, List<String>> pairs;

    private SmithyUserAgent() {
        executionEnv = System.getenv("AWS_EXECUTION_ENV");
        pairs = new LinkedHashMap<>();
    }

    private SmithyUserAgent(SmithyUserAgent from) {
        this.toolName = from.toolName;
        this.toolVersion = from.toolVersion;
        this.executionEnv = from.executionEnv;
        this.cachedValue = from.cachedValue;
        this.pairs = new LinkedHashMap<>(from.pairs);
    }

    /**
     * Create a user agent initialized with default values.
     *
     * @return the initialized user agent.
     */
    public static SmithyUserAgent create() {
        return createFrom(DEFAULT);
    }

    /**
     * Create a user agent initialized with default values.
     *
     * @return the initialized user agent.
     */
    public static SmithyUserAgent createFrom(SmithyUserAgent from) {
        return new SmithyUserAgent(from);
    }

    @Override
    public String toString() {
        if (cachedValue == null) {
            StringBuilder result = new StringBuilder();
            if (toolName != null) {
                result.append(toolName).append('/').append(toolVersion).append(' ');
            }
            result.append(STATIC_SEGMENT);
            if (executionEnv != null) {
                result.append(" exec-env/").append(executionEnv).append(' ');
            }
            for (var entry : pairs.entrySet()) {
                for (String value : entry.getValue()) {
                    result.append(entry.getKey()).append('/').append(value).append(' ');
                }
            }
            // Remove the trailing space.
            result.deleteCharAt(result.length() - 1);
            cachedValue = result.toString();
        }

        return cachedValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (!(o instanceof SmithyUserAgent)) {
            return false;
        }
        SmithyUserAgent that = (SmithyUserAgent) o;
        return Objects.equals(toolName, that.toolName)
            && Objects.equals(toolVersion, that.toolVersion)
            && Objects.equals(executionEnv, that.executionEnv)
            && Objects.equals(pairs, that.pairs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolName, toolVersion, executionEnv, pairs);
    }

    /**
     * Set the tool name and version to prepend to the front of the user-agent.
     *
     * @param toolName    Name of the tool (e.g., "my-client").
     * @param toolVersion Version of the tool (e.g., "2.0.0").
     */
    public void setTool(String toolName, String toolVersion) {
        if (toolName == null) {
            this.toolName = null;
            this.toolVersion = null;
        } else {
            this.toolName = sanitizeValue(toolName);
            this.toolVersion = sanitizeValue(Objects.requireNonNull(toolVersion, "Missing toolVersion"));
        }
        cachedValue = null;
    }

    /**
     * Get the currently set tool name.
     *
     * @return the tool name, or null.
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * Get the currently set tool version.
     *
     * @return the tool version, or null.
     */
    public String getToolVersion() {
        return toolVersion;
    }

    /**
     * Set the execution environment of the user agent (e.g., "lambda").
     *
     * <p>This will add a pair named "exec-env" (e.g., "exec-env/lambda").
     *
     * @param executionEnv Name of the environment to set.
     */
    public void setExecutionEnv(String executionEnv) {
        if (executionEnv == null) {
            this.executionEnv = null;
        } else {
            this.executionEnv = sanitizeValue(executionEnv);
        }
        cachedValue = null;
    }

    /**
     * Get the set execution environment.
     *
     * @return the execution environment or null.
     */
    public String getExecutionEnv() {
        return executionEnv;
    }

    /**
     * Add or replace a custom pair on the user agent in the form of "key/value".
     *
     * @param key   Name of the pair to set.
     * @param value Value to set.
     */
    public void putEntry(String key, String value) {
        List<String> values = new ArrayList<>();
        values.add(sanitizeValue(Objects.requireNonNull(value)));
        pairs.put(sanitizeValue(Objects.requireNonNull(key)), values);
        cachedValue = null;
    }

    /**
     * Create a pair or append to the value of an existing pair using a ",".
     *
     * <p>For example, {@code ua.appendToEntry("foo", "bar")} would turn "foo/bam" to "foo/bam,bar").
     *
     * @param key   Name of the pair to append.
     * @param value Value to append.
     */
    public void appendToEntry(String key, String value) {
        key = sanitizeValue(Objects.requireNonNull(key));
        value = sanitizeValue(Objects.requireNonNull(value));
        var current = pairs.computeIfAbsent(key, k -> new ArrayList<>());
        if (current.isEmpty()) {
            current.add(value);
        } else {
            current.set(current.size() - 1, current.get(current.size() - 1) + ',' + value);
        }
        cachedValue = null;
    }

    /**
     * Adds an entry to the UA string for a multi-value entry that can be repeated multiple times.
     *
     * @param key   Key to add.
     * @param value Value to set.
     */
    public void addEntry(String key, String value) {
        pairs.computeIfAbsent(sanitizeValue(Objects.requireNonNull(key)), k -> new ArrayList<>())
            .add(sanitizeValue(Objects.requireNonNull(value)));
        cachedValue = null;
    }

    /**
     * Get the names of each set entry.
     *
     * @return currently set names.
     */
    public Set<String> getEntryNames() {
        return pairs.keySet();
    }

    /**
     * Get the currently set value of an entry, or null if not present.
     *
     * @param key Entry to get.
     * @return the first value of the entry, or null.
     */
    public String getFirstEntry(String key) {
        var result = pairs.get(key);
        return result == null || result.isEmpty() ? null : result.get(0);
    }

    /**
     * Get the currently set value of an entry, or an empty list.
     *
     * @param key Entry to get.
     * @return the entry values.
     */
    public List<String> getEntry(String key) {
        var result = pairs.get(key);
        return result == null ? Collections.emptyList() : Collections.unmodifiableList(result);
    }

    private static String getOsFamily() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (osName.contains("win")) {
            return "windows";
        } else if (osName.contains("mac")) {
            return "macos";
        } else if (osName.contains("nux") || osName.contains("nix") || osName.contains("aix")) {
            return "linux";
        } else if (osName.contains("android")) {
            return "android";
        } else if (osName.contains("ios")) {
            return "ios";
        } else if (osName.contains("watchos")) {
            return "watchos";
        } else if (osName.contains("tvos")) {
            return "tvos";
        } else if (osName.contains("visionos")) {
            return "visionos";
        } else {
            return "other";
        }
    }

    private static String sanitizeValue(String input) {
        if (input == null) {
            return "unknown";
        }

        for (var i = 0; i < input.length(); i++) {
            var c = input.charAt(i);
            if (c < ASCII_LIMIT && DENYLIST[c]) {
                return sanitizeValueSlow(input);
            }
        }
        return input;
    }

    private static String sanitizeValueSlow(String input) {
        StringBuilder result = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c < ASCII_LIMIT && DENYLIST[c]) {
                result.append(REPLACEMENT);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
