package org.padrewin.minecordbridge.javacord;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageEditEvent;
import org.javacord.api.interaction.ApplicationCommandInteraction;
import org.javacord.api.listener.interaction.InteractionCreateListener;
import org.javacord.api.listener.message.MessageEditListener;
import org.javacord.api.util.logging.ExceptionLogger;
import org.javacord.core.interaction.ButtonInteractionImpl;
import org.javacord.core.interaction.SlashCommandInteractionImpl;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.padrewin.minecordbridge.listeners.discord.DMListener;
import org.padrewin.minecordbridge.listeners.discord.DiscordMessageListener;
import org.padrewin.minecordbridge.listeners.discord.RoleAddListener;
import org.padrewin.minecordbridge.listeners.discord.RoleRemoveListener;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JavacordHelper {

    public DiscordApi api;
    public Server discordServer;
    public RoleAddListener roleAddListener;
    public RoleRemoveListener roleRemoveListener;
    public DiscordMessageListener discordMessageListener;
    public Role[] roles;
    public TextChannel chatStreamChannel;
    private final MinecordBridge minecord = MinecordBridge.getPlugin();
    private String[] roleNames;
    private HashMap<String, String> roleAndID;
    private boolean doListeners = false;
    private TextChannel pmChannel;
    private final Database db;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private Map<String, Integer> roleCounts = new HashMap<>();
    private static final int MAX_RETRIES = 3;

    // ANSI escape codes for colors
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";

    public JavacordHelper(String[] roleNames) {
        this.roleNames = roleNames;
        roles = new Role[roleNames.length];
        roleAndID = minecord.roleAndID;
        db = MinecordBridge.getDatabase();
        parseConfig();
        if (doListeners) initListeners();

        // Schedule cache refresh every hour
        scheduler.scheduleAtFixedRate(this::refreshCache, 0, 1, TimeUnit.HOURS);

        // Add reconnect listener
        api.addReconnectListener(event -> minecord.log(ANSI_GREEN + "Reconnected to Discord." + ANSI_RESET));

        // Schedule reconnect check
        scheduleReconnectCheck();
    }

    public void disableAPI() {
        try {
            if (api != null) {
                api.disconnect().join();
            }
            api = null;
        } catch (CompletionException e) {
            minecord.error(ANSI_RED + "Error disconnecting from API! Stack Trace:" + ANSI_RESET);
            minecord.error(ANSI_RED + e.getMessage() + ANSI_RESET);
        }
        scheduler.shutdown();
    }

    public void reload() {
        if (api != null) {
            api.removeListener(roleAddListener);
            api.removeListener(roleRemoveListener);
            if (minecord.useChatStream) {
                api.removeListener(discordMessageListener);
            }
        }
        roleAddListener = null;
        roleRemoveListener = null;
        discordMessageListener = null;

        disableAPI();

        // Reload roles and configuration
        roleNames = minecord.getRolesParsed().toArray(new String[0]);
        roleAndID = new HashMap<>(minecord.roleAndID); // Reload the roleAndID map
        roles = new Role[roleNames.length];

        minecord.log(ANSI_GREEN + "Roles reloaded: " + String.join(", ", roleNames) + ANSI_RESET);

        parseConfig();
        if (doListeners) initListeners();
        refreshCache(); // Refresh cache after reload
    }

    private void initListeners() {
        roleAddListener = new RoleAddListener(roles);
        roleRemoveListener = new RoleRemoveListener(roles);
        api.addListener(roleAddListener);
        api.addListener(roleRemoveListener);

        api.addListener((InteractionCreateListener) event -> {
            try {
                if (event.getInteraction() instanceof SlashCommandInteractionImpl) {
                    SlashCommandInteractionImpl slashCommandInteraction = (SlashCommandInteractionImpl) event.getInteraction();
                    //minecord.log("Received slash command interaction event: " + ANSI_GREEN + slashCommandInteraction.toString() + ANSI_RESET);
                    //minecord.log(ANSI_GREEN + "You can ignore this." + ANSI_YELLOW + " This is not an error." + ANSI_RESET);
                    handleSlashCommand(slashCommandInteraction);
                } else if (event.getInteraction() instanceof ButtonInteractionImpl) {
                    ButtonInteractionImpl buttonInteraction = (ButtonInteractionImpl) event.getInteraction();
                    //minecord.log("Received button interaction event: " + ANSI_GREEN + buttonInteraction.toString() + ANSI_RESET);
                    //minecord.log(ANSI_GREEN + "You can ignore this." + ANSI_YELLOW + " This is not an error." + ANSI_RESET);
                    handleButtonInteraction(buttonInteraction);
                } else if (event.getInteraction() instanceof ApplicationCommandInteraction) {
                    ApplicationCommandInteraction applicationCommandInteraction = (ApplicationCommandInteraction) event.getInteraction();
                    //minecord.log("Received application command interaction: " + ANSI_GREEN + applicationCommandInteraction.toString() + ANSI_RESET);
                    //minecord.log(ANSI_GREEN + "You can ignore this." + ANSI_YELLOW + " This is not an error." + ANSI_RESET);
                    handleApplicationCommand(applicationCommandInteraction);
                } else {
                    //minecord.log(ANSI_YELLOW + "Unhandled interaction type: " + event.getClass().getName());
                    //minecord.log(ANSI_GREEN + "You can ignore this." + ANSI_YELLOW + " This is not an error." + ANSI_RESET);
                }
            } catch (Exception e) {
                // Log detailed error information
                minecord.error(ANSI_RED + "Error handling interaction: " + e.getMessage() + ANSI_RESET);
                e.printStackTrace();
            }
        });

        // Register a listener for Message Update events
        api.addListener(new MessageEditListener() {
            @Override
            public void onMessageEdit(MessageEditEvent event) {
                try {

                    //minecord.log("Message updated in channel ID: " + event.getChannel().getIdAsString() + ", Message ID: " + event.getMessage().getIdAsString());
                } catch (Exception e) {
                    minecord.error(ANSI_RED + "Error handling message update: " + e.getMessage() + ANSI_RESET);
                    e.printStackTrace();
                }
            }
        });

        minecord.log(ANSI_GREEN + "Discord listeners loaded!" + ANSI_RESET);
    }

    private void handleApplicationCommand(ApplicationCommandInteraction applicationCommandInteraction) {
    }

    private void handleSlashCommand(SlashCommandInteractionImpl slashCommandInteraction) {
    }

    private void handleButtonInteraction(ButtonInteractionImpl buttonInteraction) {
    }

    private void parseConfig() {
        if (minecord.botToken == null) {
            return;
        }

        try {
            api = new DiscordApiBuilder().setToken(minecord.botToken).setAllIntents().login().join();
            doListeners = true;
            minecord.log(ANSI_GREEN + "Connected to " + api.getYourself().getName() + "!" + ANSI_RESET);
        } catch (Exception e) {
            minecord.warn(ANSI_YELLOW + "Could not connect to API! Please enter a valid Bot Token in config.yml and reload the plugin." + ANSI_RESET);
            minecord.warn(ANSI_YELLOW + "If the bot-token is valid, please file an issue on our GitHub." + ANSI_RESET);
        }

        try {
            discordServer = api.getServerById(minecord.serverID).orElseThrow(() -> new NoSuchElementException("Server not found!"));
            minecord.log(ANSI_GREEN + "Connected to " + discordServer.getName() + " Discord server!" + ANSI_RESET);
        } catch (NoSuchElementException e) {
            minecord.warn(ANSI_YELLOW + "Server not found! Please enter a valid Server ID in config.yml and reload the plugin." + ANSI_RESET);
        }

        try {
            for (int i = 0; i < roleNames.length; i++) {
                final String roleName = roleNames[i];
                roles[i] = api.getRoleById(roleAndID.get(roleName)).orElseThrow(() -> new NoSuchElementException("Role not found: " + roleName));
                minecord.log(ANSI_GREEN + "Role " + roles[i].getName() + " loaded!" + ANSI_RESET);
            }
        } catch (NoSuchElementException e) {
            minecord.warn(ANSI_YELLOW + "One or more roles not found! Please enter valid Role ID's in the config.yml and reload the plugin." + ANSI_RESET);
        }

        if (minecord.useChatStream) {
            try {
                chatStreamChannel = api.getTextChannelById(minecord.chatStreamID).orElseThrow(() -> new NoSuchElementException("Chat stream channel not found!"));
                discordMessageListener = new DiscordMessageListener();
                api.addListener(discordMessageListener);
            } catch (NoSuchElementException e) {
                minecord.warn(ANSI_YELLOW + "The specified Chat Stream Channel cannot be found! Please make sure the channel ID is valid in the config.yml and the channel exists, then reload the plugin." + ANSI_RESET);
            }
        }

        // Refresh cache immediately after initialization
        refreshCache();
    }

    private void refreshCache() {
        try {
            int totalLinkedUsers = 0;
            roleCounts.clear();

            for (String roleName : roleNames) {
                boolean roleFound = false;
                for (Role role : roles) {
                    if (role.getName().equalsIgnoreCase(roleName)) {
                        role.getUsers(); // Refresh role's user cache
                        roleCounts.put(roleName, role.getUsers().size());
                        roleFound = true;
                        break;
                    }
                }
                if (!roleFound) {
                    minecord.warn(ANSI_YELLOW + "Role not found during cache refresh: " + roleName + ANSI_RESET);
                }
            }

            totalLinkedUsers = db.getAllLinkedUsers().size();

            for (Map.Entry<String, Integer> entry : roleCounts.entrySet()) {
                minecord.log(ANSI_GREEN + "Role " + entry.getKey() + " loaded with " + entry.getValue() + " users." + ANSI_RESET);
            }

            minecord.log(ANSI_GREEN + "Total linked: " + ANSI_RED + totalLinkedUsers + ANSI_RESET);
            minecord.log(ANSI_GREEN + "Cache refreshed successfully." + ANSI_RESET);

        } catch (Exception e) {
            minecord.error(ANSI_RED + "Error refreshing cache: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }
    }

    public boolean retroLinkSingle(Player player, String discriminatedName, String roleName) {
        try {
            Optional<User> userOpt = api.getCachedUserByDiscriminatedName(discriminatedName);
            if (!userOpt.isPresent()) {
                minecord.error(ANSI_RED + "User not found: " + discriminatedName + ANSI_RESET);
                minecord.sendMessage(player, ChatColor.RED + "User not found: " + discriminatedName);
                return false;
            }
            User user = userOpt.get();

            Optional<Role> roleOpt = discordServer.getRoleById(roleAndID.get(roleName));
            if (!roleOpt.isPresent()) {
                //minecord.error(ANSI_RED + "Role not found: " + roleName + ANSI_RESET); # Activate only for debugging.
                //minecord.error(ANSI_RED + "Note that role names are case-sensitive." + ANSI_RESET); # Activate only for debugging.
                minecord.sendMessage(player, ChatColor.RED + "Role not found: " + roleName);
                minecord.sendMessage(player, ChatColor.RED + "Note that role names are case-sensitive.");
                return false;
            }
            Role role = roleOpt.get();

            if (db.doesEntryExist(user.getId())) {
                minecord.sendMessage(player, ChatColor.RED + "User is already linked.");
                return false;
            }
            sendRoleAddMessage(role, user);
            return true;
        } catch (NoSuchElementException e) {
            minecord.error(ANSI_RED + "Error adding user to role: " + discriminatedName + ". Stack Trace:" + ANSI_RESET);
            minecord.error(ANSI_RED + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
            minecord.sendMessage(player, ChatColor.RED + "Error adding user to role: " + discriminatedName);
            return false;
        } catch (Exception e) {
            minecord.error(ANSI_RED + "General error: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
            minecord.sendMessage(player, ChatColor.RED + "General error: " + e.getMessage());
            return false;
        }
    }

    private void sendRoleAddMessage(Role role, User user) {
        boolean messageSent = false;
        int attempt = 0;

        while (!messageSent && attempt < MAX_RETRIES) {
            try {
                new MessageBuilder()
                        .append("Hmmm! :thinking: Am observat ca ai boostat server-ul nostru de discord! :rocket: ")
                        .append("\nEsti inregistrat pe server-ul nostru de minecraft? *(mc-1st.ro)* Raspunde scriind \"DA\" sau \"NU\".")
                        .send(user).thenAccept(msg -> {
                            pmChannel = msg.getChannel();
                            user.addUserAttachableListener(new DMListener(role, pmChannel));
                        }).exceptionally(ExceptionLogger.get());
                messageSent = true;
            } catch (Exception e) {
                attempt++;
                minecord.error(ANSI_RED + "Error sending message to user: " + user.getDiscriminatedName() + ". Attempt " + attempt + ". Stack Trace:" + ANSI_RESET);
                minecord.error(ANSI_RED + e.getMessage() + ANSI_RESET);
                if (attempt >= MAX_RETRIES) {
                    minecord.error(ANSI_RED + "Failed to send message after " + MAX_RETRIES + " attempts." + ANSI_RESET);
                    break;
                }
                e.printStackTrace();
            }
        }
    }

    public void retroLink() {
        int users = 0;
        for (Role role : roles) {
            User[] usersInRole = new User[role.getUsers().size()];
            usersInRole = role.getUsers().toArray(usersInRole);
            if (usersInRole.length == 0) {
                minecord.warn(ANSI_YELLOW + "No users in " + role.getName() + " role!" + ANSI_RESET);
                return;
            }
            users = getUsers(users, role, usersInRole);
        }
        minecord.log(ANSI_GREEN + "Total: " + ANSI_RED + users + ANSI_RESET);
    }

    private int getUsers(int users, Role role, User[] usersInRole) {
        for (User user : usersInRole) {
            if (db.doesEntryExist(user.getId())) break;
            sendRoleAddMessage(role, user);
            users++;
        }
        minecord.log(ANSI_GREEN + role.getName() + " : " + users + ANSI_RESET);

        return users;
    }

    private void forceReconnect() {
        if (api != null) {
            try {
                minecord.log(ANSI_GREEN + "Forcing reconnect to Discord..." + ANSI_RESET);
                api.disconnect().join();
                api = new DiscordApiBuilder().setToken(minecord.botToken).setAllIntents().login().join();
                parseConfig();
                initListeners();
                minecord.log(ANSI_GREEN + "Forced reconnection to Discord successful." + ANSI_RESET);
            } catch (Exception e) {
                minecord.error(ANSI_RED + "Error forcing reconnect to Discord: " + e.getMessage() + ANSI_RESET);
                e.printStackTrace();
            }
        }
    }

    private void scheduleReconnectCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!isConnected()) {
                minecord.error(ANSI_RED + "Detected disconnected state. Attempting to reconnect..." + ANSI_RESET);
                forceReconnect();
            }
        }, 0, 5, TimeUnit.MINUTES); // Adjust the interval as needed
    }

    private boolean isConnected() {
        try {
            return api != null && !api.getServers().isEmpty();
        } catch (Exception e) {
            minecord.error(ANSI_RED + "Error checking connection status: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
            return false;
        }
    }
}
