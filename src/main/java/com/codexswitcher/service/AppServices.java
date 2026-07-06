package com.codexswitcher.service;

public class AppServices {

    private final StoreService storeService = new StoreService();
    private final CodexService codexService = new CodexService();
    private final NetworkService networkService = new NetworkService();
    private final CloudAuthService cloudAuthService = new CloudAuthService();
    private final CloudSyncService cloudSyncService = new CloudSyncService(storeService);
    private final OpencodeService opencodeService = new OpencodeService();
    private final SkillsService skillsService = new SkillsService();
    private final VscodeService vscodeService = new VscodeService();
    private final SessionService sessionService = new SessionService();
    private final UpdateService updateService = new UpdateService();
    private final OpenAiStatusService openAiStatusService = new OpenAiStatusService();
    private final UsageService usageService = new UsageService(storeService);

    public StoreService store() {
        return storeService;
    }

    public CodexService codex() {
        return codexService;
    }

    public NetworkService network() {
        return networkService;
    }

    public CloudAuthService cloudAuth() {
        return cloudAuthService;
    }

    public CloudSyncService cloudSync() {
        return cloudSyncService;
    }

    public OpencodeService opencode() {
        return opencodeService;
    }

    public SkillsService skills() {
        return skillsService;
    }

    public VscodeService vscode() {
        return vscodeService;
    }

    public SessionService sessions() {
        return sessionService;
    }

    public UpdateService updates() {
        return updateService;
    }

    public OpenAiStatusService openAiStatus() {
        return openAiStatusService;
    }

    public UsageService usage() {
        return usageService;
    }
}
