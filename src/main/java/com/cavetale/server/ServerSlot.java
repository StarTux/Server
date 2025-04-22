package com.cavetale.server;

import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.core.event.player.PluginPlayerEvent.Detail;
import com.cavetale.mytems.util.Text;
import com.winthier.connect.Connect;
import com.winthier.connect.Redis;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import static com.cavetale.core.font.Unicode.tiny;
import static com.cavetale.mytems.util.Items.tooltip;
import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static net.kyori.adventure.text.format.TextColor.color;
import static net.kyori.adventure.text.format.TextDecoration.*;
import static net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText;

@Getter
@RequiredArgsConstructor
public final class ServerSlot implements Comparable<ServerSlot> {
    public static final String WILDCARD_PERMISSION = "server.visit.*";
    private final ServerPlugin plugin;
    public final String name;
    private String commandName;
    private MyCommand command;
    protected ServerTag tag;
    protected String permission;
    protected Component displayName;
    protected String flatDisplayName;
    protected List<Component> description;
    protected List<Component> tooltip;
    protected Component component;
    protected ItemStack itemStack;
    protected List<Component> sidebarLines;
    protected List<RemotePlayer> onlinePlayers = new ArrayList<>();

    public NetworkServer getNetworkServer() {
        return NetworkServer.of(name);
    }

    /**
     * Try to join a player if they have all the necessary permissions.
     * This will call joinPlayer() if successful.
     * @param player the player
     * @param forceOnline whether or not we are to assume the server
     *   is online, that is NOT try to wake it up.  This shall only be
     *   true in callback functions after the wakeup attempt has been
     *   done.
     * @return true if the player was allowed to pass, false
     *   otherwise.
     */
    public boolean tryToJoinPlayer(Player player, boolean forceOnline) {
        if (name.equals(plugin.serverName)) {
            player.sendMessage(text("You're already on this server", YELLOW));
            return false;
        }
        if (!hasPermission(player)) {
            player.sendMessage(text("Server not available: " + commandName, RED));
            return false;
        }
        if (tag.locked) {
            if (!player.hasPermission("server.locked")) {
                player.sendMessage(text("This server is locked!", RED));
                return false;
            }
            player.sendMessage(text("Entering a locked server!", RED, ITALIC));
        }
        if (tag.hidden) {
            if (!player.hasPermission("server.hidden")) {
                player.sendMessage(text("This server is locked!", RED));
                return false;
            }
            player.sendMessage(text("Entering a hidden server!", RED, ITALIC));
        }
        joinPlayer(player, forceOnline);
        return true;
    }

    /**
     * Send a player to this server.  The target server may still deny
     * the player entrance if they do not have the necessary
     * permissions.
     * @param player See above
     * @param See above
     * @return See above
     */
    public void joinPlayer(Player player, boolean forceOnline) {
        if (!forceOnline && !Connect.getInstance().listServers().contains(name)) {
            player.sendMessage(text()
                               .append(text("Please wait while "))
                               .append(displayName)
                               .append(text(" is starting up..."))
                               .color(YELLOW));
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    Redis.set("cavetale.server_choice." + player.getUniqueId(), name, 60L);
                    if (tag.waitOnWake) {
                        Redis.lpush("cavetale.server_wake." + name, "wake_up", 30L);
                    }
                });
            return;
        }
        player.sendMessage(text().color(GREEN)
                           .append(text("Joining "))
                           .append(displayName)
                           .append(text("...")));
        PluginPlayerEvent.Name.SWITCH_SERVER.make(plugin, player)
            .detail(Detail.NAME, name)
            .callEvent();
        Bungee.send(plugin, player, name);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Redis.del("cavetale.server_choice." + player.getUniqueId());
            });
    }

    protected void enable() {
        permission = "server.visit." + name;
        if (Bukkit.getPluginManager().getPermission(permission) == null) {
            Permission perm = new Permission(permission,
                                             "Visit the " + name + " server",
                                             PermissionDefault.FALSE);
            Bukkit.getPluginManager().addPermission(perm);
        }
    }

    protected void disable() {
        removeCommand();
    }

    private void createCommand() {
        command = new MyCommand();
        command.setPermission(permission + ";" + WILDCARD_PERMISSION);
        if (!Bukkit.getCommandMap().register("server", command)) {
            plugin.getLogger().warning("/" + commandName + ": Command registration failed. Using fallback");
        }
        plugin.syncCommands();
    }

    private void removeCommand() {
        if (command == null) return;
        command.unregister(Bukkit.getCommandMap());
        removeCommandMap(command.getName());
        removeCommandMap("server:" + command.getName());
        command = null;
        plugin.syncCommands();
    }

    private void removeCommandMap(String label) {
        if (Bukkit.getCommandMap().getKnownCommands().get(label) == command) {
            Bukkit.getCommandMap().getKnownCommands().remove(label);
        }
    }

    protected void load(ServerTag serverTag) {
        if (!Objects.equals(name, serverTag.name)) {
            throw new IllegalArgumentException("name != " + serverTag);
        }
        this.commandName = serverTag.getCommandName() != null && !serverTag.getCommandName().isEmpty()
            ? serverTag.getCommandName()
            : name;
        this.tag = serverTag;
        displayName = serverTag.parseDisplayName();
        if (Objects.equals(displayName, empty())) {
            displayName = text(name);
        }
        flatDisplayName = plainText().serialize(displayName);
        description = serverTag.parseDescription();
        tooltip = new ArrayList<>();
        tooltip.add(displayName);
        List<Component> attributes = new ArrayList<>();
        if (tag.locked) {
            attributes.add(text("locked", color(0xFF0000)));
        }
        if (tag.hidden) {
            attributes.add(text("hidden", color(0xFF00FF)));
        }
        if (!attributes.isEmpty()) {
            tooltip.add(join(separator(text(" ")), attributes).decorate(ITALIC));
        }
        tooltip.add(text("/" + commandName, GRAY));
        for (Component line : description) {
            for (Component line2 : Text.wrapLore2(plainText().serialize(line), str -> text(tiny(str), DARK_GRAY))) {
                tooltip.add(line2);
            }
        }
        component = displayName
            .hoverEvent(showText(join(separator(newline()), tooltip)))
            .clickEvent(runCommand("/server " + name));
        itemStack = serverTag.parseItemStack();
        itemStack.editMeta(meta -> {
                tooltip(meta, tooltip);
                meta.addItemFlags(ItemFlag.values());
            });
        itemStack.unsetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (command != null && !command.getName().equals(commandName)) {
            removeCommand();
        }
        if (command == null && tag.command && !name.equals(plugin.serverName)) {
            createCommand();
        } else if (command != null && (!tag.command || name.equals(plugin.serverName))) {
            removeCommand();
        }
    }

    public boolean hasPermission(CommandSender sender) {
        if (sender == Bukkit.getConsoleSender()) return true;
        return sender.hasPermission(permission)
            || sender.hasPermission(WILDCARD_PERMISSION);
    }

    public boolean canSee(CommandSender sender) {
        if (sender == Bukkit.getConsoleSender()) return true;
        return !tag.hidden || sender.hasPermission("server.hidden");
    }

    public boolean canJoin(CommandSender sender) {
        return !tag.locked || sender.hasPermission("server.locked");
    }

    public boolean shouldHaveCommand() {
        return tag.command;
    }

    @Override
    public int compareTo(ServerSlot other) {
        int result = Integer.compare(other.tag.priority, this.tag.priority);
        if (result != 0) return result;
        result = name.compareTo(other.flatDisplayName);
        return result;
    }

    final class MyCommand extends Command implements PluginIdentifiableCommand {
        MyCommand() {
            super(commandName,
                  "Switch to " + commandName, // description
                  "Usage: /" + commandName, // usageMessage
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
                sender.sendMessage("[server:" + commandName + "] Player expected");
                return true;
            }
            tryToJoinPlayer(player, false);
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
