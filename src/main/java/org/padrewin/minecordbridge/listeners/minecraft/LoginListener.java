package org.padrewin.minecordbridge.listeners.minecraft;

import org.padrewin.minecordbridge.javacord.JavacordHelper;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.awt.Color;

public final class LoginListener implements Listener {

    private final boolean updateRequired;
    private final String[] versions;
    private final Database db = MinecordBridge.getDatabase();
    private final MinecordBridge minecord;
    private final JavacordHelper js;

    public LoginListener(boolean updateRequired, String[] versions) {
        this.updateRequired = updateRequired;
        this.versions = versions;
        minecord = MinecordBridge.getPlugin();
        js = minecord.js;
    }

    @EventHandler
    public void onLogin(PlayerJoinEvent event) {

        /* Check for Updates and send message to player with permission to see updates */
        if (updateRequired && (event.getPlayer().hasPermission("minecord.update") || event.getPlayer().isOp())) {
            event.getPlayer().sendMessage("Version &c" + versions[0] + "&favailable! Actual version: &c" + versions[1] + ".");

            minecord.log("Version " + versions[0] + " available! Actual version " + versions[1] + ".");

        }

        /* Check if Username has changed since last login */
        if (db.doesEntryExist(event.getPlayer().getUniqueId())) {
            if (!db.getUsername(event.getPlayer().getUniqueId()).equals(event.getPlayer().getName())) {
                db.updateMinecraftUsername(event.getPlayer().getName(), event.getPlayer().getUniqueId());
            }
        }

        if (minecord.useChatStream) {
            new MessageBuilder()
                    .setEmbed(new EmbedBuilder()
                            .setTitle(":heavy_plus_sign:" + event.getPlayer().getName() + " joined the server")
                            .setColor(Color.green)
                    ).send(js.chatStreamChannel);
        }
    }
}
