package com.cavetale.server;

import com.cavetale.core.util.Json;
import com.winthier.connect.Connect;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

@RequiredArgsConstructor
public final class ServerSlot implements Comparable<ServerSlot> {
    public static final String WILDCARD_PERMISSION = "server.visit.*";
    private final ServerPlugin plugin;
    public final String name;
    protected MyCommand command;
    protected ServerTag tag;
    protected String permission;
    protected Component displayName;
    protected List<Component> description;
    protected Component component;
    protected ItemStack itemStack;

    public void tryToSwitch(Player player) {
        if (name.equals(plugin.serverName)) {
            player.sendMessage(Component.text("You're already on this server", NamedTextColor.YELLOW));
            return;
        }
        if (!hasPermission(player)) {
            player.sendMessage(Component.text("Server not available: " + name, NamedTextColor.RED));
            return;
        }
        if (tag.locked && !player.hasPermission("server.locked")) {
            player.sendMessage(Component.text("This server is locked!", NamedTextColor.RED));
            return;
        }
        if (!Connect.getInstance().listServers().contains(name)) {
            player.sendMessage(Component.text("This server is currently offline! Please try again later.",
                                              NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text().color(NamedTextColor.GREEN)
                           .append(Component.text("Joining "))
                           .append(displayName)
                           .append(Component.text("...")));
        Connect.getInstance().broadcast("server:switch", Json.serialize(new ServerSwitchPacket(player.getUniqueId(), name)));
        Bungee.send(player, name);
    }

    protected void enable() {
        permission = "server.visit." + name;
        command = new MyCommand();
        command.setPermission(permission + ";" + WILDCARD_PERMISSION);
        if (!Bukkit.getCommandMap().register("server", command)) {
            plugin.getLogger().warning("/" + name + ": Command registration failed. Using fallback");
        }
    }

    protected void disable() {
        command.unregister(Bukkit.getCommandMap());
        removeCommand(name);
        removeCommand("server:" + name);
    }

    private void removeCommand(String label) {
        if (Bukkit.getCommandMap().getKnownCommands().get(label) == command) {
            Bukkit.getCommandMap().getKnownCommands().remove(label);
        }
    }

    protected void load(ServerTag serverTag) {
        if (!Objects.equals(name, serverTag.name)) {
            throw new IllegalArgumentException("name != " + serverTag);
        }
        this.tag = serverTag;
        displayName = serverTag.parseDisplayName();
        if (Objects.equals(displayName, Component.empty())) {
            displayName = Component.text(name);
        }
        description = serverTag.parseDescription();
        component = Component.text()
            .append(displayName)
            .hoverEvent(Component.text()
                        .append(displayName)
                        .append(Component.newline())
                        .append(Component.text("/" + name, NamedTextColor.GRAY))
                        .append(Component.newline())
                        .append(Component.join(Component.newline(), description.toArray(new Component[0])))
                        .build())
            .clickEvent(ClickEvent.runCommand("/server " + name))
            .build();
        itemStack = serverTag.parseItemStack();
        itemStack.editMeta(meta -> {
                meta.displayName(displayName);
                meta.lore(description);
            });
    }

    public boolean hasPermission(CommandSender sender) {
        return sender.hasPermission(permission)
            || sender.hasPermission(WILDCARD_PERMISSION);
    }

    public boolean canSee(CommandSender sender) {
        return !tag.hidden || sender.hasPermission("server.hidden");
    }

    @Override
    public int compareTo(ServerSlot other) {
        return Integer.compare(other.tag.priority, this.tag.priority);
    }

    final class MyCommand extends Command implements PluginIdentifiableCommand {
        MyCommand() {
            super(name,
                  "Switch to " + name, // description
                  "Usage: /" + name, // usageMessage
                  Collections.emptyList()); // aliases
        }

        @Override
        public Plugin getPlugin() {
            return plugin;
        }

        @Override
        public boolean execute(CommandSender sender, String labal, String[] args) {
            if (args.length != 0) return false;
            Player player = sender instanceof Player ? (Player) sender : null;
            if (player == null) {
                sender.sendMessage("[server:" + name + "] Player expected");
                return true;
            }
            tryToSwitch(player);
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String labal, String[] args) {
            return Collections.emptyList();
        }

        @Override
        public boolean testPermissionSilent(CommandSender sender) {
            return hasPermission(sender);
        }
    }
}
