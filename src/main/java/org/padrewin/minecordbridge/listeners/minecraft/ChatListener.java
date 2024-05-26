package org.padrewin.minecordbridge.listeners.minecraft;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.padrewin.minecordbridge.MinecordBridge;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import org.javacord.api.entity.message.MessageBuilder;

public class ChatListener implements Listener {

    private static MinecordBridge minecord;

    public ChatListener() {
        minecord = MinecordBridge.getPlugin();
    }

    public static void sendServerStartMessage() {
    }

    public static void sendServerCloseMessage() {
    }

    @EventHandler
    public void onChatMessage(AsyncChatEvent event) {
        if (minecord.useChatStream) {
            if (minecord.usePex) {
                String groupName = "";
//                try { groupName = user.getRankLadderGroup("default").getName(); } catch (NullPointerException ignored) {}

                TextComponent message = (TextComponent) event.message();
                new MessageBuilder()
                        .append("**" + groupName + "** ")
                        .append(event.getPlayer().getName())
                        .append(" » ")
                        .append(message)
                        .send(minecord.js.chatStreamChannel);
            } else {
                TextComponent message = (TextComponent) event.message();
                new MessageBuilder()
                        .append(event.getPlayer().getName())
                        .append(" » ")
                        .append(message.content())
                        .send(minecord.js.chatStreamChannel);
            }
        }
    }

}
