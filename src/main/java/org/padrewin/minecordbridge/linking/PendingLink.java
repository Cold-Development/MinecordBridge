package org.padrewin.minecordbridge.linking;

public class PendingLink {

    private final long discordUserId;
    private final String discordUsername;
    private final String minecraftUsername;
    private final String roleName;
    private String verificationCode;
    private long createdAt;

    public PendingLink(long discordUserId, String discordUsername, String minecraftUsername, String roleName, String verificationCode) {
        this.discordUserId = discordUserId;
        this.discordUsername = discordUsername;
        this.minecraftUsername = minecraftUsername;
        this.roleName = roleName;
        this.verificationCode = verificationCode;
        this.createdAt = System.currentTimeMillis();
    }

    public long getDiscordUserId() { return discordUserId; }
    public String getDiscordUsername() { return discordUsername; }
    public String getMinecraftUsername() { return minecraftUsername; }
    public String getRoleName() { return roleName; }
    public String getVerificationCode() { return verificationCode; }

    public void setVerificationCode(String code) {
        this.verificationCode = code;
        this.createdAt = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - createdAt > 10 * 60 * 1000; // 10 min
    }
}