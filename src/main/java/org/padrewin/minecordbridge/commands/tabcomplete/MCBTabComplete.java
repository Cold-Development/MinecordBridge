package org.padrewin.minecordbridge.commands.tabcomplete;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
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
                if (sender.hasPermission("minecord.link"))
                    tabs.add("link");
                if (sender.hasPermission("minecord.unlink"))
                    tabs.add("unlink");
                if (sender.hasPermission("minecord.retrolink"))
                    tabs.add("retrolink");
                return tabs;

            case 2:
                if (args[0].equalsIgnoreCase("link") && sender.hasPermission("minecord.link")) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        tabs.add(player.getName());
                    }
                } else if (args[0].equalsIgnoreCase("retrolink") && sender.hasPermission("minecord.retrolink")) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        tabs.add(player.getName());
                    }
                }
                return tabs;

            case 3:
                if (args[0].equalsIgnoreCase("retrolink") && sender.hasPermission("minecord.retrolink")) {
                    tabs.add("<roleName>");
                }
                return tabs;

            default:
                return tabs;
        }
    }
}