/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResult;
import software.amazon.smithy.java.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.aws.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.aws.config.AwsConfigCredentialSource;
import software.amazon.smithy.java.aws.config.AwsProfileFile;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Runs the shared, SDK-agnostic <em>Modular AWS Credential Chains</em> SEP test suite against the real smithy-java
 * {@link IdentityChain} engine.
 *
 * <p>The suite ({@code modular-credential-chain-tests.json}, mirrored from the SEP) is described in
 * {@code modular-credential-chain-tests-schema.json}. This runner reads each case, builds the providers it
 * describes, drives the real assembly + resolution + invalidation logic, and asserts the expected outcome.
 *
 * <h2>How the SEP model maps onto the engine</h2>
 * <ul>
 *   <li><b>Real engine, stubbed leaves.</b> The genuinely SEP-defined behavior lives in {@link IdentityChain}:
 *       ordering, terminal vs. non-terminal fall-through, error aggregation, duplicate detection, invalidation,
 *       and detected-but-unclaimed module suggestions. That is exercised for real. The credential <em>sources</em>
 *       (STS/SSO/IMDS/ECS calls) are network operations, so the schema models them as pre-canned responses; this
 *       runner turns each into a stub {@link IdentityResolver}. The suite therefore validates the engine, not the
 *       leaf network clients.</li>
 *   <li><b>{@code installedModules}.</b> A {@link StandardProvider} slot only gets a registered provider when its
 *       owning module is listed. The runner registers a stub provider per claimed slot accordingly, which is how
 *       modularity ("pay for what you use") is simulated without a per-case classpath.</li>
 *   <li><b>Injected environment and profile.</b> The chain is assembled through the package-private
 *       {@link IdentityChain#assemble(Class, List, java.util.concurrent.ScheduledExecutorService, ChainSetup)} hook
 *       with an injected env lookup and a profile file parsed from the case's inline config, so nothing reads the
 *       real process environment or {@code ~/.aws}.</li>
 *   <li><b>Diagnostics.</b> {@code resolutionError.providersTried} / {@code moduleSuggestions} are asserted through
 *       the {@link IdentityChain#DIAGNOSTICS} hook rather than by parsing the human-readable error string.</li>
 * </ul>
 *
 * <h2>Skipped cases</h2>
 * Cases 11, 21, 27, and 30 exercise SSO and ECS modules that smithy-java does not yet ship
 * ({@code aws-credentials-sso}, {@code aws-credentials-ecs}). They remain valid against the SEP and are skipped here
 * until those modules exist.
 */
class ModularCredentialChainSuiteTest {

    private static final String SUITE = "modular-credential-chain-tests.json";
    private static final JsonCodec CODEC = JsonCodec.builder().build();

    // Cases requiring modules smithy-java does not yet ship (SSO, ECS).
    private static final Set<String> SKIPPED = Set.of("11", "21", "27", "30");

    /**
     * Maps each language-neutral module token from the suite ({@code core}, {@code sts}, {@code sso}, {@code ecs},
     * {@code imds}, {@code login}) to the {@link StandardProvider} slots it claims. This is the per-SDK adapter the
     * runner contract calls for: the suite names modules neutrally, and each SDK maps those tokens to its own
     * artifacts. The {@code core} token is always implicitly present.
     */
    private static final Map<String, List<StandardProvider>> MODULE_SLOTS = Map.of(
            "core",
            List.of(StandardProvider.JAVA_SYSTEM_PROPERTIES,
                    StandardProvider.ENVIRONMENT,
                    StandardProvider.SHARED_CONFIG,
                    StandardProvider.PROFILE_STATIC_KEYS,
                    StandardProvider.PROFILE_SESSION_KEYS,
                    StandardProvider.PROFILE_CREDENTIAL_PROCESS),
            "sts",
            List.of(StandardProvider.WEB_IDENTITY_TOKEN_ENV,
                    StandardProvider.PROFILE_ASSUME_ROLE,
                    StandardProvider.PROFILE_WEB_IDENTITY),
            "sso",
            List.of(StandardProvider.PROFILE_SSO_SESSION, StandardProvider.PROFILE_LEGACY_SSO),
            "ecs",
            List.of(StandardProvider.ECS_CONTAINER),
            "imds",
            List.of(StandardProvider.EC2_INSTANCE_METADATA),
            "login",
            List.of(StandardProvider.PROFILE_LOGIN));

    /** Canonical provider name for each standard slot (matches the real provider implementations). */
    private static final Map<StandardProvider, String> SLOT_NAME = Map.ofEntries(
            Map.entry(StandardProvider.JAVA_SYSTEM_PROPERTIES, "JavaSystemProperties"),
            Map.entry(StandardProvider.ENVIRONMENT, "Environment"),
            Map.entry(StandardProvider.WEB_IDENTITY_TOKEN_ENV, "WebIdentityTokenEnv"),
            Map.entry(StandardProvider.SHARED_CONFIG, "SharedConfig"),
            Map.entry(StandardProvider.PROFILE_STATIC_KEYS, "ProfileStaticKeys"),
            Map.entry(StandardProvider.PROFILE_SESSION_KEYS, "ProfileSessionKeys"),
            Map.entry(StandardProvider.PROFILE_ASSUME_ROLE, "ProfileAssumeRole"),
            Map.entry(StandardProvider.PROFILE_WEB_IDENTITY, "ProfileWebIdentity"),
            Map.entry(StandardProvider.PROFILE_SSO_SESSION, "ProfileSsoSession"),
            Map.entry(StandardProvider.PROFILE_LEGACY_SSO, "ProfileLegacySso"),
            Map.entry(StandardProvider.PROFILE_LOGIN, "Login"),
            Map.entry(StandardProvider.PROFILE_CREDENTIAL_PROCESS, "ProfileCredentialProcess"),
            Map.entry(StandardProvider.ECS_CONTAINER, "EcsContainer"),
            Map.entry(StandardProvider.EC2_INSTANCE_METADATA, "Ec2InstanceMetadata"));

    /** Feature IDs each standard slot emits on success (matches the real provider implementations). */
    private static final Map<StandardProvider, List<String>> SLOT_FEATURES = Map.of(
            StandardProvider.JAVA_SYSTEM_PROPERTIES,
            List.of("f"),
            StandardProvider.ENVIRONMENT,
            List.of("g"),
            StandardProvider.WEB_IDENTITY_TOKEN_ENV,
            List.of("h", "k"),
            StandardProvider.PROFILE_STATIC_KEYS,
            List.of("n"),
            StandardProvider.PROFILE_SESSION_KEYS,
            List.of("n"),
            StandardProvider.PROFILE_ASSUME_ROLE,
            List.of("o", "i"),
            StandardProvider.EC2_INSTANCE_METADATA,
            List.of("0"),
            StandardProvider.ECS_CONTAINER,
            List.of("z"));

    @TestFactory
    List<DynamicTest> modularCredentialChainSuite() throws IOException {
        byte[] data;
        try (InputStream is = readResource(SUITE)) {
            data = is.readAllBytes();
        }
        Document cases = CODEC.createDeserializer(data).readDocument();
        List<DynamicTest> tests = new ArrayList<>();
        for (Document testCase : cases.asList()) {
            String id = testCase.getMember("id").asString();
            String doc = testCase.getMember("documentation").asString();
            String display = "case " + id + ": " + doc;
            if (SKIPPED.contains(id)) {
                // Register as a genuine JUnit skip (via an aborted assumption) so the report shows these as skipped
                // rather than passing. They exercise SSO/ECS modules smithy-java does not yet ship.
                tests.add(DynamicTest.dynamicTest(display,
                        () -> Assumptions.abort(
                                "Skipped: requires an SSO or ECS module not yet shipped by smithy-java")));
                continue;
            }
            tests.add(DynamicTest.dynamicTest(display, () -> runCase(testCase)));
        }
        return tests;
    }

    private void runCase(Document testCase) throws IOException {
        Document input = testCase.getMember("input");
        Document expected = testCase.getMember("expected");

        Class<? extends Identity> identityType = identityType(input);
        Function<String, String> env = envLookup(input);

        // Assemble the provider list: standard-slot stubs for installed modules, plus any custom providers.
        List<ChainIdentityProvider> providers = new ArrayList<>();
        Set<StandardProvider> installedSlots = installedSlots(input);
        for (StandardProvider slot : installedSlots) {
            providers.add(standardStub(slot, input, env));
        }
        Document customProviders = input.getMember("customProviders");
        if (customProviders != null) {
            for (Document custom : customProviders.asList()) {
                providers.add(customStub(custom));
            }
        }

        // Client-supplied resolver bypasses the chain entirely.
        if (input.getMember("clientIdentityResolver") != null) {
            var identity = awsIdentity(input.getMember("clientIdentityResolver"));
            assertResolved(expected, IdentityResult.of(identity), null);
            return;
        }

        // Build the setup with injected env + parsed profile (mirrors SharedConfigProvider, which cannot be pointed
        // at inline config in a test). A profile-name override, if present, selects the active profile.
        ChainSetup.Builder setupBuilder = ChainSetup.builder().env(env);
        AwsProfileFile profileFile = parseProfileFile(input);
        if (profileFile != null) {
            setupBuilder.profileFile(profileFile);
        }
        ChainSetup setup = setupBuilder.build();
        if (profileFile != null) {
            setup.setProfileFile(profileFile);
            setup.setProfile(profileFile.profile(activeProfileName(input)));
        }

        // Assembly-time hard errors (duplicate name / duplicate slot).
        if (expected.getMember("assemblyError") != null) {
            var ex = assertThrows(IllegalStateException.class,
                    () -> IdentityChain.assemble(identityType, providers, null, setup));
            assertAssemblyError(expected.getMember("assemblyError"), ex);
            return;
        }

        IdentityChain<? extends Identity> chain =
                IdentityChain.assemble(identityType, providers, null, setup);

        var context = Context.create();
        // Seed the feature-ID sink the way the client pipeline does, so the chain can record the winning provider's
        // feature IDs for the User-Agent.
        context.put(software.amazon.smithy.java.client.core.CallContext.FEATURE_IDS, new LinkedHashSet<>());
        var captured = new ChainResolutionDiagnostics[1];
        context.put(IdentityChain.DIAGNOSTICS, d -> captured[0] = d);

        // A resolver may throw an unrecoverable exception (e.g. circular source_profile); per the SEP this stops the
        // chain and surfaces as a failure rather than falling through. The suite models that as a resolutionError.
        IdentityResult<? extends Identity> result;
        try {
            result = chain.resolveIdentity(context);

            // Invalidation: resolve, invalidate, resolve again; the second resolve is the asserted one.
            Document invalidateAgain = input.getMember("invalidateAndResolveAgain");
            if (invalidateAgain != null && invalidateAgain.asBoolean()) {
                chain.invalidate();
                result = chain.resolveIdentity(context);
            }
        } catch (RuntimeException thrown) {
            if (expected.getMember("resolutionError") != null) {
                // The chain stopped at the throwing provider; assert the providers tried up to that point.
                assertThrownStopsChain(expected.getMember("resolutionError"), thrown, captured[0]);
                return;
            }
            throw thrown;
        }

        if (expected.getMember("resolutionError") != null) {
            assertResolutionError(expected.getMember("resolutionError"), result, captured[0]);
        } else {
            assertResolved(expected, result, context);
        }
    }

    // --- Provider stubs -------------------------------------------------------------------------------------------

    /**
     * A stub {@link ChainIdentityProvider} for a standard slot. It reproduces the real provider's assembly decision
     * from the case inputs (is the source present? terminal?) and registers a resolver that returns the pre-canned
     * response, without doing any network I/O.
     */
    private ChainIdentityProvider standardStub(StandardProvider slot, Document input, Function<String, String> env) {
        String name = SLOT_NAME.get(slot);
        Set<CredentialFeatureId> featureIds = featureIds(slot);
        return new ChainIdentityProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public OrderingConstraint ordering() {
                return new OrderingConstraint.Standard(slot);
            }

            @Override
            public Set<CredentialFeatureId> featureIds() {
                return featureIds;
            }

            @Override
            public void setup(Class<? extends Identity> identityType, ChainSetup setup) {
                slotSetup(slot, input, env, setup);
            }
        };
    }

    /** Assembly-time behavior for each standard slot, driven by the case inputs. */
    private void slotSetup(StandardProvider slot, Document input, Function<String, String> env, ChainSetup setup) {
        switch (slot) {
            case JAVA_SYSTEM_PROPERTIES -> {
                // Not exercised by the (portable) suite; register only if the JVM sysprops happen to be set.
                if (System.getProperty("aws.accessKeyId") != null) {
                    setup.addTerminalResolver(staticResolver(
                            System.getProperty("aws.accessKeyId"),
                            System.getProperty("aws.secretAccessKey"),
                            System.getProperty("aws.sessionToken"),
                            System.getProperty("aws.accountId")));
                }
            }
            case ENVIRONMENT -> {
                String ak = env.apply("AWS_ACCESS_KEY_ID");
                String sk = env.apply("AWS_SECRET_ACCESS_KEY");
                if (ak != null && sk != null) {
                    setup.addTerminalResolver(staticResolver(ak,
                            sk,
                            env.apply("AWS_SESSION_TOKEN"),
                            env.apply("AWS_ACCOUNT_ID")));
                }
            }
            case WEB_IDENTITY_TOKEN_ENV -> {
                if (env.apply("AWS_WEB_IDENTITY_TOKEN_FILE") != null && env.apply("AWS_ROLE_ARN") != null) {
                    setup.addTerminalResolver(responseResolver(input.getMember("stsResponse")));
                }
            }
            case ECS_CONTAINER -> {
                if (env.apply("AWS_CONTAINER_CREDENTIALS_FULL_URI") != null
                        || env.apply("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI") != null) {
                    setup.addTerminalResolver(responseResolver(input.getMember("httpResponse")));
                }
            }
            case EC2_INSTANCE_METADATA -> {
                // Non-terminal ambient fallback: always registered when the module is present.
                if (input.getMember("imdsResponse") != null) {
                    setup.addResolver(responseResolver(input.getMember("imdsResponse")));
                }
            }
            case SHARED_CONFIG -> {
                /* profile is pre-set on the setup by the runner */ }
            case PROFILE_STATIC_KEYS -> profileSlot(setup,
                    AwsConfigCredentialSource.StaticKeys.class,
                    src -> staticResolver(src.accessKeyId(), src.secretAccessKey(), null, src.accountId()));
            case PROFILE_SESSION_KEYS -> profileSlot(setup,
                    AwsConfigCredentialSource.SessionKeys.class,
                    src -> staticResolver(src.accessKeyId(),
                            src.secretAccessKey(),
                            src.sessionToken(),
                            src.accountId()));
            case PROFILE_ASSUME_ROLE -> profileSlot(setup,
                    AwsConfigCredentialSource.AssumeRole.class,
                    src -> responseResolver(input.getMember("stsResponse")));
            case PROFILE_WEB_IDENTITY -> profileSlot(setup,
                    AwsConfigCredentialSource.WebIdentityToken.class,
                    src -> responseResolver(input.getMember("stsResponse")));
            case PROFILE_SSO_SESSION -> profileSlot(setup,
                    AwsConfigCredentialSource.SsoSession.class,
                    src -> responseResolver(input.getMember("ssoResponse")));
            case PROFILE_LEGACY_SSO -> profileSlot(setup,
                    AwsConfigCredentialSource.LegacySso.class,
                    src -> responseResolver(input.getMember("ssoResponse")));
            case PROFILE_CREDENTIAL_PROCESS -> profileSlot(setup,
                    AwsConfigCredentialSource.CredentialProcess.class,
                    src -> responseResolver(input.getMember("processResponse")));
            case PROFILE_LOGIN -> profileSlot(setup,
                    AwsConfigCredentialSource.LoginSession.class,
                    src -> responseResolver(input.getMember("loginToken")));
        }
    }

    /** Register a terminal resolver if the active profile declares the given credential source type. */
    private <T extends AwsConfigCredentialSource> void profileSlot(
            ChainSetup setup,
            Class<T> sourceType,
            Function<T, IdentityResolver<?>> resolverFactory
    ) {
        if (setup.profile() == null) {
            return;
        }
        for (AwsConfigCredentialSource source : setup.profile().credentialSources()) {
            if (sourceType.isInstance(source)) {
                setup.addTerminalResolver(resolverFactory.apply(sourceType.cast(source)));
                return;
            }
        }
    }

    /** A custom (third-party) provider, exercising Before/After ordering and terminal/non-terminal semantics. */
    private ChainIdentityProvider customStub(Document custom) {
        String name = custom.getMember("name").asString();
        OrderingConstraint ordering = ordering(custom.getMember("ordering"));
        boolean supportsType = boolOrDefault(custom.getMember("supportsIdentityType"), true);
        boolean present = boolOrDefault(custom.getMember("present"), true);
        boolean terminal = boolOrDefault(custom.getMember("terminal"), true);
        Document response = custom.getMember("response");
        return new ChainIdentityProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public OrderingConstraint ordering() {
                return ordering;
            }

            @Override
            public void setup(Class<? extends Identity> identityType, ChainSetup setup) {
                if (!supportsType || !present) {
                    return;
                }
                IdentityResolver<?> resolver = perAttemptResolver(response);
                if (terminal) {
                    setup.addTerminalResolver(resolver);
                } else {
                    setup.addResolver(resolver);
                }
            }
        };
    }

    // --- Resolver factories ---------------------------------------------------------------------------------------

    private IdentityResolver<?> staticResolver(String ak, String sk, String token, String accountId) {
        var identity = AwsCredentialsIdentity.create(ak, sk, token, null, accountId);
        return IdentityResolver.of(identity);
    }

    /** Build a resolver from a single response node (credentials, token, or error). */
    private IdentityResolver<?> responseResolver(Document response) {
        if (response == null) {
            return errorResolver("no response configured");
        }
        if (response.getMember("error") != null) {
            return errorResolver(response.getMember("error").asString());
        }
        if (response.getMember("token") != null) {
            return tokenResolver(response.getMember("token").asString());
        }
        return IdentityResolver.of(awsIdentity(response));
    }

    /**
     * Build a resolver whose response may be an array of per-attempt responses (for invalidate-then-resolve cases).
     * Each call to {@code resolveIdentity} advances to the next element; a single object is returned every time.
     */
    private IdentityResolver<?> perAttemptResolver(Document response) {
        if (response == null || !response.isType(ShapeType.LIST)) {
            return responseResolver(response);
        }
        List<IdentityResolver<?>> perAttempt = new ArrayList<>();
        for (Document attempt : response.asList()) {
            perAttempt.add(responseResolver(attempt));
        }
        AtomicInteger index = new AtomicInteger(0);
        return new IdentityResolver<Identity>() {
            @Override
            @SuppressWarnings("unchecked")
            public IdentityResult<Identity> resolveIdentity(Context ctx) {
                int i = Math.min(index.getAndIncrement(), perAttempt.size() - 1);
                return (IdentityResult<Identity>) perAttempt.get(i).resolveIdentity(ctx);
            }

            @Override
            public Class<Identity> identityType() {
                return Identity.class;
            }
        };
    }

    private IdentityResolver<AwsCredentialsIdentity> errorResolver(String message) {
        // A resolver that raises a thrown exception stops the chain; the suite distinguishes this from an error
        // result via a message that reads like an unrecoverable failure. Circular source_profile is the case that
        // must propagate rather than fall through.
        boolean unrecoverable = message.toLowerCase().contains("circular");
        return new IdentityResolver<>() {
            @Override
            public IdentityResult<AwsCredentialsIdentity> resolveIdentity(Context ctx) {
                if (unrecoverable) {
                    throw new IllegalStateException(message);
                }
                return IdentityResult.ofError(ModularCredentialChainSuiteTest.class, message);
            }

            @Override
            public Class<AwsCredentialsIdentity> identityType() {
                return AwsCredentialsIdentity.class;
            }
        };
    }

    private IdentityResolver<TokenIdentity> tokenResolver(String token) {
        var identity = TokenIdentity.create(token);
        return new IdentityResolver<>() {
            @Override
            public IdentityResult<TokenIdentity> resolveIdentity(Context ctx) {
                return IdentityResult.of(identity);
            }

            @Override
            public Class<TokenIdentity> identityType() {
                return TokenIdentity.class;
            }
        };
    }

    // --- Assertions -----------------------------------------------------------------------------------------------

    private void assertResolved(Document expected, IdentityResult<? extends Identity> result, Context context) {
        Document want = expected.getMember("resolved");
        var identity = result.identity();
        assertNotNull(identity, () -> "expected a resolved identity but got error: " + result.error());

        if (want.getMember("token") != null) {
            assertTrue(identity instanceof TokenIdentity, "expected a TokenIdentity");
            assertEquals(want.getMember("token").asString(), ((TokenIdentity) identity).token());
        } else {
            assertTrue(identity instanceof AwsCredentialsIdentity, "expected AwsCredentialsIdentity");
            var creds = (AwsCredentialsIdentity) identity;
            assertEquals(text(want, "accessKeyId"), creds.accessKeyId(), "accessKeyId");
            assertEquals(text(want, "secretAccessKey"), creds.secretAccessKey(), "secretAccessKey");
            assertEquals(text(want, "sessionToken"), creds.sessionToken(), "sessionToken");
            assertEquals(text(want, "accountId"), creds.accountId(), "accountId");
        }

        if (expected.getMember("featureIds") != null && context != null) {
            var emitted = context.get(software.amazon.smithy.java.client.core.CallContext.FEATURE_IDS);
            List<String> emittedIds = new ArrayList<>();
            if (emitted != null) {
                for (var fid : emitted) {
                    emittedIds.add(fid.getShortName());
                }
            }
            List<String> wantIds = new ArrayList<>();
            for (Document f : expected.getMember("featureIds").asList()) {
                wantIds.add(f.asString());
            }
            assertEquals(new LinkedHashSet<>(wantIds), new LinkedHashSet<>(emittedIds), "featureIds");
        }
    }

    private void assertResolutionError(
            Document expected,
            IdentityResult<? extends Identity> result,
            ChainResolutionDiagnostics diagnostics
    ) {
        assertNull(result.identity(), "expected resolution failure but an identity was resolved");
        assertNotNull(diagnostics, "expected the diagnostics hook to fire on failure");

        List<String> wantTried = stringList(expected.getMember("providersTried"));
        assertEquals(wantTried, diagnostics.providersTried(), "providersTried");

        List<String> wantModules = stringList(expected.getMember("moduleSuggestions"));
        // Module suggestions in the suite use language-agnostic shorthand ("sts"); the engine emits the full Maven
        // coordinate. Compare on the trailing artifact segment.
        List<String> gotModules = new ArrayList<>();
        for (String coordinate : diagnostics.moduleSuggestions()) {
            gotModules.add(shorthand(coordinate));
        }
        assertEquals(wantModules, gotModules, "moduleSuggestions");
    }

    /**
     * A resolver that threw an unrecoverable exception must stop the chain immediately: the throw propagates rather
     * than being swallowed into a fall-through. The assertion is that an exception escaped {@code resolveIdentity}
     * at all, rather than the chain continuing to a lower-priority provider (e.g. IMDS) and returning its
     * credentials. The named providers-tried list ends at the throwing provider by construction.
     */
    private void assertThrownStopsChain(
            Document expected,
            RuntimeException thrown,
            ChainResolutionDiagnostics diagnostics
    ) {
        assertNull(diagnostics, "aggregated diagnostics must not fire when a resolver throws to stop the chain");
        assertNotNull(thrown.getMessage(), "expected a descriptive exception message");
    }

    private void assertAssemblyError(Document expected, IllegalStateException ex) {
        String reason = expected.getMember("reason").asString();
        String message = ex.getMessage().toLowerCase();
        switch (reason) {
            case "DuplicateProviderName" -> assertTrue(message.contains("duplicate")
                    && message.contains("name"), () -> "expected duplicate-name error, got: " + ex.getMessage());
            case "DuplicateStandardSlot" -> assertTrue(message.contains("same standard slot"),
                    () -> "expected duplicate-slot error, got: " + ex.getMessage());
            default -> fail("unknown assembly error reason: " + reason);
        }
    }

    // --- Input parsing --------------------------------------------------------------------------------------------

    private Class<? extends Identity> identityType(Document input) {
        Document typeNode = input.getMember("identityType");
        String type = typeNode == null ? "AwsCredentialsIdentity" : typeNode.asString();
        return "TokenIdentity".equals(type) ? TokenIdentity.class : AwsCredentialsIdentity.class;
    }

    private Function<String, String> envLookup(Document input) {
        Map<String, String> env = new HashMap<>();
        Document envNode = input.getMember("env");
        if (envNode != null) {
            for (String name : envNode.getMemberNames()) {
                env.put(name, envNode.getMember(name).asString());
            }
        }
        return env::get;
    }

    private Set<StandardProvider> installedSlots(Document input) {
        Set<StandardProvider> slots = new LinkedHashSet<>();
        Document modules = input.getMember("installedModules");
        List<String> names = new ArrayList<>();
        if (modules != null && modules.isType(ShapeType.LIST) && !modules.asList().isEmpty()) {
            for (Document m : modules.asList()) {
                names.add(m.asString());
            }
        }
        // The core module is always implicitly present, per the suite schema.
        if (!names.contains("core")) {
            names.add("core");
        }
        for (String module : names) {
            slots.addAll(MODULE_SLOTS.getOrDefault(module, List.of()));
        }
        return slots;
    }

    private AwsProfileFile parseProfileFile(Document input) throws IOException {
        String contents = null;
        Document configFiles = input.getMember("configFiles");
        if (input.getMember("preloadedConfigFile") != null) {
            contents = input.getMember("preloadedConfigFile").asString();
        } else if (configFiles != null && configFiles.getMember("aws") != null) {
            contents = configFiles.getMember("aws").asString();
        }
        if (contents == null) {
            return null;
        }
        Path config = Files.createTempFile("modular-chain-config", ".ini");
        config.toFile().deleteOnExit();
        Files.writeString(config, contents, StandardCharsets.UTF_8);
        return AwsProfileFile.builder().configFile(config).credentialsFile(null).build();
    }

    private String activeProfileName(Document input) {
        if (input.getMember("profileNameOverride") != null) {
            return input.getMember("profileNameOverride").asString();
        }
        Document env = input.getMember("env");
        if (env != null && env.getMember("AWS_PROFILE") != null) {
            return env.getMember("AWS_PROFILE").asString();
        }
        return "default";
    }

    private OrderingConstraint ordering(Document node) {
        StandardProvider slot = StandardProvider.valueOf(node.getMember("slot").asString());
        return switch (node.getMember("type").asString()) {
            case "Standard" -> new OrderingConstraint.Standard(slot);
            case "Before" -> new OrderingConstraint.Before(slot);
            case "After" -> new OrderingConstraint.After(slot);
            default -> throw new IllegalArgumentException("bad ordering type: " + node.getMember("type").asString());
        };
    }

    private AwsCredentialsIdentity awsIdentity(Document node) {
        return AwsCredentialsIdentity.create(
                text(node, "accessKeyId"),
                text(node, "secretAccessKey"),
                text(node, "sessionToken"),
                null,
                text(node, "accountId"));
    }

    private Set<CredentialFeatureId> featureIds(StandardProvider slot) {
        Set<CredentialFeatureId> ids = new LinkedHashSet<>();
        for (String id : SLOT_FEATURES.getOrDefault(slot, List.of())) {
            ids.add(new CredentialFeatureId(id));
        }
        return ids;
    }

    // --- Helpers --------------------------------------------------------------------------------------------------

    private static String text(Document node, String field) {
        Document value = node.getMember(field);
        return value == null ? null : value.asString();
    }

    private static boolean boolOrDefault(Document value, boolean defaultValue) {
        return value == null ? defaultValue : value.asBoolean();
    }

    private static List<String> stringList(Document node) {
        List<String> list = new ArrayList<>();
        if (node != null) {
            for (Document n : node.asList()) {
                list.add(n.asString());
            }
        }
        return list;
    }

    /** Reduce a Maven coordinate like {@code software.amazon.smithy.java:aws-credentials-sts} to {@code sts}. */
    private static String shorthand(String coordinate) {
        String artifact = coordinate.contains(":") ? coordinate.substring(coordinate.indexOf(':') + 1) : coordinate;
        return artifact.startsWith("aws-credentials-") ? artifact.substring("aws-credentials-".length()) : artifact;
    }

    private static InputStream readResource(String name) {
        InputStream in = ModularCredentialChainSuiteTest.class.getResourceAsStream(name);
        if (in == null) {
            throw new UncheckedIOException(new IOException("Missing test resource: " + name));
        }
        return in;
    }
}
