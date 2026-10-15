package cn.plumc.camp.camp;

import org.bukkit.OfflinePlayer;
import org.bukkit.scoreboard.Team;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Member {
    public enum Permissions{
        OWNER("permission.owner"),
        ADMIN("permission.admin"),
        MEMBER("permission.member"),
        IDLE("permission.idle"),
        BLACK_LIST("permission.black_list");
        public final String permissionPrefix;
        Permissions(String permissionPrefix){
            this.permissionPrefix = permissionPrefix;
        }
        public String to(UUID uuid){
            return permissionPrefix+"."+uuid.toString();
        }
        public String to(Team team, UUID uuid) {
            // Membership is exclusive, so ordinary ranks and the single pending request keep their legacy format.
            return this == BLACK_LIST ? scoped(team, uuid) : to(uuid);
        }
        private String scoped(Team team, UUID uuid) {
            // Entries are unique across the entire scoreboard, including blacklist entries.
            String camp = Base64.getUrlEncoder().withoutPadding().encodeToString(team.getName().getBytes(StandardCharsets.UTF_8));
            return permissionPrefix + "." + camp + "." + uuid;
        }
        public static UUID uuid(String permission) {
            return UUID.fromString(permission.substring(permission.lastIndexOf(".")+1));
        }
        public static boolean in(Permissions permission, Team team, UUID uuid){
            return team.hasEntry(permission.scoped(team, uuid)) || team.hasEntry(permission.to(uuid));
        }
        public static boolean isPermission(String key){
            for (Permissions permission : values()) {
                if (key.startsWith(permission.permissionPrefix + ".")) {
                    try { uuid(key); return true; } catch (IllegalArgumentException ignored) { return false; }
                }
            }
            return false;
        }
    }

    public UUID uuid;
    public String name;
    public Team team;
    public OfflinePlayer player;

    public Member(Team team, OfflinePlayer player) {
        this.team = team;
        this.player = player;
        this.name = player.getName() == null ? player.getUniqueId().toString() : player.getName();
        this.uuid = player.getUniqueId();
    }

    public void accept(){
        if (!inIDLE()) return;
        remove(Permissions.IDLE);
        join();
    }

    public void reject(){
        if (!inIDLE()) return;
        remove(Permissions.IDLE);
    }

    public void setAdmin(boolean value){
        if (value) {
            team.addEntry(Permissions.ADMIN.to(team, uuid));
        } else {
            remove(Permissions.ADMIN);
        }
    }

    public void kick(){
        remove(Permissions.OWNER);
        remove(Permissions.ADMIN);
        remove(Permissions.MEMBER);
        remove(Permissions.IDLE);
        team.removeEntry(name);
        if (player.getName() != null) team.removeEntry(player.getName());
    }

    public void setBlackList(boolean value){
        if (value) {
            team.addEntry(Permissions.BLACK_LIST.to(team, uuid));
            kick();
        } else {
            remove(Permissions.BLACK_LIST);
        }

    }

    public void changeOwner(){
        for (String entry : new java.util.HashSet<>(team.getEntries())) {
            if (Permissions.isPermission(entry) && entry.startsWith(Permissions.OWNER.permissionPrefix + ".")) {
                team.removeEntry(entry);
            }
        }
        team.addEntry(Permissions.OWNER.to(team, uuid));
    }

    public void setIDLE() {
        team.addEntry(Permissions.IDLE.to(team, uuid));
    }

    public boolean isOwner() {
        return isMember() && Permissions.in(Permissions.OWNER, team, uuid);
    }

    public boolean isAdmin() {
        return isMember() && Permissions.in(Permissions.ADMIN, team, uuid);
    }

    public boolean isMember() {
        return !isBlackList() && !inIDLE() && (Permissions.in(Permissions.MEMBER, team, uuid)
                || (player.getName() != null && team.hasEntry(player.getName())));
    }

    public boolean isBlackList(){
        return Permissions.in(Permissions.BLACK_LIST, team, uuid);
    }

    public boolean inIDLE(){
        return Permissions.in(Permissions.IDLE, team, uuid);
    }

    public boolean hasAuthority(){
        return isMember() && (isOwner() || isAdmin());
    }

    public void join() {
        remove(Permissions.IDLE);
        team.addEntry(Permissions.MEMBER.to(team, uuid));
        if (player.getName() != null) team.addEntry(player.getName());
    }

    private void remove(Permissions permission) {
        team.removeEntry(permission.scoped(team, uuid));
        team.removeEntry(permission.to(uuid));
    }

    public String rank() {
        if (isBlackList()) return "黑名单";
        if (inIDLE()) return "待审核";
        if (isOwner()) return "拥有者 (3)";
        if (isAdmin()) return "管理员 (2)";
        return isMember() ? "成员 (1)" : "无成员权限 (0)";
    }
}
