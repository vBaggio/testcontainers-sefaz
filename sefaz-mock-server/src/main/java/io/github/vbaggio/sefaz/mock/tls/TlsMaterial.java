package io.github.vbaggio.sefaz.mock.tls;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Certificado de servidor autoassinado gerado na inicialização, com os SANs de
 * {@code SEFAZ_TLS_SANS}, e o truststore que o cliente precisa para confiar nele.
 */
public final class TlsMaterial {

    /** Senha do truststore exposto pela API admin; o cliente regrava com a senha que quiser. */
    public static final String TRUSTSTORE_PASSWORD = "changeit";
    private static final String KEY_ALIAS = "sefaz-mock-server";
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    private final KeyStore keyStore;
    private final char[] keyPassword;
    private final X509Certificate certificate;
    private final byte[] trustStorePkcs12;

    private TlsMaterial(KeyStore keyStore, char[] keyPassword, X509Certificate certificate, byte[] trustStorePkcs12) {
        this.keyStore = keyStore;
        this.keyPassword = keyPassword;
        this.certificate = certificate;
        this.trustStorePkcs12 = trustStorePkcs12;
    }

    public static TlsMaterial generate(List<String> extraSans) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            Set<String> sans = new LinkedHashSet<>(List.of("localhost", "127.0.0.1"));
            sans.addAll(extraSans);
            try {
                sans.add(InetAddress.getLocalHost().getHostName());
            } catch (UnknownHostException ignored) {
                // Sem hostname resolvível, ficam só os SANs informados.
            }

            X500Name subject = new X500Name("CN=sefaz-mock-server, O=testcontainers-sefaz");
            Instant now = Instant.now();
            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    subject,
                    new BigInteger(64, new SecureRandom()),
                    Date.from(now.minus(Duration.ofDays(1))),
                    Date.from(now.plus(Duration.ofDays(3650))),
                    subject,
                    keyPair.getPublic());
            builder.addExtension(Extension.subjectAlternativeName, false, generalNames(sans));
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
            X509Certificate certificate = new JcaX509CertificateConverter()
                    .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));

            char[] keyPassword = Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(18)).toCharArray();
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setKeyEntry(KEY_ALIAS, keyPair.getPrivate(), keyPassword, new X509Certificate[] {certificate});

            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(null, null);
            trustStore.setCertificateEntry(KEY_ALIAS, certificate);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            trustStore.store(out, TRUSTSTORE_PASSWORD.toCharArray());

            return new TlsMaterial(keyStore, keyPassword, certificate, out.toByteArray());
        } catch (GeneralSecurityException | IOException | OperatorCreationException e) {
            throw new IllegalStateException("Falha ao gerar certificado TLS", e);
        }
    }

    private static GeneralNames generalNames(Set<String> sans) {
        List<GeneralName> names = new ArrayList<>();
        for (String san : sans) {
            String value = san.trim();
            if (value.isEmpty()) {
                continue;
            }
            boolean ip = IPV4.matcher(value).matches() || value.contains(":");
            names.add(new GeneralName(ip ? GeneralName.iPAddress : GeneralName.dNSName, value));
        }
        return new GeneralNames(names.toArray(GeneralName[]::new));
    }

    public KeyStore keyStore() {
        return keyStore;
    }

    public char[] keyPassword() {
        return keyPassword.clone();
    }

    public X509Certificate certificate() {
        return certificate;
    }

    public byte[] trustStorePkcs12() {
        return trustStorePkcs12.clone();
    }

    public String certificatePem() {
        try {
            String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                    .encodeToString(certificate.getEncoded());
            return "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
