package org.padrewin.minecordbridge.listeners.minecraft;

import org.padrewin.minecordbridge.MinecordBridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.message.embed.EmbedBuilder;

import java.awt.Color;

public class LogoutListener implements Listener {

    private final MinecordBridge minecord;

    public LogoutListener() {
        minecord = MinecordBridge.getPlugin();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (minecord.useChatStream) {
            new MessageBuilder()
                    .setEmbed(new EmbedBuilder()
                            .setTitle(":heavy_minus_sign:" + event.getPlayer().getName() + " left the server")
                            .setColor(Color.red))
                    .send(minecord.js.chatStreamChannel);
        }
    }

}
