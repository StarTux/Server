package com.cavetale.server;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.bukkit.entity.Player;

public final class Bungee {
    private Bungee() { }

    public static void send(Player player, String serverName) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
        try {
            dataOutputStream.writeUTF("Connect");
            dataOutputStream.writeUTF(serverName);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        player.sendPluginMessage(ServerPlugin.instance, "BungeeCord", byteArrayOutputStream.toByteArray());
    }
}
