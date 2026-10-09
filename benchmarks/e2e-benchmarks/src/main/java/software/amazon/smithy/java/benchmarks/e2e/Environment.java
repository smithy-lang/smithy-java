/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;
import software.amazon.smithy.java.core.Version;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Captures what the benchmark ran on: JVM, OS, CPU, instance type, and the smithy-java build under test.
 */
final class Environment {

    private static final String BUILD_INFO = "/META-INF/smithy-java-e2e-benchmarks/build-info.properties";
    private static final String SMITHY_JAVA_PROPERTY_PREFIX = "smithy-java.";

    private Environment() {}

    /**
     * @param instanceType the instance type to record; see {@link #instanceType(String)}
     */
    static ObjectNode capture(String instanceType) {
        var runtime = ManagementFactory.getRuntimeMXBean();
        var buildInfo = buildInfo();
        String osName = System.getProperty("os.name", "unknown");
        String osVersion = System.getProperty("os.version", "unknown");
        String arch = System.getProperty("os.arch", "unknown");
        String distribution = distribution(osName, osVersion);

        var java = Node.objectNodeBuilder()
                .withMember("version", Runtime.version().toString())
                .withMember("vendor", System.getProperty("java.vendor", "unknown"))
                .withMember("vm_name", System.getProperty("java.vm.name", "unknown"))
                .withMember("vm_version", System.getProperty("java.vm.version", "unknown"))
                .withMember("vm_arguments", Node.fromStrings(runtime.getInputArguments()))
                .build();
        var os = Node.objectNodeBuilder()
                .withMember("name", osName)
                .withMember("version", osVersion)
                .withMember("arch", arch)
                .withMember("distribution", distribution)
                .build();

        var builder = Node.objectNodeBuilder()
                .withMember("java", java)
                .withMember("os", os)
                .withMember("os_label", arch + "-" + family(osName) + " " + distribution)
                .withMember("cpu", cpuModel(osName))
                .withMember("available_processors", Runtime.getRuntime().availableProcessors())
                .withMember("instance", instanceType)
                .withMember("sdk_version", Version.VERSION)
                .withMember("commit", buildInfo.getProperty("commit", "unknown"))
                .withMember("branch", buildInfo.getProperty("branch", "unknown"))
                .withMember("dirty_worktree", Boolean.parseBoolean(buildInfo.getProperty("dirty", "false")))
                .withMember("smithy_java_system_properties", smithyJavaSystemProperties());
        if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean) {
            builder.withMember("memory_total_bytes", bean.getTotalMemorySize());
        }
        return builder.build();
    }

    private static Properties buildInfo() {
        var properties = new Properties();
        try (InputStream in = Environment.class.getResourceAsStream(BUILD_INFO)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            // Leave the defaults in place; build info is informational.
        }
        return properties;
    }

    private static ObjectNode smithyJavaSystemProperties() {
        var sorted = new TreeMap<String, String>();
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith(SMITHY_JAVA_PROPERTY_PREFIX)) {
                sorted.put(name, System.getProperty(name));
            }
        }
        var builder = Node.objectNodeBuilder();
        sorted.forEach(builder::withMember);
        return builder.build();
    }

    private static String family(String osName) {
        String lower = osName.toLowerCase(Locale.ROOT);
        if (lower.contains("linux")) {
            return "linux";
        } else if (lower.contains("mac")) {
            return "macos";
        } else if (lower.contains("windows")) {
            return "windows";
        }
        return lower.replace(' ', '-');
    }

    private static String distribution(String osName, String osVersion) {
        if (osName.toLowerCase(Locale.ROOT).contains("linux")) {
            Path release = Path.of("/etc/os-release");
            if (Files.isReadable(release)) {
                try {
                    for (String line : Files.readAllLines(release, StandardCharsets.UTF_8)) {
                        if (line.startsWith("PRETTY_NAME=")) {
                            return line.substring("PRETTY_NAME=".length()).replace("\"", "").strip();
                        }
                    }
                } catch (IOException e) {
                    // Fall through to the generic label.
                }
            }
        }
        return osName + " " + osVersion;
    }

    private static String cpuModel(String osName) {
        String lower = osName.toLowerCase(Locale.ROOT);
        if (lower.contains("linux")) {
            Path cpuinfo = Path.of("/proc/cpuinfo");
            if (Files.isReadable(cpuinfo)) {
                try {
                    String implementer = null;
                    for (String line : Files.readAllLines(cpuinfo, StandardCharsets.UTF_8)) {
                        int colon = line.indexOf(':');
                        if (colon < 0) {
                            continue;
                        }
                        String key = line.substring(0, colon).strip();
                        String value = line.substring(colon + 1).strip();
                        if (key.equals("model name")) {
                            return value;
                        } else if (key.equals("CPU implementer") && implementer == null) {
                            implementer = value;
                        }
                    }
                    if (implementer != null) {
                        return "ARM (implementer " + implementer + ")";
                    }
                } catch (IOException e) {
                    // Fall through.
                }
            }
        } else if (lower.contains("mac")) {
            String brand = command("sysctl", "-n", "machdep.cpu.brand_string");
            if (brand != null) {
                return brand;
            }
        }
        return "unknown";
    }

    /**
     * The instance type to record: the override when given, otherwise the answer from IMDSv2, otherwise
     * {@code unknown}. The IMDS lookup is a network call with short timeouts, so callers resolve it once per run.
     */
    static String instanceType(String override) {
        if (override != null && !override.isBlank()) {
            return override;
        }
        String fromImds = queryEc2InstanceType();
        return fromImds != null ? fromImds : "unknown";
    }

    private static String queryEc2InstanceType() {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            var tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://169.254.169.254/latest/api/token"))
                    .header("X-aws-ec2-metadata-token-ttl-seconds", "21600")
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(1))
                    .build();
            String token = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString()).body().strip();
            var metadataRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://169.254.169.254/latest/meta-data/instance-type"))
                    .header("X-aws-ec2-metadata-token", token)
                    .timeout(Duration.ofSeconds(1))
                    .build();
            var response = client.send(metadataRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body().strip();
            }
        } catch (Exception e) {
            // Not on EC2 or IMDS unavailable.
        }
        return null;
    }

    private static String command(String... command) {
        try {
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (process.waitFor() == 0 && !output.isEmpty()) {
                return output;
            }
        } catch (IOException e) {
            // Command unavailable.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
