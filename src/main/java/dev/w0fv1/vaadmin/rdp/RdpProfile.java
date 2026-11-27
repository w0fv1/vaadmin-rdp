package dev.w0fv1.vaadmin.rdp;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

public record RdpProfile(String host, int port, String username, String domain, int width, int height) {
    private static final Pattern ADDRESS = Pattern.compile("(?:\\[([^\\]]+)\\]|([^:\\s]+))(?::(\\d+))?");

    public RdpProfile {
        if (host == null || host.isBlank() || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid RDP endpoint");
        }
        if (width < 1 || height < 1 || width > 8192 || height > 8192) {
            throw new IllegalArgumentException("Invalid desktop dimensions");
        }
    }

    public static RdpProfile parse(byte[] bytes) {
        Charset charset = StandardCharsets.UTF_8;
        if (bytes.length >= 2) {
            if ((bytes[0] & 255) == 255 && (bytes[1] & 255) == 254) charset = StandardCharsets.UTF_16LE;
            if ((bytes[0] & 255) == 254 && (bytes[1] & 255) == 255) charset = StandardCharsets.UTF_16BE;
        }
        var settings = new HashMap<String, String>();
        new String(bytes, charset).replaceFirst("^\uFEFF", "").lines().forEach(line -> {
            String[] parts = line.split(":", 3);
            if (parts.length == 3) settings.put(parts[0].strip().toLowerCase(Locale.ROOT), parts[2].strip());
        });
        var address = ADDRESS.matcher(settings.getOrDefault("full address", ""));
        if (!address.matches()) throw new IllegalArgumentException("Invalid full address in RDP file");
        String host = address.group(1) == null ? address.group(2) : address.group(1);
        String port = address.group(3) == null ? settings.getOrDefault("server port", "3389") : address.group(3);
        return new RdpProfile(host, Integer.parseInt(port), settings.getOrDefault("username", ""),
                settings.getOrDefault("domain", ""), Integer.parseInt(settings.getOrDefault("desktopwidth", "1280")),
                Integer.parseInt(settings.getOrDefault("desktopheight", "800")));
    }
}
