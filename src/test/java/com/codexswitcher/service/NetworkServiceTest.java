package com.codexswitcher.service;

import com.codexswitcher.model.Account;
import com.codexswitcher.model.AccountProbeStatus;
import com.codexswitcher.model.DiagnosisResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkServiceTest {

    @Test
    void probeAccountsReturnsSuccessAndFailurePerAccount() {
        TestableNetworkService service = new TestableNetworkService();
        service.outcomes.put("ok:false", successDiagnosis("/chat/completions"));
        service.failures.put("bad:false", new IOException("HTTP 401"));
        List<Account> accounts = List.of(
            new Account("ok", "https://ok.example.com/v1", "sk-ok", "", false, "proxy"),
            new Account("bad", "https://bad.example.com/v1", "sk-bad", "", false, "proxy")
        );

        List<AccountProbeStatus> results = service.probeAccounts(accounts, "gpt-5.3-codex", 10);

        assertEquals(2, results.size());
        assertTrue(results.get(0).ok());
        assertEquals("可用", results.get(0).summary());
        assertFalse(results.get(1).ok());
        assertTrue(results.get(1).detail().contains("HTTP 401"));
    }

    @Test
    void probeAccountMarksIncompleteTeamAccountAsFailure() {
        NetworkService service = new NetworkService();
        Account account = new Account("team-a", "https://team.example.com/v1", "sk-team-a", "", true, "team");

        AccountProbeStatus status = service.probeAccount(account, "gpt-5.3-codex", 10);

        assertFalse(status.ok());
        assertTrue(status.detail().contains("Org ID"));
    }

    private static DiagnosisResult successDiagnosis(String endpoint) {
        DiagnosisResult result = new DiagnosisResult();
        result.setConclusion("结论：链路正常");
        result.setSuccessEndpoint(endpoint);
        result.setDetail("detail");
        return result;
    }

    private static final class TestableNetworkService extends NetworkService {
        private final Map<String, DiagnosisResult> outcomes = new java.util.LinkedHashMap<>();
        private final Map<String, Exception> failures = new java.util.LinkedHashMap<>();

        @Override
        DiagnosisResult probeAccountEndpoints(Account account, String model, int timeoutSeconds) throws IOException, InterruptedException {
            String key = account.getName() + ":" + account.isTeam();
            if (failures.containsKey(key)) {
                Exception failure = failures.get(key);
                if (failure instanceof IOException ioException) {
                    throw ioException;
                }
                if (failure instanceof InterruptedException interruptedException) {
                    throw interruptedException;
                }
                throw new IOException(failure);
            }
            return outcomes.get(key);
        }
    }
}
