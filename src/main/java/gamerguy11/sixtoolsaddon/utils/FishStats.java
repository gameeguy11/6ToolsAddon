package gamerguy11.sixtoolsaddon.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class FishStats {
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("sixtoolsaddon").resolve("FishLogs");
    public static final Path HTML = DIR.resolve("FishStats.html");
    private static final Path DATA = DIR.resolve("catches.tsv");
    private static final Pattern CAUGHT = Pattern.compile("You caught an? (.+?): ([0-9]+(?:[.,][0-9]+)?) cm", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOG_DATE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})-\\d+\\.log(\\.gz)?$");
    private static final Path SPECIES_FILE = DIR.resolve("my_rarities.tsv");
    private static final Pattern BROADCAST = Pattern.compile("([A-Za-z0-9_]{3,16}) caught an? (Uncommon|Rare|Epic|Legendary|Mythic) (.+?): ([0-9]+(?:[.,][0-9]+)?) cm", Pattern.CASE_INSENSITIVE);
    private static final long NEAR_SECONDS = 2;
    private static final Map<String, List<Long>> ALL = new HashMap<>();
    private static final Map<String, List<Long>> BCAST = new HashMap<>();
    private static final List<String> ORDER = List.of("Normal", "Epic", "Legendary");
    private static final int[][] COLOR_REFS = {
        {0xF4E801, 0}, {0xFFFF55, 0}, {0xFFDD33, 0}, {0xFFFF00, 0},
        {0xAA00AA, 1}, {0xFF55FF, 1}, {0xC45CFF, 1}, {0xB44CFF, 1},
        {0xFFAA00, 2}, {0xFF9A1F, 2}, {0xFF8800, 2}
    };
    private static final Map<String, String> SPECIES = new HashMap<>();
    private static boolean speciesDirty;
    private static final Map<String, String> COLORS = Map.of(
        "Normal", "#ffdd33",
        "Uncommon", "#55ff55",
        "Rare", "#55aaff",
        "Epic", "#c45cff",
        "Legendary", "#ff9a1f",
        "Mythic", "#ff5577"
    );
    private static final Set<String> PREFIXES = Set.of("common", "uncommon", "rare", "epic", "legendary", "mythic");
    private static final long IDLE_GAP_SECONDS = 900;

    private static final List<Catch> CATCHES = new ArrayList<>();
    private static final Set<String> KEYS = new HashSet<>();
    private static boolean loaded;

    public record Catch(long time, String rarity, String name, double size) {
        String key(int occurrence) {
            return time + "|" + name + "|" + size + "#" + occurrence;
        }
    }

    private FishStats() {
    }

    private static void learnOwn(String rarity, String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (!rarity.equals(SPECIES.put(key, rarity))) speciesDirty = true;
    }

    public static String colorRarity(Text message, String rawName) {
        String name = parse(rawName, 0, 0).name();
        int[] found = {-1};
        message.visit((style, str) -> {
            if (found[0] == -1 && str.contains(name) && str.length() <= name.length() + 6 && style.getColor() != null) {
                found[0] = style.getColor().getRgb();
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (found[0] == -1) return null;
        int r = (found[0] >> 16) & 255, g = (found[0] >> 8) & 255, b = found[0] & 255;
        double bestDist = Double.MAX_VALUE;
        int bestIdx = -1;
        for (int[] ref : COLOR_REFS) {
            int dr = r - ((ref[0] >> 16) & 255), dg = g - ((ref[0] >> 8) & 255), db = b - (ref[0] & 255);
            double d = Math.sqrt(dr * dr + dg * dg + db * db);
            if (d < bestDist) {
                bestDist = d;
                bestIdx = ref[1];
            }
        }
        return bestDist > 90 ? null : ORDER.get(bestIdx);
    }

    private static String capitalize(String w) {
        String l = w.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    public static synchronized void record(String rawName, double size, long epochSeconds, String colorHint) {
        ensureLoaded();
        Catch c = parse(rawName, size, epochSeconds);
        if (c.rarity().equals("Normal") && colorHint != null) c = new Catch(c.time(), colorHint, c.name(), c.size());
        if (!c.rarity().equals("Normal") && !c.rarity().equals(SPECIES.put(c.name().toLowerCase(Locale.ROOT), c.rarity()))) speciesDirty = true;
        int n = 0;
        while (KEYS.contains(c.key(n))) n++;
        List<Catch> out = new ArrayList<>();
        if (tryAdd(c, n, false, out)) append(out);
    }

    public static synchronized void recordBroadcast(String text, String user) {
        if (user == null) return;
        ensureLoaded();
        Matcher m = BROADCAST.matcher(text);
        if (!m.find() || !m.group(1).equalsIgnoreCase(user)) return;
        double size;
        try {
            size = Double.parseDouble(m.group(4).replace(',', '.'));
        } catch (NumberFormatException e) {
            return;
        }
        Catch c = new Catch(System.currentTimeMillis() / 1000, capitalize(m.group(2)), m.group(3).trim(), size);
        learnOwn(c.rarity(), c.name());
        int n = 0;
        while (KEYS.contains(c.key(n))) n++;
        List<Catch> out = new ArrayList<>();
        if (tryAdd(c, n, true, out)) append(out);
    }

    private static boolean nearby(Map<String, List<Long>> map, String fk, long time) {
        List<Long> times = map.get(fk);
        if (times == null) return false;
        for (long t : times) if (Math.abs(t - time) <= NEAR_SECONDS) return true;
        return false;
    }

    private static void addTime(Map<String, List<Long>> map, String fk, long time) {
        map.computeIfAbsent(fk, k -> new ArrayList<>()).add(time);
    }

    private static boolean tryAdd(Catch c, int n, boolean broadcast, List<Catch> out) {
        String fk = c.name().toLowerCase(Locale.ROOT) + "|" + c.size();
        if (KEYS.contains(c.key(n))) return false;
        if (nearby(ALL, fk, c.time())) return false;
        KEYS.add(c.key(n));
        CATCHES.add(c);
        addTime(ALL, fk, c.time());
        out.add(c);
        return true;
    }

    private static void addDir(Set<Path> dirs, Path dir) {
        try {
            if (dir != null && Files.isDirectory(dir)) dirs.add(dir.toRealPath());
        } catch (IOException ignored) {
            Swallowed.log("FishStats:addDir", ignored);
        }
    }

    private static void addInstanceRoot(Set<Path> dirs, Path root) {
        if (root == null || !Files.isDirectory(root)) return;
        try (Stream<Path> kids = Files.list(root)) {
            for (Path k : (Iterable<Path>) kids::iterator) {
                addDir(dirs, k.resolve("logs"));
                addDir(dirs, k.resolve("minecraft").resolve("logs"));
                addDir(dirs, k.resolve(".minecraft").resolve("logs"));
            }
        } catch (IOException ignored) {
            Swallowed.log("FishStats:instanceRoot", ignored);
        }
    }

    private static Set<Path> logDirs(List<String> extra, boolean scanLaunchers) {
        Set<Path> dirs = new java.util.LinkedHashSet<>();
        addDir(dirs, FabricLoader.getInstance().getGameDir().resolve("logs"));
        if (extra != null) {
            for (String e : extra) {
                if (e == null || e.isBlank()) continue;
                Path p = Path.of(e.trim());
                addDir(dirs, p);
                addDir(dirs, p.resolve("logs"));
                if (Files.isDirectory(p)) {
                    try (Stream<Path> kids = Files.list(p)) {
                        for (Path k : (Iterable<Path>) kids::iterator) addDir(dirs, k);
                    } catch (IOException ignored) {
                        Swallowed.log("FishStats:extraKids", ignored);
                    }
                }
            }
        }
        if (scanLaunchers) {
            Path home = Path.of(System.getProperty("user.home"));
            String appData = System.getenv("APPDATA");
            Path roaming = appData != null ? Path.of(appData) : home.resolve("AppData").resolve("Roaming");
            addDir(dirs, home.resolve(".minecraft").resolve("logs"));
            addDir(dirs, roaming.resolve(".minecraft").resolve("logs"));
            addDir(dirs, home.resolve("Library").resolve("Application Support").resolve("minecraft").resolve("logs"));
            for (Path base : List.of(roaming, home.resolve(".local").resolve("share"), home.resolve("Library").resolve("Application Support"))) {
                addInstanceRoot(dirs, base.resolve("PrismLauncher").resolve("instances"));
                addInstanceRoot(dirs, base.resolve("MultiMC").resolve("instances"));
                addInstanceRoot(dirs, base.resolve("ModrinthApp").resolve("profiles"));
                addInstanceRoot(dirs, base.resolve("com.modrinth.theseus").resolve("profiles"));
            }
            addInstanceRoot(dirs, home.resolve("curseforge").resolve("minecraft").resolve("Instances"));
            addInstanceRoot(dirs, home.resolve("Documents").resolve("curseforge").resolve("minecraft").resolve("Instances"));
        }
        return dirs;
    }

    public static synchronized int[] importLogs(List<String> extra, boolean scanLaunchers) {
        ensureLoaded();
        int[] stats = new int[4];
        Set<String> users = new HashSet<>();
        net.minecraft.client.session.Session session = net.minecraft.client.MinecraftClient.getInstance().getSession();
        if (session != null) users.add(session.getUsername().toLowerCase(Locale.ROOT));
        List<Catch> added = new ArrayList<>();
        for (Path logs : logDirs(extra, scanLaunchers)) {
            stats[3]++;
            try (Stream<Path> files = Files.list(logs)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    String name = file.getFileName().toString();
                    LocalDate date;
                    if (name.equals("latest.log")) {
                        date = Instant.ofEpochMilli(file.toFile().lastModified()).atZone(ZoneId.systemDefault()).toLocalDate();
                    } else {
                        Matcher dm = LOG_DATE.matcher(name);
                        if (!dm.matches()) continue;
                        date = LocalDate.parse(dm.group(1));
                    }
                    stats[0]++;
                    readLog(file, name.endsWith(".gz"), date, added, stats, users);
                }
            } catch (IOException ignored) {
                Swallowed.log("FishStats:importLogs", ignored);
            }
        }
        append(added);
        saveSpecies();
        stats[2] = added.size();
        return stats;
    }

    private static void readLog(Path file, boolean gz, LocalDate date, List<Catch> out, int[] stats, Set<String> users) {
        Map<String, Integer> seen = new java.util.HashMap<>();
        try (InputStream raw = Files.newInputStream(file);
             InputStream in = gz ? new GZIPInputStream(raw) : raw;
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() < 12 || line.charAt(0) != '[' || line.charAt(9) != ']') continue;
                int su = line.indexOf("Setting user: ");
                if (su >= 0) {
                    users.add(line.substring(su + 14).trim().toLowerCase(Locale.ROOT));
                    continue;
                }
                int chat = line.indexOf("[CHAT]");
                if (chat < 0) continue;
                String chatText = line.substring(chat + 6);
                Matcher bm = BROADCAST.matcher(chatText);
                if (bm.find() && users.contains(bm.group(1).toLowerCase(Locale.ROOT))) {
                    try {
                        long be = LocalDateTime.of(date, LocalTime.parse(line.substring(1, 9))).atZone(ZoneId.systemDefault()).toEpochSecond();
                        double bs = Double.parseDouble(bm.group(4).replace(',', '.'));
                        Catch bc = new Catch(be, capitalize(bm.group(2)), bm.group(3).trim(), bs);
                        learnOwn(bc.rarity(), bc.name());
                        int bn = seen.merge(bc.key(0), 1, Integer::sum) - 1;
                        stats[1]++;
                        tryAdd(bc, bn, true, out);
                    } catch (RuntimeException ignored) {
                        Swallowed.log("FishStats:broadcast", ignored);
                    }
                    continue;
                }
                Matcher m = CAUGHT.matcher(chatText);
                if (!m.find()) continue;
                stats[1]++;
                long epoch;
                double size;
                try {
                    epoch = LocalDateTime.of(date, LocalTime.parse(line.substring(1, 9))).atZone(ZoneId.systemDefault()).toEpochSecond();
                    size = Double.parseDouble(m.group(2).replace(',', '.'));
                } catch (RuntimeException e) {
                    continue;
                }
                Catch c = parse(m.group(1).trim(), size, epoch);
                int n = seen.merge(c.key(0), 1, Integer::sum) - 1;
                tryAdd(c, n, false, out);
            }
        } catch (IOException | RuntimeException ignored) {
            Swallowed.log("FishStats:readLog", ignored);
        }
    }

    private static Catch parse(String rawName, double size, long epoch) {
        String name = rawName.trim();
        String rarity = "Normal";
        int space = name.indexOf(' ');
        if (space > 0 && PREFIXES.contains(name.substring(0, space).toLowerCase(Locale.ROOT))) {
            String word = name.substring(0, space).toLowerCase(Locale.ROOT);
            if (!word.equals("common")) rarity = Character.toUpperCase(word.charAt(0)) + word.substring(1);
            name = name.substring(space + 1).trim();
        }
        return new Catch(epoch, rarity, name, size);
    }

    private static void saveSpecies() {
        if (!speciesDirty) return;
        speciesDirty = false;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : SPECIES.entrySet()) sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        try {
            Files.createDirectories(DIR);
            Files.writeString(SPECIES_FILE, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            Swallowed.log("FishStats:saveSpecies", ignored);
        }
    }

    private static String effective(Catch c) {
        if (!c.rarity().equals("Normal")) return c.rarity();
        return SPECIES.getOrDefault(c.name().toLowerCase(Locale.ROOT), "Normal");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (Files.exists(SPECIES_FILE)) {
            try {
                for (String line : Files.readAllLines(SPECIES_FILE, StandardCharsets.UTF_8)) {
                    String[] p = line.split("\t", 2);
                    if (p.length == 2) SPECIES.put(p[0], p[1]);
                }
            } catch (IOException ignored) {
                Swallowed.log("FishStats:loadSpecies", ignored);
            }
        }
        if (!Files.exists(DATA)) return;
        boolean rewrite = false;
        try {
            for (String line : Files.readAllLines(DATA, StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", 4);
                if (p.length < 4) continue;
                try {
                    Catch c = new Catch(Long.parseLong(p[0]), p[1], p[2], Double.parseDouble(p[3]));
                    String fk = c.name().toLowerCase(Locale.ROOT) + "|" + c.size();
                    int n = 0;
                    while (KEYS.contains(c.key(n))) n++;
                    if (n > 0 || nearby(ALL, fk, c.time())) {
                        rewrite = true;
                        continue;
                    }
                    KEYS.add(c.key(n));
                    CATCHES.add(c);
                    addTime(ALL, fk, c.time());
                } catch (NumberFormatException ignored) {
                    Swallowed.log("FishStats:load", ignored);
                }
            }
            if (rewrite) rewriteData();
        } catch (IOException ignored) {
            Swallowed.log("FishStats:load", ignored);
        }
    }

    private static void rewriteData() {
        StringBuilder sb = new StringBuilder();
        for (Catch c : CATCHES) sb.append(c.time()).append('\t').append(c.rarity()).append('\t').append(c.name()).append('\t').append(c.size()).append('\n');
        try {
            Files.createDirectories(DIR);
            Files.writeString(DATA, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            Swallowed.log("FishStats:rewrite", ignored);
        }
    }

    private static void append(List<Catch> list) {
        if (list.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Catch c : list) sb.append(c.time()).append('\t').append(c.rarity()).append('\t').append(c.name()).append('\t').append(c.size()).append('\n');
        try {
            Files.createDirectories(DIR);
            Files.writeString(DATA, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            Swallowed.log("FishStats:append", ignored);
        }
    }

    private static String color(String rarity) {
        return COLORS.getOrDefault(rarity, "#aaaaaa");
    }

    private static String esc(String t) {
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String rate(int count, double hours) {
        return hours <= 0 ? "-" : String.format(Locale.ROOT, "%.1f", count / hours);
    }

    public static synchronized boolean writeHtml(int refreshSeconds, int serverTotal) {
        ensureLoaded();
        saveSpecies();
        List<Catch> sorted = new ArrayList<>(CATCHES);
        sorted.sort((a, b) -> Long.compare(a.time(), b.time()));

        long active = 0;
        for (int i = 1; i < sorted.size(); i++) {
            long gap = sorted.get(i).time() - sorted.get(i - 1).time();
            if (gap <= IDLE_GAP_SECONDS) active += gap;
        }
        double hours = active >= 60 ? active / 3600.0 : 0;

        long now = System.currentTimeMillis() / 1000;
        int lastHour = 0;
        Map<String, Integer> byRarity = new LinkedHashMap<>();
        for (String r : ORDER) byRarity.put(r, 0);
        Map<String, int[]> species = new LinkedHashMap<>();
        Map<String, String> speciesRarity = new LinkedHashMap<>();
        Map<String, Double> best = new LinkedHashMap<>();
        java.util.TreeMap<LocalDate, Integer> byDay = new java.util.TreeMap<>();
        for (Catch c : sorted) {
            byDay.merge(Instant.ofEpochSecond(c.time()).atZone(ZoneId.systemDefault()).toLocalDate(), 1, Integer::sum);
            String rar = effective(c);
            byRarity.merge(rar, 1, Integer::sum);
            if (now - c.time() <= 3600) lastHour++;
            String key = rar + "|" + c.name();
            species.computeIfAbsent(key, k -> new int[1])[0]++;
            speciesRarity.put(key, rar);
            best.merge(key, c.size(), Math::max);
        }

        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n<meta http-equiv=\"refresh\" content=\"").append(refreshSeconds).append("\">\n<title>Fish Stats</title>\n<style>\n")
            .append("body { background-color: #36393f; color: #dcddde; font-family: sans-serif; padding: 10px; max-width: 800px; margin: auto; }\n")
            .append("h2 { color: #fff; text-align: center; margin-bottom: 6px; }\n")
            .append(".sub { text-align: center; color: #72767d; font-size: 0.8rem; margin-bottom: 18px; }\n")
            .append(".top { display: flex; gap: 10px; margin-bottom: 14px; }\n")
            .append(".card { background: #2f3136; border-radius: 6px; padding: 12px 14px; flex: 1; text-align: center; }\n")
            .append(".card .v { font-size: 1.6rem; font-weight: bold; color: #fff; }\n")
            .append(".card .l { font-size: 0.75rem; color: #72767d; margin-top: 2px; }\n")
            .append("table { width: 100%; border-collapse: collapse; background: #2f3136; border-radius: 6px; overflow: hidden; margin-bottom: 18px; }\n")
            .append("th, td { padding: 8px 12px; text-align: left; }\n")
            .append("th { color: #72767d; font-size: 0.75rem; text-transform: uppercase; border-bottom: 1px solid #40444b; }\n")
            .append("td.n, th.n { text-align: right; }\n")
            .append("tr + tr td { border-top: 1px solid #36393f; }\n")
            .append("h3 { color: #fff; margin: 18px 0 8px; }\n")
            .append("</style>\n</head>\n<body>\n<h2>SixToolsAddon Fish Stats</h2>\n");
        h.append("<div class=\"sub\">Updated ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            .append(" &middot; refreshes every " + refreshSeconds + "s &middot; idle gaps over 15 min are not counted as fishing time</div>\n");
        h.append("<div class=\"top\">")
            .append(card(String.valueOf(sorted.size()), "Total fish"))
            .append(card(rate(sorted.size(), hours), "Fish / hour (avg)"))
            .append(card(String.valueOf(lastHour), "Last 60 min"))
            .append(card(hours <= 0 ? "-" : String.format(Locale.ROOT, "%.1f h", hours), "Active fishing time"))
            .append("</div>\n");
        if (serverTotal > 0) {
            int missing = Math.max(0, serverTotal - sorted.size());
            h.append("<div class=\"top\">")
                .append(card(String.valueOf(serverTotal), "Server total"))
                .append(card(String.valueOf(missing), "Not found in any log"))
                .append("</div>\n");
        }

        h.append("<h3>Rarities</h3>\n<table>\n<tr><th>Rarity</th><th class=\"n\">Caught</th><th class=\"n\">Share</th><th class=\"n\">Per hour (avg)</th></tr>\n");
        List<String> rarities = new ArrayList<>(ORDER);
        for (Map.Entry<String, Integer> e : byRarity.entrySet()) if (!rarities.contains(e.getKey()) && e.getValue() > 0) rarities.add(e.getKey());
        for (String r : rarities) {
            int n = byRarity.getOrDefault(r, 0);
            String share = sorted.isEmpty() ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * n / sorted.size());
            h.append("<tr><td style=\"color:").append(color(r)).append(";font-weight:bold\">").append(esc(r)).append("</td><td class=\"n\">")
                .append(n).append("</td><td class=\"n\">").append(share).append("</td><td class=\"n\">").append(rate(n, hours)).append("</td></tr>\n");
        }
        h.append("</table>\n");

        List<Map.Entry<String, int[]>> entries = new ArrayList<>(species.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        h.append("<h3>Fish</h3>\n<table>\n<tr><th>Fish</th><th class=\"n\">Caught</th><th class=\"n\">Per hour (avg)</th><th class=\"n\">Biggest (cm)</th></tr>\n");
        for (Map.Entry<String, int[]> e : entries) {
            String r = speciesRarity.get(e.getKey());
            String nm = e.getKey().substring(e.getKey().indexOf('|') + 1);
            h.append("<tr><td style=\"color:").append(color(r)).append(";font-weight:bold\">").append(esc(nm)).append("</td><td class=\"n\">")
                .append(e.getValue()[0]).append("</td><td class=\"n\">").append(rate(e.getValue()[0], hours)).append("</td><td class=\"n\">")
                .append(String.format(Locale.ROOT, "%.1f", best.get(e.getKey()))).append("</td></tr>\n");
        }
        h.append("<tr><td style=\"font-weight:bold\">Total</td><td class=\"n\" style=\"font-weight:bold\">").append(sorted.size()).append("</td><td class=\"n\">").append(rate(sorted.size(), hours)).append("</td><td class=\"n\"></td></tr>\n");
        h.append("</table>\n");
        if (!byDay.isEmpty()) {
            h.append("<h3>Catches per day</h3>\n<div class=\"sub\" style=\"text-align:left\">Logs cover ").append(byDay.firstKey()).append(" to ").append(byDay.lastKey())
                .append(". Days missing from this list have no catches in any imported log.</div>\n<table>\n<tr><th>Date</th><th class=\"n\">Caught</th></tr>\n");
            int shown = 0;
            for (Map.Entry<LocalDate, Integer> e : byDay.descendingMap().entrySet()) {
                if (shown++ >= 120) break;
                h.append("<tr><td>").append(e.getKey()).append("</td><td class=\"n\">").append(e.getValue()).append("</td></tr>\n");
            }
            h.append("</table>\n");
        }
        h.append("</body>\n</html>\n");

        try {
            Files.createDirectories(DIR);
            Files.writeString(HTML, h.toString(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException ignored) {
            Swallowed.log("FishStats:writeHtml", ignored);
            return false;
        }
    }

    private static String card(String value, String label) {
        return "<div class=\"card\"><div class=\"v\">" + value + "</div><div class=\"l\">" + label + "</div></div>";
    }
}
