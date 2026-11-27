package dev.w0fv1.vaadmin.rdp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class NtlmSeal {
    private final Mac signature;
    private final Cipher stream;
    private int sequence;

    NtlmSeal(byte[] masterKey, boolean fromClient) throws GeneralSecurityException {
        String direction = fromClient ? "client-to-server" : "server-to-client";
        signature = Mac.getInstance("HmacMD5");
        signature.init(new SecretKeySpec(derive(masterKey, direction, "signing"), "HmacMD5"));
        stream = Cipher.getInstance("RC4");
        stream.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(derive(masterKey, direction, "sealing"), "RC4"));
    }

    private static byte[] derive(byte[] key, String direction, String purpose) throws GeneralSecurityException {
        var hash = MessageDigest.getInstance("MD5");
        hash.update(key);
        return hash.digest(("session key to " + direction + " " + purpose + " key magic constant\0")
                .getBytes(StandardCharsets.US_ASCII));
    }

    private byte[] checksum(byte[] plaintext) {
        signature.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sequence).array());
        return stream.update(Arrays.copyOf(signature.doFinal(plaintext), 8));
    }

    byte[] wrap(byte[] plaintext) {
        byte[] encrypted = stream.update(plaintext);
        return ByteBuffer.allocate(16 + plaintext.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1).put(checksum(plaintext)).putInt(sequence++).put(encrypted).array();
    }

    byte[] unwrap(byte[] message) throws GeneralSecurityException {
        if (message.length <= 16) throw new GeneralSecurityException("Truncated NTLM sealed message");
        var input = ByteBuffer.wrap(message).order(ByteOrder.LITTLE_ENDIAN);
        if (input.getInt() != 1 || input.getInt(12) != sequence) {
            throw new GeneralSecurityException("Invalid NTLM signature sequence");
        }
        byte[] plaintext = stream.update(message, 16, message.length - 16);
        if (!MessageDigest.isEqual(Arrays.copyOfRange(message, 4, 12), checksum(plaintext))) {
            throw new GeneralSecurityException("NTLM signature verification failed");
        }
        sequence++;
        return plaintext;
    }
}
