package org.padrewin.minecordbridge.listeners.discord;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.javacord.api.entity.channel.TextChannel;
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
    private static Role addedRole;
    private TextChannel pmChannel;
    public static int i;
    private static final int MAX_RETRIES = 3;

    public RoleAddListener(Role[] roles) {
        minecord = MinecordBridge.getPlugin();
        db = MinecordBridge.getDatabase();
        this.roles = roles;
        i = 0;
    }

    @Override
    public void onUserRoleAdd(UserRoleAddEvent roleEvent) {

        int rolesChanged = 0;

        for (Role role : roles) {
            if (roleEvent.getRole() != role) {
                rolesChanged++;
            } else {
                addedRole = roleEvent.getRole();
            }
        }
        if (rolesChanged >= roles.length) {
            return;
        }

        if (db.doesEntryExist(roleEvent.getUser().getId())) {
            minecord.warn("User is already registered in database!");
        }

        User user = roleEvent.getUser();
        if (i == 0) {
            boolean messageSent = false;
            int attempt = 0;

            while (!messageSent && attempt < MAX_RETRIES) {
                try {
                    new MessageBuilder()
                            .append(minecord.getMessage("Auto-Role.boost_message"))
                            .append("\n")
                            .append(minecord.getMessage("Auto-Role.registration_question"))
                            .send(user).thenAccept(msg -> pmChannel = msg.getChannel()).join();
                    messageSent = true;
                } catch (Exception e) {
                    attempt++;
                    minecord.error("Error sending message: " + user.getDiscriminatedName() + ". Attempt " + attempt + ". Stack Trace:");
                    minecord.error(e.getMessage());
                    if (attempt >= MAX_RETRIES) {
                        minecord.error("Failed to send message after " + MAX_RETRIES + " attempts.");
                    }
                }
            }

            user.addUserAttachableListener(new DMListener(addedRole, pmChannel));
            i++;
        }
    }

    public static void removeListener(User user, MessageCreateListener listener) {
        user.removeUserAttachableListener(listener);
        i = 0;
    }

    public static void runCommands(MinecordBridge minecord, String[] roleNames, HashMap<String, String[]> commands, Role role, String username) {
        ConsoleCommandSender console = minecord.getServer().getConsoleSender();

        String roleName = "";
        for (String name : roleNames) {
            if (name.equalsIgnoreCase(role.getName())) {
                roleName = name;
                break;
            }
        }

        String[] cmds = commands.get(roleName);

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
