package org.padrewin.minecordbridge.commands;

import org.padrewin.minecordbridge.javacord.JavacordHelper;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.padrewin.minecordbridge.listeners.discord.DMListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.user.User;
import org.jetbrains.annotations.NotNull;

public class MCBCommand implements CommandExecutor {

    private final MinecordBridge minecord = MinecordBridge.getPlugin();
    private final JavacordHelper js;
    private TextChannel pmChannel;

    public MCBCommand() {
        js = minecord.js;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, String[] args) {
        if (args.length > 0) {
            if (sender instanceof Player player) {
                if (args[0].equalsIgnoreCase("reload")) {
                    minecord.reload();
                    minecord.sendMessage(player, "&fConfig &2reloaded&f!");
                    return true;
                } else if (args[0].equalsIgnoreCase("retrolink")) {
                    handleRetroLinkCommand(player, args);
                    return true;
                } else if (args[0].equalsIgnoreCase("link")) {
                    if (args.length == 1) return false;
                    startLinking(player, args[1]);
                    return true;
                } else if (args[0].equalsIgnoreCase("unlink")) {
                    unlink(player);
                    return true;
                } else {
                    minecord.sendMessage(player, "&cCommand not found!");
                    return true;
                }
            } else if (sender instanceof ConsoleCommandSender) {
                if (args.length == 1) {
                    if (args[0].equalsIgnoreCase("reload")) {
                        minecord.reload();
                        return true;
                    } else if (args[0].equalsIgnoreCase("retrolink")) {
                        js.retroLink();
                        return true;
                    } else if (args[0].equalsIgnoreCase("link")) {
                        minecord.warn("Command can be used only by a player!");
                        return true;
                    } else {
                        minecord.warn("Command not found!");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void handleRetroLinkCommand(Player player, String[] args) {
        if (args.length < 2) {
            minecord.sendMessage(player, "&fInvalid command usage. Use &c/minecord retrolink <username#0> <role>");
            return;
        }

        String discriminatedName;
        String roleName;

        if (args.length == 2) {
            discriminatedName = args[1];
            minecord.sendMessage(player, "&cRole is mandatory. &fPlease specify a role. &7(&fexample: &2Nitro&7)");
            return;
        } else if (args.length == 3) {
            discriminatedName = args[1];
            roleName = args[2];
        } else {
            minecord.sendMessage(player, "&cInvalid command usage. &fUse &c/minecord retrolink <username#0> <role>");
            return;
        }

        if (!discriminatedName.contains("#")) {
            minecord.sendMessage(player, "&cInvalid username format. &fPlease include a discriminator. &7(&fexample: &2user#0&7)");
            return;
        }

        try {
            boolean isLinked = js.retroLinkSingle(player, discriminatedName, roleName);
            if (isLinked) {
                minecord.sendMessage(player, "&fInstructions sent &2successfully&f.");
            }
        } catch (Exception e) {
            minecord.sendMessage(player, "&cError linking user: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startLinking(Player player, String discName) {
        Database db = MinecordBridge.getDatabase();
        if (db.doesEntryExist(player.getUniqueId())) {
            minecord.sendMessage(player, "&fAccount already &2linked&f!");
            return;
        }

        try {
            User user;
            if (discName.contains("#")) {
                user = js.api.getServerById(minecord.serverID).get().getMemberByDiscriminatedName(discName).orElse(null);
            } else {
                user = js.api.getServerById(minecord.serverID).get().getMembersByName(discName).stream().findFirst().orElse(null);
            }

            if (user == null) {
                minecord.sendMessage(player, "&cPlayer is not found on &9Discord &cserver or discriminator is not found!");
                return;
            }

            try {
                new MessageBuilder()
                        .append("You are attempting to link your Discord and Minecraft accounts. ")
                        .append("\nAnswer with \"**YES**\" to continue or \"**NO**\" if you think this was an error.")
                        .send(user).thenAccept(msg -> pmChannel = msg.getChannel()).join();
            } catch (Exception e) {
                minecord.error("Error sending message to user! Stack Trace:");
                minecord.error(e.getMessage());
            }
            user.addUserAttachableListener(new DMListener(pmChannel));
        } catch (NullPointerException e) {
            minecord.sendMessage(player, "&cPlayer is not found on &9Discord &cserver!");
            minecord.error("Player is not found on Discord server! Stack Trace:");
            minecord.error(e.getMessage());
        }
        minecord.sendMessage(player, "&fCheck your DM's on &9Discord &fto continue!");
    }

    public void unlink(Player player) {
        Database db = MinecordBridge.getDatabase();
        if (!db.doesEntryExist(player.getUniqueId())) {
            minecord.sendMessage(player, "&fYour account is already &cunlinked&f!");
            return;
        }
        db.removeLink(player.getUniqueId());
        minecord.sendMessage(player, "&fYour account is not &cunlinked&f!");
    }
}
