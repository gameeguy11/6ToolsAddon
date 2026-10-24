package com.maparts.link.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.maparts.link.capture.MapGridCapture;

public final class BackendClient {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(60);

    private static final Map<String, String> KNOWN_ERROR_MESSAGES = Map.ofEntries(
            Map.entry("VALIDATION_ERROR", "Check your input and try again."),
            Map.entry("CHALLENGE_NOT_FOUND", "Code not recognized. Request a new one on the website."),
            Map.entry("CHALLENGE_USED", "This code has already been used."),
            Map.entry("CHALLENGE_EXPIRED", "This code has expired. Request a new one on the website."),
            Map.entry("CHALLENGE_STATE_CHANGED", "Could not complete. Request a new code."),
            Map.entry("RATE_LIMITED", "Too many attempts. Please wait before trying again."),
            Map.entry("UPLOADS_IN_GAME_ONLY", "Uploads happen in-game: look at your mapart wall and run /mapupload."),
            Map.entry("STORAGE_NOT_CONFIGURED", "Image storage is not configured yet. Ask the site administrator."),
            Map.entry("MC_IDENTITY_REQUIRED", "Link a Minecraft account before uploading maparts."),
            Map.entry("UPLOAD_MISSING", "The image hasn't been uploaded yet."),
            Map.entry("UPLOAD_TOO_LARGE", "Image is too large."),
            Map.entry("INVALID_IMAGE", "The uploaded file is not a valid PNG, JPEG, or WebP image."),
            Map.entry("MAPART_NOT_FOUND", "Mapart not found."),
            Map.entry("MAPART_NOT_PROCESSING", "This mapart isn't awaiting processing."));

    private final HttpClient httpClient;
    private final String backendUrl;

    public BackendClient(String backendUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)

                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.backendUrl = backendUrl;
    }

    public record VerifyResult(
            boolean success, String message, boolean created, String mcName, String modToken,
            boolean passwordRequired, boolean passwordSetupRequired) {}

    public record TokenPollResult(String status, String modToken, String errorMessage) {}

    public record DraftResult(
            boolean success, String message, String mapartId, String uploadUrl,
            boolean owned, boolean alreadyOwned) {}

    public record SimpleResult(boolean success, String message) {}

    public record FinalizeResult(
            boolean success, String message, String mapartId,
            boolean owned, boolean alreadyOwned) {}

    public record PendingResult(int pending, String latestFrom) {}

    public CompletableFuture<VerifyResult> verify(String code, String mcName) {
        JsonObject body = new JsonObject();
        body.addProperty("code", code);
        body.addProperty("mcName", mcName);

        HttpRequest request = jsonRequest("/api/auth/mc/verify", null)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(BackendClient::parseVerifyResponse)
                .exceptionally(error -> new VerifyResult(
                        false,
                        "Could not reach the Maparts website. Please try again in a moment.",
                        false,
                        null,
                        null,
                        false,
                        false));
    }

    public CompletableFuture<TokenPollResult> pollChallengeToken(String code) {
        String encodedCode = URLEncoder.encode(code, StandardCharsets.UTF_8);
        HttpRequest request = jsonRequest("/api/auth/mc/challenge/token?code=" + encodedCode, null)
                .GET()
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    JsonObject json = parseJsonObject(response.body());

                    if (json == null) {

                        return new TokenPollResult("awaiting_password", null, null);
                    }

                    boolean ok = json.has("ok") && json.get("ok").getAsBoolean();

                    if (ok && json.has("data")) {
                        JsonObject data = json.getAsJsonObject("data");
                        String status = data.has("status")
                                ? data.get("status").getAsString()
                                : "expired";
                        String modToken = data.has("modToken") && !data.get("modToken").isJsonNull()
                                ? data.get("modToken").getAsString()
                                : null;
                        return new TokenPollResult(status, modToken, null);
                    }

                    return new TokenPollResult(
                            "error", null, errorMessage(json, response.statusCode()));
                })
                .exceptionally(error -> new TokenPollResult("awaiting_password", null, null));
    }

    public CompletableFuture<DraftResult> createDraft(
            String token, String server, String title,
            int mapsWide, int mapsTall, byte[] png,
            List<MapGridCapture.TileInfo> tiles) {
        JsonObject body = new JsonObject();

        if (server != null && !server.isBlank()) {
            body.addProperty("server", server);
        }

        if (title != null && !title.isBlank()) {
            body.addProperty("title", title);
        }

        body.addProperty("mimeType", "image/png");
        body.addProperty("fileSize", png.length);
        body.addProperty("sha256", sha256Hex(png));
        body.addProperty("mapWidth", mapsWide);
        body.addProperty("mapHeight", mapsTall);

        JsonArray tileArray = new JsonArray();

        for (MapGridCapture.TileInfo tile : tiles) {
            JsonObject entry = new JsonObject();
            entry.addProperty("col", tile.col());
            entry.addProperty("row", tile.row());

            if (tile.mapId() >= 0) {
                entry.addProperty("mapId", tile.mapId());
            }

            if (tile.name() != null && !tile.name().isBlank()) {
                entry.addProperty("name", tile.name());
            }

            tileArray.add(entry);
        }

        body.add("tiles", tileArray);

        HttpRequest request = jsonRequest("/api/maparts/drafts", token)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    JsonObject json = parseJsonObject(response.body());

                    if (json == null) {
                        return new DraftResult(false,
                                "The Maparts website returned an unexpected response (HTTP "
                                        + response.statusCode() + ").",
                                null, null, false, false);
                    }

                    if (json.has("ok") && json.get("ok").getAsBoolean()
                            && json.has("data")) {
                        JsonObject data = json.getAsJsonObject("data");
                        boolean owned = data.has("owned") && data.get("owned").getAsBoolean();
                        boolean alreadyOwned = data.has("alreadyOwned")
                                && data.get("alreadyOwned").getAsBoolean();
                        return new DraftResult(true, null,
                                data.get("mapartId").getAsString(),
                                data.has("uploadUrl")
                                        ? data.get("uploadUrl").getAsString()
                                        : null,
                                owned, alreadyOwned);
                    }

                    return new DraftResult(false,
                            errorMessage(json, response.statusCode()), null, null,
                            false, false);
                })
                .exceptionally(error -> new DraftResult(false,
                        "Could not reach the Maparts website.", null, null, false, false));
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);

            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }

            return hex.toString();
        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(e);
        }
    }

    public CompletableFuture<SimpleResult> uploadPng(String uploadUrl, byte[] png) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .timeout(UPLOAD_TIMEOUT)
                .header("Content-Type", "image/png")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(png))
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> response.statusCode() / 100 == 2
                        ? new SimpleResult(true, null)
                        : new SimpleResult(false,
                                "Uploading the image failed (HTTP "
                                        + response.statusCode() + ")."))
                .exceptionally(error ->
                        new SimpleResult(false, "Uploading the image failed."));
    }

    public CompletableFuture<FinalizeResult> finalizeDraft(String token, String mapartId) {
        HttpRequest request = jsonRequest("/api/maparts/" + mapartId + "/finalize", token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    JsonObject json = parseJsonObject(response.body());

                    if (json != null && json.has("ok") && json.get("ok").getAsBoolean()
                            && json.has("data")) {
                        JsonObject data = json.getAsJsonObject("data");

                        if (data.has("owned") && data.get("owned").getAsBoolean()) {
                            boolean alreadyOwned = data.has("alreadyOwned")
                                    && data.get("alreadyOwned").getAsBoolean();
                            return new FinalizeResult(true, null,
                                    data.get("mapartId").getAsString(), true, alreadyOwned);
                        }

                        String id = null;

                        if (data.has("mapart")) {
                            JsonObject mapart = data.getAsJsonObject("mapart");

                            if (mapart.has("id")) {
                                id = mapart.get("id").getAsString();
                            }
                        }

                        return new FinalizeResult(true, null, id, false, false);
                    }

                    return new FinalizeResult(false,
                            errorMessage(json, response.statusCode()), null, false, false);
                })
                .exceptionally(error -> new FinalizeResult(false,
                        "Could not reach the Maparts website.", null, false, false));
    }

    public CompletableFuture<PendingResult> pendingTrades(String token) {
        HttpRequest request = jsonRequest("/api/trades/pending", token)
                .GET()
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    JsonObject json = parseJsonObject(response.body());

                    if (json == null || !json.has("ok") || !json.get("ok").getAsBoolean()
                            || !json.has("data")) {
                        return null;
                    }

                    JsonObject data = json.getAsJsonObject("data");
                    int pending = data.has("pending") ? data.get("pending").getAsInt() : 0;
                    String latestFrom = data.has("latestFrom") && !data.get("latestFrom").isJsonNull()
                            ? data.get("latestFrom").getAsString()
                            : null;
                    return new PendingResult(pending, latestFrom);
                })
                .exceptionally(error -> null);
    }

    private HttpRequest.Builder jsonRequest(String path, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(backendUrl + path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", "maparts-link/1.8.0");

        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }

        return builder;
    }

    private static JsonObject parseJsonObject(String body) {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            return null;
        }
    }

    private static String errorMessage(JsonObject json, int statusCode) {
        if (statusCode == 401) {

            return "Your website link was revoked or expired — get a new code"
                    + " on the site and run /maplink again.";
        }

        if (json != null && json.has("error")) {
            JsonObject error = json.getAsJsonObject("error");

            if (error.has("code") && !error.get("code").isJsonNull()) {
                String mapped = KNOWN_ERROR_MESSAGES.get(error.get("code").getAsString());

                if (mapped != null) {
                    return mapped;
                }
            }
        }

        return "Request failed. Please try again.";
    }

    private static VerifyResult parseVerifyResponse(HttpResponse<String> response) {
        JsonObject json = parseJsonObject(response.body());

        if (json == null) {
            return new VerifyResult(
                    false,
                    "The Maparts website returned an unexpected response (HTTP "
                            + response.statusCode() + ").",
                    false,
                    null,
                    null,
                    false,
                    false);
        }

        boolean ok = json.has("ok") && json.get("ok").getAsBoolean();

        if (ok && json.has("data")) {
            JsonObject data = json.getAsJsonObject("data");
            boolean created = data.has("created") && data.get("created").getAsBoolean();
            String mcName = data.has("mcName") ? data.get("mcName").getAsString() : null;
            String modToken = data.has("modToken") && !data.get("modToken").isJsonNull()
                    ? data.get("modToken").getAsString()
                    : null;
            boolean passwordRequired = data.has("passwordRequired")
                    && data.get("passwordRequired").getAsBoolean();
            boolean passwordSetupRequired = data.has("passwordSetupRequired")
                    && data.get("passwordSetupRequired").getAsBoolean();
            return new VerifyResult(
                    true, null, created, mcName, modToken, passwordRequired, passwordSetupRequired);
        }

        return new VerifyResult(
                false, errorMessage(json, response.statusCode()), false, null, null, false, false);
    }
}
