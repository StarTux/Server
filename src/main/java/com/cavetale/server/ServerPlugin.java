package com.cavetale.server;

import com.winthier.connect.Connect;
import com.winthier.connect.Redis;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerPlugin extends JavaPlugin {
    @Getter protected static ServerPlugin instance;
    protected final ServerCommand serverCommand = new ServerCommand(this);
    protected final ServerAdminCommand serverAdminCommand = new ServerAdminCommand(this);
    protected final EventListener eventListener = new EventListener(this);
    protected final Map<String, ServerSlot> serverMap = new HashMap<>();
    protected String serverName;
    protected ServerTag serverTag;

    @Override
    public void onEnable() {
        instance = this;
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        serverCommand.enable();
        serverAdminCommand.enable();
        eventListener.enable();
        loadThisServer();
        loadOtherServers();
        long storeServerInterval = 60L * 20L; // 1 minute
        long loadServerInterval = 60L * 20L; // 1 minute
        Bukkit.getScheduler().runTaskTimer(this, this::storeThisServer, storeServerInterval, storeServerInterval);
        Bukkit.getScheduler().runTaskTimer(this, this::loadOtherServers, loadServerInterval, loadServerInterval);
    }

    @Override
    public void onDisable() {
        if (!serverTag.persistent) {
            Redis.del("cavetale.server." + serverTag.name);
            Connect.getInstance().broadcast("server:remove", serverTag.name);
        }
        for (ServerSlot slot : serverMap.values()) {
            slot.disable();
        }
        serverMap.clear();
    }

    public List<ServerSlot> getServerList() {
        var result = new ArrayList<>(serverMap.values());
        Collections.sort(result);
        return result;
    }

    protected void loadThisServer() {
        serverName = Connect.getInstance().getServerName();
        File file = new File(getDataFolder(), "tag.json");
        serverTag = ServerTag.load(file);
        serverTag.name = serverName;
        if (!file.exists()) saveThisServer();
        registerServer(serverTag);
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
        String key = "cavetale.server." + serverTag.name;
        Redis.set(key, serverTag.toJson(), serverTag.persistent ? 0L : 60L * 5L);
    }

    /**
     * Broadcast update via Connect queue.
     */
    protected void broadcastThisServer() {
        Connect.getInstance().broadcast("server:update", serverTag.toJson());
    }

    protected void registerServer(ServerTag tag) {
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
    }

    protected void unregisterServer(String name) {
        ServerSlot slot = serverMap.remove(name);
        if (slot == null) return;
        getLogger().info("Unregistering server " + name);
        slot.disable();
    }

    protected void loadOtherServers() {
        List<String> list = Connect.getInstance().listServers();
        for (String otherServer : list) {
            if (serverName.equals(otherServer)) continue;
            String key = "cavetale.server." + otherServer;
            String json = Redis.get(key);
            if (json == null) continue;
            ServerTag tag = ServerTag.fromJson(json);
            if (tag.name == null) continue;
            registerServer(tag);
        }
        // Remove missing servers
        for (String key : new ArrayList<>(serverMap.keySet())) {
            if (key.equals(serverName)) continue;
            ServerSlot slot = serverMap.get(key);
            if (slot == null || slot.tag.persistent) continue;
            if (list.contains(key)) continue;
            unregisterServer(key);
        }
    }
}
