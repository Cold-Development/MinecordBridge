package org.padrewin.minecordbridge.listeners.discord;

import org.javacord.api.DiscordApi;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.interaction.SlashCommandOptionType;
import org.padrewin.minecordbridge.MinecordBridge;

import java.util.Arrays;

public class SlashCommandRegistrar {

    private final MinecordBridge minecord = MinecordBridge.getPlugin();

    public void registerCommands(DiscordApi api) {
        try {
            SlashCommand.with("mcord", "MinecordBridge - linkare cont Minecraft",
                    Arrays.asList(
                            SlashCommandOption.createWithOptions(
                                    SlashCommandOptionType.SUB_COMMAND,
                                    "link",
                                    "Linkează contul tău Minecraft la Discord",
                                    Arrays.asList(
                                            SlashCommandOption.create(
                                                    SlashCommandOptionType.STRING,
                                                    "username",
                                                    "Numele tău exact de Minecraft",
                                                    true
                                            ),
                                            SlashCommandOption.create(
                                                    SlashCommandOptionType.STRING,
                                                    "code",
                                                    "Codul de verificare din Minecraft (pasul 2)",
                                                    false
                                            )
                                    )
                            ),
                            SlashCommandOption.create(
                                    SlashCommandOptionType.SUB_COMMAND,
                                    "status",
                                    "Verifică statusul linkării tale"
                            )
                    )
            ).createForServer(api.getServerById(minecord.serverID).get()).join();

            minecord.log(MinecordBridge.ANSI_GREEN + "Slash commands registered!" + MinecordBridge.ANSI_RESET);
        } catch (Exception e) {
            minecord.error("Error registering slash commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void unregisterCommands(DiscordApi api) {
        try {
            api.getServerById(minecord.serverID).ifPresent(server ->
                    server.getSlashCommands().join().forEach(cmd -> {
                        if (cmd.getName().equalsIgnoreCase("mcord")) {
                            cmd.deleteForServer(server).join();
                        }
                    })
            );
        } catch (Exception e) {
            minecord.error("Error unregistering slash commands: " + e.getMessage());
        }
    }
}