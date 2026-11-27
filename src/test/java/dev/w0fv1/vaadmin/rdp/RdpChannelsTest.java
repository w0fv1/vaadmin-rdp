package dev.w0fv1.vaadmin.rdp;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RdpChannelsTest {
    @Test void advertisesClipboardAndProfileSizeInConferenceCreate() throws Exception {
        byte[] packet = RdpChannels.conference(new RdpProfile("localhost", 3389, "", "", 1560, 1040));
        String bytes = new String(packet, StandardCharsets.ISO_8859_1);
        assertTrue(bytes.contains("cliprdr\0"));
        assertTrue(bytes.contains(new String(new byte[]{0x18,6,0x10,4}, StandardCharsets.ISO_8859_1)));
        assertEquals(0x7f, packet[0]);
        assertEquals(0x65, packet[1]);
    }
}
