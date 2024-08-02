package org.padrewin.minecordbridge.listeners.discord;

import org.bukkit.entity.Player;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.logging.ExceptionLogger;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.padrewin.minecordbridge.javacord.JavacordHelper;

import java.util.HashMap;
import java.util.Random;

public class DMListener implements MessageCreateListener {

    private final MinecordBridge minecord;
    private final org.bukkit.Server server;
    private final Database db;
    private String randInt;  // Changed to String for consistency
    private int step;
    private Player player;
    private final String[] roleNames;
    private Role addedRole;
    private final HashMap<String, String[]> addCommands;
    private final TextChannel channel;
    private boolean resent = false;
    private static final int MAX_RETRIES = 3;

    public DMListener(Role addedRole, TextChannel channel) {
        minecord = MinecordBridge.getPlugin();
        server = minecord.getServer();
        db = MinecordBridge.getDatabase();
        roleNames = minecord.roleNames;
        addCommands = minecord.addCommands;
        this.channel = channel;
        this.addedRole = addedRole;
        step = 1;
    }

    public DMListener(TextChannel channel) {
        minecord = MinecordBridge.getPlugin();
        server = minecord.getServer();
        db = MinecordBridge.getDatabase();
        roleNames = minecord.roleNames;
        addCommands = minecord.addCommands;
        this.channel = channel;
        step = 1;
    }

    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        JavacordHelper js = minecord.js;

        Server discordServer = js.api.getServerById(minecord.serverID).get();

        User user = event.getMessageAuthor().asUser().get();

        if (!event.getChannel().equals(channel)) {
            return;
        }

        String messageContent = event.getMessageContent().trim();

        if (messageContent.equalsIgnoreCase("resend")) {
            handleResend(user);
            return;
        } else if (messageContent.equalsIgnoreCase("cancel")) {
            event.getChannel().sendMessage(minecord.getMessage("Auto-Role.dm_cancelled"));
            RoleAddListener.removeListener(user, this);
            step = 0;
            return;
        }

        switch (step) {
            case 1:
                handleStep1(event, user);
                break;
            case 2:
                handleStep2(event, user);
                break;
            case 3:
                handleStep3(event, user, discordServer);
                break;
            default:
                break;
        }
    }

    private void handleStep1(MessageCreateEvent event, User user) {
        if (event.getMessageContent().equalsIgnoreCase("NO")) {
            event.getChannel().sendMessage(minecord.getMessage("Auto-Role.dm_thank_you"));
            RoleAddListener.removeListener(user, this);
        } else if (event.getMessageContent().equalsIgnoreCase("YES")) {
            boolean messageSent = false;
            int attempt = 0;

            while (!messageSent && attempt < MAX_RETRIES) {
                try {
                    new MessageBuilder()
                            .append(minecord.getMessage("Auto-Role.dm_instructions"))
                            .send(event.getChannel()).exceptionally(ExceptionLogger.get());
                    messageSent = true;
                } catch (Exception e) {
                    attempt++;
                    minecord.error("Error sending message: " + user.getDiscriminatedName() + ". Attempt " + attempt + ". Stack Trace:");
                    minecord.error(e.getMessage());
                    if (attempt >= MAX_RETRIES) {
                        minecord.error("Failed to send message after " + MAX_RETRIES + " attempts.");
                        event.getChannel().sendMessage(minecord.getMessage("Auto-Role.dm_something_wrong"));
                        break;
                    }
                }
            }

            if (messageSent) {
                step = 2;
                event.getChannel().sendMessage(minecord.getMessage("Auto-Role.dm_enter_minecraft_name"));
            }
        } else {
            event.getChannel().sendMessage(minecord.getMessage("Auto-Role.registration_question"));
        }
    }

    private void handleStep2(MessageCreateEvent event, User user) {
        try {
            player = server.getPlayer(event.getMessageContent());

            if (player != null && player.isOnline()) {
                randInt = getRandomNumber();
                player.sendMessage(" ");
                player.sendMessage(minecord.pluginTag + minecord.getMessage("Player-facing-messages.your_code").replace("%code%", randInt));
                player.sendMessage(" ");
                event.getChannel().sendMessage(minecord.getMessage("Auto-Role.dm_retry_code"));
                step = 3;
            } else {
                event.getChannel().sendMessage(minecord.getMessage("Auto-Role.dm_not_connected"));
            }
        } catch (NullPointerException e) {
            minecord.error("Error linking user: " + user.getDiscriminatedName() + ". Stack Trace:");
            minecord.error(e.getMessage());
            event.getChannel().sendMessage(minecord.getMessage("Auto-Role.dm_player_not_found"));
        }
    }

    private void handleStep3(MessageCreateEvent event, User user, Server discordServer) {
        try {
            if (event.getMessageContent().equals(randInt)) {
                db.insertLink(event.getMessageAuthor().getId(), player.getName(), player.getUniqueId());
                if (minecord.changeNickOnLink) {
                    discordServer.updateNickname(user, player.getName()).join();
                }
                if (addedRole != null) {
                    RoleAddListener.runCommands(minecord, roleNames, addCommands, addedRole, player.getName());
                }
                event.getChannel().sendMessage(minecord.getMessage("Auto-Role.dm_rewarded"));
                player.sendMessage(minecord.pluginTag + minecord.getMessage("Player-facing-messages.rewarded"));
                step = 4;
                RoleAddListener.removeListener(user, this);
            } else {
                event.getChannel().sendMessage(minecord.getMessage("Auto-Role.dm_code_mismatch"));
            }
        } catch (Exception e) {
            minecord.error("Error linking user: " + user.getDiscriminatedName() + ". Stack Trace:");
            minecord.error(e.getMessage());
            event.getChannel().sendMessage(minecord.getMessage("Auto-Role.dm_something_wrong"));
        }
    }

    private void handleResend(User user) {
        if (step == 3) {
            randInt = getRandomNumber();
            player.sendMessage(" ");
            player.sendMessage(minecord.pluginTag + minecord.getMessage("Player-facing-messages.new_code").replace("%code%", randInt));
            player.sendMessage(" ");
            channel.sendMessage(minecord.getMessage("Auto-Role.dm_new_code_sent"));
        } else {
            channel.sendMessage(minecord.getMessage("Auto-Role.dm_new_code_not_possible"));
        }
    }

    private String getRandomNumber() {
        Random rng = new Random();
        int number = rng.nextInt(999999);
        return String.format("%06d", number);
    }
}
