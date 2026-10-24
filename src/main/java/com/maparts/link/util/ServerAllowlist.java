package com.maparts.link.util;

import java.util.Locale;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

public final class ServerAllowlist {
    private static final String[] DOMAINS = {
            "6b6t.org",

    };

    private ServerAllowlist() {}

    public static String connected6b6tHost(MinecraftClient client) {
        ServerInfo server = client.getCurrentServerEntry();

        if (server == null || server.address == null || server.address.isBlank()) {
            return null;
        }

        String host = normalize(server.address);

        for (String domain : DOMAINS) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return host;
            }
        }

        return null;
    }

    private static String normalize(String address) {
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');

        if (colon > 0 && host.indexOf(':') == colon) {
            host = host.substring(0, colon);
        }

        return host;
    }
}
