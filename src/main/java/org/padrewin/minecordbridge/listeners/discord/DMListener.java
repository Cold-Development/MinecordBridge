package org.padrewin.minecordbridge.listeners.discord;

import org.bukkit.entity.Player;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.javacord.api.util.logging.ExceptionLogger;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.padrewin.minecordbridge.javacord.JavacordHelper;

import java.util.HashMap;
import java.util.Random;

public class DMListener implements MessageCreateListener {

    private final MinecordBridge minecord;
    private final org.bukkit.Server server;
    private final Database db;
    private String randInt;  // Changed to String for consistency
    private int step;
    private Player player;
    private final String[] roleNames;
    private Role addedRole;
    private final HashMap<String, String[]> addCommands;
    private final TextChannel channel;
    private boolean resent = false;
    private static final int MAX_RETRIES = 3;

    public DMListener(Role addedRole, TextChannel channel) {
        minecord = MinecordBridge.getPlugin();
        server = minecord.getServer();
        db = MinecordBridge.getDatabase();
        roleNames = minecord.roleNames;
        addCommands = minecord.addCommands;
        this.channel = channel;
        this.addedRole = addedRole;
        step = 1;
    }

    public DMListener(TextChannel channel) {
        minecord = MinecordBridge.getPlugin();
        server = minecord.getServer();
        db = MinecordBridge.getDatabase();
        roleNames = minecord.roleNames;
        addCommands = minecord.addCommands;
        this.channel = channel;
        step = 1;
    }

    @Override
    public void onMessageCreate(MessageCreateEvent event) {
        JavacordHelper js = minecord.js;

        Server discordServer = js.api.getServerById(minecord.serverID).get();

        User user = event.getMessageAuthor().asUser().get();

        if (!event.getChannel().equals(channel)) {
            return;
        }

        String messageContent = event.getMessageContent().trim();

        if (messageContent.equalsIgnoreCase("resend")) {
            handleResend(user);
            return;
        } else if (messageContent.equalsIgnoreCase("cancel")) {
            event.getChannel().sendMessage("Ai anulat procesul de verificare.");
            RoleAddListener.removeListener(user, this);
            step = 0;
            return;
        }

        switch (step) {
            case 1:
                handleStep1(event, user);
                break;
            case 2:
                handleStep2(event, user);
                break;
            case 3:
                handleStep3(event, user, discordServer);
                break;
            default:
                break;
        }
    }

    private void handleStep1(MessageCreateEvent event, User user) {
        if (event.getMessageContent().equalsIgnoreCase("NU")) {
            event.getChannel().sendMessage("In acest caz nu putem sa te recompensam, totusi iti multumim pentru sustinere! :heart:");
            RoleAddListener.removeListener(user, this);
        } else if (event.getMessageContent().equalsIgnoreCase("DA")) {
            boolean messageSent = false;
            int attempt = 0;

            while (!messageSent && attempt < MAX_RETRIES) {
                try {
                    new MessageBuilder()
                            .append("__**Urmeaza urmatoarele instructiuni:**__")
                            .append("\n:one: Conecteaza-te in **/lobby** pe server-ul **mc-1st.ro** folosind contul pe care iti doresti sa revendici recompensele.")
                            .append("\n:two: Scrie numele contului tau de minecraft in acest DM. __(numele trebuie sa fie exact)__")
                            .append("\n:three: Scrie codul primit in chat-ul din minecraft in acest DM.")
                            .append("\n:four: Bucura-te de beneficii! :tada:")
                            .send(event.getChannel()).exceptionally(ExceptionLogger.get());
                    messageSent = true;
                } catch (Exception e) {
                    attempt++;
                    minecord.error("Error sending message: " + user.getDiscriminatedName() + ". Attempt " + attempt + ". Stack Trace:");
                    minecord.error(e.getMessage());
                    if (attempt >= MAX_RETRIES) {
                        minecord.error("Failed to send message after " + MAX_RETRIES + " attempts.");
                        event.getChannel().sendMessage("Ceva nu a functionat, contacteaza unul dintre Owneri. :angry:");
                        break;
                    }
                }
            }

            if (messageSent) {
                step = 2;
                event.getChannel().sendMessage("Pentru a incepe, introdu numele tau de pe minecraft. __(numele trebuie sa fie exact)__");
            }
        } else {
            event.getChannel().sendMessage("Raspunde scriind \"DA\" sau \"NU\".");
        }
    }

    private void handleStep2(MessageCreateEvent event, User user) {
        try {
            player = server.getPlayer(event.getMessageContent());

            if (player != null && player.isOnline()) {
                randInt = getRandomNumber();
                player.sendMessage(" ");
                player.sendMessage(minecord.pluginTag + "Codul tau este: §4" + randInt);
                player.sendMessage(" ");
                event.getChannel().sendMessage("Introdu codul trimis in chat-ul din minecraft. Scrie \"resend\" daca ai nevoie de un nou cod!");
                step = 3;
            } else {
                event.getChannel().sendMessage("Nu esti conectat in **/lobby** pe server-ul de minecraft **mc-1st.ro**! Asigura-te ca ai scris numele corect.");
            }
        } catch (NullPointerException e) {
            minecord.error("Error linking user: " + user.getDiscriminatedName() + ". Stack Trace:");
            minecord.error(e.getMessage());
            event.getChannel().sendMessage("Jucatorul nu a fost gasit! Asigura-te ca esti conectat in **/lobby** pe server si ca ti-ai scris numele corect!");
        }
    }

    private void handleStep3(MessageCreateEvent event, User user, Server discordServer) {
        try {
            if (event.getMessageContent().equals(randInt)) {
                db.insertLink(event.getMessageAuthor().getId(), player.getName(), player.getUniqueId());
                if (minecord.changeNickOnLink) {
                    discordServer.updateNickname(user, player.getName()).join();
                }
                if (addedRole != null) {
                    RoleAddListener.runCommands(minecord, roleNames, addCommands, addedRole, player.getName());
                }
                event.getChannel().sendMessage("Ai fost recompensat, multumim pentru sustinere! :heart:");
                minecord.sendMessage(player, "Ai fost recompensat!");
                step = 4;
                RoleAddListener.removeListener(user, this);
            } else {
                event.getChannel().sendMessage("Codul introdus nu se potriveste. Scrie \"resend\" daca doresti sa primesti un nou cod.");
            }
        } catch (Exception e) {
            minecord.error("Error linking user: " + user.getDiscriminatedName() + ". Stack Trace:");
            minecord.error(e.getMessage());
            event.getChannel().sendMessage("Ceva nu a functionat, contacteaza unul dintre Owneri. :angry:");
        }
    }

    private void handleResend(User user) {
        if (step == 3) {
            randInt = getRandomNumber();
            player.sendMessage(" ");
            player.sendMessage(minecord.pluginTag + "Noul cod este: §4" + randInt);
            player.sendMessage(" ");
            channel.sendMessage("Noul cod a fost trimis. Introdu codul trimis in chat-ul din Minecraft.");
        } else {
            channel.sendMessage("Nu poti sa trimiti un nou cod in acest moment.");
        }
    }

    private String getRandomNumber() {
        Random rng = new Random();
        int number = rng.nextInt(999999);
        return String.format("%06d", number);
    }
}
