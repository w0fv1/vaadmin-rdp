package dev.w0fv1.vaadmin.rdp;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import javax.net.ssl.SSLSocket;
import org.bouncycastle.asn1.*;

final class RdpChannels {
    private final DataInputStream input;
    private final DataOutputStream output;
    private int initiator;
    private int global;
    private int clipboard;

    RdpChannels(SSLSocket socket) throws IOException {
        input = new DataInputStream(socket.getInputStream());
        output = new DataOutputStream(socket.getOutputStream());
    }

    static byte[] conference(RdpProfile profile) throws IOException {
        var blocks = ByteBuffer.allocate(216 + 12 + 20).order(ByteOrder.LITTLE_ENDIAN);
        blocks.putShort((short) 0xc001).putShort((short) 216).putInt(0x80004);
        blocks.putShort((short) profile.width()).putShort((short) profile.height());
        blocks.putShort((short) 0xca01).putShort((short) 0xaa03).putInt(0x409).putInt(2600);
        blocks.put("VAADMIN".getBytes(StandardCharsets.UTF_16LE));
        blocks.position(56);
        blocks.putInt(4).putInt(0).putInt(12);
        blocks.position(132);
        blocks.putShort((short) 0xca01).putShort((short) 1).putInt(0).putShort((short) 24);
        blocks.putShort((short) 7).putShort((short) 1);
        blocks.position(212);
        blocks.putInt(2);
        blocks.putShort((short) 0xc002).putShort((short) 12).putLong(0);
        blocks.putShort((short) 0xc003).putShort((short) 20).putInt(1);
        blocks.put("cliprdr\0".getBytes(StandardCharsets.US_ASCII)).putInt(0x80200000);
        var gcc = new ByteArrayOutputStream();
        var wire = new DataOutputStream(gcc);
        wire.write(HexFormat.of().parseHex("000500147c0001"));
        wire.writeShort(0x8000 | blocks.capacity() + 14);
        wire.write(HexFormat.of().parseHex("000800100001c00044756361"));
        wire.writeShort(0x8000 | blocks.capacity());
        wire.write(blocks.array());
        var one = new DEROctetString(new byte[]{1});
        var body = new DERSequence(new ASN1Encodable[]{one, one, ASN1Boolean.TRUE,
            parameters(34, 2, 0, 65535), parameters(1, 1, 1, 1056), parameters(65535, 64535, 65535, 65535),
            new DEROctetString(gcc.toByteArray())});
        return new DERTaggedObject(false, BERTags.APPLICATION, 101, body).getEncoded();
    }

    private static DERSequence parameters(int channels, int users, int tokens, int maxPdu) {
        return new DERSequence(Arrays.stream(new int[]{channels, users, tokens, 1, 0, 1, maxPdu, 2})
                .mapToObj(ASN1Integer::new).toArray(ASN1Integer[]::new));
    }

    void join(RdpProfile profile) throws IOException {
        write(conference(profile));
        var tagged = ASN1TaggedObject.getInstance(read());
        if (!tagged.hasTag(BERTags.APPLICATION, 102)) throw new IOException("Missing MCS Connect-Response");
        var response = ASN1Sequence.getInstance(tagged.getBaseUniversal(false, BERTags.SEQUENCE));
        if (ASN1Enumerated.getInstance(response.getObjectAt(0)).intValueExact() != 0) {
            throw new IOException("Server refused MCS connection");
        }
        byte[] gcc = ASN1OctetString.getInstance(response.getObjectAt(3)).getOctets();
        var fields = ByteBuffer.wrap(gcc).order(ByteOrder.LITTLE_ENDIAN);
        int marker = -1;
        for (int i = 0; i <= gcc.length - 4; i++) {
            if (fields.getInt(i) == 0x6e44634d) { marker = i + 4; break; }
        }
        if (marker < 0 || marker >= gcc.length) throw new IOException("Missing GCC conference response");
        int length = gcc[marker++] & 255;
        if ((length & 128) != 0 && marker < gcc.length) length = ((length & 127) << 8) | (gcc[marker++] & 255);
        if (gcc.length - marker != length) throw new IOException("Invalid GCC payload length");
        while (marker <= gcc.length - 4) {
            int type = fields.getShort(marker) & 65535;
            int size = fields.getShort(marker + 2) & 65535;
            if (size < 4 || size > gcc.length - marker) throw new IOException("Invalid server data block");
            if (type == 0x0c03 && size >= 10 && fields.getShort(marker + 6) == 1) {
                global = fields.getShort(marker + 4) & 65535;
                clipboard = fields.getShort(marker + 8) & 65535;
            }
            marker += size;
        }
        if (global == 0 || clipboard == 0) throw new IOException("Server did not allocate requested channels");
        write(new byte[]{4, 1, 0, 1, 0});
        write(new byte[]{0x28});
        byte[] attach = read();
        if (attach.length != 4 || attach[0] != 0x2e || attach[1] != 0) throw new IOException("MCS attach failed");
        initiator = Short.toUnsignedInt(ByteBuffer.wrap(attach).getShort(2));
        for (int channel : new int[]{initiator + 1001, global, clipboard}) {
            write(ByteBuffer.allocate(5).put((byte) 0x38).putShort((short) initiator).putShort((short) channel).array());
            byte[] result = read();
            if (result.length != 8 || result[0] != 0x3e || result[1] != 0
                    || Short.toUnsignedInt(ByteBuffer.wrap(result).getShort(6)) != channel) {
                throw new IOException("MCS refused channel join");
            }
        }
    }

    int userId() { return initiator + 1001; }
    int global() { return global; }
    int clipboard() { return clipboard; }

    synchronized void send(int channel, byte[] payload) throws IOException {
        if (payload.length > 32767) throw new IOException("MCS segment exceeds negotiated length");
        var data = new ByteArrayOutputStream();
        var header = new DataOutputStream(data);
        header.writeByte(0x64);
        header.writeShort(initiator);
        header.writeShort(channel);
        header.writeByte(0x70);
        if (payload.length < 128) header.writeByte(payload.length);
        else header.writeShort(0x8000 | payload.length);
        header.write(payload);
        write(data.toByteArray());
    }

    record Message(int channel, byte[] payload) {}

    Message receive() throws IOException {
        int first = input.readUnsignedByte();
        if (first != 3) {
            if ((first & 0xc3) != 0) throw new IOException("Unsupported fast-path header");
            int length = input.readUnsignedByte();
            int header = 2;
            if ((length & 128) != 0) { length = ((length & 127) << 8) | input.readUnsignedByte(); header++; }
            if (length < header) throw new IOException("Invalid fast-path packet length");
            byte[] payload = new byte[length - header];
            input.readFully(payload);
            return new Message(0, payload);
        }
        var data = new DataInputStream(new ByteArrayInputStream(readTpktBody()));
        if (data.readUnsignedByte() != 0x68) throw new IOException("Expected MCS send-data indication");
        data.readUnsignedShort();
        int channel = data.readUnsignedShort();
        data.readUnsignedByte();
        int length = data.readUnsignedByte();
        if ((length & 128) != 0) length = ((length & 127) << 8) | data.readUnsignedByte();
        if (length != data.available()) throw new IOException("Invalid MCS segment length");
        return new Message(channel, data.readNBytes(length));
    }
    void write(byte[] body) throws IOException {
        output.writeShort(0x0300);
        output.writeShort(body.length + 7);
        output.write(new byte[]{2, (byte) 0xf0, (byte) 0x80});
        output.write(body);
        output.flush();
    }

    byte[] read() throws IOException {
        if (input.readUnsignedByte() != 3) throw new IOException("Expected TPKT data");
        return readTpktBody();
    }

    private byte[] readTpktBody() throws IOException {
        if (input.readUnsignedByte() != 0) throw new IOException("Invalid TPKT header");
        int length = input.readUnsignedShort();
        if (length < 7 || input.readUnsignedByte() != 2 || input.readUnsignedShort() != 0xf080) {
            throw new IOException("Invalid X.224 data packet");
        }
        byte[] body = new byte[length - 7];
        input.readFully(body);
        return body;
    }
}
