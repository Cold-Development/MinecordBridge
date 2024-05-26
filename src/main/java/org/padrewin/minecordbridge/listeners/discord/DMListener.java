package org.padrewin.minecordbridge.listeners.discord;

import org.padrewin.minecordbridge.javacord.JavacordHelper;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.bukkit.entity.Player;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.MessageBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;

import java.util.HashMap;
import java.util.Objects;
import java.util.Random;

public class DMListener implements MessageCreateListener {

    private final MinecordBridge minecord;
    private final org.bukkit.Server server;
    private final Database db;
    private int randInt;
    private int step;
    private Player player;
    private final String[] roleNames;
    private Role addedRole;
    private final HashMap<String, String[]> addCommands;
    private final TextChannel channel;
    private boolean resent = false;

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

        if (event.getChannel() != channel) {
            return;
        }
        if (event.getMessageContent().equalsIgnoreCase("resend")) resent = true;
        if (event.getMessageContent().equalsIgnoreCase("anuleaza"))  {
            event.getChannel().sendMessage("Ai anulat procesul de verificare.");
            RoleAddListener.removeListener(user, this);
            step = 0;
        }
        if (step == 1) {
            if (event.getMessageContent().equalsIgnoreCase("NU")) {
                event.getChannel().sendMessage("In acest caz nu putem sa te recompensam, totusi iti multumim pentru sustinere! :heart:");
                RoleAddListener.removeListener(user, this);
            } else if (event.getMessageContent().equalsIgnoreCase("DA")) {
                new MessageBuilder()
                        .append("__**Urmeaza urmatoarele instructiuni:**__")
                        .append("\n:one: Conecteaza-te in **/lobby** pe server-ul **mc-1st.ro** folosind contul pe care iti doresti sa revendici recompensele.")
                        .append("\n:two: Scrie numele contului tau de minecraft in acest DM. __(numele trebuie sa fie exact)__")
                        .append("\n:three: Scrie codul primit in chat-ul din minecraft in acest DM.")
                        .append("\n:four: Bucura-te de beneficii! :tada:")
                        .send(event.getChannel());
                step = 2;
                event.getChannel().sendMessage("Pentru a incepe, introdu numele tau de pe minecraft. __(numele trebuie sa fie exact)__");
            } else {
                event.getChannel().sendMessage("Raspunde scriind \"DA\" sau \"NU\".");
            }
        } else if (step == 2) {
            try {
                if (!resent) {
                    if (Objects.requireNonNull(server.getPlayer(event.getMessageContent())).isOnline()) {
                        player = server.getPlayer(event.getMessageContent());
                        randInt = Integer.parseInt(getRandomNumber());
                        Objects.requireNonNull(server.getPlayer(event.getMessageContent())).sendMessage("§8「§c1st§8」§7» §fCodul tau este: §4" + randInt);
                        event.getChannel().sendMessage("Introdu codul trimis in chat-ul din minecraft. Scrie \"resend\" daca ai nevoie de un nou cod!");
                        step = 3;
                    } else {
                        event.getChannel().sendMessage("Nu esti conectat in **/lobby** pe server-ul de minecraft **mc-1st.ro**!");
                    }
                } else {
                    step = 3;
                    resent = false;
                }
            } catch (NullPointerException e) {
                minecord.error("Error linking user: " + user.getDiscriminatedName() + ". Stack Trace:");
                minecord.error(e.getMessage());
                event.getChannel().sendMessage("Jucatorul nu a fost gasit! Asigura-te ca esti conectat in **/lobby** pe server si ca ti-ai scris numele corect!");
            }
        } else if (step == 3) {
            try {
                if (event.getMessageContent().equals(String.format("%06d", randInt)) && !resent) {
                    db.insertLink(event.getMessageAuthor().getId(), player.getName(), player.getUniqueId());
                    if (minecord.changeNickOnLink) { discordServer.updateNickname(user, player.getName()).join(); }
                    if (addedRole != null) RoleAddListener.runCommands(minecord, roleNames, addCommands, addedRole, player.getName());
                    event.getChannel().sendMessage("Ai fost recompensat, multumim pentru sustinere! :heart:");
                    minecord.sendMessage(player, "Ai fost recompensat!");
                    step = 4;
                    RoleAddListener.removeListener(user, this);
                } else if (resent && event.getMessageContent().equalsIgnoreCase("resend")) {
                    randInt = Integer.parseInt(getRandomNumber());
                    minecord.sendMessage(player, "Codul tau este: §4" + randInt);
                    event.getChannel().sendMessage("Noul cod a fost trimis.");
                    step = 2;
                } else if (!resent && !event.getMessageContent().equals(String.format("%06d", randInt))) {
                    event.getChannel().sendMessage("Codul introdus nu se potriveste. Scrie \"resend\" daca doresti sa primesti un nou cod.");
                }
            } catch (Exception e) {
                minecord.error("Eroare verificare user: " + user.getDiscriminatedName() + ". Stack Trace:");
                minecord.error(e.getMessage());
                event.getChannel().sendMessage("Ceva nu a functionat, contacteaza unul dintre Owneri. :angry:");
            }
        }
    }

    private String getRandomNumber() {
        Random rng = new Random();
        int number = rng.nextInt(999999);
        return String.format("%06d", number);
    }

}
