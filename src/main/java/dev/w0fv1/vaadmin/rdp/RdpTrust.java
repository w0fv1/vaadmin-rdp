package dev.w0fv1.vaadmin.rdp;

import java.net.Socket;
import java.security.*;
import java.security.cert.*;
import java.util.HexFormat;
import javax.net.ssl.*;

public final class RdpTrust extends X509ExtendedTrustManager {
    private final byte[] fingerprint;
    private RdpTrust(byte[] fingerprint) { this.fingerprint = fingerprint; }

    public static SSLContext context(String sha256) throws GeneralSecurityException {
        if (sha256 == null || sha256.isBlank()) return SSLContext.getDefault();
        byte[] value = HexFormat.of().parseHex(sha256.strip().replace(":", ""));
        if (value.length != 32) throw new IllegalArgumentException("SHA-256 指纹应为 64 个十六进制字符");
        var context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{new RdpTrust(value)}, null);
        return context;
    }

    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null || chain.length == 0) throw new CertificateException("Missing RDP server certificate");
        chain[0].checkValidity();
        try {
            if (!MessageDigest.isEqual(fingerprint, MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()))) {
                throw new CertificateException("RDP server certificate fingerprint mismatch");
            }
        } catch (NoSuchAlgorithmException e) { throw new CertificateException(e); }
    }

    @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        checkServerTrusted(chain, authType);
    }
    @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        checkServerTrusted(chain, authType);
    }
    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        throw new CertificateException("RDP client trust manager");
    }
    @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        checkClientTrusted(chain, authType);
    }
    @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
        checkClientTrusted(chain, authType);
    }
    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
}
