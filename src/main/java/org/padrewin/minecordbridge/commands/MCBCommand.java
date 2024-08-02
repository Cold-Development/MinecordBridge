package org.padrewin.minecordbridge.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.user.User;
import org.jetbrains.annotations.NotNull;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.padrewin.minecordbridge.javacord.JavacordHelper;
import org.padrewin.minecordbridge.listeners.discord.DMListener;

import java.util.List;

public class MCBCommand implements CommandExecutor {

    private final MinecordBridge minecord = MinecordBridge.getPlugin();
    private final JavacordHelper js;
    private TextChannel pmChannel;

    public MCBCommand() {
        js = minecord.js;
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, String[] args) {
        if (args.length > 0) {
            if (sender instanceof Player player) {
                if (args[0].equalsIgnoreCase("reload")) {
                    minecord.reload();
                    minecord.loadMessagesConfig();  // Adaugă această linie
                    minecord.sendMessage(player, "Commands.reload_success");
                    return true;
                } else if (args[0].equalsIgnoreCase("retrolink")) {
                    handleRetroLinkCommand(player, args);
                    return true;
                } else if (args[0].equalsIgnoreCase("unlink")) {
                    handleUnlinkCommand(player, args);
                    return true;
                } else {
                    minecord.sendMessage(player, "Commands.command_not_found");
                    return true;
                }
            } else if (sender instanceof ConsoleCommandSender) {
                if (args.length == 1) {
                    if (args[0].equalsIgnoreCase("reload")) {
                        minecord.reload();
                        minecord.loadMessagesConfig();  // Adaugă această linie
                        minecord.getLogger().info(minecord.getMessage("Commands.reload_success"));
                        return true;
                    } else if (args[0].equalsIgnoreCase("retrolink")) {
                        js.retroLink();
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
            minecord.sendMessage(player, "Commands.invalid_usage_retrolink");
            return;
        }

        String discriminatedName;
        String roleName;

        if (args.length == 2) {
            discriminatedName = args[1];
            minecord.sendMessage(player, "Commands.role_mandatory");
            return;
        } else if (args.length == 3) {
            discriminatedName = args[1];
            roleName = args[2];
        } else {
            minecord.sendMessage(player, "Commands.invalid_usage_retrolink");
            return;
        }

        if (!discriminatedName.contains("#")) {
            minecord.sendMessage(player, "Commands.invalid_username_format");
            return;
        }

        try {
            boolean isLinked = js.retroLinkSingle(player, discriminatedName, roleName);
            if (isLinked) {
                minecord.sendMessage(player, "Commands.instructions_sent");
            }
        } catch (Exception e) {
            minecord.sendMessage(player, "Commands.error_linking_user" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleUnlinkCommand(Player player, String[] args) {
        if (args.length < 3) {
            minecord.sendMessage(player, "Commands.invalid_usage_unlink");
            return;
        }

        String discriminatedName = args[1];
        String roleName = args[2];

        if (!discriminatedName.contains("#")) {
            minecord.sendMessage(player, "Commands.invalid_username_format");
            return;
        }

        // Check if the role exists in the configuration
        List<String> roles = minecord.getConfig().getStringList("roles");
        if (!roles.contains(roleName)) {
            minecord.sendMessage(player, "Commands.role_not_found" + roleName);
            minecord.sendMessage(player, "Commands.note_case_sensitive");
            return;
        }

        try {
            // Obține utilizatorul de pe Discord folosind numele și discriminatorul
            User user = js.api.getServerById(minecord.serverID).get().getMemberByDiscriminatedName(discriminatedName).orElse(null);
            if (user == null) {
                minecord.sendMessage(player, "Commands.player_not_found_discord");
                return;
            }

            Database db = MinecordBridge.getDatabase();
            // Obține ID-ul utilizatorului Discord direct ca long
            long discordId = user.getId();

            // Verifică dacă utilizatorul are un cont legat în baza de date
            if (!db.doesEntryExist(discordId)) {
                minecord.sendMessage(player, "Commands.no_linked_account");
                return;
            }

            String minecraftUsername = db.getUsername(discordId);
            if (minecraftUsername == null || minecraftUsername.isEmpty()) {
                minecord.sendMessage(player, "Commands.no_minecraft_username");
                return;
            }

            executeRemoveCommands(minecraftUsername, roleName);
            db.removeLink(discordId);

            minecord.sendMessage(player, "Commands.account_unlinked");
        } catch (Exception e) {
            minecord.sendMessage(player, "Commands.error_linking_user" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void executeRemoveCommands(String minecraftUsername, String roleName) {
        try {
            List<String> removeCommands = minecord.getConfig().getStringList(roleName + ".remove-commands");

            for (String command : removeCommands) {
                String formattedCommand = command.replace("%user%", minecraftUsername);
                minecord.getServer().dispatchCommand(minecord.getServer().getConsoleSender(), formattedCommand);
            }

            minecord.log(minecraftUsername + " has lost benefits from role " + roleName + ".");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startLinking(Player player, String discName) {
        Database db = MinecordBridge.getDatabase();
        if (db.doesEntryExist(player.getUniqueId())) {
            minecord.sendMessage(player, "Commands.account_already_linked");
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
                minecord.sendMessage(player, "Commands.player_not_found_discord_simple");
                return;
            }

            try {
                new MessageBuilder()
                        .append(minecord.getMessage("Commands.linking_dm"))
                        .send(user).thenAccept(msg -> pmChannel = msg.getChannel()).join();
            } catch (Exception e) {
                minecord.error("Error sending message to user! Stack Trace:");
                minecord.error(e.getMessage());
            }
            user.addUserAttachableListener(new DMListener(pmChannel));
        } catch (NullPointerException e) {
            minecord.sendMessage(player, "Commands.player_not_found_discord_simple");
            minecord.error("Player is not found on Discord server! Stack Trace:");
            minecord.error(e.getMessage());
        }
        minecord.sendMessage(player, "Commands.check_dm");
    }
}
