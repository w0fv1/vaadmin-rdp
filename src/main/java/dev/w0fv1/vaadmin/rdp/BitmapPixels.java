package dev.w0fv1.vaadmin.rdp;

import java.nio.ByteBuffer;

final class BitmapPixels {
    static int[] decode(ByteBuffer input, int width, int height, int depth) {
        if (width < 1 || height < 1 || (long) width * height > 16777216) {
            throw new IllegalArgumentException("Invalid bitmap size");
        }
        int[] pixels = new int[width * height];
        int pos = 0;
        int white = (1 << depth) - 1;
        int foreground = white;
        boolean backgroundRun = false;
        while (input.hasRemaining()) {
            int header = Byte.toUnsignedInt(input.get());
            int code;
            int length;
            if (header >= 0xf9) {
                code = header;
                length = (code == 0xf9 || code == 0xfa) ? 8 : 1;
            } else if (header >= 0xf0) {
                code = header & 15;
                length = Short.toUnsignedInt(input.getShort());
            } else {
                boolean lite = header >= 0xc0;
                code = lite ? (header >> 4) - 6 : header >> 5;
                length = header & (lite ? 15 : 31);
                if (code == 2 || code == 7) length = length == 0 ? Byte.toUnsignedInt(input.get()) + 1 : length * 8;
                else if (length == 0) length = Byte.toUnsignedInt(input.get()) + (lite ? 16 : 32);
            }
            if (code == 6 || code == 7) {
                foreground = pixel(input, depth);
                code -= 5;
            }
            int color = 0;
            int alternate = 0;
            if (code == 3 || code == 8) color = pixel(input, depth);
            if (code == 8) { alternate = pixel(input, depth); length *= 2; }
            if (length < 1 || length > pixels.length - pos) throw new IllegalArgumentException("Invalid bitmap run");
            int mask = code == 0xf9 ? 3 : 5;
            for (int i = 0; i < length; i++, pos++) {
                int previous = pos >= width ? pixels[pos - width] : 0;
                if (code == 2 && (i & 7) == 0) mask = Byte.toUnsignedInt(input.get());
                pixels[pos] = switch (code) {
                    case 0 -> previous ^ ((backgroundRun && i == 0 && pos != width) ? foreground : 0);
                    case 1 -> previous ^ foreground;
                    case 2, 0xf9, 0xfa -> previous ^ (((mask >> (i & 7)) & 1) == 1 ? foreground : 0);
                    case 3 -> color;
                    case 4 -> pixel(input, depth);
                    case 8 -> (i & 1) == 0 ? color : alternate;
                    case 0xfd -> white;
                    case 0xfe -> 0;
                    default -> throw new IllegalArgumentException("Unknown bitmap opcode " + code);
                };
            }
            backgroundRun = code == 0;
        }
        if (pos != pixels.length) throw new IllegalArgumentException("Truncated bitmap");
        return pixels;
    }

    static int pixel(ByteBuffer input, int depth) {
        return switch (depth) {
            case 15, 16 -> Short.toUnsignedInt(input.getShort());
            case 24 -> Byte.toUnsignedInt(input.get()) | (Byte.toUnsignedInt(input.get()) << 8)
                    | (Byte.toUnsignedInt(input.get()) << 16);
            default -> throw new IllegalArgumentException("Unsupported bitmap depth " + depth);
        };
    }
}
