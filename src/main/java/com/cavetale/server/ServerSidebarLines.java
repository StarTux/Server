package com.cavetale.server;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

/**
 * Store the lines a server wants displayed in the sidebar, in a Redis
 * field and as a Connect package.
 */
@Data
public final class ServerSidebarLines {
    protected String server;
    protected List<String> lines;

    public ServerSidebarLines() { }

    public ServerSidebarLines(final String server, final List<Component> lines) {
        this.server = server;
        if (lines == null || lines.isEmpty()) {
            this.lines = null;
        } else {
            this.lines = new ArrayList<>(lines.size());
            for (Component component : lines) {
                this.lines.add(GsonComponentSerializer.gson().serialize(component));
            }
        }
    }

    public List<Component> getComponents() {
        if (lines == null || lines.isEmpty()) return null;
        List<Component> result = new ArrayList<>(lines.size());
        for (String string : lines) {
            result.add(GsonComponentSerializer.gson().deserialize(string));
        }
        return result;
    }
}
