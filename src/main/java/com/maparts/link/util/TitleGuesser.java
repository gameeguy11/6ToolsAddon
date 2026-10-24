package com.maparts.link.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.maparts.link.capture.MapGridCapture;

public final class TitleGuesser {

    private static final Pattern COUNTER = Pattern.compile(
            "(?i)[\\s\\-_.,:;|#]*[\\(\\[]?\\s*\\d{1,3}\\s*(?:/|of|di|su)\\s*\\d{1,3}\\s*[\\)\\]]?\\s*$");

    private static final Pattern TRAILING_INDEX = Pattern.compile(
            "(?i)[\\s\\-_.,:;|#]+(?:part|parte|pt\\.?)?\\s*#?\\d{1,3}\\s*$");

    private static final Pattern TRAILING_SEPARATORS = Pattern.compile(
            "[\\s\\-_.,:;|#\\(\\)\\[\\]]+$");

    private TitleGuesser() {}

    public static String cleanName(String rawName) {
        if (rawName == null) {
            return null;
        }

        String name = rawName.trim();
        String previous;

        do {
            previous = name;
            name = COUNTER.matcher(name).replaceAll("");
            name = TRAILING_INDEX.matcher(name).replaceAll("");
            name = TRAILING_SEPARATORS.matcher(name).replaceAll("").trim();
        } while (!name.equals(previous));

        return name.isBlank() ? null : name;
    }

    public static String guess(List<MapGridCapture.TileInfo> tiles) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> firstCasing = new HashMap<>();
        String best = null;
        int bestCount = 0;

        for (MapGridCapture.TileInfo tile : tiles) {
            String cleaned = cleanName(tile.name());

            if (cleaned == null) {
                continue;
            }

            String key = cleaned.toLowerCase();
            firstCasing.putIfAbsent(key, cleaned);
            int count = counts.merge(key, 1, Integer::sum);

            if (count > bestCount) {
                bestCount = count;
                best = firstCasing.get(key);
            }
        }

        return best;
    }
}
