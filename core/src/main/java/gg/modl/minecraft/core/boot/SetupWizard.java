package gg.modl.minecraft.core.boot;

import gg.modl.minecraft.core.util.PluginLogger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class SetupWizard {
    private static final long POLL_INTERVAL_MS = 5000;
    private static final int MAX_POLL_ATTEMPTS = 120; // 10 minutes

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern SUBDOMAIN_PATTERN = Pattern.compile("^[a-z0-9-]+$");
    private static final Pattern SERVER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9 -]+$");
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
            + "|^[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]*[a-zA-Z0-9])?)*$");
    private static final int API_KEY_MIN_LENGTH = 40;
    private static final int API_KEY_MAX_LENGTH = 60;

    private static final Set<String> RESERVED_SUBDOMAINS = new HashSet<>(Arrays.asList(
            "payments", "payment", "api", "app",
            "status", "mail", "www", "discord",
            "admin", "twitter", "demo", "panel",
            "ftp", "sftp", "www2", "www3",
            "billing", "stripe", "test", "staging",
            "root", "internal", "administrator", "mod",
            "beta", "dev", "portal", "dashboard",
            "modl", "support", "help", "email",
            "docs", "secure", "alpha", "cdn"
    ));

    private final PluginLogger logger;
    private final ConsoleInput input;
    private final PlatformType platformType;
    private boolean testingApi = false;

    public SetupWizard(PluginLogger logger, ConsoleInput input, PlatformType platformType) {
        this.logger = logger;
        this.input = input;
        this.platformType = platformType;
    }

    private static class RestartWizardException extends RuntimeException {}

    public BootConfig run() {
        while (true) {
            try {
                printBanner();
                BootConfig config;
                if (platformType == PlatformType.SPIGOT) {
                    config = runSpigotWizard();
                } else {
                    config = runProxyWizard();
                }
                if (config != null && confirmSetup(config)) {
                    return config;
                }
                logger.info("Restarting setup wizard...");
                logger.info("");
            } catch (RestartWizardException e) {
                logger.info("");
                logger.info("Setup wizard restarted.");
                logger.info("");
                testingApi = false;
            }
        }
    }

    // ── Input helpers ──

    private String readLine(String prompt) {
        String response = input.readLine(prompt);
        if (response != null && response.trim().equalsIgnoreCase("clear")) {
            throw new RestartWizardException();
        }
        return response;
    }

    private boolean askYesNo(String prompt) {
        while (true) {
            String response = readLine(prompt + " [yes/no]: ");
            if (response == null) return false;
            String trimmed = response.trim();
            if (trimmed.equalsIgnoreCase("yes") || trimmed.equalsIgnoreCase("y")) return true;
            if (trimmed.equalsIgnoreCase("no") || trimmed.equalsIgnoreCase("n")) return false;
            logger.info("Please enter 'yes' or 'no'.");
        }
    }

    private String askChoice(String prompt, String[] validOptions, String defaultOption) {
        while (true) {
            String response = readLine(prompt);
            if (response == null || response.trim().isEmpty()) {
                if (defaultOption != null) return defaultOption;
                logger.info("Please enter a value.");
                continue;
            }
            String trimmed = response.trim().toLowerCase();
            for (String option : validOptions) {
                if (option.equalsIgnoreCase(trimmed)) return option;
            }
            logger.info("Invalid option. Valid choices: " + String.join(", ", validOptions));
        }
    }

    private String askNonEmpty(String prompt) {
        while (true) {
            String response = readLine(prompt);
            if (response != null && !response.trim().isEmpty()) return response.trim();
            logger.info("This field cannot be empty. Please enter a value.");
        }
    }

    private int askPort(String prompt, int defaultPort) {
        while (true) {
            String response = readLine(prompt);
            if (response == null || response.trim().isEmpty()) return defaultPort;
            try {
                int port = Integer.parseInt(response.trim());
                if (port >= 1 && port <= 65535) return port;
                logger.info("Port must be between 1 and 65535.");
            } catch (NumberFormatException e) {
                logger.info("Invalid port number. Please enter a number.");
            }
        }
    }

    private String askEmail(String prompt) {
        while (true) {
            String response = askNonEmpty(prompt);
            if (response.length() > 254) {
                logger.info("Email is too long (max 254 characters).");
                continue;
            }
            if (!EMAIL_PATTERN.matcher(response).matches()) {
                logger.info("Invalid email format. Please enter a valid email address.");
                continue;
            }
            return response;
        }
    }

    private String askServerName(String prompt) {
        while (true) {
            String response = askNonEmpty(prompt);
            if (response.length() < 3) {
                logger.info("Server name must be at least 3 characters.");
                continue;
            }
            if (response.length() > 100) {
                logger.info("Server name must be 100 characters or less.");
                continue;
            }
            if (!SERVER_NAME_PATTERN.matcher(response).matches()) {
                logger.info("Server name can only contain letters, numbers, spaces, and hyphens.");
                continue;
            }
            return response;
        }
    }

    private String askSubdomain(String prompt) {
        while (true) {
            String response = askNonEmpty(prompt).toLowerCase();
            if (response.length() < 3) {
                logger.info("Subdomain must be at least 3 characters.");
                continue;
            }
            if (response.length() > 50) {
                logger.info("Subdomain must be 50 characters or less.");
                continue;
            }
            if (!SUBDOMAIN_PATTERN.matcher(response).matches()) {
                logger.info("Subdomain can only contain lowercase letters, numbers, and hyphens.");
                continue;
            }
            if (RESERVED_SUBDOMAINS.contains(response)) {
                logger.info("'" + response + "' is a reserved subdomain. Please choose a different one.");
                continue;
            }
            return response;
        }
    }

    private String askApiKey(String prompt) {
        while (true) {
            String response = askNonEmpty(prompt);
            if (response.length() < API_KEY_MIN_LENGTH || response.length() > API_KEY_MAX_LENGTH) {
                logger.info("Invalid API key length. Keys are typically ~48 characters starting with 'modl_'.");
                continue;
            }
            if (!response.startsWith("modl_")) {
                logger.info("API key should start with 'modl_'. Please check and try again.");
                continue;
            }
            return response;
        }
    }

    private String askHostAddress(String prompt) {
        while (true) {
            String response = askNonEmpty(prompt);
            if (!IP_PATTERN.matcher(response).matches()) {
                logger.info("Invalid address. Enter a valid IP (e.g. 127.0.0.1) or hostname.");
                continue;
            }
            return response;
        }
    }

    private String askAvailableEmail(RegistrationClient client) {
        while (true) {
            String email = askEmail("Admin email: ");
            try {
                RegistrationClient.AvailabilityResponse resp = client.checkAvailability(email, null, null);
                if (!resp.emailAvailable()) {
                    logger.info("This email is already registered. Please use a different email.");
                    continue;
                }
            } catch (Exception e) {
                // If the check fails, proceed anyway — the registration call will catch it
            }
            return email;
        }
    }

    private String askAvailableServerName(RegistrationClient client) {
        while (true) {
            String name = askServerName("Server name: ");
            try {
                RegistrationClient.AvailabilityResponse resp = client.checkAvailability(null, name, null);
                if (!resp.nameAvailable()) {
                    logger.info("This server name is already taken. Please choose a different one.");
                    continue;
                }
            } catch (Exception e) {
                // If the check fails, proceed anyway
            }
            return name;
        }
    }

    private String askAvailableSubdomain(RegistrationClient client) {
        while (true) {
            String subdomain = askSubdomain("Panel subdomain (e.g. \"myserver\" for myserver.modl.gg): ");
            try {
                RegistrationClient.AvailabilityResponse resp = client.checkAvailability(null, null, subdomain);
                if (!resp.subdomainAvailable()) {
                    logger.info("This subdomain is already in use or reserved. Please choose a different one.");
                    continue;
                }
            } catch (Exception e) {
                // If the check fails, proceed anyway
            }
            return subdomain;
        }
    }

    // ── Wizard flows ──

    private BootConfig runSpigotWizard() {
        boolean standalone = askYesNo("Is this a standalone server (no proxy)?");

        if (standalone) {
            return runStandaloneWizard();
        } else {
            return runBridgeOnlyWizard();
        }
    }

    private BootConfig runStandaloneWizard() {
        boolean hasAccount = askYesNo("Have you already registered a server on modl.gg?");

        BootConfig config = new BootConfig();
        config.setMode(BootConfig.Mode.STANDALONE);
        config.setTestingApi(testingApi);

        if (!collectCredentials(config, hasAccount)) {
            return config;
        }

        return config;
    }

    private BootConfig runBridgeOnlyWizard() {
        BootConfig config = new BootConfig();
        config.setMode(BootConfig.Mode.BRIDGE_ONLY);
        config.setTestingApi(testingApi);

        String proxyType = askChoice("Proxy type (velocity / bungeecord) [velocity]: ",
                new String[]{"velocity", "bungeecord"}, "velocity");
        config.setProxyType(proxyType);

        logger.info("API key is found in proxy plugin's boot.yml, make sure to setup modl on proxy first then come back here!");
        String apiKey = askApiKey("Enter your API key (from proxy setup or modl.gg panel): ");
        config.setApiKey(apiKey);

        String proxyHost = askHostAddress("Proxy server address (if local, use 127.0.0.1; if using Pterodactyl, use internal IP): ");
        config.setWizardProxyHost(proxyHost);

        int proxyPort = askPort("Proxy bridge port [25590]: ", 25590);
        config.setWizardProxyPort(proxyPort);

        logger.info("");
        logger.info("NOTE: If you are using Pterodactyl, you must allocate port " + proxyPort);
        logger.info("to the proxy server in the Pterodactyl admin panel.");
        logger.info("The bridge will not connect unless the port is allocated.");

        return config;
    }

    private BootConfig runProxyWizard() {
        boolean hasAccount = askYesNo("Have you already registered a server on modl.gg?");

        BootConfig config = new BootConfig();
        config.setMode(BootConfig.Mode.PROXY);
        config.setTestingApi(testingApi);

        if (!collectCredentials(config, hasAccount)) {
            return config;
        }

        int bridgePort = askPort("Set a port to bind TCP Query server to (communication channel between proxy and Spigot servers) [Default: 25590]: ", 25590);
        config.setBridgePort(bridgePort);

        logger.info("");
        logger.info("NOTE: If you are using Pterodactyl, you must allocate port " + bridgePort);
        logger.info("to the proxy server in the Pterodactyl admin panel.");
        logger.info("The bridge will not connect unless the port is allocated.");

        return config;
    }

    private boolean collectCredentials(BootConfig config, boolean hasAccount) {
        if (hasAccount) {
            String apiKey = askApiKey("Enter your API key: ");
            config.setApiKey(apiKey);
        } else {
            RegistrationResult result = runRegistrationFlowWithRetry();
            if (result != null) {
                config.setApiKey(result.apiKey);
            } else {
                logger.warning("Registration failed. You can configure boot.yml manually.");
                return false;
            }
        }
        return true;
    }

    private boolean confirmSetup(BootConfig config) {
        logger.info("");
        logger.info("===========================================");
        logger.info(" Setup Summary");
        logger.info("===========================================");
        logger.info("  Mode: " + config.getMode().toYaml());
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            String key = config.getApiKey();
            String masked = key.length() > 8 ? key.substring(0, 8) + "..." : "***";
            logger.info("  API Key: " + masked);
        }
        if (config.getMode() == BootConfig.Mode.BRIDGE_ONLY) {
            logger.info("  Proxy Type: " + (config.getProxyType() != null ? config.getProxyType() : "velocity"));
            logger.info("  Proxy Host: " + (config.getWizardProxyHost() != null ? config.getWizardProxyHost() : ""));
            logger.info("  Proxy Port: " + config.getWizardProxyPort());
        }
        if (config.getMode() == BootConfig.Mode.PROXY) {
            logger.info("  Bridge Port: " + config.getBridgePort());
        }
        logger.info("  Testing API: " + config.isTestingApi());
        logger.info("===========================================");
        logger.info("");

        return askYesNo("Save this configuration?");
    }

    // ── Registration flow ──

    private RegistrationResult runRegistrationFlowWithRetry() {
        while (true) {
            RegistrationResult result = runRegistrationFlow();
            if (result != null) return result;

            if (!askYesNo("Would you like to retry?")) {
                return null;
            }
            logger.info("");
        }
    }

    private RegistrationResult runRegistrationFlow() {
        RegistrationClient client = new RegistrationClient(testingApi);

        String email = askAvailableEmail(client);
        String serverName = askAvailableServerName(client);
        String subdomain = askAvailableSubdomain(client);

        if (!askYesNo("Do you agree to the Terms of Service? (https://modl.gg/terms)")) {
            logger.info("You must agree to the Terms of Service to register.");
            return null;
        }

        logger.info("Registering server...");

        try {
            RegistrationClient.RegisterResponse registerResponse = client.register(
                    email, serverName, subdomain, "free"
            );

            if (!registerResponse.success()) {
                logger.severe("Registration failed: " + registerResponse.message());
                return null;
            }

            String setupToken = registerResponse.setupToken();
            if (setupToken == null || setupToken.trim().isEmpty()) {
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

            logger.info("Registration complete!");
            return new RegistrationResult(statusResponse.apiKey());
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

    // ── Utilities ──

    private void printBanner() {
        logger.info("===========================================");
        logger.info(" modl.gg Setup Wizard");
        logger.info("===========================================");
        logger.info(" Type 'clear' at any prompt to restart.");
        logger.info("===========================================");
    }

    private static class RegistrationResult {
        final String apiKey;

        RegistrationResult(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
