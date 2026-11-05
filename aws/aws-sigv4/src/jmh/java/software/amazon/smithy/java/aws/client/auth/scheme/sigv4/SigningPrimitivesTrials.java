/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.security.MessageDigest;
import java.security.Security;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class SigningPrimitivesTrials {

    private static final Random RANDOM = new Random(1);

    @Param({"yes", "no"})
    private String useAccpParam;

    private MessageDigest digest;
    private Mac mac;
    private SigningResources resources;
    private byte[] canonical;
    private byte[] stringToSign;
    private byte[] key;
    private byte[] out;

    @Setup
    public void setup() throws Exception {
        var clazz = Class.forName("com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider");
        if (useAccpParam.equals("yes")) {
            clazz.getMethod("install").invoke(null);
        } else {
            Security.removeProvider(clazz.getName());
        }
        digest = MessageDigest.getInstance("SHA-256");
        mac = Mac.getInstance("HmacSHA256");
        resources = new SigningResources();
        canonical = new byte[300];
        RANDOM.nextBytes(canonical);
        stringToSign = new byte[120];
        RANDOM.nextBytes(stringToSign);
        key = new byte[32];
        RANDOM.nextBytes(key);
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        out = new byte[32];
    }

    @Benchmark
    public byte[] sha256_300B() throws Exception {
        digest.update(canonical);
        digest.digest(out, 0, 32);
        return out;
    }

    @Benchmark
    public byte[] hmac_clonedPads_120B() {
        return resources.hmac(key, stringToSign, 0, stringToSign.length, out);
    }

    @Benchmark
    public byte[] hmac_mac_120B() throws Exception {
        mac.update(stringToSign);
        mac.doFinal(out, 0);
        return out;
    }
}
