package net.langdua.iaup.hook;

import net.langdua.iaup.ItemsAdderUploadPlus;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

public final class ItemsAdderHookRegistrar {
    private static final String EVENT_CLASS = "dev.lone.itemsadder.api.Events.ItemsAdderPackCompressedEvent";

    private final ItemsAdderUploadPlus plugin;
    private Listener registeredListener;

    public ItemsAdderHookRegistrar(ItemsAdderUploadPlus plugin) {
        this.plugin = plugin;
    }

    public boolean register(boolean enabled, final Runnable onPackCompressed) {
        unregister();

        if (!enabled) {
            plugin.getLogger().info("Auto upload on pack compress is disabled in config.");
            return false;
        }

        Plugin itemsAdder = plugin.getServer().getPluginManager().getPlugin("ItemsAdder");
        if (itemsAdder == null || !itemsAdder.isEnabled()) {
            plugin.getLogger().warning("ItemsAdder not found. Auto upload hook not registered.");
            return false;
        }

        try {
            Class<?> eventClass = Class.forName(EVENT_CLASS);
            Listener listener = new Listener() {};
            EventExecutor executor = new EventExecutor() {
                @Override
                public void execute(Listener ignored, Event event) throws EventException {
                    onPackCompressed.run();
                }
            };

            plugin.getServer().getPluginManager().registerEvent(
                    eventClass.asSubclass(Event.class),
                    listener,
                    EventPriority.MONITOR,
                    executor,
                    plugin,
                    true
            );

            registeredListener = listener;
            plugin.getLogger().info("ItemsAdder pack hook registered.");
            return true;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("ItemsAdder API event not found. Auto upload hook disabled.");
            return false;
        }
    }

    public void unregister() {
        if (registeredListener != null) {
            HandlerList.unregisterAll(registeredListener);
            registeredListener = null;
            plugin.getLogger().info("ItemsAdder pack hook unregistered.");
        }
    }
}
