package org.padrewin.minecordbridge.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.javacord.api.entity.user.User;
import org.jetbrains.annotations.NotNull;
import org.padrewin.minecordbridge.MinecordBridge;
import org.padrewin.minecordbridge.database.Database;
import org.padrewin.minecordbridge.javacord.JavacordHelper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MCBCommand implements CommandExecutor {

    private static final int LIST_PAGE_SIZE = 5;
    private static final long SECONDS_PER_MONTH = 30L * 24 * 60 * 60;
    private static final DateTimeFormatter BOOST_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault());

    private final MinecordBridge minecord = MinecordBridge.getPlugin();
    private final JavacordHelper js;

    public MCBCommand() {
        js = minecord.js;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, String[] args) {
        if (args.length == 0) {
            return false;
        }

        if (sender instanceof Player player) {

            if (args[0].equalsIgnoreCase("boost")) {
                // Fara permission.
                // Pluginul decide singur daca playerul poate revendica milestone-ul.
                handleBoostCommand(player, args);
                return true;
            }

            // Toate celelalte subcomenzi sunt administrative.
            if (!player.hasPermission("minecord.admin")) {
                minecord.sendMessage(player, "Commands.no_permission");
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                minecord.reload();
                minecord.loadMessagesConfig();
                minecord.sendMessage(player, "Commands.reload_success");
                return true;

            } else if (args[0].equalsIgnoreCase("unlink")) {
                handleUnlinkCommand(player, args);
                return true;

            } else if (args[0].equalsIgnoreCase("info")) {
                handleInfoCommand(player, args);
                return true;

            } else if (args[0].equalsIgnoreCase("list")) {
                handleListCommand(player, args);
                return true;

            } else {
                minecord.sendMessage(player, "Commands.command_not_found");
                return true;
            }

        } else if (sender instanceof ConsoleCommandSender) {

            if (args[0].equalsIgnoreCase("reload")) {
                minecord.reload();
                minecord.loadMessagesConfig();
                minecord.getLogger().info(minecord.getMessage("Commands.reload_success"));
                return true;

            } else if (args[0].equalsIgnoreCase("info") && args.length >= 2) {
                handleInfoCommandConsole(args);
                return true;

            } else if (args[0].equalsIgnoreCase("list")) {
                handleListCommandConsole(args);
                return true;

            } else {
                minecord.warn("Command not found!");
                return true;
            }
        }

        return false;
    }

    private void handleInfoCommand(Player player, String[] args) {
        if (args.length < 2) {
            minecord.sendMessage(player, "Commands.invalid_usage_info");
            return;
        }

        String query = args[1];
        Database db = MinecordBridge.getDatabase();
        List<Map.Entry<String, String>> allLinked = db.getAllLinkedUsersWithDiscordID();

        // Search by MC username
        for (Map.Entry<String, String> entry : allLinked) {
            String mcName = entry.getKey();
            String discordId = entry.getValue();

            if (mcName.equalsIgnoreCase(query)) {
                String discordName = getDiscordName(discordId);
                player.sendMessage(minecord.applyHexColors(minecord.pluginTag +
                        "&fMinecraft: &a" + mcName + " &8| &fDiscord: &9" + discordName + " &8(&7" + discordId + "&8)"));
                return;
            }
        }

        // Search by Discord name (partial match)
        for (Map.Entry<String, String> entry : allLinked) {
            String mcName = entry.getKey();
            String discordId = entry.getValue();
            String discordName = getDiscordName(discordId);

            if (discordName.toLowerCase().contains(query.toLowerCase())) {
                player.sendMessage(minecord.applyHexColors(minecord.pluginTag +
                        "&fMinecraft: &a" + mcName + " &8| &fDiscord: &9" + discordName + " &8(&7" + discordId + "&8)"));
                return;
            }
        }

        // Search by Discord ID
        for (Map.Entry<String, String> entry : allLinked) {
            String mcName = entry.getKey();
            String discordId = entry.getValue();

            if (discordId.equals(query)) {
                String discordName = getDiscordName(discordId);
                player.sendMessage(minecord.applyHexColors(minecord.pluginTag +
                        "&fMinecraft: &a" + mcName + " &8| &fDiscord: &9" + discordName + " &8(&7" + discordId + "&8)"));
                return;
            }
        }

        minecord.sendMessage(player, "Commands.info_not_found");
    }

    private void handleInfoCommandConsole(String[] args) {
        String query = args[1];
        Database db = MinecordBridge.getDatabase();
        List<Map.Entry<String, String>> allLinked = db.getAllLinkedUsersWithDiscordID();

        for (Map.Entry<String, String> entry : allLinked) {
            String mcName = entry.getKey();
            String discordId = entry.getValue();
            String discordName = getDiscordName(discordId);

            if (mcName.equalsIgnoreCase(query) || discordName.toLowerCase().contains(query.toLowerCase()) || discordId.equals(query)) {
                minecord.log("Minecraft: " + mcName + " | Discord: " + discordName + " (" + discordId + ")");
                return;
            }
        }

        minecord.warn("No linked account found for: " + query);
    }

    private void handleListCommand(Player player, String[] args) {
        Integer page = parsePage(args);
        if (page == null) {
            minecord.sendMessage(player, "Commands.invalid_usage_list");
            return;
        }

        List<Map.Entry<String, String>> allLinked = MinecordBridge.getDatabase().getAllLinkedUsersWithDiscordID();
        if (allLinked.isEmpty()) {
            minecord.sendMessage(player, "Commands.list_empty");
            return;
        }

        int totalPages = (int) Math.ceil(allLinked.size() / (double) LIST_PAGE_SIZE);
        if (page > totalPages) {
            player.sendMessage(minecord.applyHexColors(minecord.pluginTag +
                    minecord.getMessage("Commands.list_invalid_page").replace("%pages%", String.valueOf(totalPages))));
            return;
        }

        player.sendMessage(minecord.applyHexColors(minecord.pluginTag +
                minecord.getMessage("Commands.list_header")
                        .replace("%page%", String.valueOf(page))
                        .replace("%pages%", String.valueOf(totalPages))));

        int fromIndex = (page - 1) * LIST_PAGE_SIZE;
        int toIndex = Math.min(fromIndex + LIST_PAGE_SIZE, allLinked.size());

        for (int i = fromIndex; i < toIndex; i++) {
            Map.Entry<String, String> entry = allLinked.get(i);
            String mcName = entry.getKey();
            String discordId = entry.getValue();
            String discordName = getDiscordName(discordId);

            player.sendMessage(minecord.applyHexColors(minecord.pluginTag +
                    "&fMinecraft: &a" + mcName + " &8| &fDiscord: &9" + discordName + " &8(&7" + discordId + "&8)" + getBoostSuffix(discordId)));
        }

        if (page < totalPages) {
            player.sendMessage(minecord.applyHexColors(minecord.pluginTag +
                    minecord.getMessage("Commands.list_next_page").replace("%next%", String.valueOf(page + 1))));
        }
    }

    private void handleListCommandConsole(String[] args) {
        Integer page = parsePage(args);
        if (page == null) {
            minecord.warn("Invalid page number: " + args[1]);
            return;
        }

        List<Map.Entry<String, String>> allLinked = MinecordBridge.getDatabase().getAllLinkedUsersWithDiscordID();
        if (allLinked.isEmpty()) {
            minecord.warn("No linked accounts found.");
            return;
        }

        int totalPages = (int) Math.ceil(allLinked.size() / (double) LIST_PAGE_SIZE);
        if (page > totalPages) {
            minecord.warn("Page " + page + " does not exist. Total pages: " + totalPages);
            return;
        }

        minecord.log("Linked accounts (page " + page + "/" + totalPages + "):");

        int fromIndex = (page - 1) * LIST_PAGE_SIZE;
        int toIndex = Math.min(fromIndex + LIST_PAGE_SIZE, allLinked.size());

        for (int i = fromIndex; i < toIndex; i++) {
            Map.Entry<String, String> entry = allLinked.get(i);
            String mcName = entry.getKey();
            String discordId = entry.getValue();
            String discordName = getDiscordName(discordId);
            minecord.log("Minecraft: " + mcName + " | Discord: " + discordName + " (" + discordId + ")" + getBoostSuffixConsole(discordId));
        }
    }

    private String getBoostSuffix(String discordId) {
        if (!minecord.boostTrackingEnabled) return "";

        Database db = MinecordBridge.getDatabase();
        long id = Long.parseLong(discordId);
        Long boostSince = db.getBoostSince(id);
        if (boostSince == null) {
            return " &8| &7Not boosting &8| &7Claimed: &7N/A";
        }

        return " &8| &fBoosting since: &b" + BOOST_DATE_FORMAT.format(Instant.ofEpochSecond(boostSince)) +
                " &8| &fClaimed: &e" + formatClaimedMilestones(db.getClaimedMilestones(id));
    }

    private String getBoostSuffixConsole(String discordId) {
        if (!minecord.boostTrackingEnabled) return "";

        Database db = MinecordBridge.getDatabase();
        long id = Long.parseLong(discordId);
        Long boostSince = db.getBoostSince(id);
        if (boostSince == null) {
            return " | Not boosting | Claimed: N/A";
        }

        return " | Boosting since: " + BOOST_DATE_FORMAT.format(Instant.ofEpochSecond(boostSince)) +
                " | Claimed: " + formatClaimedMilestones(db.getClaimedMilestones(id));
    }

    private String formatClaimedMilestones(Set<Integer> claimedMonths) {
        if (claimedMonths.isEmpty()) return "N/A";
        return claimedMonths.stream().sorted().map(months -> months + "mo").collect(Collectors.joining(", "));
    }

    private Integer parsePage(String[] args) {
        if (args.length < 2) {
            return 1;
        }

        try {
            int page = Integer.parseInt(args[1]);
            return page < 1 ? 1 : page;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void handleBoostCommand(Player player, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("claim")) {
            minecord.sendMessage(player, "Commands.invalid_usage_boost");
            return;
        }

        if (!minecord.boostTrackingEnabled) {
            minecord.sendMessage(player, "Commands.boost_tracking_disabled");
            return;
        }

        int requestedMilestone;

        try {
            requestedMilestone = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            minecord.sendMessage(player, "Commands.invalid_usage_boost");
            return;
        }

        Database db = MinecordBridge.getDatabase();

        // Verifica daca playerul are contul de Discord link-uit.
        String discordId = db.getDiscordID(player.getUniqueId());

        if (discordId == null) {
            minecord.sendMessage(player, "Commands.no_linked_account");
            return;
        }

        long id;

        try {
            id = Long.parseLong(discordId);
        } catch (NumberFormatException e) {
            minecord.sendMessage(player, "Commands.no_linked_account");
            return;
        }

        // Verifica daca boosteaza in momentul de fata.
        Long boostSince = db.getBoostSince(id);

        if (boostSince == null) {
            minecord.sendMessage(player, "Commands.not_boosting");
            return;
        }

        // Pluginul considera o luna = exact 30 zile.
        long monthsBoosted =
                (Instant.now().getEpochSecond() - boostSince) / SECONDS_PER_MONTH;

        Set<Integer> claimed = db.getClaimedMilestones(id);

        // Cauta milestone-ul cerut direct in configuratia pluginului.
        MinecordBridge.BoostMilestone targetMilestone = null;

        for (MinecordBridge.BoostMilestone milestone : minecord.boostMilestones) {
            if (milestone.months() == requestedMilestone) {
                targetMilestone = milestone;
                break;
            }
        }

        // Milestone-ul cerut nu exista in config.
        if (targetMilestone == null) {
            minecord.sendMessage(player, "Commands.no_milestone_available");
            return;
        }

        // Nu au trecut suficiente perioade de 30 zile.
        if (monthsBoosted < targetMilestone.months()) {
            minecord.sendMessage(player, "Commands.no_milestone_available");
            return;
        }

        // Milestone-ul a fost deja revendicat.
        if (claimed.contains(targetMilestone.months())) {
            minecord.sendMessage(player, "Commands.no_milestone_available");
            return;
        }

        // Executa DOAR reward-urile milestone-ului cerut.
        for (String rewardCommand : targetMilestone.commands()) {
            String formattedCommand = rewardCommand
                    .replace("%user%", player.getName())
                    .replace("%uuid%", player.getUniqueId().toString());

            minecord.getServer().dispatchCommand(
                    minecord.getServer().getConsoleSender(),
                    formattedCommand
            );
        }

        // Marcheaza DOAR milestone-ul acesta drept revendicat.
        db.addClaimedMilestone(id, targetMilestone.months());

        minecord.log(
                player.getName()
                        + " claimed boost milestone "
                        + targetMilestone.months()
                        + "."
        );

        player.sendMessage(
                minecord.applyHexColors(
                        minecord.pluginTag
                                + "&fAi colectat milestone-ul &#FF0000#"
                                + targetMilestone.months()
                                + "&f!"
                )
        );
    }

    private String getDiscordName(String discordId) {
        try {
            User user = js.api.getUserById(discordId).join();
            return user != null ? user.getDiscriminatedName() : "Unknown";
        } catch (Exception e) {
            return "Unknown (" + discordId + ")";
        }
    }

    private void handleUnlinkCommand(Player player, String[] args) {
        if (args.length < 3) {
            minecord.sendMessage(player, "Commands.invalid_usage_unlink");
            return;
        }

        String discriminatedName = args[1];
        String roleName = args[2];

        if (!discriminatedName.contains("#")) {
            minecord.sendMessage(player, "Commands.invalid_username_format");
            return;
        }

        List<String> roles = minecord.getConfig().getStringList("roles");
        if (!roles.contains(roleName)) {
            minecord.sendMessage(player, "Commands.role_not_found" + roleName);
            minecord.sendMessage(player, "Commands.note_case_sensitive");
            return;
        }

        try {
            User user = js.api.getServerById(minecord.serverID).get().getMemberByDiscriminatedName(discriminatedName).orElse(null);
            if (user == null) {
                minecord.sendMessage(player, "Commands.player_not_found_discord");
                return;
            }

            Database db = MinecordBridge.getDatabase();
            long discordId = user.getId();

            if (!db.doesEntryExist(discordId)) {
                minecord.sendMessage(player, "Commands.no_linked_account");
                return;
            }

            String minecraftUsername = db.getUsername(discordId);
            if (minecraftUsername == null || minecraftUsername.isEmpty()) {
                minecord.sendMessage(player, "Commands.no_minecraft_username");
                return;
            }

            // The stored role is authoritative; older rows without it retain
            // the command argument for backwards compatibility.
            String linkedRoleName = db.getRoleName(discordId);
            executeRemoveCommands(minecraftUsername,
                    linkedRoleName == null || linkedRoleName.isBlank() ? roleName : linkedRoleName);
            db.removeLink(discordId);

            minecord.sendMessage(player, "Commands.account_unlinked");
        } catch (Exception e) {
            minecord.sendMessage(player, "Commands.error_linking_user" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void executeRemoveCommands(String minecraftUsername, String roleName) {
        try {
            List<String> removeCommands = minecord.getConfig().getStringList(roleName + ".remove-commands");

            for (String command : removeCommands) {
                String formattedCommand = command.replace("%user%", minecraftUsername);
                minecord.getServer().dispatchCommand(minecord.getServer().getConsoleSender(), formattedCommand);
            }

            minecord.log(minecraftUsername + " has lost benefits from role " + roleName + ".");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
