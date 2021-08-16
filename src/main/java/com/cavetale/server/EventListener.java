package com.cavetale.server;

import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.core.util.Json;
import com.winthier.connect.event.ConnectMessageEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
    private final Map<UUID, Long> serverSwitchMap = new HashMap<>();

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
            }
            break;
        case "server:remove":
            plugin.getLogger().info("Server remove received: " + event.getMessage().getPayload().toString());
            plugin.unregisterServer(event.getMessage().getPayload().toString());
            break;
        case "server:switch": {
            ServerSwitchPacket packet = Json.deserialize(event.getMessage().getPayload().toString(), ServerSwitchPacket.class, () -> null);
            Objects.requireNonNull(packet);
            if (packet.serverName.equals(plugin.serverName)) {
                Player player = Bukkit.getPlayer(packet.playerUuid);
                if (player != null) {
                    PluginPlayerEvent.Name.SWITCH_SERVER.ultimate(plugin, player)
                        .detail("server_name", plugin.serverName)
                        .call();
                    plugin.getLogger().info("Server switch instant: " + player.getName());
                } else {
                    serverSwitchMap.put(packet.playerUuid, System.currentTimeMillis());
                }
            }
        }
        default: break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onPlayerJoin(PlayerJoinEvent event) {
        Long time = serverSwitchMap.remove(event.getPlayer().getUniqueId());
        if (time == null) return;
        long now = System.currentTimeMillis();
        if (now - time > 10000) return; // 10s
        PluginPlayerEvent.Name.SWITCH_SERVER.ultimate(plugin, event.getPlayer())
            .detail("server_name", plugin.serverName)
            .call();
        plugin.getLogger().info("Server switch delayed: " + event.getPlayer().getName());
    }
}
