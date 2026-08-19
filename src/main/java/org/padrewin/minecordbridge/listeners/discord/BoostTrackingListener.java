package org.padrewin.minecordbridge.listeners.discord;

import org.javacord.api.entity.permission.Role;
import org.javacord.api.event.server.role.UserRoleAddEvent;
import org.javacord.api.event.server.role.UserRoleRemoveEvent;
import org.javacord.api.listener.server.role.UserRoleAddListener;
import org.javacord.api.listener.server.role.UserRoleRemoveListener;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;

import java.time.Instant;

/**
 * Javacord 3.8.0 does not expose a member's premium_since (boost start) date,
 * so Discord's automatic Server Booster role add/remove is used as a proxy
 * for when a member starts/stops boosting.
 */
public class BoostTrackingListener implements UserRoleAddListener, UserRoleRemoveListener {

    private final MinecordBridge minecord;
    private final Database db;
    private final Role boosterRole;

    public BoostTrackingListener(Role boosterRole) {
        this.minecord = MinecordBridge.getPlugin();
        this.db = MinecordBridge.getDatabase();
        this.boosterRole = boosterRole;
    }

    @Override
    public void onUserRoleAdd(UserRoleAddEvent event) {
        if (boosterRole == null || event.getRole().getId() != boosterRole.getId()) return;

        long discordId = event.getUser().getId();
        if (db.hasBoostRecord(discordId)) return;

        db.startBoosting(discordId, Instant.now().getEpochSecond());
        minecord.log(event.getUser().getDiscriminatedName() + " started boosting the Discord server.");
    }

    @Override
    public void onUserRoleRemove(UserRoleRemoveEvent event) {
        if (boosterRole == null || event.getRole().getId() != boosterRole.getId()) return;

        db.stopBoosting(event.getUser().getId());
        minecord.log(event.getUser().getDiscriminatedName() + " is no longer boosting the Discord server.");
    }
}
