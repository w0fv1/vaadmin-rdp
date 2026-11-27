package dev.w0fv1.vaadmin.rdp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLSocket;
import jcifs.context.SingletonContext;
import jcifs.ntlmssp.Type1Message;
import jcifs.ntlmssp.Type2Message;
import jcifs.ntlmssp.Type3Message;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import static jcifs.ntlmssp.NtlmFlags.*;

final class CredSsp {
    private static final int REQUIRED = NTLMSSP_NEGOTIATE_SIGN | NTLMSSP_NEGOTIATE_SEAL
            | NTLMSSP_NEGOTIATE_EXTENDED_SESSIONSECURITY | NTLMSSP_NEGOTIATE_128 | NTLMSSP_NEGOTIATE_KEY_EXCH;
    private static final int OFFER = REQUIRED | NTLMSSP_NEGOTIATE_UNICODE | NTLMSSP_NEGOTIATE_NTLM
            | NTLMSSP_REQUEST_TARGET | NTLMSSP_NEGOTIATE_ALWAYS_SIGN;
    private final SSLSocket socket;

    CredSsp(SSLSocket socket) {
        this.socket = socket;
    }

    void authenticate(RdpProfile profile, String password) throws IOException, GeneralSecurityException {
        var context = SingletonContext.getInstance();
        String workstation = "VAADMIN";
        var type1 = new Type1Message(context, OFFER, profile.domain(), workstation);
        byte[] negotiate = type1.toByteArray();
        send(negoToken(negotiate));
        var challenge = receive();
        var tokens = ASN1Sequence.getInstance(challenge.get(1));
        var item = ASN1Sequence.getInstance(tokens.getObjectAt(0));
        byte[] raw = ASN1OctetString.getInstance(ASN1TaggedObject.getInstance(item.getObjectAt(0)), true).getOctets();
        var type2 = new Type2Message(raw);
        int negotiated = type1.getFlags() & type2.getFlags();
        if ((negotiated & REQUIRED) != REQUIRED) throw new IOException("NTLM confidentiality requirements not met");
        var type3 = new Type3Message(context, type2, "TERMSRV/" + profile.host(), password, profile.domain(),
                profile.username(), workstation, negotiated, true);
        type3.setupMIC(negotiate, raw);
        byte[] key = type3.getMasterKey();
        var outgoing = new NtlmSeal(key, true);
        var incoming = new NtlmSeal(key, false);
        var cert = socket.getSession().getPeerCertificates()[0];
        byte[] publicKey = SubjectPublicKeyInfo.getInstance(cert.getPublicKey().getEncoded()).getPublicKeyData().getOctets();
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);
        send(negoToken(type3.toByteArray()), field(3, outgoing.wrap(binding("Client-To-Server", nonce, publicKey))),
                field(5, nonce));
        var proof = receive();
        if (!proof.containsKey(3) || !MessageDigest.isEqual(binding("Server-To-Client", nonce, publicKey),
                incoming.unwrap(ASN1OctetString.getInstance(proof.get(3)).getOctets()))) {
            throw new GeneralSecurityException("Server failed CredSSP public key binding");
        }
        var credentials = new DERSequence(new ASN1Encodable[]{
                field(0, profile.domain().getBytes(StandardCharsets.UTF_16LE)),
                field(1, profile.username().getBytes(StandardCharsets.UTF_16LE)),
                field(2, password.getBytes(StandardCharsets.UTF_16LE))});
        byte[] delegated = new DERSequence(new ASN1Encodable[]{
                new DERTaggedObject(0, new ASN1Integer(1)), field(1, credentials.getEncoded())}).getEncoded();
        try {
            send(field(2, outgoing.wrap(delegated)));
        } finally {
            Arrays.fill(delegated, (byte) 0);
        }
    }

    private static byte[] binding(String direction, byte[] nonce, byte[] key) throws GeneralSecurityException {
        var sha = MessageDigest.getInstance("SHA-256");
        sha.update(("CredSSP " + direction + " Binding Hash\0").getBytes(StandardCharsets.US_ASCII));
        sha.update(nonce);
        return sha.digest(key);
    }

    private static DERTaggedObject field(int number, byte[] data) {
        return new DERTaggedObject(number, new DEROctetString(data));
    }

    private static DERTaggedObject negoToken(byte[] data) {
        return new DERTaggedObject(1, new DERSequence(new DERSequence(field(0, data))));
    }

    private void send(ASN1Encodable... fields) throws IOException {
        var message = new ASN1EncodableVector();
        message.add(new DERTaggedObject(0, new ASN1Integer(6)));
        for (var field : fields) message.add(field);
        socket.getOutputStream().write(new DERSequence(message).getEncoded());
        socket.getOutputStream().flush();
    }

    private Map<Integer, ASN1Object> receive() throws IOException {
        var reader = new ASN1InputStream(socket.getInputStream(), 1 << 20);
        var sequence = ASN1Sequence.getInstance(reader.readObject());
        if (sequence == null) throw new IOException("Server closed during CredSSP");
        var fields = new HashMap<Integer, ASN1Object>();
        for (var entry : sequence) {
            var tagged = ASN1TaggedObject.getInstance(entry);
            if (fields.put(tagged.getTagNo(), tagged.getExplicitBaseObject()) != null) {
                throw new IOException("Duplicate CredSSP field");
            }
        }
        if (fields.containsKey(4)) {
            int status = ASN1Integer.getInstance(fields.get(4)).getValue().intValue();
            if (status != 0) throw new IOException("CredSSP rejected: 0x" + Integer.toHexString(status));
        }
        if (!fields.containsKey(0) || ASN1Integer.getInstance(fields.get(0)).intValueExact() < 5) {
            throw new IOException("CredSSP server lacks nonce binding");
        }
        return fields;
    }
}
