package org.padrewin.minecordbridge.listeners.discord;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.server.role.UserRoleAddEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.listener.server.role.UserRoleAddListener;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;

import java.util.HashMap;

public class RoleAddListener implements UserRoleAddListener {

    private final MinecordBridge minecord;
    private final Database db;
    private final Role[] roles;

    public RoleAddListener(Role[] roles) {
        minecord = MinecordBridge.getPlugin();
        db = MinecordBridge.getDatabase();
        this.roles = roles;
    }

    @Override
    public void onUserRoleAdd(UserRoleAddEvent roleEvent) {
        Role addedRole = findTrackedRole(roleEvent.getRole());
        if (addedRole == null) return;

        if (db.doesEntryExist(roleEvent.getUser().getId())) return;

        try {
            new MessageBuilder()
                    .append("Mulțumim pentru susținere! Pentru a-ți activa beneficiile Minecraft, folosește comanda ")
                    .append("`/mcord link username:NumeMinecraft` ")
                    .append("pe serverul nostru Discord.")
                    .send(roleEvent.getUser());
            minecord.log(roleEvent.getUser().getDiscriminatedName() + " got a tracked role and was sent linking instructions.");
        } catch (Exception e) {
            minecord.warn("Could not send a DM to " + roleEvent.getUser().getDiscriminatedName() + ". They may have DMs disabled.");
        }
    }

    private Role findTrackedRole(Role eventRole) {
        for (Role role : roles) {
            if (role != null && role.getId() == eventRole.getId()) return role;
        }
        return null;
    }

    public static void removeListener(User user, MessageCreateListener listener) {
        user.removeUserAttachableListener(listener);
    }

    public static void runCommands(MinecordBridge minecord, String[] roleNames, HashMap<String, String[]> commands, Role role, String username) {
        ConsoleCommandSender console = minecord.getServer().getConsoleSender();

        String roleName = null;
        for (String name : roleNames) {
            String roleId = minecord.roleAndID.get(name);
            if (roleId != null && roleId.equals(String.valueOf(role.getId()))) {
                roleName = name;
                break;
            }
        }

        if (roleName == null) return;
        String[] cmds = commands.get(roleName);
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
        minecord.log(username + " now has benefits from role " + roleName + ".");
    }
}
