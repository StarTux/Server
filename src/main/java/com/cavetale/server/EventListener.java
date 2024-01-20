package com.cavetale.server;

import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.event.hud.PlayerHudEvent;
import com.cavetale.core.event.hud.PlayerHudPriority;
import com.cavetale.core.util.Json;
import com.cavetale.server.sql.SQLBack;
import com.winthier.connect.event.ConnectMessageEvent;
import com.winthier.title.TitlePlugin;
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
import org.bukkit.event.player.PlayerQuitEvent;
import static com.cavetale.core.font.Unicode.tiny;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.format.NamedTextColor.*;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final ServerPlugin plugin;
    private final List<Component> sidebarLines = new ArrayList<>();
    private final List<Component> whoLines = new ArrayList<>();
    private static final boolean CUSTOM_TAB_LIST = false;

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (CUSTOM_TAB_LIST) {
            Bukkit.getScheduler().runTaskTimer(plugin, this::makeWhoLines, 0L, 1L);
        }
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

    private void makeWhoLines() {
        whoLines.clear();
        List<RemotePlayer> playerList = new ArrayList<>(Connect.get().getRemotePlayers());
        Collections.sort(playerList, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
        List<Component> playerNames = new ArrayList<>();
        for (RemotePlayer online : playerList) {
            playerNames.add(TitlePlugin.getPlayerListName(online.getUniqueId()));
        }
        whoLines.add(join(separator(space()), playerNames));
    }

    @EventHandler
    private void onPlayerHud(PlayerHudEvent event) {
        if (!sidebarLines.isEmpty()) {
            event.sidebar(PlayerHudPriority.INFO, sidebarLines);
        }
        final Player player = event.getPlayer();
        List<Component> header = new ArrayList<>();
        if (plugin.serverSlot != null) {
            header.add(textOfChildren(text(tiny("server "), GRAY),
                                      plugin.serverSlot.displayName,
                                      space(),
                                      text(Connect.get().getOnlinePlayerCount(), WHITE),
                                      text(tiny(" online"), GRAY)));
        }
        if (CUSTOM_TAB_LIST) header.addAll(whoLines);
        event.header(PlayerHudPriority.HIGHEST, header);
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

    /**
     * Remember the last server that somebody quit.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    private void onPlayerQuit(PlayerQuitEvent event) {
        final var uuid = event.getPlayer().getUniqueId();
        plugin.database().update(SQLBack.class)
            .set("lastServer", NetworkServer.current().registeredName)
            .where(w -> w.eq("player", uuid))
            .async(null);
    }
}
