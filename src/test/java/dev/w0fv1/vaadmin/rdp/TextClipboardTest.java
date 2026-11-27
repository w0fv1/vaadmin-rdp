package dev.w0fv1.vaadmin.rdp;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TextClipboardTest {
    @Test void movesUnicodeBetweenPeersWithoutSharingSessionState() throws Exception {
        var aToB = new ArrayDeque<byte[]>();
        var bToA = new ArrayDeque<byte[]>();
        var received = new ArrayList<String>();
        var a = new TextClipboard(aToB::add, text -> {});
        var b = new TextClipboard(bToA::add, received::add);
        a.offer("中文 abc\n第二行😀");
        b.receive(aToB.remove());
        while (!bToA.isEmpty()) a.receive(bToA.remove());
        b.receive(aToB.remove());
        assertEquals(List.of("中文 abc\n第二行😀"), received);
    }
}
