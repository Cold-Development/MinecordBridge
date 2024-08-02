package org.padrewin.minecordbridge;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.luckperms.api.LuckPerms;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.javacord.api.DiscordApi;
import org.padrewin.minecordbridge.commands.MCBCommand;
import org.padrewin.minecordbridge.commands.tabcomplete.MCBTabComplete;
import org.padrewin.minecordbridge.database.Database;
import org.padrewin.minecordbridge.javacord.JavacordHelper;
import org.padrewin.minecordbridge.lib.LibrarySetup;
import org.padrewin.minecordbridge.listeners.minecraft.ChatListener;
import org.padrewin.minecordbridge.listeners.minecraft.LoginListener;
import org.padrewin.minecordbridge.listeners.minecraft.LogoutListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MinecordBridge extends JavaPlugin {

    public FileConfiguration config;
    public File customConfigFile;
    public Plugin permissionsPlugin = null;
    public PluginManager pluginManager;
    private static Database db;
    public static String[] versions = new String[2];
    public boolean debugMode = false;
    public boolean usePex = false;
    public boolean useLuckPerms = false;
    public boolean changeNickOnLink;
    public String botToken;
    public String serverID;
    public JavacordHelper js;
    public LuckPerms lp;
    public String[] roleNames;
    public HashMap<String, String> roleAndID = new HashMap<>(64);
    public HashMap<String, String[]> addCommands = new HashMap<>(144);
    public HashMap<String, String[]> removeCommands = new HashMap<>(144);
    public String chatStreamID;
    public String chatStreamMessageFormat;
    public boolean useChatStream;
    private List<String> rolesParsed = new ArrayList<>();
    public String pluginTag;

    private DiscordApi discordApi;
    // ANSI escape codes for colors
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";

    private FileConfiguration messagesConfig = null;
    private File messagesFile = null;

    public static MinecordBridge getPlugin() {
        return getPlugin(MinecordBridge.class);
    }

    @Override
    public void onEnable() {

        String name = getDescription().getName();
        getLogger().info("");
        getLogger().info("  ____ ___  _     ____  ");
        getLogger().info(" / ___/ _ \\| |   |  _ \\ ");
        getLogger().info("| |  | | | | |   | | | |");
        getLogger().info("| |__| |_| | |___| |_| |");
        getLogger().info(" \\____\\___/|_____|____/");
        getLogger().info("    " + name + " v" + getDescription().getVersion());
        getLogger().info("    Author(s): " + (String)getDescription().getAuthors().get(0));
        getLogger().info("    (c) Cold Development. All rights reserved.");
        getLogger().info("");

        /* Load Dependencies */
        LibrarySetup librarySetup = new LibrarySetup();
        librarySetup.loadLibraries();

        /* Load and Initiate Configs */
        try {
            reloadCustomConfig();
            config = getCustomConfig();
            saveCustomConfig();
        } catch (Exception e) {
            error("Error setting up the config! Contact the developer if you cannot fix this issue. Stack Trace:");
            error(e.getMessage());
        }

        /* Load the Database */
        try {
            db = new Database("minecord.sqlite.db");
            log(ANSI_GREEN + "Database Found! Path is " + ANSI_YELLOW + db.getDbPath() + ANSI_RESET);
        } catch (SQLException e) {
            error("Error setting up database! Is there permissions issue preventing the database file creation?");
            error("Exception Message:" + e.getMessage());
            error("SQL State: " + e.getSQLState());
        }

        /* Config Parsing */
        if (parseConfig()) {
            parseRoles();
            initChatStream();
            js = new JavacordHelper(roleNames);
            initListeners();
        } else {
            error("Config not properly configured! Plugin will not function!");
        }

        /* Get the Plugin manager for finding other permissions plugins */
        pluginManager = getServer().getPluginManager();
        permissionsPlugin = getPermissionsPlugin(pluginManager);

        /* Commands */
        try {
            Objects.requireNonNull(this.getCommand("minecord")).setExecutor(new MCBCommand());
            Objects.requireNonNull(this.getCommand("minecord")).setTabCompleter(new MCBTabComplete());
        } catch (NullPointerException e) {
            error("Error setting up commands! Contact the developer if you cannot fix this issue. Stack Trace:");
            error(e.getMessage());
        }

        if (useChatStream) {
            ChatListener.sendServerStartMessage();
        }

        // Load messages.yml
        saveDefaultMessagesConfig();
        loadMessagesConfig();
    }

    @Override
    public void onDisable() {
        if (useChatStream) {
            ChatListener.sendServerCloseMessage();
        }

        if (js != null) {
            js.disableAPI();
            getLogger().info("API closed.");
        }

        // Unregister all listeners
        HandlerList.unregisterAll(this);

        // Close the database connection
        if (db != null) {
            db.close(); // Remove try-catch if SQLException is not thrown

            // Close the Discord API connection gracefully
            if (discordApi != null) {
                discordApi.disconnect().join();
                getLogger().info("Discord API closed.");
            }
        }
    }

    public void reload() {
        reloadCustomConfig();
        config = getCustomConfig();
        saveCustomConfig();

        loadMessagesConfig();

        PlayerJoinEvent.getHandlerList().unregister(this);
        if (useChatStream) {
            PlayerQuitEvent.getHandlerList().unregister(this);
            AsyncChatEvent.getHandlerList().unregister(this);
        }

        /* Reload database if it's gone */
        try {
            if (!db.testConnection()) {
                if (db.getDbPath().isEmpty() || db.getDbPath().isBlank() || db.getDbPath() == null) {
                    db = new Database("minecord.sqlite.db");
                }
            }
        } catch (SQLException e) {
            error("Error setting up database! Is there permissions issue preventing the database file creation? View the following error message:");
            error("Error Message: " + e.getMessage());
            error("SQL State: " + e.getSQLState());
        }

        if (parseConfig()) {
            parseRoles();
            initListeners();
            initChatStream();
        } else {
            error("Config not properly configured! Plugin will not function properly!");
            return;
        }

        if (js == null) {
            js = new JavacordHelper(roleNames);
        } else {
            js.reload(); // Ensure roles are reloaded in JavacordHelper
        }
    }

    public void initListeners() {
        try {
            new UpdateChecker(this, 118054).getVersion(version -> {
                // Initializes Login Listener when no Updates
                if (compareVersions(this.getPluginMeta().getVersion(), version) < 0) {
                    versions[0] = version;
                    versions[1] = this.getPluginMeta().getVersion();
                    getServer().getPluginManager().registerEvents(new LoginListener(true, versions), this);
                } else {
                    getServer().getPluginManager().registerEvents(new LoginListener(false, versions), this);
                }
            });
        } catch (Exception e) {
            error("Error initializing Update Checker! Contact the developer if you cannot fix this issue. Stack Trace:");
            error(e.getMessage());
        }
        log("Minecraft listeners loaded!");
    }

    public boolean parseConfig() {
        try {
            botToken = getConfigString("bot-token");
            if (getConfigString("bot-token").equalsIgnoreCase("BOTTOKEN") || getConfigString("bot-token").equalsIgnoreCase("")) throw new Exception();
        } catch (Exception e) {
            saveDefaultConfig();
            warn("Invalid Bot Token! Please enter a valid Bot Token in config.yml and reload the plugin.");
            return false;
        }

        try {
            serverID = getConfigString("server-id");
            if (getConfigString("server-id").equalsIgnoreCase("000000000000000000") || getConfigString("server-id").equalsIgnoreCase("")) throw new Exception();
            log("Discord server found!");
        } catch (Exception e) {
            saveDefaultConfig();
            warn("Invalid server ID! Please enter a valid server ID in config.yml and reload the plugin.");
            return false;
        }

        // Load plugin-tag from config
        pluginTag = applyHexColors(getConfigString("plugin-tag"));
        if (pluginTag == null || pluginTag.isEmpty()) {
            pluginTag = "&8「&#6E00A5M&#7914B2i&#8427BFn&#8F3BCCe&#9A4FD8c&#A563E5o&#B076F2r&#BB8AFFd&8」&7»&f "; // Default value if not set in config
        } else {
            pluginTag = applyHexColors(pluginTag); // Apply hex colors if pluginTag is set in config
        }

        changeNickOnLink = getConfigBool("change-nickname-on-link");

        log("Config loaded!");
        return true;
    }

    public Plugin getPermissionsPlugin(PluginManager pluginManager) {
        try {
            permissionsPlugin = pluginManager.getPlugin("PermissionsEx");
            assert permissionsPlugin != null;
            if (permissionsPlugin.isEnabled() && getConfigBool("chatstream-use-permission-groups")) {
                usePex = true;
                useLuckPerms = false;
                log("PermissionsEx Detected! Hooking Permissions");
            }
        } catch (AssertionError | NullPointerException e) {
            try {
                permissionsPlugin = pluginManager.getPlugin("LuckPerms");
                assert permissionsPlugin != null;
                if (permissionsPlugin.isEnabled() && getConfigBool("chatstream-use-permission-groups")) {
                    RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
                    if (provider != null) {
                        lp = provider.getProvider();
                    }
                    useLuckPerms = true;
                    usePex = false;
                    log("LuckPerms detected! Hooking permissions");
                }
            } catch (AssertionError | NullPointerException f) {
                log("No permissions plugin found!");
            }
        }
        return permissionsPlugin;
    }

    private void parseRoles() {
        try {
            // Clear existing role data
            addCommands.clear();
            removeCommands.clear();
            roleAndID.clear();

            roleNames = config.getStringList("roles").toArray(new String[0]);

            for (String roleName : roleNames) {
                String[] tempAdd = config.getStringList(roleName + ".add-commands").toArray(new String[0]);
                addCommands.put(roleName, tempAdd);

                String[] tempRemove = config.getStringList(roleName + ".remove-commands").toArray(new String[0]);
                removeCommands.put(roleName, tempRemove);

                roleAndID.put(roleName, Objects.requireNonNull(config.getConfigurationSection(roleName)).getString("role-id"));
            }

            // Actualizăm lista de roluri procesate
            rolesParsed = Arrays.asList(roleNames);

            //log(ANSI_GREEN + "Roles parsed: " + String.join(", ", roleNames) + ANSI_RESET);
        } catch (Exception e) {
            saveDefaultConfig();
            error("Error parsing roles! Make sure the config.yml is correct and reload the plugin. Stack Trace:");
            error(e.getMessage());
        }
    }

    // Getter pentru rolurile procesate
    public List<String> getRolesParsed() {
        return rolesParsed;
    }

    public void initChatStream() {
        useChatStream = getConfigBool("enable-chatstream");
        if (!useChatStream) return;
        log("ChatStream enabled! Loading necessary config items");
        try {
            chatStreamID = getConfigString("chatstream-channel");
            chatStreamMessageFormat = applyHexColors(getConfigString("chatstream-message-format"));
        } catch (Exception e) {
            saveDefaultConfig();
            warn("Invalid channel ID for ChatStream! Please enter a valid channel ID in the config.yml and reload the plugin.");
        }
        getServer().getPluginManager().registerEvents(new LogoutListener(), this);
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
    }

    public String getConfigString(String entryName) {
        return config.getString(entryName);
    }

    public boolean getConfigBool(String entryName) {
        return config.getBoolean(entryName);
    }

    public static Database getDatabase() {
        return db;
    }

    public void reloadCustomConfig() {
        saveDefaultConfig();
        config = YamlConfiguration.loadConfiguration(customConfigFile);

        // Look for defaults in the jar
        Reader defConfigStream = null;
        try {
            defConfigStream = new InputStreamReader(Objects.requireNonNull(this.getResource("config.yml")), StandardCharsets.UTF_8);
        } catch (Exception e) {
            error("Error loading default config! Contact the developer if you cannot fix this issue. Stack Trace:");
            error(e.getMessage());
        }
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
            config.setDefaults(defConfig);
        }
    }

    public FileConfiguration getCustomConfig() {
        if (config == null) {
            reloadCustomConfig();
        }
        return config;
    }

    public void saveCustomConfig() {
        if (config == null || customConfigFile == null) {
            return;
        }
        try {
            getCustomConfig().save(customConfigFile);
        } catch (IOException ex) {
            getLogger().log(Level.SEVERE, "Could not save config to " + customConfigFile, ex);
        }
    }

    @Override
    public void saveDefaultConfig() {
        if (customConfigFile == null) {
            customConfigFile = new File(getDataFolder(), "config.yml");
        }
        if (!customConfigFile.exists()) {
            this.saveResource("config.yml", false);
        }
    }

    public void saveDefaultMessagesConfig() {
        if (messagesFile == null) {
            messagesFile = new File(getDataFolder(), "messages.yml");
        }
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
    }

    public void loadMessagesConfig() {
        if (messagesFile == null) {
            messagesFile = new File(getDataFolder(), "messages.yml");
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

    }

    public String getMessage(String key) {
        if (messagesConfig == null) {
            loadMessagesConfig();
        }

        String message = messagesConfig.getString(key);
        if (message == null) {
            return "No message found: " + key;
        }

        return message;
    }

    public void log(String message) {
        this.getLogger().log(Level.INFO, message);
    }

    public void warn(String message) {
        this.getLogger().log(Level.WARNING, message);
    }

    public void error(String message) {
        this.getLogger().log(Level.SEVERE, message);
    }

    public void debug(String message) {
        this.getLogger().log(Level.FINE, message);
    }

    public void sendMessage(CommandSender sender, String messageKey) {
        String message = getMessage(messageKey);
        if (sender instanceof Player player) {
            player.sendMessage(applyHexColors(pluginTag) + applyHexColors(message));
        } else {
            log(message);
        }
    }

    // Method to compare two versions numerically
    private int compareVersions(String installedVersion, String newestVersion) {
        String[] installedParts = installedVersion.split("\\.");
        String[] newestParts = newestVersion.split("\\.");

        int minLength = Math.min(installedParts.length, newestParts.length);
        for (int i = 0; i < minLength; i++) {
            int installedPart = Integer.parseInt(installedParts[i]);
            int newestPart = Integer.parseInt(newestParts[i]);
            if (installedPart < newestPart) {
                return -1; // installed version is older
            } else if (installedPart > newestPart) {
                return 1; // installed version is newer
            }
        }

        // If we reach here, versions are equal up to minLength
        // So, if one version has more parts, it is considered newer
        if (installedParts.length < newestParts.length) {
            return -1; // installed version is older
        } else if (installedParts.length > newestParts.length) {
            return 1; // installed version is newer
        } else {
            return 0; // versions are exactly the same
        }
    }

    public String applyHexColors(String message) {
        Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length());

        while (matcher.find()) {
            String hexColor = matcher.group(1);
            String replacement = ChatColor.of("#" + hexColor).toString();
            matcher.appendReplacement(buffer, replacement);
        }

        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
