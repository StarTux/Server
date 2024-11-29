package com.cavetale.server;

import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandNode;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.util.Json;
import com.cavetale.mytems.Mytems;
import com.winthier.connect.Redis;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

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
        rootNode.addChild("set").arguments("<key> <value>")
            .description("Change server settings")
            .completableList(List.of("persistent",
                                     "prio", "priority",
                                     "locked",
                                     "hidden",
                                     "lockedandhidden",
                                     "displayName",
                                     "description",
                                     "material",
                                     "waitonwake",
                                     "command",
                                     "commandname"))
            .senderCaller(this::set);
        rootNode.addChild("wakeup").arguments("<server>")
            .description("Wake up server")
            .completers(CommandArgCompleter.supplyList(() -> new ArrayList<>(plugin.serverMap.keySet())))
            .senderCaller(this::wakeUp);
        rootNode.addChild("send").arguments("<player> <server>")
            .description("Send a player to a server")
            .completers(CommandArgCompleter.ONLINE_PLAYERS,
                        CommandArgCompleter.supplyList(() -> new ArrayList<>(plugin.serverMap.keySet())))
            .senderCaller(this::send);
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
        sender.sendMessage(text("Reloaded this server", YELLOW));
        return true;
    }

    boolean refresh(CommandSender sender, String[] args) {
        plugin.loadOtherServers();
        sender.sendMessage(text("Reloaded other servers", YELLOW));
        return true;
    }

    boolean list(CommandSender sender, String[] args) {
        List<Component> servers = new ArrayList<>();
        for (ServerSlot slot : plugin.getServerList()) {
            servers.add(text().content(slot.name).color(YELLOW)
                        .hoverEvent(HoverEvent.showText(text()
                                                        .append(slot.displayName)
                                                        .append(Component.newline())
                                                        .append(Component.join(JoinConfiguration.separator(Component.newline()),
                                                                               slot.description.toArray(new Component[0])))))
                        .clickEvent(ClickEvent.runCommand("/serveradmin info " + slot.name))
                        .build());
        }
        sender.sendMessage(text().content(servers.size() + " servers: ").color(YELLOW)
                           .append(Component.join(JoinConfiguration.separator(text(", ", DARK_GRAY)),
                                                  servers.toArray(new Component[0]))));
        return true;
    }

    boolean info(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        ServerSlot slot = plugin.serverMap.get(args[0]);
        if (slot == null) {
            throw new CommandWarn("Unknown server: " + args[0]);
        }
        sender.sendMessage(text().content("Server Info " + slot.name).color(YELLOW)
                           .append(Component.newline())
                           .append(text(slot.tag.prettyPrint())));
        return true;
    }

    boolean set(CommandSender sender, String[] args) {
        if (args.length < 2) return false;
        String key = args[0];
        String value = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        try {
            switch (key) {
            case "persistent":
                plugin.serverTag.persistent = Boolean.parseBoolean(value);
                break;
            case "priority": case "prio":
                plugin.serverTag.priority = Integer.parseInt(value);
                break;
            case "locked":
                plugin.serverTag.locked = Boolean.parseBoolean(value);
                break;
            case "hidden":
                plugin.serverTag.hidden = Boolean.parseBoolean(value);
                break;
            case "lockedandhidden":
                plugin.serverTag.hidden = Boolean.parseBoolean(value);
                plugin.serverTag.locked = Boolean.parseBoolean(value);
                break;
            case "displayName": {
                Component comp = Json.deserializeComponent(value);
                value = Json.serializeComponent(comp);
                plugin.serverTag.displayName = value;
                break;
            }
            case "description": {
                Component comp = Json.deserializeComponent(value);
                value = Json.serializeComponent(comp);
                plugin.serverTag.description = List.of(value);
                break;
            }
            case "material": {
                ItemStack item = Mytems.deserializeItem(value);
                if (item != null) {
                    value = Mytems.forItem(item).serializeItem(item);
                    plugin.serverTag.material = value;
                    break;
                }
                Material mat = Material.valueOf(value.toUpperCase());
                value = mat.getKey().getKey();
                plugin.serverTag.material = value;
                break;
            }
            case "waitonwake":
                plugin.serverTag.waitOnWake = Boolean.parseBoolean(value);
                if (plugin.serverTag.waitOnWake) {
                    try {
                        try (PrintWriter out = new PrintWriter("WaitOnWake")) {
                            out.println(plugin.serverName);
                        }
                    } catch (FileNotFoundException fnfe) {
                        throw new UncheckedIOException(fnfe);
                    }
                } else {
                    new File("WaitOnWake").delete();
                }
                break;
            case "command":
                plugin.serverTag.command = Boolean.parseBoolean(value);
                break;
            case "commandname":
                plugin.serverTag.commandName = value;
                break;
            default:
                throw new CommandWarn("Invalid key: " + key);
            }
        } catch (IllegalArgumentException iae) {
            throw new CommandWarn("Invalid value: " + value);
        }
        sender.sendMessage(text("Set " + key + " to " + value, YELLOW));
        plugin.saveThisServer();
        plugin.registerServer(plugin.serverTag);
        plugin.storeThisServer();
        plugin.broadcastThisServer();
        return true;
    }

    boolean wakeUp(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        String name = args[0];
        Redis.lpush("cavetale.server_wake." + name, "wake_up", 30L);
        sender.sendMessage(text("Wake up signal sent to server " + name));
        return true;
    }

    private boolean send(CommandSender sender, String[] args) {
        if (args.length != 2) return false;
        final Player player = CommandArgCompleter.requirePlayer(args[0]);
        final ServerSlot slot = plugin.serverMap.get(args[1]);
        if (slot == null) {
            throw new CommandWarn("Unknown server: " + args[1]);
        }
        sender.sendMessage(text("Sending " + player.getName() + " to " + slot.getName() + "...", YELLOW));
        slot.joinPlayer(player, false);
        return true;
    }
}
