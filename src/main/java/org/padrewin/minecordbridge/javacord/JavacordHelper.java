package org.padrewin.minecordbridge.javacord;

import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.padrewin.minecordbridge.listeners.discord.DiscordMessageListener;
import org.padrewin.minecordbridge.listeners.discord.DMListener;
import org.padrewin.minecordbridge.listeners.discord.RoleAddListener;
import org.padrewin.minecordbridge.listeners.discord.RoleRemoveListener;
import org.bukkit.entity.Player;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.util.HashMap;
import java.util.NoSuchElementException;

public class JavacordHelper {

    public DiscordApi api;
    public Server discordServer;
    public RoleAddListener roleAddListener;
    public RoleRemoveListener roleRemoveListener;
    public DiscordMessageListener discordMessageListener;
    public Role[] roles;
    public TextChannel chatStreamChannel;
    private final MinecordBridge minecord = MinecordBridge.getPlugin();
    private final String[] roleNames;
    private final HashMap<String, String> roleAndID;
    private boolean doListeners = false;
    private TextChannel pmChannel;
    private final Database db;

    public JavacordHelper(String[] roleNames) {
        this.roleNames = roleNames;
        roles = new Role[roleNames.length];
        roleAndID = minecord.roleAndID;
        parseConfig();
        if (doListeners) initListeners();
        db = MinecordBridge.getDatabase();
    }

    public void disableAPI() {
        try {
            if (api != null) {
                minecord.log("Attempting to disconnect from API...");
                api.disconnect();
                minecord.log("Successfully disconnected from API.");
            }
            api = null;
        } catch (Exception e) {
            minecord.error("Error disconnecting from API! Contact padrewin for assistance.");
        }
    }

    public void reload() {
        api.removeListener(roleAddListener);
        api.removeListener(roleRemoveListener);
        roleAddListener = null;
        roleRemoveListener = null;
        if (minecord.useChatStream) {
            api.removeListener(discordMessageListener);
            discordMessageListener = null;
        }

        disableAPI();
        parseConfig();
        if (doListeners) initListeners();
    }

    private void initListeners() {
        roleAddListener = new RoleAddListener(roles);
        roleRemoveListener = new RoleRemoveListener(roles);
        api.addListener(roleAddListener);
        api.addListener(roleRemoveListener);
        minecord.log("Discord listener loaded!");
    }

    private void parseConfig() {
        if (minecord.botToken == null) {
            return;
        }

        try {
            api = new DiscordApiBuilder().setToken(minecord.botToken).setAllIntents().login().join();
            doListeners = true;
            minecord.log("Connected to " + api.getYourself().getName() + ".");
        } catch (NullPointerException e) {
            minecord.warn("Can't connect to API! Use a valid BOT_TOKEN in config.yml and restart the plugin.");
            minecord.warn("If your BOT_TOKEN is valid, contact the developer.");
        }

        try {
            if (api.getServerById(minecord.serverID).isPresent())
                discordServer = api.getServerById(minecord.serverID).get();
            minecord.log("Connected to " + discordServer.getName() + " Discord server.");
        } catch (Exception e) {
            minecord.warn("Discord server not found! Use a valid SERVER_ID in config.yml and restart the plugin.");
        }

        try {
            for (int i = 0; i < roleNames.length; i++) {
                if (api.getRoleById(roleAndID.get(roleNames[i])).isPresent())
                    roles[i] = api.getRoleById(roleAndID.get(roleNames[i])).get();
                minecord.log("Discord " + roles[i].getName() + " role was loaded!");
            }
        } catch (Exception e) {
            minecord.warn("One or more roles couldn't be found! Use a valid ROLE_ID in the config.yml and restart the plugin.");
        }

        if (minecord.useChatStream) {
            try {
                if (api.getTextChannelById(minecord.chatStreamID).isPresent()) {
                    chatStreamChannel = api.getTextChannelById(minecord.chatStreamID).get();
                }
                discordMessageListener = new DiscordMessageListener();
                api.addListener(discordMessageListener);
            } catch (Exception e) {
                minecord.warn("Specified Chat Stream channel can't be found! Make sure that CHANNEL_ID is valid in the config.yml and restart the plugin.");
            }
        }

    }

    public void retroLink(Player player) {
        int users = 0;
        for (Role role : roles) {
            User[] usersInRole = new User[role.getUsers().size()];
            usersInRole = role.getUsers().toArray(usersInRole);
            if (usersInRole.length == 0) {
                minecord.warn("No users found in " + role.getName() + " role!");
                minecord.sendMessage(player,"No users found in " + role.getName() + " role!");
                break;
            }
            users = getUsers(users, role, usersInRole);
            minecord.sendMessage(player, role.getName() + ": " + users);
        }
        minecord.log("Total: " + users);
        minecord.sendMessage(player,"Total: " + users);
    }

    public void retroLinkSingle(String discriminatedName, String roleName) {
        try {
            Role role = discordServer.getRoleById(roleAndID.get(roleName)).get();
            User user = api.getCachedUserByDiscriminatedName(discriminatedName).get();
            if (db.doesEntryExist(user.getId())) return;
            sendRoleAddMessage(role, user);
        } catch (NoSuchElementException e) {
            minecord.error("Error in adding role to the user: " + discriminatedName + ". Stack Trace:");
            minecord.error(e.getMessage());
        }
    }

    private void sendRoleAddMessage(Role role, User user) {
        try {
            if (api.getServerById(minecord.serverID).isPresent()) {
                new MessageBuilder()
                        .append("Hmmm! :thinking: Am observat ca ai boostat server-ul nostru de discord! :rocket: ")
                        .append("\nEsti inregistrat pe server-ul nostru de minecraft? *(mc-1st.ro)* Raspunde scriind \"DA\" sau \"NU\".")
                        .send(user).thenAccept(msg -> pmChannel = msg.getChannel()).join();
            }
        } catch (Exception e) {
            minecord.error("Error sending message to the user: " + user.getDiscriminatedName() + ". Stack Trace:");
            minecord.error(e.getMessage());
        }
        user.addUserAttachableListener(new DMListener(role, pmChannel));
    }

    public void retroLink() {
        int users = 0;
        for (Role role : roles) {
            User[] usersInRole = new User[role.getUsers().size()];
            usersInRole = role.getUsers().toArray(usersInRole);
            if (usersInRole.length == 0) {
                minecord.warn("No users found in " + role.getName() + " role!");
                return;
            }
            users = getUsers(users, role, usersInRole);
        }
        minecord.log("Total: " + users);
    }

    private int getUsers(int users, Role role, User[] usersInRole) {
        for (User user : usersInRole) {
            if (db.doesEntryExist(user.getId())) break;
            sendRoleAddMessage(role, user);
            users++;
        }
        minecord.log(role.getName() + " : " + users);

        return users;
    }

}