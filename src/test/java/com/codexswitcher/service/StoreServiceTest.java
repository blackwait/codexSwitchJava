package com.codexswitcher.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreServiceTest {

    @BeforeEach
    void resetHomeStore() throws IOException {
        BaseSupport.deleteRecursively(BaseSupport.CODEX_DIR);
        Files.createDirectories(BaseSupport.CODEX_DIR);
    }

    @Test
    void saveAccountTestModelPersistsForNextLaunch() throws IOException {
        StoreService service = new StoreService();

        service.saveAccountTestModel("gpt-5.4");

        assertEquals("gpt-5.4", service.loadAccountTestModel());
        String storeText = Files.readString(BaseSupport.PROFILE_STORE, StandardCharsets.UTF_8);
        assertTrue(storeText.contains("\"account_test_model\""));
        assertTrue(storeText.contains("gpt-5.4"));
    }

    @Test
    void loadAccountTestModelFallsBackToGpt53Codex() {
        StoreService service = new StoreService();

        assertEquals("gpt-5.3-codex", service.loadAccountTestModel());
    }
}
