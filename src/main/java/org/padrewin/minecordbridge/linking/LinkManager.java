package org.padrewin.minecordbridge.linking;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LinkManager {

    private final ConcurrentHashMap<String, PendingLink> pendingByMcName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PendingLink> pendingByDiscordId = new ConcurrentHashMap<>();

    public PendingLink createPendingLink(long discordUserId, String discordUsername, String mcUsername, String roleName, String code) {
        cleanExpired();

        PendingLink existing = pendingByDiscordId.get(discordUserId);
        if (existing != null && !existing.isExpired()) {
            return null;
        }
        if (existing != null) removePending(discordUserId);

        PendingLink pending = new PendingLink(discordUserId, discordUsername, mcUsername, roleName, code);
        pendingByMcName.put(mcUsername.toLowerCase(), pending);
        pendingByDiscordId.put(discordUserId, pending);
        return pending;
    }

    public PendingLink getPendingByDiscordId(long discordUserId) {
        PendingLink p = pendingByDiscordId.get(discordUserId);
        if (p != null && p.isExpired()) { removePending(discordUserId); return null; }
        return p;
    }

    public PendingLink verifyCode(long discordUserId, String code) {
        PendingLink p = pendingByDiscordId.get(discordUserId);
        if (p == null || p.isExpired()) {
            if (p != null) removePending(discordUserId);
            return null;
        }
        if (p.getVerificationCode().equals(code)) {
            removePending(discordUserId);
            return p;
        }
        return null;
    }

    public void removePending(long discordUserId) {
        PendingLink p = pendingByDiscordId.remove(discordUserId);
        if (p != null) pendingByMcName.remove(p.getMinecraftUsername().toLowerCase());
    }

    public void cleanExpired() {
        List<Long> toRemove = new ArrayList<>();
        for (Map.Entry<Long, PendingLink> e : pendingByDiscordId.entrySet()) {
            if (e.getValue().isExpired()) toRemove.add(e.getKey());
        }
        toRemove.forEach(this::removePending);
    }

    public void clearAll() {
        pendingByMcName.clear();
        pendingByDiscordId.clear();
    }
}