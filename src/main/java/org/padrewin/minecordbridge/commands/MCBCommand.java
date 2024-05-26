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
                    minecord.sendMessage(player, "&fConfig reloaded!");
                    return true;
                } else if (args[0].equalsIgnoreCase("retrolink")) {
                    if (args.length == 1) {
                        js.retroLink(player);
                    } else if (args.length == 3) {
                        js.retroLinkSingle(args[1], args[2]);
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("link")) {
                    if (args.length == 1) return false;
                    startLinking(player, args[1]);
                    return true;
                } else if (args[0].equalsIgnoreCase("unlink")) {
                    unlink(player);
                    return true;
                } else {
                    minecord.sendMessage(player, "&cComanda nu a fost gasita!");
                    return true;
                }
            } else if (sender instanceof ConsoleCommandSender) {
                if (args.length == 1) {
                    if (args[0].equalsIgnoreCase("reload")) {
                        minecord.reload();
                        return true;
                    } else if (args[0].equalsIgnoreCase("retrolink")) {
                        js.retroLink();
                    } else if (args[0].equalsIgnoreCase("link")) {
                        minecord.warn("Comanda poate fi folosita decat de un jucator!");
                        return true;
                    } else {
                        minecord.warn("Comanda nu a fost gasita!");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void startLinking(Player player, String discName) {
        Database db = MinecordBridge.getDatabase();
        if (db.doesEntryExist(player.getUniqueId())) {
            minecord.sendMessage(player, "&cContul tau este deja verificat!");
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
                minecord.sendMessage(player, "&c Jucatorul nu este pe server-ul de Discord sau discriminatorul este incorect!");
                return;
            }

            try {
                new MessageBuilder()
                        .append("Tocmai incerci sa iti verifici contul de minecraft pe server-ul de discord. ")
                        .append("\nRaspunde scriind \"DA\" pentru a continua, sau \"NU\" daca crezi ca a fost o greseala.")
                        .send(user).thenAccept(msg -> pmChannel = msg.getChannel()).join();
            } catch (Exception e) {
                minecord.error("Error sending message to user! Stack Trace:");
                minecord.error(e.getMessage());
            }
            user.addUserAttachableListener(new DMListener(pmChannel));
        } catch (NullPointerException e) {
            minecord.sendMessage(player, "&c Jucatorul nu este pe server-ul de Discord!");
            minecord.error("Jucatorul nu este pe server-ul de Discord! Stack Trace:");
            minecord.error(e.getMessage());
        }
        minecord.sendMessage(player, "Verifica mesajele private pe Discord pentru a continua!");
    }

    public void unlink(Player player) {
        Database db = MinecordBridge.getDatabase();
        if (!db.doesEntryExist(player.getUniqueId())) {
            minecord.sendMessage(player, "&cContul tau nu este verificat!");
            return;
        }
        db.removeLink(player.getUniqueId());
        minecord.sendMessage(player, "Contul a fost scos din verificare cu succes!");
    }

}