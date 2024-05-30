package com.cavetale.server.sql;

import com.cavetale.core.back.BackLocation;
import com.cavetale.core.connect.NetworkServer;
import com.winthier.sql.SQLRow;
import com.winthier.sql.SQLRow.Name;
import com.winthier.sql.SQLRow.NotNull;
import java.util.Date;
import java.util.UUID;
import lombok.Data;

@Data
@NotNull @Name("back")
public final class SQLBack implements SQLRow {
    @Id private Integer id;
    @Unique private UUID player;
    @Keyed @VarChar(40) private String server;
    @VarChar(40) private String plugin;
    @VarChar(40) private String world;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    @VarChar(255) private String description;
    private Date created;
    @VarChar(40) private String lastServer;

    public SQLBack() { }

    public SQLBack load(BackLocation backLocation) {
        this.player = backLocation.getPlayerUuid();
        this.server = backLocation.getServer().registeredName;
        this.plugin = backLocation.getPlugin();
        this.world = backLocation.getWorld();
        this.x = backLocation.getX();
        this.y = backLocation.getY();
        this.z = backLocation.getZ();
        this.yaw = backLocation.getYaw();
        this.pitch = backLocation.getPitch();
        this.description = backLocation.getDescription();
        this.created = new Date();
        this.lastServer = server;
        return this;
    }

    public BackLocation toBackLocation() {
        final boolean logoutServer = lastServer != null && lastServer.equals(server);
        return new BackLocation(player, NetworkServer.of(server), plugin, world, x, y, z, pitch, yaw, description, logoutServer);
    }
}
