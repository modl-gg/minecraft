package gg.modl.minecraft.core.boot;

import gg.modl.minecraft.core.util.PluginLogger;

import java.util.ArrayList;
import java.util.List;

public class SetupWizard {
    private static final long POLL_INTERVAL_MS = 5000;
    private static final int MAX_POLL_ATTEMPTS = 120; // 10 minutes

    private final PluginLogger logger;
    private final ConsoleInput input;
    private final PlatformType platformType;
    private boolean testingApi = false;

    public SetupWizard(PluginLogger logger, ConsoleInput input, PlatformType platformType) {
        this.logger = logger;
        this.input = input;
        this.platformType = platformType;
    }

    public BootConfig run() {
        printBanner();

        if (platformType == PlatformType.SPIGOT) {
            return runSpigotWizard();
        } else {
            return runProxyWizard();
        }
    }

    private BootConfig runSpigotWizard() {
        String response = input.readLine("Is this a standalone server (no proxy)? [yes/no]: ");
        testingApi = response != null && response.contains("--test-mode");
        if (testingApi) {
            logger.info("Test mode enabled — using api.modl.top");
        }
        boolean standalone = response != null &&
                (response.replace("--test-mode", "").trim().equalsIgnoreCase("yes") ||
                 response.replace("--test-mode", "").trim().equalsIgnoreCase("y"));

        if (standalone) {
            return runStandaloneWizard();
        } else {
            return runBridgeOnlyWizard();
        }
    }

    private BootConfig runStandaloneWizard() {
        boolean hasAccount = input.confirm("Have you already registered a server on modl.gg?");

        BootConfig config = new BootConfig();
        config.setMode(BootConfig.Mode.STANDALONE);
        config.setTestingApi(testingApi);

        if (hasAccount) {
            String apiKey = input.readLine("Enter your API key: ");
            String panelUrl = input.readLine("Panel domain (e.g. server.modl.gg or support.server.com): ");
            config.setApiKey(apiKey != null ? apiKey.trim() : "");
            config.setPanelUrl(normalizePanelUrl(panelUrl));
        } else {
            RegistrationResult result = runRegistrationFlowWithRetry();
            if (result != null) {
                config.setApiKey(result.apiKey);
                config.setPanelUrl(result.panelUrl);
            } else {
                logger.warning("Registration failed. You can configure boot.yml manually.");
                return config;
            }
        }

        saveAndPrint(config);
        return config;
    }

    private BootConfig runBridgeOnlyWizard() {
        BootConfig config = new BootConfig();
        config.setMode(BootConfig.Mode.BRIDGE_ONLY);
        config.setTestingApi(testingApi);

        String panelUrl = input.readLine("Panel domain (e.g. server.modl.gg or support.server.com): ");
        config.setPanelUrl(normalizePanelUrl(panelUrl));

        String apiKey = input.readLine("Enter your API key (from proxy setup or modl.gg panel): ");
        logger.info("If you don't have one, register using the proxy's setup wizard first.");

        config.setApiKey(apiKey != null ? apiKey.trim() : "");

        saveAndPrint(config);
        return config;
    }

    private BootConfig runProxyWizard() {
        String response = input.readLine("Have you already registered a server on modl.gg? [yes/no]: ");
        testingApi = response != null && response.contains("--test-mode");
        if (testingApi) {
            logger.info("Test mode enabled using api.modl.top");
        }
        boolean hasAccount = response != null &&
                (response.replace("--test-mode", "").trim().equalsIgnoreCase("yes") ||
                 response.replace("--test-mode", "").trim().equalsIgnoreCase("y"));

        BootConfig config = new BootConfig();
        config.setMode(BootConfig.Mode.PROXY);
        config.setTestingApi(testingApi);

        if (hasAccount) {
            String apiKey = input.readLine("Enter your API key: ");
            String panelUrl = input.readLine("Panel domain (e.g. server.modl.gg or support.server.com): ");
            config.setApiKey(apiKey != null ? apiKey.trim() : "");
            config.setPanelUrl(normalizePanelUrl(panelUrl));
        } else {
            RegistrationResult result = runRegistrationFlowWithRetry();
            if (result != null) {
                config.setApiKey(result.apiKey);
                config.setPanelUrl(result.panelUrl);
            } else {
                logger.warning("Registration failed. You can configure boot.yml manually.");
                return config;
            }
        }

        configureBackendBridges(config);

        saveAndPrint(config);
        return config;
    }

    private void configureBackendBridges(BootConfig config) {
        boolean localBackends = input.confirm("Are the backend server(s) on this machine (say no if using Pterodactyl or docker?");
        List<BootConfig.BackendBridge> bridges = new ArrayList<>();

        if (localBackends) {
            bridges.add(new BootConfig.BackendBridge("127.0.0.1", 25590));
        } else {
            String ipsStr = input.readLine("Enter backend server IPs (comma-separated): ");
            if (ipsStr != null && !ipsStr.trim().isEmpty()) {
                for (String ip : ipsStr.split(",")) {
                    String trimmed = ip.trim();
                    if (!trimmed.isEmpty()) {
                        bridges.add(new BootConfig.BackendBridge(trimmed, 25590));
                        logger.info("Added backend bridge: " + trimmed + ":25590");
                    }
                }
            }
        }

        config.setBackendBridges(bridges);
    }

    private RegistrationResult runRegistrationFlowWithRetry() {
        while (true) {
            RegistrationResult result = runRegistrationFlow();
            if (result != null) return result;

            if (!input.confirm("Would you like to retry?")) {
                return null;
            }
            logger.info("");
        }
    }

    private RegistrationResult runRegistrationFlow() {
        String email = input.readLine("Admin email: ");
        String serverName = input.readLine("Server name: ");
        String subdomain = input.readLine("Panel subdomain (e.g. \"myserver\" for myserver.modl.gg): ");

        if (!input.confirm("Do you agree to the Terms of Service? (https://modl.gg/terms)")) {
            logger.info("You must agree to the Terms of Service to register.");
            return null;
        }

        logger.info("Registering server...");
        RegistrationClient client = new RegistrationClient(testingApi);

        try {
            RegistrationClient.RegisterResponse registerResponse = client.register(
                    email != null ? email.trim() : "",
                    serverName != null ? serverName.trim() : "",
                    subdomain != null ? subdomain.trim() : "",
                    "free"
            );

            if (!registerResponse.success()) {
                logger.severe("Registration failed: " + registerResponse.message());
                return null;
            }

            String setupToken = registerResponse.setupToken();
            if (setupToken == null || setupToken.isBlank()) {
                logger.severe("Registration succeeded but no setup token was returned.");
                return null;
            }

            logger.info("Check your email and click the verification link.");
            logger.info("Waiting for verification... (polling every 5s)");

            RegistrationClient.CliStatusResponse statusResponse = pollForCompletion(client, setupToken);
            if (statusResponse == null) {
                logger.severe("Setup did not complete in time. Check the panel for your API key.");
                return null;
            }

            logger.info("Setup complete!");
            return new RegistrationResult(statusResponse.apiKey(), statusResponse.message());
        } catch (Exception e) {
            logger.severe("Registration error: " + e.getMessage());
            return null;
        }
    }

    private RegistrationClient.CliStatusResponse pollForCompletion(RegistrationClient client, String setupToken)
            throws Exception {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            Thread.sleep(POLL_INTERVAL_MS);

            RegistrationClient.CliStatusResponse status = client.pollCliStatus(setupToken);

            if (status.isFailed()) {
                logger.severe("Server provisioning failed: " + status.message());
                return null;
            }

            if (status.isComplete()) {
                return status;
            }

            // Log progress
            Boolean emailVerified = status.emailVerified();
            String provisioningStatus = status.provisioningStatus();
            if (emailVerified != null && emailVerified) {
                logger.info("  Email verified. Provisioning status: " +
                        (provisioningStatus != null ? provisioningStatus : "pending") +
                        " (attempt " + (attempt + 1) + ")");
            } else {
                logger.info("  Waiting for email verification... (attempt " + (attempt + 1) + ")");
            }
        }

        return null;
    }

    private void printBanner() {
        logger.info("===========================================");
        logger.info("  modl.gg Setup Wizard");
        logger.info("===========================================");
    }

    private void saveAndPrint(BootConfig config) {
        logger.info("Configuration saved to boot.yml.");
        logger.info("===========================================");
    }

    private static String normalizePanelUrl(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        return trimmed.replaceAll("/+$", "");
    }

    private record RegistrationResult(String apiKey, String panelUrl) {}
}
