package org.padrewin.minecordbridge.database;

import org.padrewin.minecordbridge.MinecordBridge;
import org.bukkit.plugin.Plugin;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Database manager class with specific pull/push methods for a sqlite database
 * @author padrewin
 */

public class Database {

    private String dbPath;
    private Connection dbcon;
    private final MinecordBridge minecord;

    /**
     * Constructor; Builds a new database object
     * @param dbName The name of the database; should be user-supplied. If it exists, a connection will be made. If it does not exist, it will be created and initialized
     */
    public Database(String dbName) throws SQLException {
        minecord = MinecordBridge.getPlugin();
        Plugin plugin = MinecordBridge.getPlugin();
        dbPath = (plugin.getDataFolder() + "/" + dbName);
        dbPath = "jdbc:sqlite:" + dbPath;
        dbcon = DriverManager.getConnection(dbPath);

        // Check if the table exists and update its structure if necessary
        updateTableStructure("link",
                "CREATE TABLE IF NOT EXISTS link(minecraftid TEXT NOT NULL, discordid TEXT NOT NULL, username TEXT NOT NULL)");
    }

    /**
     * Accessor; Returns the SQL database path;
     * @return path of the SQLite DB
     */
    public String getDbPath() {
        return dbPath;
    }

    /**
     * Accessor; Returns the SQL database connection;
     * @return connection to the SQLite DB
     */
    public boolean testConnection() {
        try {
            PreparedStatement stmt = dbcon.prepareStatement("SELECT totalVotes FROM streaks");
            stmt.executeQuery();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Inserts a new link between a discord and minecraft account
     * @param discordID Discord User ID
     * @param username Minecraft Username
     * @param minecraftID Minecraft UUID
     */
    public void insertLink(long discordID, String username, UUID minecraftID) {
        try {
            PreparedStatement stmt = dbcon.prepareStatement("INSERT INTO link(minecraftid,discordid,username) VALUES (?,?,?)");
            stmt.setString(1, minecraftID.toString());
            stmt.setString(2, Long.toString(discordID));
            stmt.setString(3, username);
            stmt.execute();
        } catch (SQLException e) {
            minecord.error("Error inserting link into database! Stack Trace:");
            minecord.error(e.getMessage());
        }
    }

    public String getUsername(UUID minecraftID) {
        ResultSet rs;
        String username;
        try {
            PreparedStatement stmt = dbcon.prepareStatement("SELECT username FROM link WHERE minecraftid=?");
            stmt.setString(1, minecraftID.toString());
            rs = stmt.executeQuery();
            username = rs.getString("username");

            return username;

        } catch (SQLException e) {
            minecord.error("Error getting username from database! Stack Trace:");
            minecord.error(e.getMessage());
            return null;
        }

    }

    public void updateMinecraftUsername(String newUsername, UUID minecraftID) {
        try {
            PreparedStatement stmt = dbcon.prepareStatement("UPDATE link SET username=? WHERE minecraftid=?");
            stmt.setString(1, newUsername);
            stmt.setString(2, minecraftID.toString());

            stmt.execute();

        } catch (SQLException e) {
            minecord.error("Error updating username in database! Stack Trace:");
            minecord.error(e.getMessage());
        }
    }

    public boolean doesEntryExist(UUID minecraftID) {
        ResultSet rs;
        try {
            PreparedStatement stmt = dbcon.prepareStatement("SELECT minecraftid FROM link WHERE minecraftid=?");
            stmt.setString(1, minecraftID.toString());
            rs = stmt.executeQuery();

            return rs.next();
        } catch (SQLException e) {
            Objects.requireNonNull(minecord.getServer().getPlayer(minecraftID)).sendMessage("§§8「§c1st§8」§7»§f Account already linked!");
            return false;
        }
    }

    public String getUsername(long discordID) {
        ResultSet rs;
        try {
            PreparedStatement stmt = dbcon.prepareStatement("SELECT username FROM link WHERE discordid=?");
            stmt.setString(1, Long.toString(discordID));
            rs = stmt.executeQuery();

            return rs.getString("username");
        } catch (SQLException e) {
            minecord.error("Error getting username from database! Stack Trace:");
            minecord.error(e.getMessage());
            return "";
        }
    }

    public String getUUID(long discordID) {
        ResultSet rs;
        try {
            PreparedStatement stmt = dbcon.prepareStatement("SELECT minecraftid FROM link WHERE discordid=?");
            stmt.setString(1, Long.toString(discordID));
            rs = stmt.executeQuery();
            return rs.getString("minecraftid");
        } catch (SQLException e) {
            minecord.error("Error getting minecraftid from database! Stack Trace:");
            minecord.error(e.getMessage());
            return "";
        }
    }

    public boolean doesEntryExist(long discordID) {
        ResultSet rs;
        try {
            PreparedStatement stmt = dbcon.prepareStatement("SELECT discordid FROM link WHERE discordid=?");
            stmt.setString(1, Long.toString(discordID));
            rs = stmt.executeQuery();

            return rs.next();
        } catch (SQLException e) {
            minecord.error("Error checking if entry exists in database! Stack Trace:");
            minecord.error(e.getMessage());
            return false;
        }
    }

    public void removeLink(long discordID) {
        try {
            PreparedStatement stmt = dbcon.prepareStatement("DELETE FROM link WHERE discordid=?");
            stmt.setString(1, Long.toString(discordID));
            stmt.execute();

        } catch (SQLException e) {
            minecord.error("Error removing link from database! Stack Trace:");
            minecord.error(e.getMessage());
        }
    }

    public void removeLink(UUID minecraftID) {
        try {
            PreparedStatement stmt = dbcon.prepareStatement("DELETE FROM link WHERE minecraftid=?");
            stmt.setString(1, minecraftID.toString());
            stmt.execute();

        } catch (SQLException e) {
            minecord.error("Error removing link from database! Stack Trace:");
            minecord.error(e.getMessage());
        }
    }

    /* Private Methods */

    private void updateTableStructure(String tableName, String createTableQuery) throws SQLException {
        DatabaseMetaData meta = dbcon.getMetaData();
        ResultSet rs = meta.getTables(null, null, tableName, null);

        if (rs.next()) {
            // Table exists
            ResultSet columns = meta.getColumns(null, null, tableName, null);
            List<String> existingColumns = new ArrayList<>();

            while (columns.next()) {
                existingColumns.add(columns.getString("COLUMN_NAME"));
            }

            // Check for columns to add
            try (Statement stmt = dbcon.createStatement()) {
                String[] createTableParts = createTableQuery.split("\\(");
                String columnsPart = createTableParts[1];
                columnsPart = columnsPart.substring(0, columnsPart.length() - 1); // Remove trailing ')'
                String[] requiredColumns = columnsPart.split(",");

                for (String requiredColumn : requiredColumns) {
                    requiredColumn = requiredColumn.trim().split("\\s+")[0]; // Get only the column name
                    if (!existingColumns.contains(requiredColumn)) {
                        // Column doesn't exist, add it
                        stmt.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + requiredColumn);
                        if (minecord.debugMode) minecord.debug("Added column '" + requiredColumn + "' to table '" + tableName + "'.");
                    }
                }

                // Check for columns to remove
                for (String existingColumn : existingColumns) {
                    if (!createTableQuery.contains(existingColumn)) {
                        // Column exists in the table but not in the required structure, remove it
                        stmt.executeUpdate("ALTER TABLE " + tableName + " DROP COLUMN " + existingColumn);
                        if (minecord.debugMode) minecord.debug("Removed column '" + existingColumn + "' from table '" + tableName + "'.");
                    }
                }
            }
        } else {
            // Table does not exist, create it
            if (minecord.debugMode) minecord.debug("Creating table '" + tableName + "'...");
            try (Statement stmt = dbcon.createStatement()) {
                stmt.executeUpdate(createTableQuery);
                if (minecord.debugMode) minecord.debug("Table '" + tableName + "' created successfully.");
            }
        }
    }

    public void close() {
        try {
            if (dbcon != null && !dbcon.isClosed()) {
                dbcon.close();
            }
        } catch (SQLException e) {
            minecord.error("Error closing the database connection: " + e.getMessage());
        }
    }

    /**
     * Retrieves all linked users from the database.
     * @return List of UUIDs of all linked users.
     */
    public List<UUID> getAllLinkedUsers() {
        List<UUID> linkedUsers = new ArrayList<>();
        try {
            PreparedStatement stmt = dbcon.prepareStatement("SELECT minecraftid FROM link");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                linkedUsers.add(UUID.fromString(rs.getString("minecraftid")));
            }
        } catch (SQLException e) {
            minecord.error("Error retrieving all linked users from database! Stack Trace:");
            minecord.error(e.getMessage());
        }
        return linkedUsers;
    }
}