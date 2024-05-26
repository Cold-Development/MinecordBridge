package org.padrewin.minecordbridge.listeners.discord;

import org.padrewin.minecordbridge.javacord.JavacordHelper;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import net.luckperms.api.*;

public class DiscordMessageListener implements MessageCreateListener {

    private final MinecordBridge minecord;
    private String messageFormat;

    public DiscordMessageListener() {
        minecord = MinecordBridge.getPlugin();
        messageFormat = minecord.chatStreamMessageFormat;
    }

    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        JavacordHelper js = minecord.js;
        Database db = MinecordBridge.getDatabase();
        if (event.getChannel() != minecord.js.chatStreamChannel || event.getMessageAuthor().isYourself()) return;
        String group = "";
        String prefix = "";
        String role = "";
        User user = event.getMessageAuthor().asUser().get();

        try {
            if (db.doesEntryExist(event.getMessageAuthor().getId())) {
                Player player = minecord.getServer().getPlayer(db.getUUID(event.getMessageAuthor().getId()));
                if (minecord.usePex) {
                    assert player != null;
                } else if (minecord.useLuckPerms) {
                    LuckPerms luckPerms = minecord.lp;
                    assert player != null;
                    net.luckperms.api.model.user.User luckPermsUser = luckPerms.getPlayerAdapter(Player.class).getUser(player);
                    group = luckPermsUser.getPrimaryGroup();
                }
            }
        } catch (AssertionError e) {
            minecord.error("Error fetching database information. Stack Trace:");
            minecord.error(e.getMessage());
        }

        try {
            role = user.getRoles(js.api.getServerById(minecord.serverID).get()).get(0).getName();
        } catch (NullPointerException ignored) {}

        colorCodeFormatting();

        //Placeholder Replacement
        messageFormat = messageFormat.replaceAll("%GROUP%", group);
        messageFormat = messageFormat.replaceAll("%PREFIX%", prefix);
        messageFormat = messageFormat.replaceAll("%ROLE%", role);
        messageFormat = messageFormat.replaceAll("%user%", event.getMessageAuthor().getName());
        messageFormat = messageFormat.replaceAll("%MESSAGE%", event.getMessageContent());

        minecord.getServer().broadcast(Component.text(messageFormat));

    }

    private void colorCodeFormatting() {
        messageFormat = messageFormat.replaceAll("&a", "§a");
        messageFormat = messageFormat.replaceAll("&b", "§b");
        messageFormat = messageFormat.replaceAll("&c", "§c");
        messageFormat = messageFormat.replaceAll("&d", "§d");
        messageFormat = messageFormat.replaceAll("&e", "§e");
        messageFormat = messageFormat.replaceAll("&f", "§f");
        messageFormat = messageFormat.replaceAll("&1", "§1");
        messageFormat = messageFormat.replaceAll("&2", "§2");
        messageFormat = messageFormat.replaceAll("&3", "§3");
        messageFormat = messageFormat.replaceAll("&4", "§4");
        messageFormat = messageFormat.replaceAll("&5", "§5");
        messageFormat = messageFormat.replaceAll("&6", "§6");
        messageFormat = messageFormat.replaceAll("&7", "§7");
        messageFormat = messageFormat.replaceAll("&8", "§8");
        messageFormat = messageFormat.replaceAll("&9", "§9");
        messageFormat = messageFormat.replaceAll("&0", "§0");
    }

}