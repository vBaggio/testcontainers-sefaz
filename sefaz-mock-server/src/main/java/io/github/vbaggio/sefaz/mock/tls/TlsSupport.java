package io.github.vbaggio.sefaz.mock.tls;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsParameters;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

/** {@link SSLContext} do servidor e leitura do certificado de cliente. */
public final class TlsSupport {

    private TlsSupport() {
    }

    public static HttpsConfigurator configurator(TlsMaterial material, ClientAuth clientAuth) {
        SSLContext context = sslContext(material);
        return new HttpsConfigurator(context) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters parameters = context.getDefaultSSLParameters();
                parameters.setNeedClientAuth(clientAuth == ClientAuth.NEED);
                if (clientAuth == ClientAuth.WANT) {
                    parameters.setWantClientAuth(true);
                }
                params.setSSLParameters(parameters);
            }
        };
    }

    /** Subject do certificado apresentado pelo cliente, ou {@code null}. */
    public static String clientCertificate(HttpExchange exchange) {
        if (!(exchange instanceof HttpsExchange https)) {
            return null;
        }
        try {
            Certificate[] chain = https.getSSLSession().getPeerCertificates();
            if (chain.length > 0 && chain[0] instanceof X509Certificate x509) {
                return x509.getSubjectX500Principal().getName();
            }
        } catch (SSLPeerUnverifiedException e) {
            // Cliente não apresentou certificado.
        }
        return null;
    }

    private static SSLContext sslContext(TlsMaterial material) {
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(material.keyStore(), material.keyPassword());
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), new TrustManager[] {new AcceptAnyClient()}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao configurar TLS", e);
        }
    }

    /** Qualquer certificado de cliente é aceito; a SEFAZ fake não valida ICP-Brasil. */
    private static final class AcceptAnyClient extends X509ExtendedTrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            throw new UnsupportedOperationException("Servidor não valida certificados de servidor");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
            throw new UnsupportedOperationException("Servidor não valida certificados de servidor");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
            throw new UnsupportedOperationException("Servidor não valida certificados de servidor");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
