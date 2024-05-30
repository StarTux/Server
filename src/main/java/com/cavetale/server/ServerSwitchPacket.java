package com.cavetale.server;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor
public final class ServerSwitchPacket {
    protected UUID playerUuid;
    protected String serverName;
}
