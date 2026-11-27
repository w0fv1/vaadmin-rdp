package dev.w0fv1.vaadmin.rdp;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

final class RdpTransport {
    private static final byte[] REQUEST = HexFormat.of().parseHex("030000130ee000000000000100080002000000");

    static SSLSocket connect(RdpProfile profile, SSLContext context) throws IOException {
        var socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(profile.host(), profile.port()), 10000);
            socket.setSoTimeout(15000);
            negotiate(socket.getInputStream(), socket.getOutputStream());
            var tls = (SSLSocket) context.getSocketFactory().createSocket(socket, profile.host(), profile.port(), true);
            var parameters = tls.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            tls.setSSLParameters(parameters);
            tls.startHandshake();
            return tls;
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    static void negotiate(InputStream input, OutputStream output) throws IOException {
        output.write(REQUEST);
        output.flush();
        var data = new DataInputStream(input);
        if (data.readUnsignedShort() != 0x0300 || data.readUnsignedShort() != 19) {
            throw new IOException("Unexpected RDP negotiation packet");
        }
        byte[] response = new byte[15];
        data.readFully(response);
        var fields = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN);
        if (response[0] != 14 || (response[1] & 255) != 0xd0 || fields.getShort(9) != 8) {
            throw new IOException("Invalid X.224 connection confirmation");
        }
        int value = fields.getInt(11);
        if (response[7] != 2 || value != 2) {
            throw new IOException("CredSSP negotiation rejected (type " + response[7] + ", value " + value + ")");
        }
    }
}
