package dev.w0fv1.vaadmin.rdp;

import java.security.GeneralSecurityException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NtlmSealTest {
    @Test void signsAndEncryptsInSequenceAndRejectsModifiedData() throws Exception {
        byte[] key = HexFormat.of().parseHex("55555555555555555555555555555555");
        var send = new NtlmSeal(key, true);
        var receive = new NtlmSeal(key, true);
        byte[] data = "Plaintext".getBytes(java.nio.charset.StandardCharsets.UTF_16LE);
        byte[] sealed = send.wrap(data);
        assertArrayEquals(data, receive.unwrap(sealed));
        byte[] second = send.wrap(data);
        assertFalse(java.util.Arrays.equals(sealed, second));
        second[16] ^= 1;
        assertThrows(GeneralSecurityException.class, () -> receive.unwrap(second));
    }
}
