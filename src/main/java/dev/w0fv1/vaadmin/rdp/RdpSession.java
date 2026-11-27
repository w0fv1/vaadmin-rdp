package dev.w0fv1.vaadmin.rdp;

import java.io.*;
import java.awt.image.BufferedImage;
import java.nio.*;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

public final class RdpSession implements AutoCloseable {
    private final SSLSocket socket;
    private final RdpChannels channels;
    private final DesktopActivation activation;
    private final DesktopImage desktop;
    private final TextClipboard clipboard;
    private final Consumer<String> failure;
    private long revision;
    private volatile boolean closed;

    private RdpSession(SSLSocket socket, RdpChannels channels, DesktopActivation activation,
                       RdpProfile profile, Consumer<String> remoteText, Consumer<String> failure) {
        this.socket = socket;
        this.channels = channels;
        this.activation = activation;
        this.desktop = new DesktopImage(profile.width(), profile.height());
        this.clipboard = new TextClipboard(data -> channels.send(channels.clipboard(), data), remoteText);
        this.failure = failure;
    }

    public static RdpSession connect(RdpProfile profile, String password, SSLContext trust,
                                     Consumer<String> remoteText, Consumer<String> failure) throws Exception {
        SSLSocket socket = RdpTransport.connect(profile, trust);
        try {
            new CredSsp(socket).authenticate(profile, password);
            var channels = new RdpChannels(socket);
            channels.join(profile);
            var activation = new DesktopActivation(channels);
            activation.start(profile);
            var session = new RdpSession(socket, channels, activation, profile, remoteText, failure);
            socket.setSoTimeout(0);
            Thread.startVirtualThread(session::readUpdates);
            return session;
        } catch (Exception e) {
            socket.close();
            throw e;
        }
    }

    private void readUpdates() {
        try {
            while (!closed) {
                var message = channels.receive();
                if (message.channel() == 0) {
                    synchronized (desktop) {
                        if (desktop.fastPath(message.payload())) revision++;
                    }
                } else if (message.channel() == channels.clipboard()) {
                    clipboard.receive(message.payload());
                } else if (message.channel() == channels.global()) {
                    var pdu = ByteBuffer.wrap(message.payload()).order(ByteOrder.LITTLE_ENDIAN);
                    if (pdu.remaining() >= 18 && (pdu.getShort(2) & 15) == 7) {
                        int type = Byte.toUnsignedInt(pdu.get(14));
                        pdu.position(18);
                        if (type == 2 && pdu.remaining() >= 2 && pdu.getShort(18) == 1) {
                            synchronized (desktop) { desktop.bitmap(pdu); revision++; }
                        } else if (type == 47 && pdu.getInt() != 0) {
                            throw new IOException("Server disconnected the session");
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (!closed) failure.accept(e.getMessage() == null ? "Remote session ended" : e.getMessage());
        } finally {
            close();
        }
    }

    public record Frame(long revision, byte[] png) {}

    public Frame frameAfter(long previous) throws IOException {
        long version;
        BufferedImage snapshot;
        synchronized (desktop) {
            version = revision;
            if (version == previous || version == 0) return null;
            var image = desktop.image;
            snapshot = new BufferedImage(image.getColorModel(), image.copyData(null), false, null);
        }
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(snapshot, "png", bytes);
        return new Frame(version, bytes.toByteArray());
    }

    public void key(int scanCode, boolean extended, boolean pressed) throws IOException {
        if (scanCode < 1 || scanCode > 127) throw new IllegalArgumentException("Invalid scan code");
        input(4, (extended ? 0x100 : 0) | (pressed ? 0 : 0x8000), scanCode, 0);
    }

    public void pointer(int flags, int x, int y) throws IOException {
        if (x < 0 || x >= desktop.image.getWidth() || y < 0 || y >= desktop.image.getHeight()) return;
        input(0x8001, flags, x, y);
    }

    private void input(int type, int flags, int first, int second) throws IOException {
        activation.data(28, DesktopActivation.buffer(16).putShort((short) 1).putShort((short) 0).putInt(0)
                .putShort((short) type).putShort((short) flags).putShort((short) first).putShort((short) second).array());
    }

    public void pasteShortcut() throws IOException {
        var events = DesktopActivation.buffer(52).putShort((short) 4).putShort((short) 0);
        int timestamp = (int) System.currentTimeMillis();
        int[] codes = {42, 82, 82, 42};
        for (int i = 0; i < codes.length; i++) {
            int flags = (i < 2 ? 0 : 0x8000) | (codes[i] == 82 ? 0x100 : 0);
            events.putInt(timestamp + i).putShort((short) 4).putShort((short) flags)
                    .putShort((short) codes[i]).putShort((short) 0);
        }
        activation.data(28, events.array());
    }

    public void offerText(String text) throws IOException {
        try {
            clipboard.offer(text).get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Clipboard interrupted", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IOException("Remote clipboard unavailable", e);
        }
    }

    @Override public void close() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) {}
    }
}
