package com.codexswitcher.service;

import com.codexswitcher.model.Account;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexServiceTest {

    @Test
    void prepareAccountTestLaunchUsesRequestedAccountAndModel() throws IOException {
        TestableCodexService service = new TestableCodexService("C:\\tools\\codex.cmd");
        Account account = new Account("proxy-a", "https://proxy.example.com/v1", "sk-proxy-a", "", false, "proxy");

        CodexService.AccountTestLaunch launch = service.prepareAccountTestLaunch(account, "gpt-5.4");

        assertNotNull(launch);
        assertEquals(List.of("C:\\tools\\codex.cmd", "chat", "-m", "gpt-5.4"), launch.command());
        assertEquals("sk-proxy-a", launch.environment().get("OPENAI_API_KEY"));
        assertEquals("https://proxy.example.com/v1", launch.environment().get("OPENAI_BASE_URL"));
        assertTrue(!launch.environment().containsKey("OPENAI_ORG_ID"));
    }

    @Test
    void testAccountsStartsEveryValidAccount() throws IOException {
        TestableCodexService service = new TestableCodexService("C:\\tools\\codex.cmd");
        List<Account> accounts = List.of(
            new Account("team-a", "https://team.example.com/v1", "sk-team-a", "org-team-a", true, "team"),
            new Account("proxy-a", "https://proxy.example.com/v1", "sk-proxy-a", "", false, "proxy")
        );

        int started = service.testAccounts(accounts, "gpt-5.2-codex");

        assertEquals(2, started);
        assertEquals(2, service.launches.size());
        assertEquals("org-team-a", service.launches.get(0).environment().get("OPENAI_ORG_ID"));
        assertEquals("sk-proxy-a", service.launches.get(1).environment().get("OPENAI_API_KEY"));
    }

    @Test
    void restartCodexAppStopsExistingCodexBeforeLaunch() throws IOException {
        TestableCodexService service = new TestableCodexService("C:\\tools\\codex.cmd");

        service.restartCodexApp();

        assertEquals(List.of("stop", "launch"), service.restartActions);
    }

    @Test
    void codexProcessMatcherIncludesCodexAppAndCliButExcludesSwitcher() {
        TestableCodexService service = new TestableCodexService("C:\\tools\\codex.cmd");

        assertTrue(service.isCodexProcessCommand("C:\\Program Files\\WindowsApps\\OpenAI.Codex_26\\app\\Codex.exe"));
        assertTrue(service.isCodexProcessCommand("C:\\Program Files\\WindowsApps\\OpenAI.Codex_26\\app\\resources\\codex.exe"));
        assertFalse(service.isCodexProcessCommand("C:\\Program Files\\CodexSwitcher\\CodexSwitcher.exe"));
        assertFalse(service.isCodexProcessCommand("C:\\Program Files\\nodejs\\node.exe"));
    }

    private static final class TestableCodexService extends CodexService {
        private final String executable;
        private final List<AccountTestLaunch> launches = new ArrayList<>();
        private final List<String> restartActions = new ArrayList<>();

        private TestableCodexService(String executable) {
            this.executable = executable;
        }

        @Override
        public String findCodexExecutable() {
            return executable;
        }

        @Override
        void launchAccountTest(AccountTestLaunch launch) {
            launches.add(launch);
        }

        @Override
        void stopRunningCodexProcesses() {
            restartActions.add("stop");
        }

        @Override
        void launchCodexApp() {
            restartActions.add("launch");
        }
    }
}
