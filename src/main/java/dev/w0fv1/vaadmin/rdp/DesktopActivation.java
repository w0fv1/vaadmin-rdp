package dev.w0fv1.vaadmin.rdp;

import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class DesktopActivation {
    private final RdpChannels channels;
    private int shareId;
    private int server;
    private short depth;

    DesktopActivation(RdpChannels channels) {
        this.channels = channels;
    }

    void start(RdpProfile profile) throws IOException {
        var info = buffer(1024 + 2 * (profile.username().length() + profile.domain().length()));
        info.putInt(0x40).putInt(0).putInt(0x00090353);
        String[] parts = {profile.domain(), profile.username(), "", "", ""};
        for (var part : parts) info.putShort((short) (part.length() * 2));
        for (var part : parts) info.put(part.getBytes(StandardCharsets.UTF_16LE)).putShort((short) 0);
        info.putShort((short) 2);
        for (var part : new String[]{"0.0.0.0", "vaadmin-rdp"}) {
            info.putShort((short) (2 + part.length() * 2)).put(part.getBytes(StandardCharsets.UTF_16LE)).putShort((short) 0);
        }
        info.put(new byte[172]).putInt(0).putInt(7).putShort((short) 0);
        channels.send(channels.global(), bytes(info));
        for (;;) {
            var message = channels.receive();
            if (message.channel() != channels.global()) throw new IOException("Unexpected pre-activation channel");
            var pdu = ByteBuffer.wrap(message.payload()).order(ByteOrder.LITTLE_ENDIAN);
            int length = Short.toUnsignedInt(pdu.getShort());
            int type = Short.toUnsignedInt(pdu.getShort());
            if (length == 0x80 && type == 0) {
                if (pdu.remaining() < 12 || pdu.get() != (byte) 0xff || pdu.getInt(8) != 7) {
                    throw new IOException("Unsupported server licensing response");
                }
                continue;
            }
            if ((type & 15) != 1 || length != message.payload().length) throw new IOException("Expected Demand Active PDU");
            server = Short.toUnsignedInt(pdu.getShort());
            shareId = pdu.getInt();
            int descriptionSize = Short.toUnsignedInt(pdu.getShort());
            pdu.getShort();
            pdu.position(pdu.position() + descriptionSize);
            int count = Short.toUnsignedInt(pdu.getShort());
            pdu.getShort();
            for (int index = 0; index < count; index++) {
                int offset = pdu.position();
                int capabilityType = Short.toUnsignedInt(pdu.getShort());
                int capabilityLength = Short.toUnsignedInt(pdu.getShort());
                if (capabilityLength < 4 || capabilityLength > pdu.limit() - offset) {
                    throw new IOException("Invalid capability length");
                }
                if (capabilityType == 2) depth = pdu.getShort();
                pdu.position(offset + capabilityLength);
            }
            break;
        }
        var caps = buffer(512);
        capability(caps, 1, 24).putShort((short) 1).putShort((short) 3).putShort((short) 0x200)
                .putShort((short) 0).putShort((short) 0).putShort((short) 0x40d).put(new byte[8]);
        capability(caps, 2, 28).putShort(depth).putShort((short) 1).putShort((short) 1).putShort((short) 1)
                .putShort((short) profile.width()).putShort((short) profile.height()).putShort((short) 0)
                .putShort((short) 0).putShort((short) 1).putShort((short) 0).putShort((short) 1).putShort((short) 0);
        capability(caps, 3, 88).put(new byte[20]).putShort((short) 1).putShort((short) 20).putShort((short) 0)
                .putShort((short) 1).putShort((short) 0).putShort((short) 0x4a).put(new byte[48]).putShort((short) 1252).putShort((short) 0);
        capability(caps, 13, 88).putShort((short) 0x11).putShort((short) 0).putInt(0x409).putInt(4)
                .putInt(0).putInt(12).put(new byte[64]);
        capability(caps, 8, 10).putShort((short) 0).putShort((short) 0).putShort((short) 20);
        capability(caps, 20, 12).putInt(0).putInt(1600);
        capability(caps, 19, 40).put(new byte[36]);
        capability(caps, 10, 8).putShort((short) 6).putShort((short) 0);
        capability(caps, 5, 12).putInt(0).putShort((short) 2).putShort((short) 2);
        capability(caps, 7, 12).putLong(0);
        capability(caps, 9, 8).putInt(0);
        capability(caps, 14, 8).putShort((short) 1).putShort((short) 0);
        capability(caps, 12, 8).putInt(0);
        capability(caps, 16, 52).put(new byte[48]);
        capability(caps, 15, 8).putInt(0);
        var active = buffer(caps.position() + 23);
        active.putShort((short) active.capacity()).putShort((short) 0x13).putShort((short) channels.userId())
                .putInt(shareId).putShort((short) 1002).putShort((short) 3).putShort((short) (4 + caps.position()))
                .put(new byte[]{'R','D','P'}).putShort((short) 15).putShort((short) 0).put(bytes(caps));
        channels.send(channels.global(), bytes(active));
        data(31, buffer(4).putShort((short) 1).putShort((short) server).array());
        for (int action : new int[]{4, 1}) data(20, buffer(8).putShort((short) action).array());
        data(39, buffer(8).putShort((short) 0).putShort((short) 0).putShort((short) 3).putShort((short) 50).array());
    }

    void data(int type, byte[] payload) throws IOException {
        var pdu = buffer(18 + payload.length);
        pdu.putShort((short) pdu.capacity()).putShort((short) 0x17).putShort((short) channels.userId())
                .putInt(shareId).put((byte) 0).put((byte) 1).putShort((short) (payload.length + 4))
                .put((byte) type).put((byte) 0).putShort((short) 0).put(payload);
        channels.send(channels.global(), pdu.array());
    }

    private static ByteBuffer capability(ByteBuffer output, int type, int length) {
        return output.putShort((short) type).putShort((short) length);
    }

    static ByteBuffer buffer(int length) {
        return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
    }

    static byte[] bytes(ByteBuffer data) {
        return Arrays.copyOf(data.array(), data.position());
    }
}
