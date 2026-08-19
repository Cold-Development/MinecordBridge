package org.padrewin.minecordbridge.database;

import org.bukkit.plugin.Plugin;
import org.padrewin.minecordbridge.MinecordBridge;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Database manager class with specific pull/push methods for a sqlite database
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
                "CREATE TABLE IF NOT EXISTS link(minecraftid TEXT NOT NULL, discordid TEXT NOT NULL, username TEXT NOT NULL, rolename TEXT)");
        updateTableStructure("boosts",
                "CREATE TABLE IF NOT EXISTS boosts(discordid TEXT NOT NULL, boostsince BIGINT NOT NULL, claimedmilestones TEXT)");
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
    public void insertLink(long discordID, String username, UUID minecraftID, String roleName) {
        try {
            PreparedStatement stmt = dbcon.prepareStatement("INSERT INTO link(minecraftid,discordid,username,rolename) VALUES (?,?,?,?)");
            stmt.setString(1, minecraftID.toString());
            stmt.setString(2, Long.toString(discordID));
            stmt.setString(3, username);
            stmt.setString(4, roleName);
            stmt.execute();
        } catch (SQLException e) {
            minecord.error("Error inserting link into database! Stack Trace:");
            minecord.error(e.getMessage());
        }
    }

    public String getRoleName(long discordID) {
        try (PreparedStatement stmt = dbcon.prepareStatement("SELECT rolename FROM link WHERE discordid=?")) {
            stmt.setString(1, Long.toString(discordID));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("rolename") : null;
            }
        } catch (SQLException e) {
            minecord.error("Error getting role name from database! Stack Trace:");
            minecord.error(e.getMessage());
            return null;
        }
    }

    public void updateRoleName(long discordID, String roleName) {
        try (PreparedStatement stmt = dbcon.prepareStatement("UPDATE link SET rolename=? WHERE discordid=?")) {
            stmt.setString(1, roleName);
            stmt.setString(2, Long.toString(discordID));
            stmt.executeUpdate();
        } catch (SQLException e) {
            minecord.error("Error updating role name in database! Stack Trace:");
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
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                minecord.warn("No entry found in database for Discord ID: " + discordID);
            } else {
                //minecord.log("Removed link for Discord ID: " + discordID);
            }
        } catch (SQLException e) {
            minecord.error("Error removing link from database! Stack Trace:");
            minecord.error(e.getMessage());
        }
    }

    public void removeLink(UUID minecraftID) {
        try {
            PreparedStatement stmt = dbcon.prepareStatement("DELETE FROM link WHERE minecraftid=?");
            stmt.setString(1, minecraftID.toString());
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected == 0) {
                minecord.warn("No entry found in database for Minecraft ID: " + minecraftID);
            } else {
                //minecord.log("Removed link for Minecraft ID: " + minecraftID);
            }
        } catch (SQLException e) {
            minecord.error("Error removing link from database! Stack Trace:");
            minecord.error(e.getMessage());
        }
    }

    public String getDiscordID(UUID minecraftID) {
        try (PreparedStatement stmt = dbcon.prepareStatement("SELECT discordid FROM link WHERE minecraftid=?")) {
            stmt.setString(1, minecraftID.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("discordid") : null;
            }
        } catch (SQLException e) {
            minecord.error("Error getting discordid from database! Stack Trace:");
            minecord.error(e.getMessage());
            return null;
        }
    }

    public boolean hasBoostRecord(long discordID) {
        try (PreparedStatement stmt = dbcon.prepareStatement("SELECT discordid FROM boosts WHERE discordid=?")) {
            stmt.setString(1, Long.toString(discordID));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            minecord.error("Error checking boost record in database! Stack Trace:");
            minecord.error(e.getMessage());
            return false;
        }
    }

    public void startBoosting(long discordID, long boostSinceEpochSeconds) {
        try (PreparedStatement stmt = dbcon.prepareStatement("INSERT INTO boosts(discordid, boostsince, claimedmilestones) VALUES (?,?,?)")) {
            stmt.setString(1, Long.toString(discordID));
            stmt.setLong(2, boostSinceEpochSeconds);
            stmt.setString(3, "");
            stmt.execute();
        } catch (SQLException e) {
            minecord.error("Error inserting boost record into database! Stack Trace:");
            minecord.error(e.getMessage());
        }
    }

    public void stopBoosting(long discordID) {
        try (PreparedStatement stmt = dbcon.prepareStatement("DELETE FROM boosts WHERE discordid=?")) {
            stmt.setString(1, Long.toString(discordID));
            stmt.executeUpdate();
        } catch (SQLException e) {
            minecord.error("Error removing boost record from database! Stack Trace:");
            minecord.error(e.getMessage());
        }
    }

    public Long getBoostSince(long discordID) {
        try (PreparedStatement stmt = dbcon.prepareStatement("SELECT boostsince FROM boosts WHERE discordid=?")) {
            stmt.setString(1, Long.toString(discordID));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong("boostsince") : null;
            }
        } catch (SQLException e) {
            minecord.error("Error getting boost since from database! Stack Trace:");
            minecord.error(e.getMessage());
            return null;
        }
    }

    public List<Long> getAllBoostingDiscordIds() {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement stmt = dbcon.prepareStatement("SELECT discordid FROM boosts");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                try {
                    ids.add(Long.parseLong(rs.getString("discordid")));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (SQLException e) {
            minecord.error("Error retrieving boosting Discord IDs from database! Stack Trace:");
            minecord.error(e.getMessage());
        }
        return ids;
    }

    public Set<Integer> getClaimedMilestones(long discordID) {
        Set<Integer> claimed = new HashSet<>();
        try (PreparedStatement stmt = dbcon.prepareStatement("SELECT claimedmilestones FROM boosts WHERE discordid=?")) {
            stmt.setString(1, Long.toString(discordID));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String raw = rs.getString("claimedmilestones");
                    if (raw != null && !raw.isBlank()) {
                        for (String part : raw.split(",")) {
                            try {
                                claimed.add(Integer.parseInt(part.trim()));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            minecord.error("Error getting claimed boost milestones from database! Stack Trace:");
            minecord.error(e.getMessage());
        }
        return claimed;
    }

    public void addClaimedMilestone(long discordID, int months) {
        Set<Integer> claimed = getClaimedMilestones(discordID);
        claimed.add(months);
        String joined = claimed.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));

        try (PreparedStatement stmt = dbcon.prepareStatement("UPDATE boosts SET claimedmilestones=? WHERE discordid=?")) {
            stmt.setString(1, joined);
            stmt.setString(2, Long.toString(discordID));
            stmt.executeUpdate();
        } catch (SQLException e) {
            minecord.error("Error updating claimed boost milestones in database! Stack Trace:");
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
                    String columnDefinition = requiredColumn.trim();
                    String columnName = columnDefinition.split("\\s+")[0];
                    if (!existingColumns.contains(columnName)) {
                        // Column doesn't exist, add it
                        stmt.executeUpdate("ALTER TABLE " + tableName + " ADD COLUMN " + columnDefinition);
                        if (minecord.debugMode) minecord.debug("Added column '" + columnName + "' to table '" + tableName + "'.");
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

    public List<Map.Entry<String, String>> getAllLinkedUsersWithDiscordID() {
        List<Map.Entry<String, String>> linkedUsers = new ArrayList<>();
        try {
            PreparedStatement stmt = dbcon.prepareStatement("SELECT username, discordid FROM link");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String username = rs.getString("username");
                String discordID = rs.getString("discordid");
                linkedUsers.add(new AbstractMap.SimpleEntry<>(username, discordID));
            }
        } catch (SQLException e) {
            minecord.error("Error retrieving all linked users with Discord ID from database! Stack Trace:");
            minecord.error(e.getMessage());
        }
        return linkedUsers;
    }

    public List<LinkedAccount> getAllLinkedAccounts() {
        List<LinkedAccount> linkedAccounts = new ArrayList<>();
        try (PreparedStatement stmt = dbcon.prepareStatement("SELECT username, discordid, rolename FROM link");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                linkedAccounts.add(new LinkedAccount(
                        rs.getString("username"), rs.getString("discordid"), rs.getString("rolename")));
            }
        } catch (SQLException e) {
            minecord.error("Error retrieving linked accounts from database! Stack Trace:");
            minecord.error(e.getMessage());
        }
        return linkedAccounts;
    }

    public record LinkedAccount(String username, String discordId, String roleName) { }
}
