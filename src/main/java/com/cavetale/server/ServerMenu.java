package com.cavetale.server;

import com.cavetale.core.font.GuiOverlay;
import com.cavetale.core.menu.MenuItemEvent;
import com.cavetale.core.struct.Vec2i;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.util.Gui;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import static com.cavetale.core.font.Unicode.tiny;
import static com.cavetale.server.ServerPlugin.serverPlugin;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextColor.color;

@RequiredArgsConstructor
public final class ServerMenu {
    private static final int SIZE = 6 * 9;
    private final Player player;
    private final GuiGroup lockedGroup = new GuiGroup(0, 5, 8, 5);
    private final GuiGroup mainGroup = new GuiGroup(0, 1, 2, 4);
    private final GuiGroup minigameGroup = new GuiGroup(4, 1, 8, 4);

    public void open() {
        final Gui gui = new Gui(serverPlugin())
            .size(SIZE)
            .title(textOfChildren(Mytems.EARTH, text(" Server List", color(0x0021f3))))
            .layer(GuiOverlay.BLANK, color(0x0013de))
            .layer(GuiOverlay.SERVER_MENU, color(0x0021f3))
            .layer(GuiOverlay.TITLE_BAR, color(0x020079));
        gui.setItem(Gui.OUTSIDE, null, click -> {
                if (!click.isLeftClick()) return;
                MenuItemEvent.openMenu(player);
            });
        for (ServerSlot serverSlot : serverPlugin().getServerList()) {
            if (!serverSlot.shouldHaveCommand() || !serverSlot.hasPermission(player) || !serverSlot.canSee(player) || serverSlot.getNetworkServer().isTechnical()) continue;
            final GuiGroup guiGroup;
            if (serverSlot.getTag().isLocked() || serverSlot.getTag().isHidden()) {
                guiGroup = lockedGroup;
            } else if (serverSlot.getNetworkServer().isMinigame() || serverSlot.getNetworkServer().isEvent()) {
                guiGroup = minigameGroup;
            } else {
                guiGroup = mainGroup;
            }
            final Vec2i guiSlot = guiGroup.next();
            gui.setItem(guiSlot.x, guiSlot.z, serverSlot.getItemStack(), click -> {
                    serverSlot.tryToJoinPlayer(player, false);
                });
            if (serverSlot.getTag().isHidden() || serverSlot.getTag().isLocked()) {
                gui.highlight(guiSlot.x, guiSlot.z, color(0xff0000));
            }
        }
        final Vec2i homeSlot = mainGroup.next();
        gui.setItem(homeSlot.x, homeSlot.z, Mytems.HOME.createIcon(List.of(text("Home", GREEN),
                                                                           text("/home", GRAY),
                                                                           text(tiny("To the home worlds"), DARK_GRAY))),
                    click -> player.performCommand("home"));
        gui.open(player);
    }

    public final class GuiGroup {
        private final int ax;
        private final int ay;
        private final int bx;
        private final int by;
        private int x;
        private int y;

        GuiGroup(final int ax, final int ay, final int bx, final int by) {
            this.ax = ax;
            this.ay = ay;
            this.bx = bx;
            this.by = by;
            this.x = ax;
            this.y = ay;
        }

        public Vec2i next() {
            final Vec2i result = Vec2i.of(x, y);
            x += 1;
            if (x > bx) {
                x = ax;
                y += 1;
            }
            return result;
        }
    }
}
