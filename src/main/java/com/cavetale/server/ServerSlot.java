package com.cavetale.server;

import com.cavetale.core.event.player.PluginPlayerEvent.Detail;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.winthier.connect.Connect;
import com.winthier.connect.Redis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
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
    protected List<Component> sidebarLines;

    public void tryToSwitch(Player player, boolean forceOnline) {
        if (name.equals(plugin.serverName)) {
            player.sendMessage(Component.text("You're already on this server", NamedTextColor.YELLOW));
            return;
        }
        if (!hasPermission(player)) {
            player.sendMessage(Component.text("Server not available: " + name, NamedTextColor.RED));
            return;
        }
        if (tag.locked) {
            if (!player.hasPermission("server.locked")) {
                player.sendMessage(Component.text("This server is locked!", NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("Entering a locked server!", NamedTextColor.RED, TextDecoration.ITALIC));
        }
        if (!forceOnline && !Connect.getInstance().listServers().contains(name)) {
            player.sendMessage(Component.text()
                               .append(Component.text("Please wait while "))
                               .append(displayName)
                               .append(Component.text(" is starting up..."))
                               .color(NamedTextColor.YELLOW));
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    Redis.set("cavetale.server_choice." + player.getUniqueId(), name, 60L);
                    if (tag.waitOnWake) {
                        Redis.lpush("cavetale.server_wake." + name, "wake_up", 30L);
                    }
                });
            return;
        }
        player.sendMessage(Component.text().color(NamedTextColor.GREEN)
                           .append(Component.text("Joining "))
                           .append(displayName)
                           .append(Component.text("...")));
        PluginPlayerEvent.Name.SWITCH_SERVER.ultimate(plugin, player)
            .detail(Detail.NAME, name)
            .call();
        Bungee.send(plugin, player, name);
    }

    protected void enable() {
        permission = "server.visit." + name;
        if (Bukkit.getPluginManager().getPermission(permission) == null) {
            Permission perm = new Permission(permission,
                                             "Visit the " + name + " server",
                                             PermissionDefault.FALSE);
            Bukkit.getPluginManager().addPermission(perm);
        }
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
        TextComponent.Builder tooltip = Component.text().append(displayName);
        List<Component> attributes = new ArrayList<>();
        if (tag.locked) {
            attributes.add(Component.text("locked", TextColor.color(0xFF0000)));
        }
        if (tag.hidden) {
            attributes.add(Component.text("hidden", TextColor.color(0xFF00FF)));
        }
        if (!attributes.isEmpty()) {
            tooltip.append(Component.newline());
            tooltip.append(Component.join(JoinConfiguration.separator(Component.text(" ")),
                                          attributes).decorate(TextDecoration.ITALIC));
        }
        tooltip.append(Component.newline())
            .append(Component.text("/" + name, NamedTextColor.GRAY));
        tooltip.append(Component.newline())
            .append(Component.join(JoinConfiguration.separator(Component.newline()),
                                   description.toArray(new Component[0])));
        component = Component.text()
            .append(displayName)
            .hoverEvent(HoverEvent.showText(tooltip.build()))
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
            tryToSwitch(player, false);
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
