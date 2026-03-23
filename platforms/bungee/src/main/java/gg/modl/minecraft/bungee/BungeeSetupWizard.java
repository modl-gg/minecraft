package gg.modl.minecraft.bungee;

import gg.modl.minecraft.core.boot.BootConfig;
import gg.modl.minecraft.core.boot.ConsoleInput;
import gg.modl.minecraft.core.boot.PlatformType;
import gg.modl.minecraft.core.boot.SetupWizard;
import gg.modl.minecraft.core.util.PluginLogger;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.event.EventHandler;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * BungeeCord setup wizard that intercepts all console input directly.
 * <p>
 * Three interception strategies (tried in order):
 * <ol>
 *   <li><b>JLine 3 widget replacement</b> — replaces the {@code accept-line} widget on
 *       BungeeCord's LineReader so input is captured before command dispatch. Cleanest
 *       approach, works on modern BungeeCord with JLine 3.</li>
 *   <li><b>PluginManager commandMap wrapping</b> — replaces the private command map via
 *       reflection so unrecognized commands are routed to a catch-all. Works on any
 *       BungeeCord version.</li>
 *   <li><b>{@code setup <answer>} command</b> — registers a "setup" command as a last
 *       resort if reflection fails entirely.</li>
 * </ol>
 */
public class BungeeSetupWizard implements Listener {
    private final Plugin plugin;
    private final PluginLogger logger;
    private final Consumer<BootConfig> onComplete;
    private final BlockingQueue<String> inputQueue = new LinkedBlockingQueue<>();
    private volatile boolean active = false;

    private Runnable interceptorCleanup;
    private boolean useFallbackCommand = false;

    public BungeeSetupWizard(Plugin plugin, PluginLogger logger, Consumer<BootConfig> onComplete) {
        this.plugin = plugin;
        this.logger = logger;
        this.onComplete = onComplete;
    }

    public void start() {
        active = true;
        plugin.getProxy().getPluginManager().registerListener(plugin, this);

        if (!tryJLineInterceptor()) {
            if (!tryCommandMapInterceptor()) {
                installFallbackCommand();
            }
        }

        plugin.getProxy().getScheduler().schedule(plugin, () -> {
            try {
                if (useFallbackCommand) {
                    logger.info("Respond to prompts by typing: setup <answer>");
                    logger.info("Example: setup yes");
                } else {
                    logger.info("Type your answers in the proxy console.");
                    logger.info("Append --test-mode to the first answer to use the testing API.");
                }
                logger.info("");

                ConsoleInput input = new QueuedConsoleInput();
                SetupWizard wizard = new SetupWizard(logger, input, PlatformType.BUNGEECORD);
                BootConfig config = wizard.run();

                if (config != null) {
                    config.save(plugin.getDataFolder().toPath());
                    logger.info("Configuration saved to boot.yml.");
                    logger.info("Initializing plugin...");
                    onComplete.accept(config);
                } else {
                    fallbackToTemplate();
                }
            } catch (Exception e) {
                logger.severe("Setup wizard error: " + e.getMessage());
                fallbackToTemplate();
            } finally {
                active = false;
                if (interceptorCleanup != null) interceptorCleanup.run();
                plugin.getProxy().getPluginManager().unregisterListener(this);
            }
        }, 3, TimeUnit.SECONDS);
    }

    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        if (!active) return;
        event.setCancelReason(new TextComponent("Server is running first-time setup. Please try again shortly."));
        event.setCancelled(true);
    }

    private void fallbackToTemplate() {
        try {
            BootConfig.saveTemplate(plugin.getDataFolder().toPath());
        } catch (IOException ignored) {}
        logger.warning("Setup cancelled. A boot.yml template has been created.");
        logger.info("Edit plugins/" + plugin.getDescription().getName() + "/boot.yml and restart.");
    }

    // ── Strategy 1: JLine 3 widget replacement ──

    @SuppressWarnings("unchecked")
    private boolean tryJLineInterceptor() {
        try {
            Object proxy = plugin.getProxy();
            Method getConsoleReader = proxy.getClass().getMethod("getConsoleReader");
            Object reader = getConsoleReader.invoke(proxy);

            Class<?> lineReaderClass = Class.forName("org.jline.reader.LineReader");
            if (!lineReaderClass.isInstance(reader)) return false;

            Class<?> widgetClass = Class.forName("org.jline.reader.Widget");

            Method getWidgets = lineReaderClass.getMethod("getWidgets");
            Map<String, Object> widgets = (Map<String, Object>) getWidgets.invoke(reader);
            String acceptLineKey = (String) lineReaderClass.getField("ACCEPT_LINE").get(null);

            Method getBuffer = lineReaderClass.getMethod("getBuffer");
            Method getTerminal = lineReaderClass.getMethod("getTerminal");

            Object originalWidget = widgets.get(acceptLineKey);
            if (originalWidget == null) {
                Method getBuiltins = lineReaderClass.getMethod("getBuiltinWidgets");
                Map<String, Object> builtins = (Map<String, Object>) getBuiltins.invoke(reader);
                originalWidget = builtins.get(acceptLineKey);
            }
            final Object savedOriginal = originalWidget;
            final Method widgetApply = widgetClass.getMethod("apply");

            Object customWidget = Proxy.newProxyInstance(
                    widgetClass.getClassLoader(),
                    new Class<?>[]{ widgetClass },
                    (p, method, args) -> {
                        if (!method.getName().equals("apply")) {
                            if (method.getName().equals("toString")) return "BungeeSetupWizardWidget";
                            if (method.getName().equals("hashCode")) return System.identityHashCode(p);
                            if (method.getName().equals("equals")) return p == (args != null ? args[0] : null);
                            return null;
                        }

                        if (!active) {
                            return savedOriginal != null ? widgetApply.invoke(savedOriginal) : true;
                        }

                        Object buffer = getBuffer.invoke(reader);
                        String line = buffer.toString();
                        inputQueue.offer(line);

                        buffer.getClass().getMethod("clear").invoke(buffer);
                        Object terminal = getTerminal.invoke(reader);
                        Object writer = terminal.getClass().getMethod("writer").invoke(terminal);
                        writer.getClass().getMethod("println").invoke(writer);
                        writer.getClass().getMethod("flush").invoke(writer);

                        return true;
                    }
            );

            widgets.put(acceptLineKey, customWidget);
            interceptorCleanup = () -> widgets.remove(acceptLineKey);

            logger.info("Console input interceptor installed (JLine widget)");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Strategy 2: PluginManager commandMap wrapping ──

    @SuppressWarnings("unchecked")
    private boolean tryCommandMapInterceptor() {
        try {
            PluginManager pm = plugin.getProxy().getPluginManager();
            Field mapField = PluginManager.class.getDeclaredField("commandMap");
            mapField.setAccessible(true);
            Map<String, Command> originalMap = (Map<String, Command>) mapField.get(pm);
            InterceptingCommandMap interceptMap = new InterceptingCommandMap(originalMap, inputQueue);
            interceptMap.active = true;
            mapField.set(pm, interceptMap);

            interceptorCleanup = () -> {
                interceptMap.active = false;
                try { mapField.set(pm, originalMap); } catch (Exception ignored) {}
            };

            logger.info("Console input interceptor installed (command map)");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static class InterceptingCommandMap implements Map<String, Command> {
        private final Map<String, Command> delegate;
        private final CatchAllCommand catchAll;
        volatile boolean active = false;

        InterceptingCommandMap(Map<String, Command> delegate, BlockingQueue<String> queue) {
            this.delegate = delegate;
            this.catchAll = new CatchAllCommand(queue);
        }

        @Override
        public Command get(Object key) {
            Command cmd = delegate.get(key);
            if (cmd == null && active && key instanceof String) {
                catchAll.lastCommand.set((String) key);
                return catchAll;
            }
            return cmd;
        }

        @Override public int size() { return delegate.size(); }
        @Override public boolean isEmpty() { return delegate.isEmpty(); }
        @Override public boolean containsKey(Object key) { return delegate.containsKey(key); }
        @Override public boolean containsValue(Object value) { return delegate.containsValue(value); }
        @Override public Command put(String key, Command value) { return delegate.put(key, value); }
        @Override public Command remove(Object key) { return delegate.remove(key); }
        @Override public void putAll(Map<? extends String, ? extends Command> m) { delegate.putAll(m); }
        @Override public void clear() { delegate.clear(); }
        @Override public Set<String> keySet() { return delegate.keySet(); }
        @Override public Collection<Command> values() { return delegate.values(); }
        @Override public Set<Entry<String, Command>> entrySet() { return delegate.entrySet(); }
    }

    private static class CatchAllCommand extends Command {
        final ThreadLocal<String> lastCommand = new ThreadLocal<>();
        private final BlockingQueue<String> queue;

        CatchAllCommand(BlockingQueue<String> queue) {
            super("__setup_catchall__");
            this.queue = queue;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            String name = lastCommand.get();
            lastCommand.remove();
            StringBuilder sb = new StringBuilder();
            if (name != null) sb.append(name);
            for (String arg : args) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(arg);
            }
            queue.offer(sb.toString());
        }
    }

    // ── Strategy 3: Fallback "setup" command ──

    private void installFallbackCommand() {
        useFallbackCommand = true;
        SetupCommand cmd = new SetupCommand();
        plugin.getProxy().getPluginManager().registerCommand(plugin, cmd);
        interceptorCleanup = () -> {};
        logger.info("Console input interceptor installed (setup command)");
    }

    private class SetupCommand extends Command {
        SetupCommand() { super("setup"); }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (sender instanceof ProxiedPlayer) {
                sender.sendMessage(new TextComponent(ChatColor.RED + "This command can only be used from the console."));
                return;
            }
            if (!active) {
                sender.sendMessage(new TextComponent("Setup wizard is not running."));
                return;
            }
            inputQueue.offer(String.join(" ", args));
        }
    }

    // ── Console input ──

    private class QueuedConsoleInput implements ConsoleInput {
        @Override
        public String readLine(String prompt) {
            logger.info(prompt);
            try {
                return inputQueue.take();
            } catch (InterruptedException e) {
                return null;
            }
        }
    }
}
