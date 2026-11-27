package dev.w0fv1.vaadmin.rdp;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class DesktopImage {
    final BufferedImage image;

    DesktopImage(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

    boolean fastPath(byte[] packet) throws IOException {
        var updates = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
        boolean changed = false;
        while (updates.hasRemaining()) {
            int header = Byte.toUnsignedInt(updates.get());
            if ((header & 0x80) != 0 && (updates.get() & 0x20) != 0) throw new IOException("Unexpected bulk compression");
            int size = Short.toUnsignedInt(updates.getShort());
            if (size > updates.remaining()) throw new IOException("Truncated fast-path update");
            var update = updates.slice(updates.position(), size).order(ByteOrder.LITTLE_ENDIAN);
            updates.position(updates.position() + size);
            if ((header & 15) == 1) {
                if ((header & 0x30) != 0) throw new IOException("Fragmented bitmap update");
                bitmap(update);
                changed = true;
            }
        }
        return changed;
    }

    void bitmap(ByteBuffer update) throws IOException {
        if (update.getShort() != 1) throw new IOException("Unexpected bitmap update header");
        int count = Short.toUnsignedInt(update.getShort());
        for (int i = 0; i < count; i++) {
            int x = Short.toUnsignedInt(update.getShort());
            int y = Short.toUnsignedInt(update.getShort());
            int right = Short.toUnsignedInt(update.getShort());
            int bottom = Short.toUnsignedInt(update.getShort());
            int width = Short.toUnsignedInt(update.getShort());
            int height = Short.toUnsignedInt(update.getShort());
            int depth = Short.toUnsignedInt(update.getShort());
            int flags = Short.toUnsignedInt(update.getShort());
            int length = Short.toUnsignedInt(update.getShort());
            if (length > update.remaining() || right < x || bottom < y || right >= image.getWidth()
                    || bottom >= image.getHeight() || right - x >= width || bottom - y >= height
                    || (long) width * height > (long) image.getWidth() * image.getHeight()) {
                throw new IOException("Invalid bitmap rectangle");
            }
            var data = update.slice(update.position(), length).order(ByteOrder.LITTLE_ENDIAN);
            update.position(update.position() + length);
            int[] pixels;
            if ((flags & 1) != 0) {
                if ((flags & 0x400) == 0) data.position(8);
                pixels = BitmapPixels.decode(data, width, height, depth);
            } else {
                int stride = ((width * ((depth + 7) / 8)) + 3) & ~3;
                pixels = new int[width * height];
                for (int row = 0; row < height; row++) {
                    data.position(row * stride);
                    for (int col = 0; col < width; col++) pixels[row * width + col] = BitmapPixels.pixel(data, depth);
                }
            }
            for (int index = 0; index < pixels.length; index++) {
                int p = pixels[index];
                if (depth == 16 || depth == 15) {
                    int r = (p >> (depth - 5) & 31) * 255 / 31;
                    int mask = depth == 16 ? 63 : 31;
                    int g = ((p >> 5) & mask) * 255 / mask;
                    pixels[index] = (r << 16) | (g << 8) | ((p & 31) * 255 / 31);
                }
            }
            for (int row = 0; row <= bottom - y; row++) {
                image.setRGB(x, y + row, right - x + 1, 1, pixels, (height - row - 1) * width, width);
            }
        }
    }
}
