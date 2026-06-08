package com.codexswitcher.service;

import com.codexswitcher.model.Account;
import com.codexswitcher.model.CloudAuthSession;
import com.codexswitcher.model.CloudSyncSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class StoreService extends BaseSupport {

    private static final String DEFAULT_ACCOUNT_TEST_MODEL = "gpt-5.3-codex";

    public ObjectNode loadStoreNode() {
        ObjectNode root;
        if (Files.exists(PROFILE_STORE)) {
            try {
                root = (ObjectNode) JSON.readTree(PROFILE_STORE.toFile());
            } catch (Exception ignored) {
                root = JSON.createObjectNode();
            }
        } else {
            root = JSON.createObjectNode();
        }
        if (!(root.get("profiles") instanceof ObjectNode)) {
            root.set("profiles", JSON.createObjectNode());
        }
        if (!(root.get("teams") instanceof ObjectNode)) {
            root.set("teams", JSON.createObjectNode());
        }
        if (!root.has("active")) {
            root.putNull("active");
        }
        if (!(root.get("cloud_sync") instanceof ObjectNode)) {
            root.set("cloud_sync", JSON.createObjectNode());
        }
        return root;
    }

    public void saveStoreNode(ObjectNode node) throws IOException {
        writeJson(PROFILE_STORE, node);
        Files.writeString(PROFILE_STORE, System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    public List<Account> buildAccounts() {
        ObjectNode root = loadStoreNode();
        List<Account> accounts = new ArrayList<>();
        TreeSet<String> teamNames = new TreeSet<>();
        root.with("teams").fieldNames().forEachRemaining(teamNames::add);
        for (String name : teamNames) {
            JsonNode node = root.with("teams").path(name);
            accounts.add(new Account(
                name,
                node.path("base_url").asText(""),
                node.path("api_key").asText(""),
                node.path("model_name").asText(""),
                node.path("org_id").asText(""),
                true,
                "team"
            ));
        }
        TreeSet<String> profileNames = new TreeSet<>();
        root.with("profiles").fieldNames().forEachRemaining(profileNames::add);
        for (String name : profileNames) {
            JsonNode node = root.with("profiles").path(name);
            String baseUrl = node.path("base_url").asText("");
            String accountType = node.path("account_type").asText(
                "https://api.openai.com/v1".equals(baseUrl) ? "official" : "proxy"
            );
            accounts.add(new Account(name, baseUrl, node.path("api_key").asText(""), node.path("model_name").asText(""), "", false, accountType));
        }
        accounts.sort(Comparator.comparing(Account::isTeam).reversed().thenComparing(Account::getName, String.CASE_INSENSITIVE_ORDER));
        return accounts;
    }

    public Account getActiveAccount() {
        ObjectNode root = loadStoreNode();
        String active = root.path("active").asText("");
        if (isBlank(active)) {
            return null;
        }
        if (active.startsWith("team:")) {
            String name = active.substring(5);
            JsonNode node = root.with("teams").path(name);
            if (node.isObject()) {
                return new Account(name, node.path("base_url").asText(""), node.path("api_key").asText(""),
                    node.path("model_name").asText(""), node.path("org_id").asText(""), true, "team");
            }
            return null;
        }
        JsonNode node = root.with("profiles").path(active);
        if (node.isObject()) {
            String baseUrl = node.path("base_url").asText("");
            String accountType = node.path("account_type").asText(
                "https://api.openai.com/v1".equals(baseUrl) ? "official" : "proxy"
            );
            return new Account(active, baseUrl, node.path("api_key").asText(""), node.path("model_name").asText(""), "", false, accountType);
        }
        return null;
    }

    public void setActiveAccount(Account account) throws IOException {
        ObjectNode root = loadStoreNode();
        if (account == null || isBlank(account.getName())) {
            root.putNull("active");
        } else if (account.isTeam()) {
            root.put("active", "team:" + account.getName());
        } else {
            root.put("active", account.getName());
        }
        saveStoreNode(root);
    }

    public void upsertAccount(Account account) throws IOException {
        ObjectNode root = loadStoreNode();
        upsertAccount(root, account);
        saveStoreNode(root);
    }

    public int exportAccounts(Path target) throws IOException {
        AccountBatchExport payload = new AccountBatchExport();
        payload.format = "codex-switcher-accounts";
        payload.version = "1";
        payload.exportedAt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now());
        payload.accounts = buildAccounts();
        Account active = getActiveAccount();
        if (active != null) {
            payload.active = new ActiveAccount(active.getName(), active.isTeam());
        }
        writeJson(target, payload);
        Files.writeString(target, System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        return payload.accounts.size();
    }

    public BatchImportResult importAccounts(Path source) throws IOException {
        JsonNode rootNode = JSON.readTree(source.toFile());
        return importAccounts(rootNode);
    }

    public BatchImportResult importAccounts(JsonNode rootNode) throws IOException {
        ParsedBatch parsedBatch = parseBatch(rootNode);
        ObjectNode root = loadStoreNode();
        root.with("profiles").removeAll();
        root.with("teams").removeAll();
        root.putNull("active");
        int imported = 0;
        int skipped = 0;
        for (Account account : parsedBatch.accounts) {
            Account normalized = normalizeAccount(account);
            if (normalized == null) {
                skipped++;
                continue;
            }
            upsertAccount(root, normalized);
            imported++;
        }
        boolean restoredActive = restoreActive(root, parsedBatch.active);
        saveStoreNode(root);
        return new BatchImportResult(parsedBatch.accounts.size(), imported, skipped, restoredActive);
    }

    public CloudSyncApplyResult applyCloudAccounts(JsonNode rootNode) throws IOException {
        ParsedBatch parsedBatch = parseBatch(rootNode);
        if (parsedBatch.accounts.isEmpty() && !buildAccounts().isEmpty()) {
            throw new IOException("云端配置为空，已保留本地账号");
        }
        BatchImportResult importResult = importAccounts(rootNode);
        Account activeAccount = getActiveAccount();
        boolean activeApplied = false;
        if (importResult.restoredActive && activeAccount != null) {
            applyAccountConfig(activeAccount);
            activeApplied = true;
        }
        return new CloudSyncApplyResult(importResult.total, importResult.imported, importResult.skipped,
            importResult.restoredActive, activeApplied, activeAccount == null ? "" : activeAccount.getName());
    }

    public void deleteAccount(Account account) throws IOException {
        if (account == null || isBlank(account.getName())) {
            return;
        }
        ObjectNode root = loadStoreNode();
        if (account.isTeam()) {
            root.with("teams").remove(account.getName());
        } else {
            root.with("profiles").remove(account.getName());
        }
        String active = root.path("active").asText("");
        String key = account.isTeam() ? "team:" + account.getName() : account.getName();
        if (active.equals(key)) {
            root.putNull("active");
        }
        saveStoreNode(root);
    }

    public void applyAccountConfig(Account account) throws IOException {
        updateConfigBaseUrl(account.getBaseUrl());
        updateConfigModel(account.getModelName());
        updateAuth(account.getApiKey(), account.isTeam() ? account.getOrgId() : "");
        setActiveAccount(account);
    }

    public void ensureDefaultConfigExists() throws IOException {
        if (Files.exists(CONFIG_PATH) && !readText(CONFIG_PATH).trim().isEmpty()) {
            return;
        }
        writeText(CONFIG_PATH, defaultConfigToml());
    }

    public ObjectNode buildAccountsSyncPayload() {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode accounts = root.putArray("accounts");
        for (Account account : buildAccounts()) {
            ObjectNode item = accounts.addObject();
            item.put("name", account.getName());
            item.put("base_url", account.getBaseUrl());
            item.put("api_key", account.getApiKey());
            item.put("model_name", trimToEmpty(account.getModelName()));
            item.put("org_id", trimToEmpty(account.getOrgId()));
            item.put("team", account.isTeam());
            item.put("account_type", trimToEmpty(account.getAccountType()));
        }
        Account active = getActiveAccount();
        if (active != null) {
            ObjectNode activeNode = root.putObject("active");
            activeNode.put("name", active.getName());
            activeNode.put("team", active.isTeam());
        }
        return root;
    }

    public CloudSyncSettings loadCloudSyncSettings() {
        JsonNode node = loadStoreNode().path("cloud_sync");
        CloudSyncSettings settings = new CloudSyncSettings();
        settings.setEnabled(node.path("enabled").asBoolean(false));
        String serverUrl = trimToEmpty(node.path("server_url").asText(""));
        settings.setServerUrl(isBlank(serverUrl) ? CloudSyncSettings.DEFAULT_SERVER_URL : serverUrl);
        String projectName = trimToEmpty(node.path("project_name").asText(""));
        settings.setProjectName(isBlank(projectName) ? CloudSyncSettings.DEFAULT_PROJECT_NAME : projectName);
        settings.setAuthSession(loadCloudAuthSession(node.path("auth")));
        return settings;
    }

    public void saveCloudSyncSettings(CloudSyncSettings settings) throws IOException {
        ObjectNode root = loadStoreNode();
        ObjectNode node = root.with("cloud_sync");
        node.put("enabled", settings != null && settings.isEnabled());
        if (settings == null || isBlank(settings.getServerUrl())) {
            node.put("server_url", CloudSyncSettings.DEFAULT_SERVER_URL);
        } else {
            node.put("server_url", trimToEmpty(settings.getServerUrl()));
        }
        if (settings == null || isBlank(settings.getProjectName())) {
            node.put("project_name", CloudSyncSettings.DEFAULT_PROJECT_NAME);
        } else {
            node.put("project_name", trimToEmpty(settings.getProjectName()));
        }
        if (settings != null && settings.getAuthSession().isLoggedIn()) {
            writeCloudAuthSession(node, settings.getAuthSession());
        } else {
            node.remove("auth");
        }
        saveStoreNode(root);
    }

    public void clearCloudAuthSession() throws IOException {
        ObjectNode root = loadStoreNode();
        root.with("cloud_sync").remove("auth");
        saveStoreNode(root);
    }

    private CloudAuthSession loadCloudAuthSession(JsonNode authNode) {
        CloudAuthSession session = new CloudAuthSession();
        if (authNode == null || !authNode.isObject()) {
            return session;
        }
        if (authNode.has("user_id")) {
            session.setUserId(authNode.path("user_id").asLong());
        }
        session.setUsername(trimToEmpty(authNode.path("username").asText("")));
        session.setToken(trimToEmpty(authNode.path("token").asText("")));
        return session;
    }

    private void writeCloudAuthSession(ObjectNode cloudSyncNode, CloudAuthSession session) {
        ObjectNode auth = cloudSyncNode.with("auth");
        if (session.getUserId() != null) {
            auth.put("user_id", session.getUserId());
        } else {
            auth.putNull("user_id");
        }
        auth.put("username", trimToEmpty(session.getUsername()));
        auth.put("token", trimToEmpty(session.getToken()));
    }

    public void saveVscodeInstallDir(Path path) throws IOException {
        ObjectNode root = loadStoreNode();
        if (path == null) {
            root.putNull("vscode_install_dir");
        } else {
            root.put("vscode_install_dir", path.toAbsolutePath().toString());
        }
        saveStoreNode(root);
    }

    public Path loadVscodeInstallDir() {
        String value = loadStoreNode().path("vscode_install_dir").asText("");
        if (isBlank(value)) {
            return null;
        }
        Path path = Path.of(value);
        return Files.exists(path) ? path : null;
    }

    public void saveAccountTestModel(String model) throws IOException {
        ObjectNode root = loadStoreNode();
        String value = trimToEmpty(model);
        if (isBlank(value)) {
            root.putNull("account_test_model");
        } else {
            root.put("account_test_model", value);
        }
        saveStoreNode(root);
    }

    public String loadAccountTestModel() {
        String value = trimToEmpty(loadStoreNode().path("account_test_model").asText(""));
        return isBlank(value) ? DEFAULT_ACCOUNT_TEST_MODEL : value;
    }

    private void updateAuth(String apiKey, String orgId) throws IOException {
        var data = readJsonMap(AUTH_PATH);
        data.put("OPENAI_API_KEY", apiKey);
        if (isBlank(orgId)) {
            data.remove("OPENAI_ORG_ID");
        } else {
            data.put("OPENAI_ORG_ID", orgId);
        }
        writeJson(AUTH_PATH, data);
        verifyWritable(AUTH_PATH);
        Files.writeString(AUTH_PATH, System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private void updateConfigBaseUrl(String newUrl) throws IOException {
        String text = readText(CONFIG_PATH);
        String lineEnding = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(List.of(text.split("\\R", -1)));
        if (lines.size() == 1 && lines.get(0).isEmpty()) {
            lines.clear();
        }

        var sectionPattern = java.util.regex.Pattern.compile("^\\s*\\[([^\\]]+)]\\s*$");
        var providerPattern = java.util.regex.Pattern.compile("^\\s*model_provider\\s*=\\s*[\"']([^\"']+)[\"']");
        var basePattern = java.util.regex.Pattern.compile("^\\s*base_url\\s*=");
        String activeProvider = null;
        String currentSection = null;
        var providerSections = new java.util.LinkedHashMap<String, Integer>();
        var providerBaseUrls = new java.util.LinkedHashMap<String, Integer>();
        var providerOrder = new ArrayList<String>();

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            var sectionMatcher = sectionPattern.matcher(line.trim());
            if (sectionMatcher.matches()) {
                currentSection = sectionMatcher.group(1).trim().replace("\"", "").replace("'", "");
                if (currentSection.startsWith("model_providers.")) {
                    String provider = currentSection.substring("model_providers.".length());
                    providerSections.putIfAbsent(provider, index);
                    if (!providerOrder.contains(provider)) {
                        providerOrder.add(provider);
                    }
                }
                continue;
            }
            if (currentSection == null && activeProvider == null) {
                var providerMatcher = providerPattern.matcher(line.trim());
                if (providerMatcher.find()) {
                    activeProvider = providerMatcher.group(1).trim();
                }
            }
            if (currentSection != null && currentSection.startsWith("model_providers.")) {
                String provider = currentSection.substring("model_providers.".length());
                if (!providerBaseUrls.containsKey(provider) && basePattern.matcher(line.trim()).find()) {
                    providerBaseUrls.put(provider, index);
                }
            }
        }

        String targetProvider = null;
        if (!isBlank(activeProvider) && providerSections.containsKey(activeProvider)) {
            targetProvider = activeProvider;
        } else if (!providerBaseUrls.isEmpty()) {
            targetProvider = providerBaseUrls.keySet().iterator().next();
        } else if (!providerOrder.isEmpty()) {
            targetProvider = providerOrder.get(0);
        }

        if (lines.isEmpty()) {
            String provider = isBlank(activeProvider) ? "codexzh" : activeProvider;
            lines.add("model_provider = \"" + provider + "\"");
            lines.add("");
            lines.add("[model_providers." + provider + "]");
            lines.add("base_url = \"" + newUrl + "\"");
        } else if (!isBlank(targetProvider)) {
            Integer baseIndex = providerBaseUrls.get(targetProvider);
            if (baseIndex != null) {
                String original = lines.get(baseIndex);
                int indentLength = original.length() - original.stripLeading().length();
                String indent = indentLength > 0 ? original.substring(0, indentLength) : "";
                lines.set(baseIndex, indent + "base_url = \"" + newUrl + "\"");
            } else {
                lines.add(providerSections.get(targetProvider) + 1, "base_url = \"" + newUrl + "\"");
            }
        } else {
            String provider = isBlank(activeProvider) ? "codexzh" : activeProvider;
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            lines.add("[model_providers." + provider + "]");
            lines.add("base_url = \"" + newUrl + "\"");
        }

        String output = String.join(lineEnding, lines);
        if (!output.endsWith(lineEnding)) {
            output += lineEnding;
        }
        writeText(CONFIG_PATH, output);
    }

    private void updateConfigModel(String modelName) throws IOException {
        String value = trimToEmpty(modelName);
        if (isBlank(value)) {
            return;
        }
        String text = readText(CONFIG_PATH);
        String lineEnding = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(List.of(text.split("\\R", -1)));
        if (lines.size() == 1 && lines.get(0).isEmpty()) {
            lines.clear();
        }
        var sectionPattern = java.util.regex.Pattern.compile("^\\s*\\[([^\\]]+)]\\s*$");
        var modelPattern = java.util.regex.Pattern.compile("^\\s*model\\s*=");
        Integer modelIndex = null;
        Integer firstSectionIndex = null;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (sectionPattern.matcher(line.trim()).matches()) {
                firstSectionIndex = index;
                break;
            }
            if (modelPattern.matcher(line.trim()).find()) {
                modelIndex = index;
            }
        }
        String targetLine = "model = \"" + value + "\"";
        if (modelIndex != null) {
            lines.set(modelIndex, targetLine);
        } else if (firstSectionIndex != null) {
            lines.add(firstSectionIndex, targetLine);
        } else {
            lines.add(targetLine);
        }
        String output = String.join(lineEnding, lines);
        if (!output.endsWith(lineEnding)) {
            output += lineEnding;
        }
        writeText(CONFIG_PATH, output);
    }

    private void upsertAccount(ObjectNode root, Account account) {
        ObjectNode profiles = root.with("profiles");
        ObjectNode teams = root.with("teams");
        if (account.isTeam()) {
            profiles.remove(account.getName());
            ObjectNode team = JSON.createObjectNode();
            team.put("base_url", account.getBaseUrl());
            team.put("api_key", account.getApiKey());
            team.put("model_name", trimToEmpty(account.getModelName()));
            team.put("org_id", account.getOrgId());
            teams.set(account.getName(), team);
        } else {
            teams.remove(account.getName());
            ObjectNode profile = JSON.createObjectNode();
            profile.put("base_url", account.getBaseUrl());
            profile.put("api_key", account.getApiKey());
            profile.put("model_name", trimToEmpty(account.getModelName()));
            profile.put("account_type", firstNonBlank(account.getAccountType(), "proxy"));
            profiles.set(account.getName(), profile);
        }
    }

    private ParsedBatch parseBatch(JsonNode root) throws IOException {
        if (root == null || root.isNull()) {
            return new ParsedBatch(List.of(), null);
        }
        if (root.isArray()) {
            return new ParsedBatch(parseAccountsArray(root), null);
        }
        if (!root.isObject()) {
            throw new IOException("导入文件不是有效的 JSON 对象或数组");
        }
        List<Account> accounts = new ArrayList<>();
        ActiveAccount active = null;
        if (root.path("accounts").isArray()) {
            accounts.addAll(parseAccountsArray(root.path("accounts")));
            active = parseActive(root.path("active"));
        } else if (root.path("profiles").isObject() || root.path("teams").isObject()) {
            accounts.addAll(parseAccountsFromStoreNode(root));
            active = parseActive(root.path("active"));
        } else {
            throw new IOException("未识别的账号导入格式");
        }
        return new ParsedBatch(accounts, active);
    }

    private List<Account> parseAccountsArray(JsonNode arrayNode) {
        List<Account> accounts = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            Account account = parseAccountNode(node);
            if (account != null) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    private List<Account> parseAccountsFromStoreNode(JsonNode root) {
        List<Account> accounts = new ArrayList<>();
        JsonNode teamsNode = root.path("teams");
        if (teamsNode.isObject()) {
            teamsNode.fieldNames().forEachRemaining(name -> {
                JsonNode node = teamsNode.path(name);
                accounts.add(new Account(
                    name,
                    node.path("base_url").asText(""),
                    node.path("api_key").asText(""),
                    node.path("model_name").asText(""),
                    node.path("org_id").asText(""),
                    true,
                    "team"
                ));
            });
        }
        JsonNode profilesNode = root.path("profiles");
        if (profilesNode.isObject()) {
            profilesNode.fieldNames().forEachRemaining(name -> {
                JsonNode node = profilesNode.path(name);
                String baseUrl = node.path("base_url").asText("");
                String accountType = node.path("account_type").asText(
                    "https://api.openai.com/v1".equals(baseUrl) ? "official" : "proxy"
                );
                accounts.add(new Account(name, baseUrl, node.path("api_key").asText(""), node.path("model_name").asText(""), "", false, accountType));
            });
        }
        return accounts;
    }

    private Account parseAccountNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String name = readText(node, "name");
        String baseUrl = readText(node, "baseUrl", "base_url");
        String apiKey = readText(node, "apiKey", "api_key");
        String modelName = readText(node, "modelName", "model_name", "model");
        String orgId = readText(node, "orgId", "org_id");
        boolean team = readBoolean(node, "team") || "team".equalsIgnoreCase(readText(node, "accountType", "account_type"));
        String accountType = readText(node, "accountType", "account_type");
        if (!team && isBlank(accountType)) {
            accountType = "https://api.openai.com/v1".equals(baseUrl) ? "official" : "proxy";
        }
        return new Account(name, baseUrl, apiKey, modelName, orgId, team, team ? "team" : accountType);
    }

    private String readText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode field = node.get(key);
            if (field != null && !field.isNull()) {
                return field.asText("");
            }
        }
        return "";
    }

    private boolean readBoolean(JsonNode node, String key) {
        JsonNode field = node.get(key);
        return field != null && field.asBoolean(false);
    }

    private ActiveAccount parseActive(JsonNode activeNode) {
        if (activeNode == null || activeNode.isNull()) {
            return null;
        }
        if (activeNode.isTextual()) {
            String active = activeNode.asText("");
            if (isBlank(active)) {
                return null;
            }
            if (active.startsWith("team:")) {
                return new ActiveAccount(active.substring(5), true);
            }
            return new ActiveAccount(active, false);
        }
        if (!activeNode.isObject()) {
            return null;
        }
        String name = readText(activeNode, "name");
        if (isBlank(name)) {
            return null;
        }
        boolean team = readBoolean(activeNode, "team");
        return new ActiveAccount(name, team);
    }

    private Account normalizeAccount(Account account) {
        if (account == null) {
            return null;
        }
        String name = trimToEmpty(account.getName());
        String baseUrl = trimToEmpty(account.getBaseUrl());
        String apiKey = trimToEmpty(account.getApiKey());
        String modelName = trimToEmpty(account.getModelName());
        String orgId = trimToEmpty(account.getOrgId());
        boolean team = account.isTeam();
        String accountType = trimToEmpty(account.getAccountType());
        if (isBlank(name) || isBlank(baseUrl) || isBlank(apiKey)) {
            return null;
        }
        if (team && isBlank(orgId)) {
            return null;
        }
        if (team) {
            accountType = "team";
        } else if (isBlank(accountType)) {
            accountType = "https://api.openai.com/v1".equals(baseUrl) ? "official" : "proxy";
        }
        return new Account(name, baseUrl, apiKey, modelName, orgId, team, accountType);
    }

    private boolean restoreActive(ObjectNode root, ActiveAccount active) {
        if (active == null || isBlank(active.name)) {
            return false;
        }
        if (active.team) {
            if (root.with("teams").has(active.name)) {
                root.put("active", "team:" + active.name);
                return true;
            }
            return false;
        }
        if (root.with("profiles").has(active.name)) {
            root.put("active", active.name);
            return true;
        }
        return false;
    }

    private static class ParsedBatch {
        private final List<Account> accounts;
        private final ActiveAccount active;

        private ParsedBatch(List<Account> accounts, ActiveAccount active) {
            this.accounts = accounts;
            this.active = active;
        }
    }

    private static class ActiveAccount {
        public String name;
        public boolean team;

        private ActiveAccount() {
        }

        private ActiveAccount(String name, boolean team) {
            this.name = name;
            this.team = team;
        }
    }

    private static class AccountBatchExport {
        public String format;
        public String version;
        public String exportedAt;
        public List<Account> accounts;
        public ActiveAccount active;
    }

    public static class BatchImportResult {
        public final int total;
        public final int imported;
        public final int skipped;
        public final boolean restoredActive;

        public BatchImportResult(int total, int imported, int skipped, boolean restoredActive) {
            this.total = total;
            this.imported = imported;
            this.skipped = skipped;
            this.restoredActive = restoredActive;
        }
    }

    public static class CloudSyncApplyResult extends BatchImportResult {
        public final boolean activeApplied;
        public final String activeAccountName;

        public CloudSyncApplyResult(int total, int imported, int skipped, boolean restoredActive, boolean activeApplied, String activeAccountName) {
            super(total, imported, skipped, restoredActive);
            this.activeApplied = activeApplied;
            this.activeAccountName = activeAccountName;
        }
    }
}
