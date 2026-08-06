package cn.plumc.camp.camp;

import org.bukkit.OfflinePlayer;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

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
        public static UUID uuid(String permission) {
            return UUID.fromString(permission.substring(permission.lastIndexOf(".")+1));
        }
        public static boolean in(Permissions permission, Team team, UUID uuid){
            return team.hasEntry(permission.to(uuid));
        }
        public static boolean isPermission(String key){
            return key.startsWith("permission.");
        }
    }

    public UUID uuid;
    public String name;
    public Team team;
    public OfflinePlayer player;

    public Member(Team team, OfflinePlayer player) {
        this.team = team;
        this.player = player;
        this.name = player.getName();
        this.uuid = player.getUniqueId();
    }

    public void accept(){
        if (!inIDLE()) return;
        team.removeEntry(Permissions.IDLE.to(uuid));
        team.addPlayer(player);
    }

    public void reject(){
        if (!inIDLE()) return;
        team.removeEntry(Permissions.IDLE.to(uuid));
    }

    public void setAdmin(boolean value){
        if (value) {
            team.addEntry(Permissions.ADMIN.to(uuid));
        } else {
            team.removeEntry(Permissions.ADMIN.to(uuid));
        }
    }

    public void kick(){
        team.removeEntry(Permissions.OWNER.to(uuid));
        team.removeEntry(Permissions.ADMIN.to(uuid));
        team.removePlayer(player);
    }

    public void setBlackList(boolean value){
        if (value) {
            team.addEntry(Permissions.BLACK_LIST.to(uuid));
            kick();
        } else {
            team.removeEntry(Permissions.BLACK_LIST.to(uuid));
        }

    }

    public void changeOwner(){
        for (String entry : team.getEntries()) {
            if (entry.startsWith(Permissions.OWNER.permissionPrefix)) {
                team.removeEntry(entry);
            }
        }
        team.addEntry(Permissions.OWNER.to(uuid));
    }

    public void setIDLE() {
        team.addEntry(Permissions.IDLE.to(uuid));
    }

    public boolean isOwner() {
        return isMember() && Permissions.in(Permissions.OWNER, team, uuid);
    }

    public boolean isAdmin() {
        return isMember() && Permissions.in(Permissions.ADMIN, team, uuid);
    }

    public boolean isMember() {
        return !Permissions.in(Permissions.BLACK_LIST, team, uuid) && !Permissions.in(Permissions.IDLE, team, uuid) && team.hasPlayer(player);
    }

    public boolean isBlackList(){
        return Permissions.in(Permissions.BLACK_LIST, team, uuid);
    }

    public boolean inIDLE(){
        return Permissions.in(Permissions.IDLE, team, uuid);
    }

    public boolean hasAuthority(){
        return isMember() && isOwner() || isAdmin();
    }
}
