/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class TestCertificateGenerator {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static CertificateBundle generateCertificates() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);

        KeyPair caKeyPair = keyGen.generateKeyPair();
        X509Certificate caCert = generateCACertificate(caKeyPair);

        KeyPair serverKeyPair = keyGen.generateKeyPair();
        X509Certificate serverCert = generateServerCertificate(serverKeyPair, caKeyPair, caCert);

        return new CertificateBundle(caCert, serverCert, serverKeyPair.getPrivate());
    }

    private static X509Certificate generateCACertificate(KeyPair keyPair) throws Exception {
        var issuer = new X500Name("CN=Test CA, O=Test, C=US");
        var serial = BigInteger.valueOf(System.currentTimeMillis());
        var notBefore = new Date();
        var notAfter = new Date(notBefore.getTime() + 365L * 24 * 60 * 60 * 1000);

        // Create extension utils for key identifiers
        var extUtils = new JcaX509ExtensionUtils();
        var subjectKeyIdentifier = extUtils.createSubjectKeyIdentifier(keyPair.getPublic());

        var certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                notBefore,
                notAfter,
                issuer,
                keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                .addExtension(Extension.keyUsage,
                        true,
                        new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign))
                .addExtension(Extension.subjectKeyIdentifier, false, subjectKeyIdentifier);

        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        var certHolder = certBuilder.build(signer);

        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }

    private static X509Certificate generateServerCertificate(
            KeyPair serverKeyPair,
            KeyPair caKeyPair,
            X509Certificate caCert
    ) throws Exception {
        // Get the issuer directly from the CA cert to ensure exact match
        var issuer = X500Name.getInstance(caCert.getSubjectX500Principal().getEncoded());
        var subject = new X500Name("CN=localhost, O=Test, C=US");
        var serial = BigInteger.valueOf(System.currentTimeMillis());
        var notBefore = new Date();
        var notAfter = new Date(notBefore.getTime() + 365L * 24 * 60 * 60 * 1000);

        var sanNames = new GeneralName[] {
                new GeneralName(GeneralName.dNSName, "localhost"),
                new GeneralName(GeneralName.iPAddress, "127.0.0.1")
        };

        // Create extension utils for key identifiers
        var extUtils = new JcaX509ExtensionUtils();
        var subjectKeyIdentifier = extUtils.createSubjectKeyIdentifier(serverKeyPair.getPublic());
        var authorityKeyIdentifier = extUtils.createAuthorityKeyIdentifier(caCert.getPublicKey());

        var certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serial,
                notBefore,
                notAfter,
                subject,
                serverKeyPair.getPublic())
                .addExtension(Extension.subjectAlternativeName, false, new GeneralNames(sanNames))
                .addExtension(Extension.keyUsage,
                        true,
                        new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment))
                .addExtension(Extension.extendedKeyUsage,
                        false,
                        new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth))
                .addExtension(Extension.subjectKeyIdentifier, false, subjectKeyIdentifier)
                .addExtension(Extension.authorityKeyIdentifier, false, authorityKeyIdentifier);

        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(caKeyPair.getPrivate());
        var certHolder = certBuilder.build(signer);

        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }

    public static class CertificateBundle {
        public final X509Certificate caCertificate;
        public final X509Certificate serverCertificate;
        public final PrivateKey serverPrivateKey;

        public CertificateBundle(
                X509Certificate caCertificate,
                X509Certificate serverCertificate,
                PrivateKey serverPrivateKey
        ) {
            this.caCertificate = caCertificate;
            this.serverCertificate = serverCertificate;
            this.serverPrivateKey = serverPrivateKey;
        }
    }
}
