package com.maparts.link.command;

import com.maparts.link.capture.MapGridCapture;
import com.maparts.link.http.BackendClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class UploadPipeline {
    private static final long RETRY_DELAY_SECONDS = 5;

    record Outcome(boolean success, String message, String url, String urlLabel) {
        static Outcome ok(String mapartId) {
            return new Outcome(true, null,
                    LinkCommand.BACKEND_URL + "/maparts/" + mapartId,
                    "Open it on the site");
        }

        static Outcome owned(String mapartId, boolean alreadyOwned) {
            return new Outcome(true,
                    alreadyOwned
                            ? "You already own this mapart."
                            : "This mapart already exists — you've been added as an owner.",
                    LinkCommand.BACKEND_URL + "/maparts/" + mapartId,
                    "Open it on the site");
        }

        static Outcome fail(String message) {
            return new Outcome(false, message, null, null);
        }
    }

    private UploadPipeline() {}

    static CompletableFuture<Outcome> upload(
            BackendClient backend, String token, String server, String title,
            MapGridCapture.Result capture, Runnable onRetrying) {
        return attempt(backend, token, server, title, capture)
                .thenCompose(outcome -> {
                    if (outcome.success()) {
                        return CompletableFuture.completedFuture(outcome);
                    }

                    if (onRetrying != null) {
                        onRetrying.run();
                    }

                    return afterDelay()
                            .thenCompose(ignored -> attempt(backend, token, server, title, capture));
                });
    }

    private static CompletableFuture<Void> afterDelay() {
        return CompletableFuture.runAsync(
                () -> {},
                CompletableFuture.delayedExecutor(RETRY_DELAY_SECONDS, TimeUnit.SECONDS));
    }

    private static CompletableFuture<Outcome> attempt(
            BackendClient backend, String token, String server, String title,
            MapGridCapture.Result capture) {
        return backend
                .createDraft(token, server, title, capture.mapsWide(), capture.mapsTall(),
                        capture.png(), capture.tiles())
                .thenCompose(draft -> {
                    if (!draft.success()) {
                        return CompletableFuture.completedFuture(
                                Outcome.fail(draft.message()));
                    }

                    if (draft.owned()) {
                        return CompletableFuture.completedFuture(
                                Outcome.owned(draft.mapartId(), draft.alreadyOwned()));
                    }

                    return backend.uploadPng(draft.uploadUrl(), capture.png())
                            .thenCompose(upload -> {
                                if (!upload.success()) {
                                    return CompletableFuture.completedFuture(
                                            Outcome.fail(upload.message()));
                                }

                                return backend
                                        .finalizeDraft(token, draft.mapartId())
                                        .thenApply(result -> {
                                            if (!result.success()) {
                                                return Outcome.fail(result.message());
                                            }

                                            if (result.owned()) {
                                                return Outcome.owned(
                                                        result.mapartId(),
                                                        result.alreadyOwned());
                                            }

                                            return Outcome.ok(
                                                    result.mapartId() != null
                                                            ? result.mapartId()
                                                            : draft.mapartId());
                                        });
                            });
                });
    }
}
