package cn.plumc.camp.camp;

import org.bukkit.scoreboard.Team;

import javax.annotation.Nullable;

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
            if (entry.equals(ACCEPT_ALL.key)) return ACCEPT_ALL;
            if (entry.startsWith(ACCEPT_ALL.key)) return ACCEPT_KEY;
        }
        return DEFAULT;
    }

    public static void set(Team team, InviteRule inviteRule, @Nullable String value){
        clearRule(team);
        switch (inviteRule) {
            case DEFAULT -> team.addEntry(DEFAULT.key);
            case ACCEPT_ALL -> team.addEntry(ACCEPT_ALL.key);
            case ACCEPT_KEY -> team.addEntry(ACCEPT_KEY.key+"."+value);
        }
    }

    public static void clearRule(Team team) {
        for (String entry : team.getEntries()) {
            if (entry.startsWith(ACCEPT_KEY.key)) team.removeEntry(entry);
            if (entry.equals(ACCEPT_ALL.key)) team.removeEntry(entry);
            if (entry.equals(DEFAULT.key)) team.removeEntry(entry);
        }
    }

    public static String getAcceptKey(Team team) {
        for (String entry : team.getEntries()) {
            if (entry.startsWith(ACCEPT_KEY.key)) return entry.substring(ACCEPT_KEY.key.length()+1);
        }
        return null;
    }

    public static boolean isInviteRule(String key) {
        return key.startsWith("camp.invite_rule.");
    }
}
