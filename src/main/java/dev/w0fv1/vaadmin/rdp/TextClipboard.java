package dev.w0fv1.vaadmin.rdp;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import static dev.w0fv1.vaadmin.rdp.DesktopActivation.buffer;

final class TextClipboard {
    interface Sender { void send(byte[] packet) throws IOException; }
    private static final int LIMIT = 4 * 1024 * 1024;
    private final Sender sender;
    private final Consumer<String> remote;
    private final ByteArrayOutputStream fragments = new ByteArrayOutputStream();
    private String local = "";
    private final java.util.ArrayDeque<java.util.concurrent.CompletableFuture<Void>> announcements = new java.util.ArrayDeque<>();
    private int expected;

    TextClipboard(Sender sender, Consumer<String> remote) {
        this.sender = sender;
        this.remote = remote;
    }

    synchronized java.util.concurrent.CompletableFuture<Void> offer(String text) throws IOException {
        if (text.length() > LIMIT / 4 - 8) throw new IOException("Clipboard text too large");
        local = text;
        var acknowledged = new java.util.concurrent.CompletableFuture<Void>();
        announcements.add(acknowledged);
        send(2, 0, buffer(36).putInt(13).array());
        return acknowledged;
    }

    synchronized void receive(byte[] chunk) throws IOException {
        var part = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN);
        if (part.remaining() < 8) throw new IOException("Missing clipboard channel header");
        int length = part.getInt();
        int flags = part.getInt();
        if ((flags & 1) != 0) { fragments.reset(); expected = length; }
        if (length < 8 || length > LIMIT || length != expected || part.remaining() > length - fragments.size()) {
            throw new IOException("Invalid clipboard fragment");
        }
        fragments.write(chunk, 8, part.remaining());
        if ((flags & 2) == 0) return;
        if (fragments.size() != expected) throw new IOException("Incomplete clipboard message");
        var message = ByteBuffer.wrap(fragments.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        fragments.reset();
        expected = 0;
        int type = Short.toUnsignedInt(message.getShort());
        int result = Short.toUnsignedInt(message.getShort());
        if (message.getInt() != message.remaining()) throw new IOException("Invalid clipboard payload");
        switch (type) {
            case 1 -> {
                send(7, 0, buffer(16).putShort((short) 1).putShort((short) 0).putShort((short) 1)
                        .putShort((short) 12).putInt(2).putInt(0).array());
                announcements.add(new java.util.concurrent.CompletableFuture<>());
                send(2, 0, new byte[0]);
            }
            case 2 -> {
                boolean unicode = false;
                while (message.remaining() >= 36) {
                    unicode |= message.getInt() == 13;
                    message.position(message.position() + 32);
                }
                if (message.hasRemaining()) throw new IOException("Invalid clipboard format list");
                send(3, 1, new byte[0]);
                if (unicode) send(4, 0, buffer(4).putInt(13).array());
            }
            case 3 -> {
                var pending = announcements.poll();
                if (pending != null) {
                    if (result == 1) pending.complete(null);
                    else pending.completeExceptionally(new IOException("Server rejected clipboard text"));
                }
            }
            case 4 -> {
                if (message.remaining() == 4 && message.getInt() == 13) {
                    var windows = local.replace("\r\n", "\n").replace("\n", "\r\n") + "\0";
                    send(5, 1, windows.getBytes(StandardCharsets.UTF_16LE));
                } else send(5, 2, new byte[0]);
            }
            case 5 -> {
                if (result == 1) {
                    if (message.remaining() % 2 != 0) throw new IOException("Invalid UTF-16 clipboard data");
                    String text = StandardCharsets.UTF_16LE.decode(message).toString();
                    int end = text.indexOf('\0');
                    remote.accept((end < 0 ? text : text.substring(0, end)).replace("\r\n", "\n"));
                }
            }
            default -> {}
        }
    }

    private void send(int type, int flags, byte[] data) throws IOException {
        var message = buffer(8 + data.length).putShort((short) type).putShort((short) flags)
                .putInt(data.length).put(data).array();
        for (int offset = 0; offset < message.length; offset += 1600) {
            int chunk = Math.min(1600, message.length - offset);
            int channelFlags = (offset == 0 ? 1 : 0) | (offset + chunk == message.length ? 2 : 0) | 0x10;
            sender.send(buffer(8 + chunk).putInt(message.length).putInt(channelFlags)
                    .put(message, offset, chunk).array());
        }
    }
}
