package org.padrewin.minecordbridge.javacord;

import org.bukkit.Bukkit;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageEditEvent;
import org.javacord.api.interaction.ApplicationCommandInteraction;
import org.javacord.api.listener.interaction.InteractionCreateListener;
import org.javacord.api.listener.message.MessageEditListener;
import org.javacord.core.interaction.ButtonInteractionImpl;
import org.javacord.core.interaction.SlashCommandInteractionImpl;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.padrewin.minecordbridge.listeners.discord.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JavacordHelper {

    public SlashCommandListener slashCommandListener;
    public DiscordApi api;
    public Server discordServer;
    public RoleAddListener roleAddListener;
    public RoleRemoveListener roleRemoveListener;
    public BoostTrackingListener boostTrackingListener;
    public DiscordMessageListener discordMessageListener;
    public Role[] roles;
    private Role boosterRole;
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

        scheduler.scheduleAtFixedRate(() -> {
            refreshCache();
        }, 0, 1, TimeUnit.MINUTES);

        if (api != null) {
            api.addReconnectListener(event -> minecord.log(ANSI_GREEN + "Reconnected to Discord." + ANSI_RESET));
        }

        scheduleReconnectCheck();
    }

    public void disableAPI() {
        disconnectApi();
        scheduler.shutdown();
    }

    private void disconnectApi() {
        try {
            if (api != null) {
                api.disconnect().join();
            }
            api = null;
        } catch (CompletionException e) {
            minecord.error(ANSI_RED + "Error disconnecting from API! Stack Trace:" + ANSI_RESET);
            minecord.error(ANSI_RED + e.getMessage() + ANSI_RESET);
        }
    }

    public void reload() {
        if (api != null) {
            if (roleAddListener != null) api.removeListener(roleAddListener);
            if (roleRemoveListener != null) api.removeListener(roleRemoveListener);
            if (boostTrackingListener != null) api.removeListener(boostTrackingListener);
            if (slashCommandListener != null) api.removeListener(slashCommandListener);
            if (minecord.useChatStream && discordMessageListener != null) {
                api.removeListener(discordMessageListener);
            }
        }
        roleAddListener = null;
        roleRemoveListener = null;
        boostTrackingListener = null;
        boosterRole = null;
        slashCommandListener = null;
        discordMessageListener = null;

        // The scheduler belongs to this helper and must survive /minecord reload;
        // otherwise periodic benefit reconciliation silently stops after reload.
        disconnectApi();

        roleNames = minecord.getRolesParsed().toArray(new String[0]);
        roleAndID = new HashMap<>(minecord.roleAndID);
        roles = new Role[roleNames.length];

        parseConfig();
        if (doListeners) initListeners();
        refreshCache();

        minecord.log(ANSI_GREEN + "Reload finished successfully." + ANSI_RESET);
    }

    private void initListeners() {
        roleAddListener = new RoleAddListener(roles);
        roleRemoveListener = new RoleRemoveListener(roles);
        api.addListener(roleAddListener);
        api.addListener(roleRemoveListener);

        if (minecord.boostTrackingEnabled && boosterRole != null) {
            boostTrackingListener = new BoostTrackingListener(boosterRole);
            api.addListener(boostTrackingListener);
        }

        // Slash command listener
        slashCommandListener = new SlashCommandListener(roles);
        api.addListener(slashCommandListener);

        // Register slash commands with Discord
        SlashCommandRegistrar registrar = new SlashCommandRegistrar();
        registrar.registerCommands(api);

        api.addListener((InteractionCreateListener) event -> {
            try {
                if (event.getInteraction() instanceof SlashCommandInteractionImpl slashCommandInteraction) {
                    handleSlashCommand(slashCommandInteraction);
                } else if (event.getInteraction() instanceof ButtonInteractionImpl buttonInteraction) {
                    handleButtonInteraction(buttonInteraction);
                } else if (event.getInteraction() instanceof ApplicationCommandInteraction applicationCommandInteraction) {
                    handleApplicationCommand(applicationCommandInteraction);
                }
            } catch (Exception e) {
                minecord.error(ANSI_RED + "Error handling interaction: " + e.getMessage() + ANSI_RESET);
                e.printStackTrace();
            }
        });

        api.addListener(new MessageEditListener() {
            @Override
            public void onMessageEdit(MessageEditEvent event) {
                try {
                    // placeholder
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
            doListeners = false;
            return;
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
            }
        } catch (NoSuchElementException e) {
            minecord.warn(ANSI_YELLOW + "One or more roles not found! Please enter valid Role ID's in the config.yml and reload the plugin." + ANSI_RESET);
        }

        if (minecord.boostTrackingEnabled) {
            try {
                boosterRole = api.getRoleById(minecord.boosterRoleId).orElseThrow(() -> new NoSuchElementException("Booster role not found!"));
            } catch (NoSuchElementException e) {
                minecord.warn(ANSI_YELLOW + "Boost tracking is enabled but the booster role ID is invalid! Please check config.yml." + ANSI_RESET);
                boosterRole = null;
            }
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
    }

    private void refreshCache() {
        try {
            int totalLinkedUsers = 0;
            roleCounts.clear();

            for (String roleName : roleNames) {
                boolean roleFound = false;
                for (Role role : roles) {
                    String roleId = roleAndID.get(roleName);
                    if (role != null && roleId != null && roleId.equals(String.valueOf(role.getId()))) {
                        role.getUsers();
                        roleCounts.put(roleName, role.getUsers().size());
                        roleFound = true;
                        break;
                    }
                }
                if (!roleFound) {
                    minecord.warn(ANSI_YELLOW + "Role not found during cache refresh: " + roleName + ANSI_RESET);
                }
            }

            checkLinkedAccounts();
            reconcileBoosters();

            totalLinkedUsers = db.getAllLinkedUsers().size();

            minecord.log(ANSI_GREEN + "Total linked: " + ANSI_RED + totalLinkedUsers + ANSI_RESET);
            minecord.log(ANSI_GREEN + "Cache refreshed successfully." + ANSI_RESET);

        } catch (Exception e) {
            minecord.error(ANSI_RED + "Error refreshing cache: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }
    }

    /**
     * Reconciles linked accounts after restarts or missed Discord events.
     * The role is saved when the account is linked, so each account is checked
     * against the same role that granted its Minecraft benefits.
     */
    private void checkLinkedAccounts() {
        if (api == null || discordServer == null) return;
        try {
            List<Database.LinkedAccount> linkedUsers = db.getAllLinkedAccounts();

            for (Database.LinkedAccount account : linkedUsers) {
                String playerName = account.username();
                String discordID = account.discordId();

                User user = api.getUserById(discordID).join();

                if (user != null) {
                    String roleName = account.roleName();
                    if (roleName == null || roleName.isBlank()) {
                        roleName = inferLegacyRoleName(user);
                        if (roleName != null) db.updateRoleName(Long.parseLong(discordID), roleName);
                    }

                    if (roleName == null) {
                        minecord.warn("Cannot determine the benefit role for legacy link " + discordID + ". It will not be removed automatically.");
                        continue;
                    }

                    String roleId = roleAndID.get(roleName);
                    if (roleId == null) {
                        minecord.warn("Configured role '" + roleName + "' for linked user " + discordID + " no longer exists. Keeping the link untouched.");
                        continue;
                    }

                    boolean stillHasRole = user.getRoles(discordServer).stream()
                            .anyMatch(role -> roleId.equals(String.valueOf(role.getId())));
                    if (!stillHasRole) {
                        minecord.log(ANSI_YELLOW + "User " + user.getDiscriminatedName() + " no longer has role " + roleName + ". Removing benefits." + ANSI_RESET);
                        removeMinecraftPerks(playerName, roleName);
                        db.removeLink(Long.parseLong(discordID));
                    }
                } else {
                    removeLegacyOrKnownAccount(account);
                }
            }
        } catch (Exception e) {
            minecord.error(ANSI_RED + "Error during linked-account check: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }
    }

    /**
     * Reconciles boost tracking after restarts. Members who already had the
     * Booster role before the feature was enabled (or before the bot was
     * online to catch the role-add event) start being tracked from now, since
     * Javacord cannot report their real boost start date. Members who lost
     * the role while the bot was offline have their record cleared.
     */
    private void reconcileBoosters() {
        if (!minecord.boostTrackingEnabled || boosterRole == null) return;

        try {
            Set<Long> currentBoosterIds = new HashSet<>();
            for (User user : boosterRole.getUsers()) {
                long discordId = user.getId();
                currentBoosterIds.add(discordId);
                if (!db.hasBoostRecord(discordId)) {
                    db.startBoosting(discordId, Instant.now().getEpochSecond());
                    minecord.log(ANSI_GREEN + "Discovered existing booster " + user.getDiscriminatedName() + "; boost tracking starts now." + ANSI_RESET);
                }
            }

            for (Long trackedId : db.getAllBoostingDiscordIds()) {
                if (!currentBoosterIds.contains(trackedId)) {
                    db.stopBoosting(trackedId);
                }
            }
        } catch (Exception e) {
            minecord.error(ANSI_RED + "Error reconciling boosters: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }
    }

    private String inferLegacyRoleName(User user) {
        for (String roleName : roleNames) {
            String roleId = roleAndID.get(roleName);
            if (roleId != null && user.getRoles(discordServer).stream()
                    .anyMatch(role -> roleId.equals(String.valueOf(role.getId())))) {
                return roleName;
            }
        }
        // A legacy row has no role information. With exactly one configured role
        // we can safely migrate it even after the user has lost that role.
        return roleNames.length == 1 ? roleNames[0] : null;
    }

    private void removeLegacyOrKnownAccount(Database.LinkedAccount account) {
        String roleName = account.roleName();
        if ((roleName == null || roleName.isBlank()) && roleNames.length == 1) roleName = roleNames[0];
        if (roleName == null || roleName.isBlank()) {
            minecord.warn("User with Discord ID " + account.discordId() + " is unavailable, but their legacy benefit role is unknown.");
            return;
        }
        minecord.log(ANSI_YELLOW + "User with Discord ID " + account.discordId() + " is not available. Removing benefits for role " + roleName + "." + ANSI_RESET);
        removeMinecraftPerks(account.username(), roleName);
        db.removeLink(Long.parseLong(account.discordId()));
    }

    private void removeMinecraftPerks(String playerName, String roleName) {
        UUID playerUUID = Bukkit.getOfflinePlayer(playerName).getUniqueId();

        if (!minecord.getConfig().contains(roleName)) {
            minecord.log(ANSI_RED + "Role '" + roleName + "' not found in config.yml." + ANSI_RESET);
            return;
        }

        List<String> removeCommands = minecord.getConfig().getStringList(roleName + ".remove-commands");

        if (removeCommands.isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTask(minecord, () -> {
            for (String command : removeCommands) {
                String processedCommand = command.replace("%user%", playerName).replace("%uuid%", playerUUID.toString());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
            }
        });
    }

    private void forceReconnect() {
        if (api != null) {
            try {
                minecord.log(ANSI_GREEN + "Forcing reconnect to Discord..." + ANSI_RESET);
                disconnectApi();
                parseConfig();
                if (doListeners) initListeners();
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
        }, 0, 5, TimeUnit.MINUTES);
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
