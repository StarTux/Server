package com.cavetale.server.back;

import com.cavetale.core.back.Back;
import com.cavetale.core.command.AbstractCommand;
import com.cavetale.server.ServerPlugin;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class BackCommand extends AbstractCommand<ServerPlugin> {
    public BackCommand(final ServerPlugin plugin) {
        super(plugin, "back");
    }

    @Override
    protected void onEnable() {
        rootNode.denyTabCompletion()
            .description("Return to previous survival location")
            .playerCaller(BackCommand::back);
    }

    public static void back(Player player) {
        Back.sendBack(player, backLocation -> {
                if (backLocation == null) {
                    player.sendMessage(text("You don't have a location to return to", RED));
                } else {
                    player.sendMessage(text("Going back: " + backLocation.getDescription(), GRAY));
                }
            });
    }
}
