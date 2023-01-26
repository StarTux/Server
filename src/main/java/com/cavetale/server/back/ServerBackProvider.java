package com.cavetale.server.back;

import com.cavetale.core.back.BackLocation;
import com.cavetale.core.back.BackProvider;
import com.cavetale.core.back.event.PlayerBackEvent;
import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import com.cavetale.core.event.connect.ConnectMessageEvent;
import com.cavetale.core.util.Json;
import com.cavetale.server.ServerPlugin;
import com.cavetale.server.sql.SQLBack;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import static com.cavetale.server.ServerPlugin.database;

public final class ServerBackProvider implements BackProvider, Listener {
    private static final String BACK = "server:back";
    private HashMap<UUID, Long> cooldowns = new HashMap<>();

    public void enable() {
        register();
        Bukkit.getPluginManager().registerEvents(this, ServerPlugin.getInstance());
    }

    @Override
    public void store(BackLocation backLocation) {
        // If this store is dont shortly after a back request, we
        // assume that it was caused by the player leaving, thus we
        // impose a cooldown to avoid flip-flopping.
        Long cd = cooldowns.remove(backLocation.getPlayerUuid());
        if (cd != null && cd > System.currentTimeMillis()) return;
        database().saveAsync(new SQLBack().load(backLocation), null);
    }

    @Override
    public void reset(UUID playerUuid) {
        database().find(SQLBack.class)
            .eq("player", playerUuid)
            .deleteAsync(null);
    }

    @Override
    public void reset(UUID playerUuid, Plugin plugin) {
        database().find(SQLBack.class)
            .eq("player", playerUuid)
            .eq("plugin", plugin.getName())
            .deleteAsync(null);
    }

    @Override
    public void resetAll(Plugin plugin) {
        database().find(SQLBack.class)
            .eq("plugin", plugin.getName()) // Keyed
            .deleteAsync(null);
    }

    @Override
    public void load(UUID playerUuid, Consumer<BackLocation> callback) {
        database().find(SQLBack.class).eq("player", playerUuid).findUniqueAsync(row -> {
                callback.accept(row != null ? row.toBackLocation() : null);
            });
    }

    @Override
    public void back(UUID playerUuid, Consumer<BackLocation> callback) {
        cooldowns.put(playerUuid, System.currentTimeMillis() + 10_000L);
        load(playerUuid, backLocation -> {
                if (callback != null) callback.accept(backLocation);
                if (backLocation == null) {
                    cooldowns.remove(playerUuid);
                    return;
                }
                Connect.get().sendMessage(backLocation.getServer(), BACK, Json.serialize(backLocation));
            });
    }

    @EventHandler
    private void onConnectMessage(ConnectMessageEvent event) {
        switch (event.getChannel()) {
        case BACK: {
            BackLocation backLocation = Json.deserialize(event.getPayload(), BackLocation.class);
            RemotePlayer player = Connect.get().getRemotePlayer(backLocation.getPlayerUuid());
            if (player == null) return;
            if (!new PlayerBackEvent(player, backLocation).callEvent()) return;
            player.bring(ServerPlugin.getInstance(), backLocation.getLocation(), p -> {
                    if (p != null) reset(p.getUniqueId());
                });
            break;
        }
        default: break;
        }
    }
}
