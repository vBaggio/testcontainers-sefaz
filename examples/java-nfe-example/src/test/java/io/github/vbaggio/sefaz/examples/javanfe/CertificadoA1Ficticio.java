package io.github.vbaggio.sefaz.examples.javanfe;

import br.com.swconsultoria.certificado.Certificado;
import br.com.swconsultoria.certificado.CertificadoService;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Certificado A1 (PKCS12) autoassinado com CNPJ, no formato que a Java_Certificado lê: o CNPJ vai
 * no Subject Alternative Name, como {@code otherName} com o OID ICP-Brasil {@code 2.16.76.1.3.3}
 * (ADR-005). Não é um certificado ICP-Brasil; só serve contra a SEFAZ fake.
 */
public final class CertificadoA1Ficticio {

    public static final String SENHA = "123456";
    private static final ASN1ObjectIdentifier OID_CNPJ = new ASN1ObjectIdentifier("2.16.76.1.3.3");

    private CertificadoA1Ficticio() {
    }

    /** Bytes do arquivo .pfx, protegido por {@link #SENHA}. */
    public static byte[] pfx(String cnpj, String razaoSocial) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();

            X500Name subject = new X500Name("CN=" + razaoSocial + ":" + cnpj + ", OU=Certificado ficticio, O=ICP-Brasil, C=BR");
            Instant agora = Instant.now();
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    subject,
                    new BigInteger(64, new SecureRandom()),
                    Date.from(agora.minus(Duration.ofDays(1))),
                    Date.from(agora.plus(Duration.ofDays(365))),
                    subject,
                    keyPair.getPublic());
            GeneralName cnpjOtherName = new GeneralName(GeneralName.otherName, new DERSequence(new ASN1Encodable[] {
                    OID_CNPJ, new DERTaggedObject(true, 0, new DEROctetString(cnpj.getBytes(StandardCharsets.US_ASCII)))
            }));
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(cnpjOtherName));
            X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                    builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));

            KeyStore pkcs12 = KeyStore.getInstance("PKCS12");
            pkcs12.load(null, null);
            pkcs12.setKeyEntry(razaoSocial, keyPair.getPrivate(), SENHA.toCharArray(), new X509Certificate[] {certificate});
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pkcs12.store(out, SENHA.toCharArray());
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar certificado fictício", e);
        }
    }

    /** {@link Certificado} da Java_Certificado pronto para {@code ConfiguracoesNfe}. */
    public static Certificado certificado(String cnpj, String razaoSocial) {
        try {
            return CertificadoService.certificadoPfxBytes(pfx(cnpj, razaoSocial), SENHA);
        } catch (Exception e) {
            throw new IllegalStateException("Java_Certificado recusou o certificado fictício", e);
        }
    }
}
