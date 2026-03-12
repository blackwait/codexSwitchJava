package com.codexswitcher.ui;

import com.codexswitcher.app.AppState;
import com.codexswitcher.service.AppServices;
import javafx.application.Platform;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class AppContext {

    private final AppState state;
    private final AppServices services;
    private final ExecutorService executor;
    private final Runnable refreshAll;
    private final Consumer<Integer> updateBadge;

    public AppContext(AppState state, AppServices services, ExecutorService executor, Runnable refreshAll, Consumer<Integer> updateBadge) {
        this.state = state;
        this.services = services;
        this.executor = executor;
        this.refreshAll = refreshAll;
        this.updateBadge = updateBadge;
    }

    public AppState state() {
        return state;
    }

    public AppServices services() {
        return services;
    }

    public void refreshAll() {
        refreshAll.run();
    }

    public void updateBadge(int count) {
        updateBadge.accept(count);
    }

    public <T> void runAsync(ThrowingSupplier<T> supplier, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }, executor).whenComplete((value, throwable) -> Platform.runLater(() -> {
            if (throwable != null) {
                Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
                onError.accept(cause);
            } else {
                onSuccess.accept(value);
            }
        }));
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
