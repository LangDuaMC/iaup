package net.langdua.iaup;

import net.langdua.iaup.config.PluginSettings;
import net.langdua.iaup.hook.ItemsAdderHookRegistrar;
import net.langdua.iaup.lifecycle.PluginLifecycle;
import net.langdua.iaup.service.ItemsAdderConfigUpdater;
import net.langdua.iaup.service.MessageService;
import net.langdua.iaup.service.S3UploadService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ItemsAdderUploadPlus extends JavaPlugin implements CommandExecutor {
    private final AtomicBoolean uploadInProgress = new AtomicBoolean(false);

    private final PluginLifecycle lifecycle = new PluginLifecycle();
    private final ItemsAdderConfigUpdater iaConfigUpdater = new ItemsAdderConfigUpdater();

    private MessageService messages;
    private ItemsAdderHookRegistrar hookRegistrar;
    private volatile PluginSettings settings;

    @Override
    public void onEnable() {
        messages = new MessageService(this);
        hookRegistrar = new ItemsAdderHookRegistrar(this);

        saveDefaultConfig();
        if (!loadSettings()) {
            getLogger().severe("Failed to load configuration; disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getCommand("iaup") != null) {
            getCommand("iaup").setExecutor(this);
        } else {
            getLogger().warning("Command 'iaup' not registered in plugin.yml.");
        }

        lifecycle.beginNewEpoch();
        rebindHooks();
    }

    @Override
    public void onDisable() {
        lifecycle.shutdown();
        if (hookRegistrar != null) {
            hookRegistrar.unregister();
        }
        Bukkit.getScheduler().cancelTasks(this);
        uploadInProgress.set(false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            reloadPlugin(sender);
            return true;
        }

        if (args.length == 0 || "upload".equalsIgnoreCase(args[0])) {
            startUpload(sender, "manual");
            return true;
        }

        sender.sendMessage("Usage: /" + label + " [upload|reload]");
        return true;
    }

    private void reloadPlugin(CommandSender sender) {
        lifecycle.beginNewEpoch();
        uploadInProgress.set(false);

        if (!loadSettings()) {
            messages.send(sender, "locale.upload-failed");
            return;
        }

        rebindHooks();
        messages.send(sender, "locale.reload");
    }

    private boolean loadSettings() {
        reloadConfig();
        try {
            settings = PluginSettings.fromConfig(getConfig());
            saveConfig();
            return true;
        } catch (Exception e) {
            getLogger().warning("Invalid configuration: " + e.getMessage());
            return false;
        }
    }

    private void rebindHooks() {
        PluginSettings snapshot = settings;
        if (snapshot == null) {
            return;
        }
        hookRegistrar.register(snapshot.autoUploadOnPack(), new Runnable() {
            @Override
            public void run() {
                startUpload(Bukkit.getConsoleSender(), "itemsadder-pack-compressed");
            }
        });
    }

    private void startUpload(CommandSender sender, String reason) {
        if (lifecycle.isShuttingDown()) {
            return;
        }

        if (!uploadInProgress.compareAndSet(false, true)) {
            messages.send(sender, "locale.upload-in-progress");
            return;
        }

        final PluginSettings snapshot = settings;
        if (snapshot == null) {
            uploadInProgress.set(false);
            messages.send(sender, "locale.upload-failed");
            return;
        }

        final File packFile = snapshot.outputFile();
        if (packFile == null || !packFile.exists()) {
            uploadInProgress.set(false);
            messages.send(sender, "locale.missing-pack");
            return;
        }

        final long runEpoch = lifecycle.currentEpoch();
        final boolean manualUpload = "manual".equalsIgnoreCase(reason);
        messages.send(sender, "locale.upload-start");

        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                try {
                    if (!lifecycle.isCurrent(runEpoch)) {
                        return;
                    }

                    S3UploadService.UploadResult result = new S3UploadService(snapshot.s3())
                            .upload(packFile, snapshot.uid(), snapshot.cacheBust());

                    if (!lifecycle.isCurrent(runEpoch)) {
                        return;
                    }

                    boolean iaUpdated = true;
                    if (snapshot.updateItemsAdderConfig()) {
                        File iaConfig = resolveItemsAdderConfigFile(snapshot);
                        iaUpdated = iaConfigUpdater.update(iaConfig, result.downloadUrl());
                    }

                    if (!lifecycle.isCurrent(runEpoch)) {
                        return;
                    }

                    messages.send(sender, "locale.upload-success");
                    if (snapshot.updateItemsAdderConfig()) {
                        messages.send(sender, iaUpdated ? "locale.ia-update-success" : "locale.ia-update-failed");
                    }
                    if (manualUpload && snapshot.autoIareloadAfterManualUpload()) {
                        Bukkit.getScheduler().runTask(ItemsAdderUploadPlus.this, new Runnable() {
                            @Override
                            public void run() {
                                if (!lifecycle.isCurrent(runEpoch)) {
                                    return;
                                }
                                boolean ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "iareload");
                                if (!ok) {
                                    getLogger().warning("Failed to dispatch /iareload after successful manual upload.");
                                }
                            }
                        });
                    }
                } catch (Exception e) {
                    getLogger().warning("Upload failed (" + reason + "): " + e.getMessage());
                    e.printStackTrace();
                    if (lifecycle.isCurrent(runEpoch)) {
                        messages.send(sender, "locale.upload-failed");
                    }
                } finally {
                    uploadInProgress.set(false);
                }
            }
        });
    }

    private File resolveItemsAdderConfigFile(PluginSettings snapshot) {
        String override = snapshot.itemsAdderConfigPathOverride();
        if (!override.isEmpty()) {
            return new File(override);
        }

        Plugin itemsAdder = getServer().getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder == null) {
            return null;
        }
        return new File(itemsAdder.getDataFolder(), "config.yml");
    }
}
