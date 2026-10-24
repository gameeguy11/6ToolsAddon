package com.maparts.link.util;

import java.net.URI;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class Links {
    private Links() {}

    public static MutableText link(String label, String url) {
        return Text.literal(label).styled(style -> style
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withUnderline(true)
                .withFormatting(Formatting.AQUA));
    }

    public static MutableText prefixedLink(String prefix, String label, String url) {
        return Text.literal(prefix).formatted(Formatting.GRAY)
                .append(link(label, url));
    }
}
