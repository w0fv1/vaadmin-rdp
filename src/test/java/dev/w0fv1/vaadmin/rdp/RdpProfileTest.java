package dev.w0fv1.vaadmin.rdp;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RdpProfileTest {
    @Test void importsWindowsProfileWithIpv6AndUnicode() {
        var bytes = ("\uFEFFfull address:s:[2001:db8::1]:3390\r\nusername:s:用户\r\n"
                + "desktopwidth:i:1560\r\ndesktopheight:i:1040").getBytes(StandardCharsets.UTF_16LE);
        var profile = RdpProfile.parse(bytes);
        assertEquals("2001:db8::1", profile.host());
        assertEquals(3390, profile.port());
        assertEquals("用户", profile.username());
        assertEquals(1560, profile.width());
        assertEquals(1040, profile.height());
    }

    @Test void rejectsInvalidPortAndMissingAddress() {
        for (String input : new String[]{"full address:s:host:65536", "username:s:alone"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> RdpProfile.parse(input.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
