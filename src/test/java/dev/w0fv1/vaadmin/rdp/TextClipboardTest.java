package dev.w0fv1.vaadmin.rdp;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TextClipboardTest {
    @Test void clearsUnavailableRemoteTextInsteadOfRetainingEarlierSelection() throws Exception {
        var received = new ArrayList<String>();
        var clipboard = new TextClipboard(ignored -> {}, received::add);
        var hex = java.util.HexFormat.of();
        clipboard.receive(hex.parseHex("0c00000003000000050001000400000041000000"));
        clipboard.receive(hex.parseHex("08000000030000000200000000000000"));
        clipboard.receive(hex.parseHex("08000000030000000500020000000000"));
        assertEquals(List.of("A", "", ""), received);
    }

    @Test void movesUnicodeBetweenPeersWithoutSharingSessionState() throws Exception {
        var aToB = new ArrayDeque<byte[]>();
        var bToA = new ArrayDeque<byte[]>();
        var received = new ArrayList<String>();
        var a = new TextClipboard(aToB::add, text -> {});
        var b = new TextClipboard(bToA::add, received::add);
        var acknowledged = a.offer("中文 abc\n第二行😀");
        b.receive(aToB.remove());
        assertFalse(acknowledged.isDone());
        while (!bToA.isEmpty()) a.receive(bToA.remove());
        b.receive(aToB.remove());
        assertTrue(acknowledged.isDone());
        assertEquals(List.of("中文 abc\n第二行😀"), received);
    }
    @Test void fragmentsFromIndependentSessionsRemainIsolated() throws Exception {
        var framesA = new ArrayDeque<byte[]>();
        var framesB = new ArrayDeque<byte[]>();
        var responsesA = new ArrayDeque<byte[]>();
        var responsesB = new ArrayDeque<byte[]>();
        var receivedA = new ArrayList<String>();
        var receivedB = new ArrayList<String>();
        var sourceA = new TextClipboard(framesA::add, ignored -> {});
        var sourceB = new TextClipboard(framesB::add, ignored -> {});
        var targetA = new TextClipboard(responsesA::add, receivedA::add);
        var targetB = new TextClipboard(responsesB::add, receivedB::add);
        String first = "甲\n".repeat(900);
        String second = "乙\n".repeat(1050);
        sourceA.offer(first);
        sourceB.offer(second);
        targetA.receive(framesA.remove());
        targetB.receive(framesB.remove());
        while (!responsesA.isEmpty()) sourceA.receive(responsesA.remove());
        while (!responsesB.isEmpty()) sourceB.receive(responsesB.remove());
        while (!framesA.isEmpty() || !framesB.isEmpty()) {
            if (!framesA.isEmpty()) targetA.receive(framesA.remove());
            if (!framesB.isEmpty()) targetB.receive(framesB.remove());
        }
        assertEquals(List.of(first), receivedA);
        assertEquals(List.of(second), receivedB);
    }
}
