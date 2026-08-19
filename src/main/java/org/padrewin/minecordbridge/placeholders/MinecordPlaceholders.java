package org.padrewin.minecordbridge.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;

public class MinecordPlaceholders extends PlaceholderExpansion {

    private final MinecordBridge minecord;

    public MinecordPlaceholders(MinecordBridge minecord) {
        this.minecord = minecord;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "minecordbridge";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", minecord.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return minecord.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        if (params.equalsIgnoreCase("boosting_since")) {
            Database db = MinecordBridge.getDatabase();
            String discordId = db.getDiscordID(player.getUniqueId());
            if (discordId == null) return "0";

            Long boostSince = db.getBoostSince(Long.parseLong(discordId));
            return boostSince != null ? String.valueOf(boostSince) : "0";
        }

        return null;
    }
}
