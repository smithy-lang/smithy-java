/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.auth.scheme.sigv4;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.HexFormat;

final class P256TestMath {

    static final BigInteger P =
            new BigInteger("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF", 16);
    static final BigInteger A = P.subtract(BigInteger.valueOf(3));
    static final BigInteger B =
            new BigInteger("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B", 16);
    static final BigInteger GX =
            new BigInteger("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296", 16);
    static final BigInteger GY =
            new BigInteger("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5", 16);
    static final BigInteger N =
            new BigInteger("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);

    private P256TestMath() {}

    /** {@code null} coordinates denote the point at infinity. */
    record Point(BigInteger x, BigInteger y) {
        boolean isInfinity() {
            return x == null;
        }

        boolean isOnCurve() {
            BigInteger lhs = y.multiply(y).mod(P);
            BigInteger rhs = x.multiply(x).mod(P).multiply(x).add(A.multiply(x)).add(B).mod(P);
            return lhs.equals(rhs);
        }
    }

    /** Jacobian point (X, Y, Z) with x = X/Z^2, y = Y/Z^3. Z == 0 is infinity. */
    private record Jacobian(BigInteger x, BigInteger y, BigInteger z) {
        static final Jacobian INFINITY = new Jacobian(BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO);

        boolean isInfinity() {
            return z.signum() == 0;
        }

        Point toAffine() {
            if (isInfinity()) {
                return new Point(null, null);
            }
            BigInteger zInv = z.modInverse(P);
            BigInteger zInv2 = zInv.multiply(zInv).mod(P);
            BigInteger zInv3 = zInv2.multiply(zInv).mod(P);
            return new Point(x.multiply(zInv2).mod(P), y.multiply(zInv3).mod(P));
        }
    }

    static Point multiplyG(BigInteger k) {
        return multiply(new Point(GX, GY), k);
    }

    static Point multiply(Point p, BigInteger k) {
        Jacobian base = new Jacobian(p.x, p.y, BigInteger.ONE);
        Jacobian acc = Jacobian.INFINITY;
        for (int i = k.bitLength() - 1; i >= 0; i--) {
            acc = dbl(acc);
            if (k.testBit(i)) {
                acc = add(acc, base);
            }
        }
        return acc.toAffine();
    }

    private static Jacobian dbl(Jacobian p) {
        if (p.isInfinity() || p.y.signum() == 0) {
            return Jacobian.INFINITY;
        }
        // dbl-2001-b (a = -3)
        BigInteger z2 = p.z.multiply(p.z).mod(P);
        BigInteger alpha = BigInteger.valueOf(3).multiply(p.x.subtract(z2)).multiply(p.x.add(z2)).mod(P);
        BigInteger y2 = p.y.multiply(p.y).mod(P);
        BigInteger beta = p.x.multiply(y2).mod(P);
        BigInteger x3 = alpha.multiply(alpha).subtract(beta.shiftLeft(3)).mod(P);
        BigInteger y3 = alpha.multiply(beta.shiftLeft(2).subtract(x3)).subtract(y2.multiply(y2).shiftLeft(3)).mod(P);
        BigInteger z3 = p.y.multiply(p.z).shiftLeft(1).mod(P);
        return new Jacobian(x3, y3, z3);
    }

    private static Jacobian add(Jacobian p, Jacobian q) {
        if (p.isInfinity()) {
            return q;
        }
        if (q.isInfinity()) {
            return p;
        }
        // add-2007-bl
        BigInteger z1z1 = p.z.multiply(p.z).mod(P);
        BigInteger z2z2 = q.z.multiply(q.z).mod(P);
        BigInteger u1 = p.x.multiply(z2z2).mod(P);
        BigInteger u2 = q.x.multiply(z1z1).mod(P);
        BigInteger s1 = p.y.multiply(q.z).multiply(z2z2).mod(P);
        BigInteger s2 = q.y.multiply(p.z).multiply(z1z1).mod(P);
        BigInteger h = u2.subtract(u1).mod(P);
        BigInteger r = s2.subtract(s1).shiftLeft(1).mod(P);
        if (h.signum() == 0) {
            return r.signum() == 0 ? dbl(p) : Jacobian.INFINITY;
        }
        BigInteger i = h.shiftLeft(1).modPow(BigInteger.TWO, P);
        BigInteger j = h.multiply(i).mod(P);
        BigInteger v = u1.multiply(i).mod(P);
        BigInteger x3 = r.multiply(r).subtract(j).subtract(v.shiftLeft(1)).mod(P);
        BigInteger y3 = r.multiply(v.subtract(x3)).subtract(s1.multiply(j).shiftLeft(1)).mod(P);
        BigInteger z3 = p.z.add(q.z).modPow(BigInteger.TWO, P).subtract(z1z1).subtract(z2z2).multiply(h).mod(P);
        return new Jacobian(x3, y3, z3);
    }

    static ECPublicKey publicKey(ECPrivateKey privateKey) {
        return publicKey(multiplyG(privateKey.getS()));
    }

    static ECPublicKey publicKey(Point point) {
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(new ECPoint(point.x, point.y), SigV4aKeyDerivation.p256()));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    static ECPublicKey publicKey(String hexX, String hexY) {
        return publicKey(new Point(new BigInteger(hexX, 16), new BigInteger(hexY, 16)));
    }

    static String coordinateHex(BigInteger coordinate) {
        String hex = coordinate.toString(16);
        return "0".repeat(64 - hex.length()) + hex;
    }

    static boolean verify(ECPublicKey key, byte[] data, byte[] derSignature) {
        try {
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(key);
            signature.update(data);
            return signature.verify(derSignature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    static boolean verifyHex(ECPublicKey key, byte[] data, String hexSignature) {
        int end = hexSignature.length();
        while (end > 0 && hexSignature.charAt(end - 1) == '*') {
            end--;
        }
        return verify(key, data, HexFormat.of().parseHex(hexSignature, 0, end));
    }
}
