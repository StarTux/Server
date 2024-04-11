package com.cavetale.server;

import com.cavetale.core.chat.Chat;
import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.suggestCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class WhoCommand extends AbstractCommand<ServerPlugin> {
    protected WhoCommand(final ServerPlugin plugin) {
        super(plugin, "who");
    }

    @Override
    protected void onEnable() {
        rootNode.denyTabCompletion()
            .description("List online players")
            .senderCaller(this::who);
    }

    private void who(CommandSender sender) {
        final Player senderPlayer = sender instanceof Player player
            ? player
            : null;
        for (ServerSlot slot : plugin.serverMap.values()) {
            slot.onlinePlayers.clear();
        }
        for (RemotePlayer player : Connect.get().getRemotePlayers()) {
            if (senderPlayer != null && Chat.doesIgnore(senderPlayer.getUniqueId(), player.getUniqueId())) continue;
            ServerSlot slot = plugin.serverMap.get(player.getOriginServerName());
            if (slot == null) continue;
            slot.onlinePlayers.add(player);
        }
        int totalCount = 0;
        List<Component> lines = new ArrayList<>();
        for (ServerSlot slot : plugin.getServerList()) {
            if (!slot.canSee(sender)) continue;
            List<RemotePlayer> playerList = slot.onlinePlayers;
            if (sender instanceof Player) {
                playerList.removeIf(it -> {
                        Player online = Bukkit.getPlayer(it.getUniqueId());
                        return online != null && online.getGameMode() == GameMode.SPECTATOR && online.hasPermission("chat.invisible");
                    });
            }
            if (playerList.size() == 0) continue;
            Collections.sort(playerList, (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName()));
            totalCount += playerList.size();
            Component nameComponent = join(noSeparators(),
                                           slot.displayName,
                                           text(" (", GRAY),
                                           text("" + playerList.size(), WHITE),
                                           text(")", GRAY));
            if (slot.canJoin(sender) && slot.tag.command) {
                String cmd = "/" + slot.name;
                nameComponent = nameComponent
                    .clickEvent(suggestCommand(cmd))
                    .hoverEvent(showText(text(cmd, GREEN)));
            }
            List<Component> playerNames = new ArrayList<>();
            for (RemotePlayer online : playerList) {
                boolean staff = online.hasPermission("group.trusted");
                playerNames.add(text(online.getName(), staff ? GOLD : WHITE)
                                .clickEvent(suggestCommand("/msg " + online.getName()))
                                .hoverEvent(showText(join(noSeparators(), new Component[] {
                                                text(online.getName(), (staff ? GOLD : WHITE)),
                                                newline(),
                                                text((staff ? "Staff member" : "Player"), DARK_GRAY),
                                                newline(),
                                                text("/msg " + online.getName(), GRAY),
                                            }))));
            }
            lines.add(join(noSeparators(),
                           nameComponent,
                           space(),
                           join(separator(text(", ", GRAY)), playerNames)));
        }
        sender.sendMessage(join(noSeparators(),
                                text("Player List (", GRAY), text(totalCount), text(")", GRAY),
                                newline(),
                                join(separator(newline()), lines)));
    }
}
