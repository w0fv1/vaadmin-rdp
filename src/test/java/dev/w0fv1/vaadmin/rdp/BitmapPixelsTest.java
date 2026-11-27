package dev.w0fv1.vaadmin.rdp;

import java.nio.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BitmapPixelsTest {
    @Test void decodesColorRunAndPriorScanline() {
        var data = ByteBuffer.wrap(new byte[]{0x62,0,(byte)0xf8,2}).order(ByteOrder.LITTLE_ENDIAN);
        assertArrayEquals(new int[]{0xf800,0xf800,0xf800,0xf800}, BitmapPixels.decode(data,2,2,16));
    }
}
