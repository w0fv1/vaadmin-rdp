package dev.w0fv1.vaadmin.rdp;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class RdpTransportTest {
    private static final HexFormat HEX = HexFormat.of();

    @Test void requiresCredSspAndLeavesTlsBytesUnread() throws IOException {
        var input = new ByteArrayInputStream(HEX.parseHex("030000130ed00000000000020008000200000016"));
        var sent = new ByteArrayOutputStream();
        RdpTransport.negotiate(input, sent);
        assertEquals("030000130ee000000000000100080002000000", HEX.formatHex(sent.toByteArray()));
        assertEquals(0x16, input.read());
    }

    @Test void refusesPlainRdpAndTruncatedReplies() {
        for (var response : new String[]{"030000130ed000000000000200080000000000", "03000013"}) {
            assertThrows(IOException.class, () -> RdpTransport.negotiate(
                    new ByteArrayInputStream(HEX.parseHex(response)), new ByteArrayOutputStream()));
        }
    }
}
