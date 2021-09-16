package com.cavetale.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class ServerCommand implements TabExecutor {
    private final ServerPlugin plugin;

    public void enable() {
        plugin.getCommand("server").setExecutor(this);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length > 1) return false;
        if (args.length == 0) {
            listServers(sender);
            return true;
        }
        Player player = sender instanceof Player ? (Player) sender : null;
        if (player == null) {
            sender.sendMessage("[server:server] Player expected");
            return true;
        }
        ServerSlot serverSlot = plugin.serverMap.get(args[0]);
        if (serverSlot == null || !serverSlot.hasPermission(sender)) {
            sender.sendMessage(Component.text("Server not found: " + args[0]));
            return true;
        }
        serverSlot.tryToSwitch(player, false);
        return true;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length != 1) return Collections.emptyList();
        return plugin.getServerList().stream()
            .filter(slot -> slot.canSee(sender) && slot.hasPermission(sender))
            .map(slot -> slot.name)
            .filter(name -> name.contains(args[0]))
            .collect(Collectors.toList());
    }

    public void listServers(CommandSender sender) {
        List<Component> list = new ArrayList<>();
        for (ServerSlot slot : plugin.getServerList()) {
            if (!slot.hasPermission(sender) || !slot.canSee(sender)) continue;
            list.add(slot.component);
        }
        Component message = Component.text()
            .content("Available servers: ").color(NamedTextColor.AQUA)
            .append(Component.join(Component.text(", ", NamedTextColor.GRAY), list))
            .build();
        sender.sendMessage(message);
    }
}
