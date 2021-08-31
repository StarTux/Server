package com.cavetale.server;

import com.cavetale.core.event.player.PluginPlayerEvent;
import com.winthier.connect.Redis;
import com.winthier.connect.event.ConnectMessageEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final ServerPlugin plugin;

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    private void onConnectMessage(ConnectMessageEvent event) {
        switch (event.getMessage().getChannel()) {
        case "server:update":
            ServerTag serverTag = ServerTag.fromJson(event.getMessage().getPayload().toString());
            if (serverTag.name != null && !serverTag.name.equals(plugin.serverName)) {
                plugin.getLogger().info("Server update received: " + serverTag.name);
                plugin.registerServer(serverTag);
                plugin.serverUpdateReceived(serverTag);
            }
            break;
        case "server:remove":
            plugin.getLogger().info("Server remove received: " + event.getMessage().getPayload().toString());
            plugin.unregisterServer(event.getMessage().getPayload().toString());
            break;
        default: break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        final String redisKey = "cavetale.server_switch." + player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String name = Redis.get(redisKey);
                if (name == null || !name.equals(plugin.serverName)) return;
                Redis.del(redisKey);
                Bukkit.getScheduler().runTask(plugin, () -> {
                        PluginPlayerEvent.Name.SWITCH_SERVER.ultimate(plugin, player)
                            .detail(PluginPlayerEvent.Detail.NAME, plugin.serverName)
                            .call();
                        plugin.getLogger().info("Server switch: " + player.getName());
                    });
            });
    }
}
