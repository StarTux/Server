package com.cavetale.server;

import com.cavetale.core.util.Json;
import com.cavetale.server.back.BackCommand;
import com.cavetale.server.back.ServerBackProvider;
import com.winthier.connect.Connect;
import com.winthier.connect.Redis;
import com.winthier.sql.SQLDatabase;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import static com.cavetale.server.sql.SQLStatic.getDatabaseClasses;

public final class ServerPlugin extends JavaPlugin {
    public static final String SERVER_SIDEBAR_PREFIX = "cavetale.server-sidebar.";
    @Getter protected static ServerPlugin instance;
    protected final ServerCommand serverCommand = new ServerCommand(this);
    protected final BackCommand backCommand = new BackCommand(this);
    protected final WhoCommand whoCommand = new WhoCommand(this);
    protected final ServerAdminCommand serverAdminCommand = new ServerAdminCommand(this);
    protected final EventListener eventListener = new EventListener(this);
    protected final Map<String, ServerSlot> serverMap = new HashMap<>();
    protected String serverName;
    protected ServerTag serverTag;
    protected ServerSlot serverSlot;
    protected boolean syncing;
    protected boolean enabling;
    protected boolean disabling;
    protected long sidebarLinesUpdated;
    protected final ServerBackProvider backProvider = new ServerBackProvider();
    private final SQLDatabase database = new SQLDatabase(this);

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        enabling = true;
        database.registerTables(getDatabaseClasses());
        database.createAllTables();
        Bungee.register(this);
        backProvider.enable();
        serverCommand.enable();
        backCommand.enable();
        whoCommand.enable();
        serverAdminCommand.enable();
        eventListener.enable();
        loadThisServer();
        Bukkit.getScheduler().runTaskTimer(this, this::storeThisServer, 60L * 20L, 60L * 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::loadOtherServers, 0L, 60L * 20L);
        new MenuListener().enable();
        enabling = false;
    }

    @Override
    public void onDisable() {
        disabling = true;
        if (!serverTag.persistent) {
            Redis.del("cavetale.server." + serverTag.name);
            Connect.getInstance().broadcast("server:remove", serverTag.name);
        }
        for (ServerSlot slot : serverMap.values()) {
            slot.disable();
        }
        serverMap.clear();
        if (serverTag.waitOnWake) {
            Redis.del("cavetale.server_wake." + serverName);
        }
        syncCommandsNow();
        database.waitForAsyncTask();
        database.close();
    }

    public List<ServerSlot> getServerList() {
        var result = new ArrayList<>(serverMap.values());
        Collections.sort(result);
        return result;
    }

    /**
     * Set the sidebar lines of this server. This method can be
     * spammed with identical contents and will only trigger an update
     * once every 30 seconds or when necessary.
     *
     * The lines are broadcast via Connect (which uses Redis lpush)
     * whenever the content changes.
     *
     * The content is stored in Redis for 10 minutes: If the content
     * changed, or if the previous set operation is older than 60
     * seconds.
     *
     * Other servers will receive the broadcast live, or load the
     * Redis key alongside their 60 second loadOtherServers() refresh.
     */
    public void setServerSidebarLines(List<Component> lines) {
        ServerSlot slot = serverMap.get(serverName);
        if (slot == null) return;
        long now = System.currentTimeMillis();
        boolean didChange = !Objects.equals(slot.sidebarLines, lines);
        boolean broadcastRequired = didChange;
        boolean redisRefreshRequired = didChange || sidebarLinesUpdated < now - 1000L * 60L;
        if (!broadcastRequired && !redisRefreshRequired) return;
        slot.sidebarLines = lines;
        ServerSidebarLines pack = new ServerSidebarLines(serverName, lines);
        String serialized = Json.serialize(pack);
        if (broadcastRequired) {
            Connect.getInstance().broadcast("server:sidebar", serialized);
        }
        if (redisRefreshRequired) {
            sidebarLinesUpdated = now;
            String redisKey = SERVER_SIDEBAR_PREFIX + serverName;
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    if (lines == null || lines.isEmpty()) {
                        Redis.del(redisKey);
                    } else {
                        Redis.set(redisKey, serialized, 600L);
                    }
                });
        }
    }

    protected void loadThisServer() {
        serverName = Connect.getInstance().getServerName();
        File file = new File(getDataFolder(), "tag.json");
        serverTag = ServerTag.load(file);
        serverTag.name = serverName;
        if (!file.exists()) saveThisServer();
        if (new File("WaitOnWake").exists()) {
            serverTag.waitOnWake = true;
        }
        serverSlot = registerServer(serverTag);
        storeThisServer();
        broadcastThisServer();
    }

    protected void saveThisServer() {
        File file = new File(getDataFolder(), "tag.json");
        getDataFolder().mkdirs();
        serverTag.save(file);
    }

    /**
     * Put in Redis storage.
     */
    protected void storeThisServer() {
        final String key = "cavetale.server." + serverTag.name;
        final String json = serverTag.toJson();
        final boolean persist = serverTag.persistent;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                Redis.set(key, json, persist ? 0L : 60L * 5L);
            });
    }

    /**
     * Broadcast update via Connect queue.
     */
    protected void broadcastThisServer() {
        Connect.getInstance().broadcast("server:update", serverTag.toJson());
    }

    protected ServerSlot registerServer(ServerTag tag) {
        ServerSlot slot = serverMap.get(tag.name);
        boolean isNew = false;
        if (slot == null) {
            isNew = true;
            slot = new ServerSlot(this, tag.name);
        }
        slot.load(tag);
        if (isNew) {
            getLogger().info("Registering server " + tag.name);
            slot.enable();
            serverMap.put(tag.name, slot);
        }
        return slot;
    }

    protected void unregisterServer(String name) {
        ServerSlot slot = serverMap.remove(name);
        if (slot == null) return;
        getLogger().info("Unregistering server " + name);
        slot.disable();
    }

    protected void loadOtherServers() {
        Bukkit.getServer().getScheduler().runTaskAsynchronously(this, () -> {
                // async
                Set<String> allKeys = Redis.keys("cavetale.server.*");
                Bukkit.getServer().getScheduler().runTask(this, () -> {
                        // sync
                        List<String> list = new ArrayList<>();
                        for (String redisKey : allKeys) {
                            String theServerName = redisKey.substring(16);
                            if (serverName.equals(theServerName)) continue;
                            String json = Redis.get(redisKey);
                            if (json == null) continue;
                            ServerTag tag = ServerTag.fromJson(json);
                            if (tag.name == null) continue;
                            registerServer(tag);
                            list.add(tag.name);
                        }
                        // Remove missing servers
                        for (String key : new ArrayList<>(serverMap.keySet())) {
                            if (key.equals(serverName)) continue;
                            ServerSlot slot = serverMap.get(key);
                            if (slot == null || slot.tag.persistent) continue;
                            if (list.contains(key)) continue;
                            unregisterServer(key);
                        }
                        // Load server infos
                        for (Map.Entry<String, ServerSlot> entry : serverMap.entrySet()) {
                            String key = entry.getKey();
                            ServerSlot slot = entry.getValue();
                            String value = Redis.get(SERVER_SIDEBAR_PREFIX + key);
                            ServerSidebarLines serverSidebarLines = Json.deserialize(value, ServerSidebarLines.class);
                            slot.sidebarLines = serverSidebarLines != null
                                ? serverSidebarLines.getComponents()
                                : null;
                        }
                        eventListener.updateSidebarLines();
                    });
            });
    }

    protected void syncCommands() {
        if (enabling || disabling || syncing) return;
        syncing = true;
        Bukkit.getScheduler().runTask(this, this::syncCommandsNow);
    }

    private void syncCommandsNow() {
        syncing = false;
        getLogger().info("Syncing commands");
        try {
            getServer().getClass().getMethod("syncCommands").invoke(getServer());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void serverUpdateReceived(ServerTag tag) {
        ServerSlot slot = serverMap.get(tag.name);
        if (slot == null) return;
        List<UUID> uuids = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            uuids.add(player.getUniqueId());
        }
        final String name = slot.name;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                for (UUID uuid : uuids) {
                    String choice = Redis.get("cavetale.server_choice." + uuid);
                    if (choice != null && name.equals(choice)) {
                        Bukkit.getScheduler().runTask(this, () -> {
                                Player player = Bukkit.getPlayer(uuid);
                                if (player != null) {
                                    slot.tryToJoinPlayer(player, true);
                                }
                            });
                    }
                }
            });
    }

    public static SQLDatabase database() {
        return instance.database;
    }

    public static void serverSidebar(List<Component> lines) {
        instance.setServerSidebarLines(lines);
    }

    public static ServerPlugin serverPlugin() {
        return instance;
    }
}
