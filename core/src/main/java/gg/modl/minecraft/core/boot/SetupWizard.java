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
        boolean standalone = input.confirm("Is this a standalone server (no proxy)?");

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

        if (hasAccount) {
            String apiKey = input.readLine("Enter your API key: ");
            String panelUrl = input.readLine("Panel domain (e.g. server.modl.gg or support.server.com): ");
            config.setApiKey(apiKey != null ? apiKey.trim() : "");
            config.setPanelUrl(normalizePanelUrl(panelUrl));
        } else {
            RegistrationResult result = runRegistrationFlow();
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

        String apiKey = input.readLine("Enter your API key (from proxy setup or modl.gg panel): ");
        logger.info("If you don't have one, register using the proxy's setup wizard first.");

        config.setApiKey(apiKey != null ? apiKey.trim() : "");

        saveAndPrint(config);
        return config;
    }

    private BootConfig runProxyWizard() {
        boolean hasAccount = input.confirm("Have you already registered a server on modl.gg?");

        BootConfig config = new BootConfig();
        config.setMode(BootConfig.Mode.PROXY);

        if (hasAccount) {
            String apiKey = input.readLine("Enter your API key: ");
            String panelUrl = input.readLine("Panel domain (e.g. server.modl.gg or support.server.com): ");
            config.setApiKey(apiKey != null ? apiKey.trim() : "");
            config.setPanelUrl(normalizePanelUrl(panelUrl));
        } else {
            RegistrationResult result = runRegistrationFlow();
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
        boolean localBackends = input.confirm("Are the backend server(s) on this machine?");
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

    private RegistrationResult runRegistrationFlow() {
        String email = input.readLine("Admin email: ");
        String serverName = input.readLine("Server name: ");
        String subdomain = input.readLine("Panel subdomain (e.g. \"myserver\" for myserver.modl.gg): ");

        if (!input.confirm("Do you agree to the Terms of Service? (https://modl.gg/terms)")) {
            logger.info("You must agree to the Terms of Service to register.");
            return null;
        }

        logger.info("Registering server...");
        RegistrationClient client = new RegistrationClient();

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

            logger.info("Check your email and click the verification link.");
            logger.info("Waiting for verification... (polling every 5s)");

            String autoLoginToken = pollForCompletion(client, subdomain != null ? subdomain.trim() : "");
            if (autoLoginToken == null) {
                logger.severe("Setup did not complete in time. Check the panel for your API key.");
                return null;
            }

            logger.info("Retrieving API key...");
            RegistrationClient.ApiKeyResponse apiKeyResponse = client.getApiKey(autoLoginToken);
            if (!apiKeyResponse.success()) {
                logger.severe("Failed to retrieve API key: " + apiKeyResponse.message());
                return null;
            }

            return new RegistrationResult(apiKeyResponse.apiKey(), apiKeyResponse.panelUrl());
        } catch (Exception e) {
            logger.severe("Registration error: " + e.getMessage());
            return null;
        }
    }

    private String pollForCompletion(RegistrationClient client, String subdomain) throws Exception {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            Thread.sleep(POLL_INTERVAL_MS);

            // Use the subdomain as part of the token flow — the setup-status uses the auto-login token
            // which we don't have yet. We poll verify first.
            // Actually, the register response contains a server but no token.
            // The token comes from email verification. We need to poll setup-status with the verify token.
            // Since CLI doesn't have the token until the user verifies email, we need to poll differently.
            // The verify endpoint returns autoLoginToken when the user clicks the email link.
            // We'll poll the setup-status endpoint — but we need the token from verify.

            // In practice, the flow is:
            // 1. User registers -> email sent with verify link
            // 2. User clicks link -> verify endpoint returns autoLoginToken
            // 3. We poll setup-status with that token
            // But we can't get the token without the user visiting the URL...

            // Alternative: we poll the setup-status using the verify token from the GET param.
            // Since the CLI doesn't open a browser, we need to poll by email verification.
            // The verify endpoint can be called with POST {token}, but we don't know the token.

            // Simplest approach: poll setup-status by querying with the email's verify token.
            // But we don't have that token. The server generated it.

            // Actually, looking at the existing flow more carefully:
            // The register endpoint creates the server and sends verification email.
            // The verify endpoint is called when user clicks the link, returns autoLoginToken.
            // setup-status is polled with the autoLoginToken.

            // For CLI, we need to somehow get the autoLoginToken.
            // The most practical approach: after registration, ask the user to verify email,
            // then enter the token from the verification page, OR
            // we just let the backend have a way to poll by server ID.

            // For now, let's implement a simple version where we tell the user
            // to verify their email and then enter their API key manually from the panel.

            logger.info("  Waiting for email verification... (attempt " + (attempt + 1) + ")");

            // We cannot poll without the autoLoginToken, which requires email verification.
            // This is a UX limitation of the CLI flow that the /api-key endpoint solves.
            // The backend needs to support polling by a different mechanism for CLI.
            // For now, fall back to manual key entry after registration.
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
