package org.padrewin.minecordbridge;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.padrewin.minecordbridge.commands.MCBCommand;
import org.padrewin.minecordbridge.commands.tabcomplete.MCBTabComplete;
import org.padrewin.minecordbridge.database.Database;
import org.padrewin.minecordbridge.javacord.JavacordHelper;
import org.padrewin.minecordbridge.lib.LibrarySetup;
import org.padrewin.minecordbridge.listeners.minecraft.ChatListener;
import org.padrewin.minecordbridge.listeners.minecraft.LoginListener;
import org.padrewin.minecordbridge.listeners.minecraft.LogoutListener;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Objects;
import java.util.logging.Level;

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

    public static MinecordBridge getPlugin() {
        return getPlugin(MinecordBridge.class);
    }

    @Override
    public void onEnable() {

        /* Load Dependencies */
        LibrarySetup librarySetup = new LibrarySetup();
        librarySetup.loadLibraries();

        /* Load and Initiate Configs */
        try {
            reloadCustomConfig();
            config = getCustomConfig();
            saveCustomConfig();
        } catch (Exception e) {
            error("Error setting up the config! Contact padrewin for assistance. Stack Trace:");
            error(e.getMessage());
        }

        /* Load the Database */
        try {
            db = new Database("minecord.sqlite.db");
            log("Database found! Path is " + db.getDbPath());
        } catch (SQLException e) {
            error("Error setting up database!");
            error("Exception message:" + e.getMessage());
            error("MySQL status: " + e.getSQLState());
        }

        /* Config Parsing */
        if (parseConfig()) {
            parseRoles();
            initChatStream();
            js = new JavacordHelper(roleNames);
            initListeners();
        } else {
            error("Invalid config! Please make sure you're setting up properly.");
        }

        /* Get the Plugin manager for finding other permissions plugins */
        pluginManager = getServer().getPluginManager();
        permissionsPlugin = getPermissionsPlugin(pluginManager);

        /* Commands */
        try {
            Objects.requireNonNull(this.getCommand("minecord")).setExecutor(new MCBCommand());
            Objects.requireNonNull(this.getCommand("minecord")).setTabCompleter(new MCBTabComplete());
        } catch (NullPointerException e) {
            error("Error setting up commands! Contact padrewin for assistance. Stack Trace:");
            error(e.getMessage());
        }

        if (useChatStream) {
            ChatListener.sendServerStartMessage();
        }

    }

    @Override
    public void onDisable() {
        if (useChatStream) {
            ChatListener.sendServerCloseMessage();
        }

        if (js != null) {
            js.disableAPI();
        }
    }

    public void reload() {
        reloadCustomConfig();
        config = getCustomConfig();
        saveCustomConfig();

        PlayerJoinEvent.getHandlerList().unregister(this);
        if (useChatStream) {
            PlayerQuitEvent.getHandlerList().unregister(this);
            AsyncChatEvent.getHandlerList().unregister(this);
        }

        /* Reload database if it's gone */
        try {
            if (!db.testConnection())
                if (db.getDbPath().isEmpty() || db.getDbPath().isBlank() || db.getDbPath() == null) new Database("minecordbridge.sqlite.db");
        } catch (SQLException e) {
            error("Error setting up database! Check error:");
            error("Error message: " + e.getMessage());
            error("MySQL status: " + e.getSQLState());
        }

        if (parseConfig()) {
            parseRoles();
            initListeners();
            initChatStream();
        } else {
            error("Config is invalid. Plugin won't function properly!");
            return;
        }

        if (js == null) {
            js = new JavacordHelper(roleNames);
        } else {
            js.reload();
        }

    }

    public void initListeners() {
        try {
            new org.padrewin.minecordbridge.UpdateChecker(this, 88409).getVersion(version -> {
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
            error("Error checking version update! Contact padrewin for assistance. Stack Trace:");
            error(e.getMessage());
        }
        log("Minecraft listener loaded!");
    }

    public boolean parseConfig() {
        try {
            botToken = getConfigString("bot-token");
            if (getConfigString("bot-token").equalsIgnoreCase("BOT_TOKEN") || getConfigString("bot-token").equalsIgnoreCase("")) throw new Exception();
        } catch (Exception e) {
            saveDefaultConfig();
            warn("Your BOT_TOKEN is invalid! Make sure you're using a valid BOT_TOKEN in config.yml and restart the plugin.");
            return false;
        }

        try {
            serverID = getConfigString("server-id");
            if (getConfigString("server-id").equalsIgnoreCase("SERVER_ID") || getConfigString("server-id").equalsIgnoreCase("")) throw new Exception();
            log("Discord server found!");
        } catch (Exception e) {
            saveDefaultConfig();
            warn("SERVER_ID invalid! Make sure you're using a valid SERVER_ID in config.yml and restart the plugin.");
            return false;
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
                log("PermissionsEx was found! Setting up the permissions..");
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
                    log("LuckPerms was found! Setting up the permissions..");
                }
            } catch (AssertionError | NullPointerException f) {
                log("Plugin permissions were not found!");
            }
        }
        return permissionsPlugin;
    }

    private void parseRoles() {
        try {
            roleNames = new String[config.getStringList("roles").size()];

            roleNames = config.getStringList("roles").toArray(roleNames);

            for (String roleName : roleNames) {
                String[] tempAdd = new String[config.getStringList(roleName + ".add-commands").size()];
                tempAdd = config.getStringList(roleName + ".add-commands").toArray(tempAdd);
                addCommands.put(roleName, tempAdd);

                String[] tempRemove = new String[config.getStringList(roleName + ".remove-commands").size()];
                tempRemove = config.getStringList(roleName + ".remove-commands").toArray(tempRemove);
                removeCommands.put(roleName, tempRemove);

                roleAndID.put(roleName, Objects.requireNonNull(config.getConfigurationSection(roleName)).getString("role-id"));
            }
        } catch (Exception e) {
            saveDefaultConfig();
            error("Error fetching roles! Make sure that config.yml is properly configured and restart the plugin. Stack Trace:");
        }
    }

    public void initChatStream() {
        useChatStream = getConfigBool("enable-chatstream");
        if (!useChatStream) return;
        log("ChatStream enabled! Loading necessary configurations..");
        try {
            chatStreamID = getConfigString("chatstream-channel");
            chatStreamMessageFormat = replaceColors(getConfigString("chatstream-message-format"));
        } catch (Exception e) {
            saveDefaultConfig();
            warn("Invalid CHANNEL_ID for ChatStream! Use a valid CHANNEL_ID in the config.yml and restart the plugin.");
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
            error("Error loading default config! Contact padrewin for assistance. Stack Trace:");
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
            getLogger().log(Level.SEVERE, "Error saving config in " + customConfigFile, ex);
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

    public void sendMessage(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            player.sendMessage("§8「§c1st§8」§7» §f" + replaceColors(message));
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

    /**
     * The escape sequence for minecraft special chat codes
     */
    public static final char ESCAPE = '§';

    /**
     * Replace all the color codes (prepended with &) with the corresponding color code.
     * This uses raw char arrays, so it can be considered to be extremely fast.
     *
     * @param text the text to replace the color codes in
     * @return string with color codes replaced
     */
    public static String replaceColors(String text) {
        char[] chrarray = text.toCharArray();

        for (int index = 0; index < chrarray.length; index ++) {
            char chr = chrarray[index];

            // Ignore anything that we don't want
            if (chr != '&') {
                continue;
            }

            if ((index + 1) == chrarray.length) {
                // we are at the end of the array
                break;
            }

            // get the forward char
            char forward = chrarray[index + 1];

            // is it in range?
            if ((forward >= '0' && forward <= '9') || (forward >= 'a' && forward <= 'f') || (forward >= 'k' && forward <= 'r')) {
                // It is! Replace the char we are at now with the escape sequence
                chrarray[index] = ESCAPE;
            }
        }

        // Rebuild the string and return it
        return new String(chrarray);
    }
}