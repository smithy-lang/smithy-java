/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AwsHomeResolverTest {

    @Test
    void homeEnvVariableWinsOnAllPlatforms() {
        Map<String, String> env = Map.of("HOME", "/home/alice");
        Path home = AwsHomeResolver.resolveHome(env::get, propsFor("Mac OS X", null));
        assertEquals(Paths.get("/home/alice"), home);
    }

    @Test
    void userProfileIsIgnoredOnNonWindowsWhenPlatformKnown() {
        Map<String, String> env = new HashMap<>();
        env.put("USERPROFILE", "C:/Users/alice");
        env.put("HOMEDRIVE", "C:");
        env.put("HOMEPATH", "/Users/alice");
        Path home = AwsHomeResolver.resolveHome(env::get, propsFor("Linux", "/home/linux-user"));
        assertEquals(Paths.get("/home/linux-user"), home);
    }

    @Test
    void userProfileUsedOnWindows() {
        Map<String, String> env = Map.of("USERPROFILE", "C:/Users/alice");
        Path home = AwsHomeResolver.resolveHome(env::get, propsFor("Windows 11", null));
        assertEquals(Paths.get("C:/Users/alice"), home);
    }

    @Test
    void homeDriveAndHomePathOnWindowsWhenUserProfileAbsent() {
        Map<String, String> env = Map.of("HOMEDRIVE", "D:", "HOMEPATH", "/Users/bob");
        Path home = AwsHomeResolver.resolveHome(env::get, propsFor("Windows 10", null));
        assertEquals(Paths.get("D:/Users/bob"), home);
    }

    @Test
    void platformUnknownFallsBackToWindowsVars() {
        // SEP: if the platform is indeterminate, also consult USERPROFILE / HOMEDRIVE+HOMEPATH.
        Map<String, String> env = Map.of("USERPROFILE", "C:/Users/u");
        Path home = AwsHomeResolver.resolveHome(env::get, propsFor(null, null));
        assertEquals(Paths.get("C:/Users/u"), home);
    }

    @Test
    void userHomeSystemPropertyIsFinalFallback() {
        Function<String, String> noEnv = k -> null;
        Path home = AwsHomeResolver.resolveHome(noEnv, propsFor("Linux", "/home/fallback"));
        assertEquals(Paths.get("/home/fallback"), home);
    }

    @Test
    void nullReturnedWhenNothingResolves() {
        Path home = AwsHomeResolver.resolveHome(k -> null, propsFor("Linux", null));
        assertNull(home);
    }

    @Test
    void tildeAloneExpandsToHome() {
        assertEquals(Paths.get("/home/u"), AwsHomeResolver.expandTilde("~", Paths.get("/home/u")));
    }

    @Test
    void tildeSlashExpandsToHomeSubpath() {
        assertEquals(Paths.get("/home/u/.aws/config"),
                AwsHomeResolver.expandTilde("~/.aws/config", Paths.get("/home/u")));
    }

    @Test
    void tildeBackslashAlsoExpands() {
        assertEquals(Paths.get("/home/u").resolve(".aws/config"),
                AwsHomeResolver.expandTilde("~\\.aws/config", Paths.get("/home/u")));
    }

    @Test
    void nonTildePathReturnedUnchanged() {
        assertEquals(Paths.get("/tmp/x"), AwsHomeResolver.expandTilde("/tmp/x", Paths.get("/home/u")));
    }

    @Test
    void tildeUsernameFormIsLeftAlone() {
        // "~alice/..." is not supported (SEP marks it should, not must).
        assertEquals(Paths.get("~alice/.aws/config"),
                AwsHomeResolver.expandTilde("~alice/.aws/config", Paths.get("/home/u")));
    }

    @Test
    void tildeWithoutHomeReturnedUnchanged() {
        assertEquals(Paths.get("~/.aws/config"),
                AwsHomeResolver.expandTilde("~/.aws/config", null));
    }

    private static Function<String, String> propsFor(String osName, String userHome) {
        Map<String, String> props = new HashMap<>();
        if (osName != null) {
            props.put("os.name", osName);
        }
        if (userHome != null) {
            props.put("user.home", userHome);
        }
        return props::get;
    }
}
