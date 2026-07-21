package cn.plumc.camp.camp;

import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class CampInfo {
    private HashMap<UUID, Member> members;
    public HashMap<Long, String> tempLogs;
    public Plugin plugin;
    public Team team;
    public InviteRule inviteRule;
    public String id;
    public Component name;

    public CampInfo(Plugin plugin, Team team){
        this.plugin = plugin;
        this.team = team;
        this.inviteRule = InviteRule.get(team);
        this.id = team.getName();
        this.name = team.displayName();
        this.tempLogs = new HashMap<>();
        loadMember();
    }

    private void loadMember(){
        HashSet<OfflinePlayer> players = new HashSet<>();
        Server server = plugin.getServer();
        for (String entry : team.getEntries()) {
            if (Member.Permissions.isPermission(entry)) players.add(server.getOfflinePlayer(Member.Permissions.uuid(entry)));
            if (!InviteRule.isInviteRule(entry)) players.add(server.getOfflinePlayer(entry));
        }
        this.members = new HashMap<>();
        for (OfflinePlayer player : players) {
            members.put(player.getUniqueId(), new Member(team, player));
        }
    }

    public boolean inviteMember(UUID handler, UUID uuid, String message) {
        if (inBlackList(uuid)) return false;
        if (hasExistingMember(uuid)) return false;
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        members.put(uuid, new Member(team, player));
        members.get(uuid).setIDLE();
        log(handler, "%s 提交加入申请%s".formatted(player.getName(), Objects.nonNull(message)? ": "+message : ""));
        return true;
    }

    public boolean acceptMember(UUID handler, UUID uuid) {
        if (hasExistingMember(uuid)) return false;
        Member member = members.get(uuid);
        if (Objects.isNull(member)) return false;
        if (!member.inIDLE()) return false;
        member.accept();
        log(handler, "成员 %s 的邀请已被接受".formatted(member.name));
        return true;
    }

    public boolean rejectMember(UUID handler, UUID uuid) {
        if (hasExistingMember(uuid)) return false;
        Member member = members.get(uuid);
        if (Objects.isNull(member)) return false;
        if (!member.inIDLE()) return false;
        member.reject();
        members.remove(uuid);
        log(handler, "成员 %s 的邀请已被拒绝".formatted(member.name));
        return true;
    }

    public boolean addMember(UUID handler, UUID uuid) {
        if (hasExistingMember(uuid)) return false;
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        team.addPlayer(player);
        members.put(uuid, new Member(team, player));
        log(handler, "添加成员 %s".formatted(player.getName()));
        return true;
    }

    public boolean kickMember(UUID handler, UUID uuid) {
        if (!hasExistingMember(uuid)) return false;
        getExistingMember(uuid).kick();
        members.remove(uuid);
        log(handler, "踢出成员 %s".formatted(plugin.getServer().getOfflinePlayer(uuid).getName()));
        return true;
    }

    public boolean setAdmin(UUID handler, UUID uuid, boolean value) {
        if (!hasExistingMember(uuid)) return false;
        getExistingMember(uuid).setAdmin(value);
        log(handler, "%s管理员 %s".formatted(value? "任命": "罢免", plugin.getServer().getOfflinePlayer(uuid).getName()));
        return true;
    }

    public boolean setBlackList(UUID handler, UUID uuid, boolean value) {
        Member member = members.get(uuid);
        if (Objects.isNull(member)) {
            if (!value) return false;
            team.addEntry(Member.Permissions.BLACK_LIST.to(uuid));
            members.put(uuid, new Member(team, plugin.getServer().getOfflinePlayer(uuid)));
        } else {
            if (member.isBlackList() && value) return false;
            member.setBlackList(value);
        }
        log(handler, "%s黑名单 %s".formatted(value? "添加": "移除", plugin.getServer().getOfflinePlayer(uuid).getName()));
        return true;
    }

    public boolean setOwner(UUID handler, UUID uuid) {
        if (!hasExistingMember(uuid)) return false;
        getExistingMember(uuid).changeOwner();
        log(handler, "%s 被设为新拥有者".formatted(plugin.getServer().getOfflinePlayer(uuid).getName()));
        return true;
    }

    public boolean setInviteRule(UUID handler, InviteRule inviteRule) {
        InviteRule.set(team, inviteRule, null);
        log(handler, "加入规则已被修改为 %s".formatted(inviteRule.info));
        return true;
    }

    public boolean setAcceptKey(UUID handler, String key){
        InviteRule.set(team, this.inviteRule, key);
        return true;
    }

    public boolean hasExistingMember(UUID uuid) {
        return members.containsKey(uuid) && members.get(uuid).isMember();
    }

    public boolean inBlackList(UUID uuid) {
        return members.containsKey(uuid) && members.get(uuid).isBlackList();
    }

    public Member getExistingMember(UUID uuid) {
        return hasExistingMember(uuid) ? members.get(uuid) : null;
    }

    public Member getBlackListMember(UUID uuid) {
        return members.containsKey(uuid) && members.get(uuid).isBlackList() ? members.get(uuid) : null;
    }

    public HashMap<UUID, Member> getMembers() {
        HashMap<UUID, Member> members = new HashMap<>();
        this.members.forEach((key, value) -> {
            if (value.isMember()) members.put(key, value);
        });
        return members;
    }

    public List<UUID> getBlackList() {
        List<UUID> blackList = new ArrayList<>();
        this.members.forEach((key, value) -> {
            if (value.isBlackList()) blackList.add(key);
        });
        return  blackList;
    }

    public void log(UUID handler, String message) {
        tempLogs.put(
                System.currentTimeMillis(),
                "处理人: %s 操作: %s".formatted(plugin.getServer().getOfflinePlayer(handler).getName(), message)
        );
    }
}
