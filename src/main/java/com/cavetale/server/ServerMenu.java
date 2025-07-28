package com.cavetale.server;

import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.font.GuiOverlay;
import com.cavetale.core.menu.MenuItemEvent;
import com.cavetale.core.struct.Vec2i;
import com.cavetale.mytems.Mytems;
import com.cavetale.mytems.util.Gui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import static com.cavetale.core.font.Unicode.tiny;
import static com.cavetale.server.ServerPlugin.serverPlugin;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextColor.color;

@RequiredArgsConstructor
public final class ServerMenu {
    private static final int SIZE = 6 * 9;
    private final Player player;
    private final GuiGroup mainGroup = new GuiGroup(2, 1, 6, 1);
    private final GuiGroup minigameGroup = new GuiGroup(0, 3, 8, 6);

    public void open() {
        final Gui gui = new Gui(serverPlugin())
            .size(SIZE)
            .layer(GuiOverlay.SERVER_MENU, WHITE);
        gui.setItem(Gui.OUTSIDE, null, click -> {
                if (!click.isLeftClick()) return;
                MenuItemEvent.openMenu(player);
                player.playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1f);
            });
        final List<Entry> entries = new ArrayList<>();
        for (ServerSlot serverSlot : serverPlugin().getServerList()) {
            if (!serverSlot.shouldHaveCommand() || !serverSlot.hasPermission(player) || !serverSlot.canSee(player) || serverSlot.getNetworkServer().isTechnical()) continue;
            final GuiGroup guiGroup;
            if (serverSlot.getNetworkServer() == NetworkServer.CAVETALE || serverSlot.getNetworkServer() == NetworkServer.BETA) {
                guiGroup = minigameGroup;
            } else if (serverSlot.getNetworkServer().isMinigame() || serverSlot.getNetworkServer().isEvent()) {
                guiGroup = minigameGroup;
            } else {
                guiGroup = mainGroup;
            }
            entries.add(new Entry(serverSlot.getName(),
                                  guiGroup,
                                  serverSlot.getItemStack(),
                                  () -> serverSlot.tryToJoinPlayer(player, false),
                                  serverSlot.getTag().isHidden() || serverSlot.getTag().isLocked()));
        }
        entries.add(new Entry("home",
                              mainGroup,
                              Mytems.HOME.createIcon(List.of(text("Home", GREEN),
                                                             text("/home", DARK_GRAY),
                                                             text(tiny("To the home worlds"), GRAY))),
                              () -> player.performCommand("home"),
                              false));
        entries.add(new Entry("mine",
                              mainGroup,
                              Mytems.HASTY_PICKAXE.createIcon(List.of(text("Mining World", GOLD),
                                                                      text("/mine", DARK_GRAY),
                                                                      text(tiny("Go mining in our weekly"), GRAY),
                                                                      text(tiny("resetting mining world"), GRAY),
                                                                      text(tiny("with huge ores, epic"), GRAY),
                                                                      text(tiny("dungeons and dangerous"), GRAY),
                                                                      text(tiny("monster hives."), GRAY))),
                              () -> player.performCommand("mine"),
                              false));
        entries.add(new Entry("market",
                              mainGroup,
                              Mytems.GOLDEN_COIN.createIcon(List.of(text("Market", YELLOW),
                                                                    text("/market", DARK_GRAY),
                                                                    text(tiny("Visit our market to trade"), GRAY),
                                                                    text(tiny("items with other players."), GRAY),
                                                                    text(tiny("You can even start your"), GRAY),
                                                                    text(tiny("own shop once you reach"), GRAY),
                                                                    text(tiny("the right tier."), GRAY))),
                              () -> player.performCommand("market"),
                              false));
        entries.sort(Comparator.comparing(Entry::getName));
        for (Entry entry : entries) {
            final Vec2i guiSlot = entry.guiGroup.next();
            gui.setItem(guiSlot.x, guiSlot.z, entry.itemStack, click -> {
                    if (!(click.isLeftClick())) return;
                    entry.click.run();
                    player.closeInventory();
                    player.playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1f);
                });
            if (entry.red) {
                gui.highlight(guiSlot.x, guiSlot.z, color(0x440000));
            }
        }
        gui.open(player);
    }

    @Value
    public final class Entry {
        private final String name;
        private final GuiGroup guiGroup;
        private final ItemStack itemStack;
        private final Runnable click;
        private final boolean red;
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
