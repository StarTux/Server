package com.cavetale.server;

import com.cavetale.core.event.player.PluginPlayerEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.format.NamedTextColor.*;

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
        if (serverSlot == null || !serverSlot.shouldHaveCommand() || !serverSlot.hasPermission(sender)) {
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
            .filter(slot -> slot.shouldHaveCommand() && slot.canSee(sender) && slot.hasPermission(sender))
            .map(slot -> slot.name)
            .filter(name -> name.contains(args[0]))
            .collect(Collectors.toList());
    }

    public void listServers(CommandSender sender) {
        List<Component> list = new ArrayList<>();
        List<Component> hiddenList = new ArrayList<>();
        for (ServerSlot slot : plugin.getServerList()) {
            if (!slot.shouldHaveCommand() || !slot.hasPermission(sender) || !slot.canSee(sender)) continue;
            if (slot.tag.hidden || slot.tag.locked) {
                hiddenList.add(slot.component);
            } else {
                list.add(slot.component);
            }
        }
        sender.sendMessage(text("Available servers: ", AQUA).append(join(separator(text(", ", GRAY)), list)));
        if (!hiddenList.isEmpty()) {
            sender.sendMessage(text("Hidden or locked servers: ", RED).append(join(separator(text(", ", GRAY)), hiddenList)));
        }
        if (sender instanceof Player) {
            PluginPlayerEvent.Name.VIEW_SERVER_LIST.call(plugin, (Player) sender);
        }
    }
}
