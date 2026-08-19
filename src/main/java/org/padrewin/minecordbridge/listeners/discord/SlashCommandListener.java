package org.padrewin.minecordbridge.listeners.discord;

import org.bukkit.entity.Player;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommandInteraction;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.listener.interaction.SlashCommandCreateListener;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.padrewin.minecordbridge.linking.LinkManager;
import org.padrewin.minecordbridge.linking.PendingLink;

import java.util.Optional;
import java.util.Random;

public class SlashCommandListener implements SlashCommandCreateListener {

    private final MinecordBridge minecord;
    private final Database db;
    private final LinkManager linkManager;
    private final Role[] roles;

    public SlashCommandListener(Role[] roles) {
        this.minecord = MinecordBridge.getPlugin();
        this.db = MinecordBridge.getDatabase();
        this.linkManager = minecord.getLinkManager();
        this.roles = roles;
    }

    @Override
    public void onSlashCommandCreate(SlashCommandCreateEvent event) {
        SlashCommandInteraction interaction = event.getSlashCommandInteraction();
        if (!interaction.getCommandName().equalsIgnoreCase("mcord")) return;

        String subcommand = interaction.getOptions().isEmpty() ? "" : interaction.getOptions().get(0).getName();

        switch (subcommand.toLowerCase()) {
            case "link" -> handleLink(interaction);
            case "status" -> handleStatus(interaction);
            default -> interaction.createImmediateResponder()
                    .setContent("❌ Comandă necunoscută. Folosește `/mcord link` sau `/mcord status`.")
                    .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL)
                    .respond();
        }
    }

    private void handleLink(SlashCommandInteraction interaction) {
        User user = interaction.getUser();
        Server server = interaction.getServer().orElse(null);
        if (server == null) {
            interaction.createImmediateResponder()
                    .setContent("❌ Această comandă poate fi folosită doar pe un server Discord!")
                    .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
            return;
        }

        // Already linked?
        if (db.doesEntryExist(user.getId())) {
            interaction.createImmediateResponder()
                    .setContent("✅ Contul tău este deja linkuit! Folosește `/mcord status` pentru detalii.")
                    .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
            return;
        }

        // Has eligible role?
        Role matchedRole = null;
        String matchedRoleName = null;
        for (Role role : roles) {
            if (role != null && user.getRoles(server).stream().anyMatch(userRole -> userRole.getId() == role.getId())) {
                matchedRole = role;
                for (String name : minecord.roleNames) {
                    if (minecord.roleAndID.get(name) != null &&
                            minecord.roleAndID.get(name).equals(String.valueOf(role.getId()))) {
                        matchedRoleName = name;
                        break;
                    }
                }
                break;
            }
        }
        if (matchedRole == null) {
            interaction.createImmediateResponder()
                    .setContent("❌ Nu ai un rol eligibil pentru a-ți linka contul. Asigură-te că ai rolul necesar (ex: Nitro Booster).")
                    .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
            return;
        }

        // Parse options from subcommand
        Optional<SlashCommandInteractionOption> subCmd = interaction.getOptions().stream().findFirst();
        if (subCmd.isEmpty()) return;

        Optional<SlashCommandInteractionOption> usernameOpt = subCmd.get().getOptionByName("username");
        Optional<SlashCommandInteractionOption> codeOpt = subCmd.get().getOptionByName("code");

        String mcUsername = usernameOpt.isPresent() ? usernameOpt.get().getStringValue().orElse(null) : null;
        String code = codeOpt.isPresent() ? codeOpt.get().getStringValue().orElse(null) : null;

        // =============================================
        // STEP 2: /mcord link <username> <code>
        // =============================================
        if (mcUsername != null && code != null) {
            PendingLink pending = linkManager.getPendingByDiscordId(user.getId());
            if (pending == null) {
                interaction.createImmediateResponder()
                        .setContent("❌ Nu ai o cerere de linkare activă. Rulează mai întâi `/mcord link username:<NumeMinecraft>` fără cod.")
                        .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
                return;
            }

            if (!pending.getMinecraftUsername().equalsIgnoreCase(mcUsername)) {
                interaction.createImmediateResponder()
                        .setContent("❌ Numele nu corespunde cu cererea activă. Ai inițiat linkarea pentru **" + pending.getMinecraftUsername() + "**.")
                        .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
                return;
            }

            PendingLink verified = linkManager.verifyCode(user.getId(), code);
            if (verified == null) {
                interaction.createImmediateResponder()
                        .setContent("❌ Codul nu este corect! Verifică codul din chat-ul Minecraft și încearcă din nou.")
                        .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
                return;
            }

            // SUCCESS — link accounts
            Player mcPlayer = minecord.getServer().getPlayer(verified.getMinecraftUsername());
            if (mcPlayer != null && mcPlayer.isOnline()) {
                db.insertLink(user.getId(), mcPlayer.getName(), mcPlayer.getUniqueId(), verified.getRoleName());
                mcPlayer.sendMessage(minecord.applyHexColors(minecord.pluginTag + minecord.getMessage("Player-facing-messages.rewarded")));
            } else {
                org.bukkit.OfflinePlayer offlinePlayer = minecord.getServer().getOfflinePlayer(verified.getMinecraftUsername());
                db.insertLink(user.getId(), verified.getMinecraftUsername(), offlinePlayer.getUniqueId(), verified.getRoleName());
            }

            // Change nickname if configured
            if (minecord.changeNickOnLink) {
                try {
                    server.updateNickname(user, verified.getMinecraftUsername()).join();
                } catch (Exception ignored) {}
            }

            // Run add commands for the role
            if (verified.getRoleName() != null) {
                for (Role r : roles) {
                    String roleId = minecord.roleAndID.get(verified.getRoleName());
                    if (roleId != null && roleId.equals(String.valueOf(r.getId()))) {
                        RoleAddListener.runCommands(minecord, minecord.roleNames, minecord.addCommands, r, verified.getMinecraftUsername());
                        break;
                    }
                }
            }

            interaction.createImmediateResponder()
                    .setContent("🎉 **Cont linkuit cu succes!** Beneficiile au fost aplicate. Mulțumim pentru suport! ❤️")
                    .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();

            minecord.log(verified.getMinecraftUsername() + " linked to Discord user " + verified.getDiscordUsername());
            return;
        }

        // =============================================
        // STEP 1: /mcord link <username> (fără cod)
        // =============================================
        if (mcUsername == null) {
            interaction.createImmediateResponder()
                    .setContent("❌ Introdu numele tău de Minecraft. Exemplu: `/mcord link username:NumeMinecraft`")
                    .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
            return;
        }

        // Check if already has pending
        PendingLink existingPending = linkManager.getPendingByDiscordId(user.getId());
        if (existingPending != null) {
            interaction.createImmediateResponder()
                    .setContent("⏳ Ai deja o cerere activă pentru **" + existingPending.getMinecraftUsername() + "**.\nFolosește `/mcord link username:" + existingPending.getMinecraftUsername() + " code:<cod>` pentru a finaliza.")
                    .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
            return;
        }

        // Player MUST be online
        Player mcPlayer = minecord.getServer().getPlayer(mcUsername);
        if (mcPlayer == null || !mcPlayer.isOnline()) {
            interaction.createImmediateResponder()
                    .setContent("❌ Jucătorul **" + mcUsername + "** nu este conectat pe server!\n\n" +
                            "📋 **Ce trebuie să faci:**\n" +
                            "1️⃣ Conectează-te în **/lobby** pe **mc-1st.ro** cu contul **" + mcUsername + "**\n" +
                            "2️⃣ Apoi rulează din nou `/mcord link username:" + mcUsername + "`")
                    .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
            return;
        }

        // Generate code, create pending, send code to MC player
        String verificationCode = String.format("%06d", new Random().nextInt(999999));

        PendingLink pending = linkManager.createPendingLink(
                user.getId(), user.getDiscriminatedName(), mcUsername, matchedRoleName, verificationCode);

        if (pending == null) {
            interaction.createImmediateResponder()
                    .setContent("❌ Eroare la crearea cererii. Încearcă din nou.")
                    .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
            return;
        }

        // Send code to Minecraft player
        mcPlayer.sendMessage(" ");
        mcPlayer.sendMessage(minecord.applyHexColors(minecord.pluginTag +
                minecord.getMessage("Player-facing-messages.your_code").replace("%code%", verificationCode)));
        mcPlayer.sendMessage(" ");

        interaction.createImmediateResponder()
                .setContent("✅ Cod de verificare trimis în chat-ul Minecraft al lui **" + mcUsername + "**!\n\n" +
                        "📋 **Pasul următor:**\n" +
                        "Copiază codul din Minecraft și rulează:\n" +
                        "`/mcord link username:" + mcUsername + " code:<codul_primit>`\n\n" +
                        "⏰ Codul expiră în 10 minute.")
                .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
    }

    private void handleStatus(SlashCommandInteraction interaction) {
        User user = interaction.getUser();

        if (db.doesEntryExist(user.getId())) {
            String username = db.getUsername(user.getId());
            interaction.createImmediateResponder()
                    .setContent("✅ Contul tău este linkuit la: **" + (username != null ? username : "necunoscut") + "**")
                    .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
        } else {
            PendingLink pending = linkManager.getPendingByDiscordId(user.getId());
            if (pending != null) {
                interaction.createImmediateResponder()
                        .setContent("⏳ Ai o cerere activă pentru: **" + pending.getMinecraftUsername() + "**. Completează verificarea cu `/mcord link username:" + pending.getMinecraftUsername() + " code:<cod>`!")
                        .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
            } else {
                interaction.createImmediateResponder()
                        .setContent("❌ Contul tău nu este linkuit. Folosește `/mcord link username:<NumeMinecraft>` pentru a începe!")
                        .setFlags(org.javacord.api.entity.message.MessageFlag.EPHEMERAL).respond();
            }
        }
    }
}
