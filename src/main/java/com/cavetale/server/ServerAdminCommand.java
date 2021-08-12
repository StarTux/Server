package com.cavetale.server;

import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

@RequiredArgsConstructor
public final class ServerAdminCommand implements TabExecutor {
    private final ServerPlugin plugin;
    private CommandNode rootNode;

    public void enable() {
        rootNode = new CommandNode("serveradmin");
        rootNode.addChild("reload").denyTabCompletion()
            .description("Reload this server")
            .senderCaller(this::reload);
        rootNode.addChild("refresh").denyTabCompletion()
            .description("Reload other servers")
            .senderCaller(this::refresh);
        rootNode.addChild("list").denyTabCompletion()
            .description("List other servers")
            .senderCaller(this::list);
        rootNode.addChild("info").arguments("<name>")
            .description("Get server info")
            .senderCaller(this::info)
            .completableList(c -> plugin.getServerList().stream()
                             .map(slot -> slot.name)
                             .collect(Collectors.toList()));
        plugin.getCommand("serveradmin").setExecutor(this);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return rootNode.call(sender, command, alias, args);
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return rootNode.complete(sender, command, alias, args);
    }

    boolean reload(CommandSender sender, String[] args) {
        plugin.loadThisServer();
        sender.sendMessage(Component.text("Reloaded this server", NamedTextColor.YELLOW));
        return true;
    }

    boolean refresh(CommandSender sender, String[] args) {
        plugin.loadOtherServers();
        sender.sendMessage(Component.text("Reloaded other servers", NamedTextColor.YELLOW));
        return true;
    }

    boolean list(CommandSender sender, String[] args) {
        List<Component> servers = new ArrayList<>();
        for (ServerSlot slot : plugin.getServerList()) {
            servers.add(Component.text().content(slot.name).color(NamedTextColor.YELLOW)
                        .hoverEvent(HoverEvent.showText(Component.text()
                                                        .append(slot.displayName)
                                                        .append(Component.newline())
                                                        .append(Component.join(Component.newline(), slot.description.toArray(new Component[0])))))
                        .clickEvent(ClickEvent.runCommand("/serveradmin info " + slot.name))
                        .build());
        }
        sender.sendMessage(Component.text().content(servers.size() + " servers: ").color(NamedTextColor.YELLOW)
                           .append(Component.join(Component.text(", ", NamedTextColor.DARK_GRAY),
                                                  servers.toArray(new Component[0]))));
        return true;
    }

    boolean info(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        ServerSlot slot = plugin.serverMap.get(args[0]);
        if (slot == null) {
            throw new CommandWarn("Unknown server: " + args[0]);
        }
        sender.sendMessage(Component.text().content("Server Info " + slot.name).color(NamedTextColor.YELLOW)
                           .append(Component.newline())
                           .append(Component.text(slot.tag.prettyPrint())));
        return true;
    }
}
