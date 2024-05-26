package org.padrewin.minecordbridge.listeners.discord;

import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.event.server.role.UserRoleRemoveEvent;
import org.javacord.api.listener.server.role.UserRoleRemoveListener;

import java.util.HashMap;

public class RoleRemoveListener implements UserRoleRemoveListener {

    private final Database db = MinecordBridge.getDatabase();
    private final MinecordBridge minecord = MinecordBridge.getPlugin();
    private final String[] roleNames;
    private final Role[] roles;
    private final HashMap<String, String[]> removeCommands;
    private String username;

    public RoleRemoveListener(Role[] roles) {
        roleNames = minecord.roleNames;
        this.roles = roles;
        removeCommands = minecord.removeCommands;
    }

    @Override
    public void onUserRoleRemove(UserRoleRemoveEvent roleEvent) {

        int rolesChanged = 0;

        username = db.getUsername(roleEvent.getUser().getId());

        for (Role role : roles) {
            if (roleEvent.getRole() != role) {
                rolesChanged++;
            }
        }
        if (rolesChanged >= roles.length || !db.doesEntryExist(roleEvent.getUser().getId())) {
            return;
        }

        try {
            db.removeLink(roleEvent.getUser().getId());
            runCommands(roleEvent.getRole());
        } catch (Exception e) {
            minecord.error("Error removing roles: " + username + ". Stack Trace:");
            minecord.error(e.getMessage());
        }
    }

    private void runCommands(Role role) {
        RoleAddListener.runCommands(minecord, roleNames, removeCommands, role, username);
    }

}
