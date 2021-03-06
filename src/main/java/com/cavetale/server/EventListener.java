package com.cavetale.server;

import com.cavetale.core.connect.Connect;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.util.Json;
import com.winthier.connect.event.ConnectMessageEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final ServerPlugin plugin;
    private final List<Component> sidebarLines = new ArrayList<>();

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Deny login if they lack the permission.  This uses the LOW
     * priority because PERM uses LOWEST to set up the permissions!
     */
    @EventHandler(priority = EventPriority.LOW)
    private void onPlayerLogin(PlayerLoginEvent event) {
        ServerSlot slot = plugin.serverMap.get(plugin.serverName);
        if (slot == null) return;
        Player player = event.getPlayer();
        if (slot.tag.locked && !player.hasPermission("server.locked")) {
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, Component.text("Server is locked!"));
            plugin.getLogger().info("Denying " + player.getName() + " because server is locked");
        }
        if (!slot.hasPermission(player)) {
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, Component.text("You're not whitelisted!"));
            plugin.getLogger().info("Denying " + player.getName() + " for lack of permission");
        }
    }

    @EventHandler
    private void onConnectMessage(ConnectMessageEvent event) {
        switch (event.getMessage().getChannel()) {
        case "server:update":
            ServerTag serverTag = ServerTag.fromJson(event.getMessage().getPayload());
            if (serverTag.name != null && !serverTag.name.equals(plugin.serverName)) {
                plugin.getLogger().info("Server update received: " + serverTag.name);
                plugin.registerServer(serverTag);
                plugin.serverUpdateReceived(serverTag);
            }
            break;
        case "server:remove":
            plugin.getLogger().info("Server remove received: " + event.getMessage().getPayload());
            plugin.unregisterServer(event.getMessage().getPayload());
            break;
        case "server:sidebar":
            ServerSidebarLines serverSidebarLines = Json.deserialize(event.getMessage().getPayload(),
                                                                     ServerSidebarLines.class);
            if (serverSidebarLines == null) return;
            plugin.getLogger().info("Server sidebar received: " + serverSidebarLines.server);
            ServerSlot serverSlot = plugin.serverMap.get(serverSidebarLines.server);
            if (serverSlot == null) return;
            serverSlot.sidebarLines = serverSidebarLines.getComponents();
            updateSidebarLines();
            break;
        default: break;
        }
    }

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        if (!sidebarLines.isEmpty()) {
            event.sidebar(PlayerHudPriority.INFO, sidebarLines);
        }
        event.header(PlayerHudPriority.HIGHEST,
                     List.of(join(noSeparators(), text(tiny("server "), GRAY), (plugin.serverSlot != null ? plugin.serverSlot.displayName : empty())),
                             join(noSeparators(), text(tiny("players "), GRAY), text(Connect.get().getOnlinePlayerCount(), WHITE))));
    }

    protected void updateSidebarLines() {
        List<String> keys = new ArrayList<>(plugin.serverMap.keySet());
        keys.remove(plugin.serverName);
        Collections.sort(keys);
        sidebarLines.clear();
        for (String key : keys) {
            ServerSlot slot = plugin.serverMap.get(key);
            if (slot.sidebarLines == null) continue;
            sidebarLines.addAll(slot.sidebarLines);
        }
    }
}
