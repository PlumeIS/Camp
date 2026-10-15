package cn.plumc.camp.camp;

import cn.plumc.camp.Camp;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.List;

public class CampInfo {
    public HashMap<UUID, Member> members;
    public HashMap<Long, String> tempLogs;
    public final Map<UUID, String> applicationReasons = new HashMap<>();
    public Plugin plugin;
    public Team team;
    public InviteRule inviteRule;
    public String id;
    public Component name;
    public String nameContent;
    public TextColor color;

    public CampInfo(Plugin plugin, Team team){
        this.plugin = plugin;
        this.team = team;
        this.inviteRule = InviteRule.get(team);
        this.id = team.getName();
        this.name = team.displayName();
        this.nameContent = LegacyComponentSerializer.legacyAmpersand().serialize(name);
        this.color = team.hasColor() ? team.color() : TextColor.color(Color.WHITE.asRGB());
        this.tempLogs = new HashMap<>();
        loadMember();
        // Loading is read-only: legacy entries continue to work without a bulk migration.
    }

    private void loadMember(){
        HashSet<OfflinePlayer> players = new HashSet<>();
        Server server = plugin.getServer();
        for (String entry : team.getEntries()) {
            if (Member.Permissions.isPermission(entry)) players.add(server.getOfflinePlayer(Member.Permissions.uuid(entry)));
            else if (entry.matches("[A-Za-z0-9_]{1,16}")) players.add(server.getOfflinePlayer(entry));
        }
        this.members = new HashMap<>();
        for (OfflinePlayer player : players) {
            members.put(player.getUniqueId(), new Member(team, player));
        }
    }

    public boolean inviteMember(UUID handler, UUID uuid, String message) {
        if (plugin instanceof Camp camp && (camp.isCampMember(uuid) || camp.camps.stream().anyMatch(c -> {
            Member member = c.members.get(uuid);
            return member != null && member.inIDLE();
        }))) return false;
        if (inBlackList(uuid)) return false;
        if (hasExistingMember(uuid)) return false;
        if (members.containsKey(uuid) && members.get(uuid).inIDLE()) return false;
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        members.put(uuid, new Member(team, player));
        members.get(uuid).setIDLE();
        applicationReasons.put(uuid, message == null || message.isBlank() ? "未填写理由" : message);
        saveReasons();
        log(handler, "%s 提交加入申请%s".formatted(player.getName(), Objects.nonNull(message)? ": "+message : ""));
        if (plugin instanceof Camp camp) camp.getReminders().notifyCamp(this);
        return true;
    }

    public boolean acceptMember(UUID handler, UUID uuid) {
        if (plugin instanceof Camp camp && camp.isCampMember(uuid)) return false;
        if (hasExistingMember(uuid)) return false;
        Member member = members.get(uuid);
        if (Objects.isNull(member)) return false;
        if (!member.inIDLE()) return false;
        member.accept();
        applicationReasons.remove(uuid);
        saveReasons();
        log(handler, "成员 %s 的申请已被接受".formatted(member.name));
        return true;
    }

    public boolean rejectMember(UUID handler, UUID uuid) {
        if (hasExistingMember(uuid)) return false;
        Member member = members.get(uuid);
        if (Objects.isNull(member)) return false;
        if (!member.inIDLE()) return false;
        member.reject();
        members.remove(uuid);
        applicationReasons.remove(uuid);
        saveReasons();
        log(handler, "成员 %s 的申请已被拒绝".formatted(member.name));
        return true;
    }

    public boolean addMember(UUID handler, UUID uuid) {
        if (hasExistingMember(uuid)) return false;
        if (inBlackList(uuid)) return false;
        if (plugin instanceof Camp camp) camp.removeMembershipForMove(uuid);
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        Member member = new Member(team, player);
        member.join();
        members.put(uuid, member);
        applicationReasons.remove(uuid);
        saveReasons();
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
            team.addEntry(Member.Permissions.BLACK_LIST.to(team, uuid));
            members.put(uuid, new Member(team, plugin.getServer().getOfflinePlayer(uuid)));
        } else {
            if (member.isBlackList() && value) return false;
            member.setBlackList(value);
        }
        applicationReasons.remove(uuid);
        saveReasons();
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
        this.inviteRule = inviteRule;
        log(handler, "加入规则已被修改为 %s".formatted(inviteRule.info));
        return true;
    }

    public boolean setAcceptKey(UUID handler, String key){
        InviteRule.set(team, InviteRule.ACCEPT_KEY, key);
        this.inviteRule = InviteRule.ACCEPT_KEY;
        log(handler, "加入规则已被修改为 %s（密钥已更新）".formatted(this.inviteRule.info));
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
        long time = System.currentTimeMillis();
        while (tempLogs.containsKey(time)) time++;
        String actor = handler == null ? "控制台" : plugin.getServer().getOfflinePlayer(handler).getName();
        if (actor == null) actor = handler.toString();
        String line = "处理人: %s 操作: %s".formatted(actor, message);
        tempLogs.put(time, line);
        int limit = Math.max(1, plugin.getConfig().getInt("logs.preview-limit", 200));
        if (tempLogs.size() > limit) tempLogs.remove(Collections.min(tempLogs.keySet()));
        if (plugin instanceof Camp camp) camp.getStorage().appendLog(id, time, line);
    }

    public void saveReasons() {
        if (plugin instanceof Camp camp) camp.getStorage().saveReasons(id, applicationReasons);
    }
}
