package org.padrewin.minecordbridge.listeners.discord;

import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
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
    public RoleRemoveListener(Role[] roles) {
        roleNames = minecord.roleNames;
        this.roles = roles;
        removeCommands = minecord.removeCommands;
    }

    @Override
    public void onUserRoleRemove(UserRoleRemoveEvent roleEvent) {
        String removedRoleName = getConfiguredRoleName(roleEvent.getRole());
        if (removedRoleName == null || !db.doesEntryExist(roleEvent.getUser().getId())) return;

        String username = db.getUsername(roleEvent.getUser().getId());
        String linkedRoleName = db.getRoleName(roleEvent.getUser().getId());
        // A linked account can have benefits for one configured role only. Do not
        // remove them when a different tracked role is removed.
        if (linkedRoleName != null && !linkedRoleName.equalsIgnoreCase(removedRoleName)) return;

        try {
            db.removeLink(roleEvent.getUser().getId());

            // Log before running remove commands
            minecord.log(roleEvent.getUser().getDiscriminatedName() + " has lost benefits from role " + roleEvent.getRole().getName() + ".");

            runRemoveCommands(removedRoleName, username);

        } catch (Exception e) {
            minecord.error("Error removing roles: " + username + ". Stack Trace:");
            minecord.error(e.getMessage());
        }
    }

    private String getConfiguredRoleName(Role role) {
        for (String name : roleNames) {
            String roleId = minecord.roleAndID.get(name);
            if (roleId != null && roleId.equals(String.valueOf(role.getId()))) return name;
        }
        return null;
    }

    private void runRemoveCommands(String roleName, String username) {
        ConsoleCommandSender console = minecord.getServer().getConsoleSender();

        String[] cmds = removeCommands.get(roleName);
        if (cmds == null) return;

        for (String cmdSend : cmds) {
            if (cmdSend.contains("%user%")) {
                cmdSend = cmdSend.replace("%user%", username);
            }

            try {
                String finalCmdSend = cmdSend;
                Bukkit.getScheduler().callSyncMethod(minecord, () -> Bukkit.dispatchCommand(console, finalCmdSend)).get();
            } catch (Exception e) {
                minecord.error("Error executing command: " + cmdSend + ". Stack Trace:");
                minecord.error(e.getMessage());
            }
        }
    }
}
