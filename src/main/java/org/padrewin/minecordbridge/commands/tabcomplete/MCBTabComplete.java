package org.padrewin.minecordbridge.commands.tabcomplete;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MCBTabComplete implements TabCompleter {

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {

        ArrayList<String> tabs = new ArrayList<>();

        switch (args.length) {
            case 1:
                if (sender.hasPermission("minecord.update"))
                    tabs.add("reload");
                if (sender.hasPermission("minecord.unlink"))
                    tabs.add("unlink");
                if (sender.hasPermission("minecord.info"))
                    tabs.add("info");
                if (sender.hasPermission("minecord.list"))
                    tabs.add("list");
                if (sender.hasPermission("minecord.boost"))
                    tabs.add("boost");
                return tabs;

            case 2:
                if (args[0].equalsIgnoreCase("info") && sender.hasPermission("minecord.info")) {
                    tabs.add("<mcName/discordName/discordId>");
                } else if (args[0].equalsIgnoreCase("list") && sender.hasPermission("minecord.list")) {
                    tabs.add("<page>");
                } else if (args[0].equalsIgnoreCase("boost") && sender.hasPermission("minecord.boost")) {
                    tabs.add("claim");
                }
                return tabs;

            default:
                return tabs;
        }
    }
}