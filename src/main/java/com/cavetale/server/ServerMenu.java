package com.cavetale.server;

import com.cavetale.core.font.GuiOverlay;
import com.cavetale.core.menu.MenuItemEvent;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.util.Gui;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextColor.color;

@RequiredArgsConstructor
public final class ServerMenu {
    private static final int SIZE = 6 * 9;
    private final Player player;

    public void open() {
        final Gui gui = new Gui()
            .size(SIZE)
            .title(textOfChildren(Mytems.EARTH, text(" Server List", WHITE)))
            .layer(GuiOverlay.BLANK, color(0xadd8e6));
        gui.setItem(Gui.OUTSIDE, null, click -> {
                if (!click.isLeftClick()) return;
                MenuItemEvent.openMenu(player);
            });
        int nextGuiSlot = 9;
        for (ServerSlot serverSlot : ServerPlugin.serverPlugin().getServerList()) {
            if (!serverSlot.shouldHaveCommand() || !serverSlot.hasPermission(player) || !serverSlot.canSee(player)) continue;
            final int guiSlot = nextGuiSlot++;
            gui.setItem(guiSlot, serverSlot.getItemStack(), click -> {
                    serverSlot.tryToJoinPlayer(player, false);
                });
            if (serverSlot.getTag().isHidden() || serverSlot.getTag().isLocked()) {
                gui.highlight(guiSlot, color(0xff0000));
            }
        }
        gui.open(player);
    }
}
