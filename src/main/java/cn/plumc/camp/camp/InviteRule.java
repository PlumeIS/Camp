package cn.plumc.camp.camp;

import org.bukkit.scoreboard.Team;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;

public enum InviteRule {
    DEFAULT("camp.invite_rule.default", "默认/申请"),
    ACCEPT_ALL("camp.invite_rule.accept_all", "全部接受"),
    ACCEPT_KEY("camp.invite_rule.accept_key", "填写密钥");
    public final String key;
    public final String info;
    InviteRule(String key, String info) {
        this.key = key;
        this.info = info;
    }
    public static InviteRule get(Team team){
        for (String entry : team.getEntries()) {
            if (entry.equals(ACCEPT_ALL.scopedKey(team)) || entry.equals(ACCEPT_ALL.key)) return ACCEPT_ALL;
            if (entry.startsWith(ACCEPT_KEY.scopedKey(team) + ".") || entry.startsWith(ACCEPT_KEY.key + ".")) return ACCEPT_KEY;
        }
        return DEFAULT;
    }

    public static void set(Team team, InviteRule inviteRule, @Nullable String value){
        if (inviteRule == ACCEPT_KEY && (value == null || value.isBlank() || value.length() > 128))
            throw new IllegalArgumentException("密钥不能为空，且最多 128 个字符。");
        clearRule(team);
        switch (inviteRule) {
            case DEFAULT -> team.addEntry(DEFAULT.scopedKey(team));
            case ACCEPT_ALL -> team.addEntry(ACCEPT_ALL.scopedKey(team));
            case ACCEPT_KEY -> team.addEntry(ACCEPT_KEY.scopedKey(team)+"."+value);
        }
    }

    public static void clearRule(Team team) {
        for (String entry : new HashSet<>(team.getEntries())) {
            if (entry.startsWith(ACCEPT_KEY.key + ".") || entry.startsWith(ACCEPT_KEY.scopedKey(team) + ".")) team.removeEntry(entry);
            if (entry.equals(ACCEPT_ALL.key)) team.removeEntry(entry);
            if (entry.equals(DEFAULT.key)) team.removeEntry(entry);
            for (InviteRule rule : values()) if (entry.equals(rule.scopedKey(team))) team.removeEntry(entry);
        }
    }

    public static String getAcceptKey(Team team) {
        for (String entry : team.getEntries()) {
            if (entry.startsWith(ACCEPT_KEY.scopedKey(team) + ".")) return entry.substring(ACCEPT_KEY.scopedKey(team).length()+1);
            if (entry.startsWith(ACCEPT_KEY.key + ".")) return entry.substring(ACCEPT_KEY.key.length()+1);
        }
        return null;
    }

    public static boolean isInviteRule(String key) {
        return key.startsWith("camp.invite_rule.");
    }

    private String scopedKey(Team team) {
        // Identical entries in different teams would otherwise move between camps.
        return key + "@" + Base64.getUrlEncoder().withoutPadding().encodeToString(team.getName().getBytes(StandardCharsets.UTF_8));
    }
}
