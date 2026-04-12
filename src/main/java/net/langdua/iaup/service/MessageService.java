package net.langdua.iaup.service;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;

public final class MessageService {
    private final JavaPlugin plugin;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void send(CommandSender sender, String path) {
        String msg = plugin.getConfig().getString(path);
        if (msg == null || msg.trim().isEmpty()) {
            return;
        }
        if (sender == null) {
            plugin.getLogger().info(msg);
            return;
        }
        if (!plugin.isEnabled()) {
            safeSendDirect(sender, msg);
            return;
        }

        try {
            Bukkit.getScheduler().runTask(plugin, new Runnable() {
                @Override
                public void run() {
                    safeSendDirect(sender, msg);
                }
            });
        } catch (IllegalPluginAccessException e) {
            safeSendDirect(sender, msg);
        } catch (Exception e) {
            plugin.getLogger().fine("Message scheduling failed: " + e.getMessage());
            safeSendDirect(sender, msg);
        }
    }

    private void safeSendDirect(CommandSender sender, String msg) {
        try {
            sender.sendMessage(msg);
        } catch (Throwable ignored) {
        }
    }
}
