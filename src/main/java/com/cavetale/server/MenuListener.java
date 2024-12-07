package com.cavetale.server;

import com.cavetale.core.menu.MenuItemClickEvent;
import com.cavetale.core.menu.MenuItemEntry;
import com.cavetale.core.menu.MenuItemEvent;
import com.cavetale.mytems.Mytems;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class MenuListener implements Listener {
    public static final String MENU_KEY = "server:server";

    protected void enable() {
        Bukkit.getPluginManager().registerEvents(this, ServerPlugin.serverPlugin());
    }

    @EventHandler
    private void onMenuItem(MenuItemEvent event) {
        ServerPlugin.serverPlugin().getLogger().info(event.getEventName());
        event.addItem(builder -> builder
                      .priority(MenuItemEntry.Priority.HOTBAR)
                      .key(MENU_KEY)
                      .icon(Mytems.EARTH.createIcon(List.of(text("Servers", GREEN)))));
    }

    @EventHandler
    private void onMenuItemClick(MenuItemClickEvent event) {
        if (MENU_KEY.equals(event.getEntry().getKey())) {
            new ServerMenu(event.getPlayer()).open();
        }
    }
}
