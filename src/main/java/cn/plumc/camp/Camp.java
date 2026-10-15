package cn.plumc.camp;

import cn.plumc.camp.camp.CampInfo;
import cn.plumc.camp.camp.Member;
import cn.plumc.camp.commands.CampCommand;
import cn.plumc.camp.requests.ApplicationReminders;
import cn.plumc.camp.storage.CampStorage;
import cn.plumc.camp.ui.CampMenus;
import cn.plumc.camp.export.CampExporter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Camp extends JavaPlugin {
    public static Camp INSTANCE;

    public Logger logger;
    private List<String> excepts;
    private Scoreboard scoreboard;
    public List<CampInfo> camps = new ArrayList<>();
    private CampStorage storage;
    private ApplicationReminders reminders;
    private CampMenus menus;
    private CampExporter exporter;

    public Camp() {
        if (Objects.isNull(INSTANCE)) INSTANCE = this;
        logger = getLogger();
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        saveDefaultConfig();
        excepts = getConfig().getStringList("excepts");
        scoreboard = getServer().getScoreboardManager().getMainScoreboard();
        try { storage = new CampStorage(getDataFolder().toPath().resolve("data"), logger); }
        catch (java.io.IOException e) {
            logger.log(Level.SEVERE, "Cannot initialize camp storage", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        CampCommand command = new CampCommand();
        Objects.requireNonNull(getCommand("camp")).setExecutor(command);
        getCommand("camp").setTabCompleter(command);
        reminders = new ApplicationReminders(this);
        menus = new CampMenus(this, command);
        exporter = new CampExporter(this);
        getServer().getPluginManager().registerEvents(reminders, this);
        getServer().getPluginManager().registerEvents(menus, this);
        loadCamps();
        getServer().getOnlinePlayers().forEach(reminders::notifyPlayer);
    }

    @Override public void onDisable() {
        if (menus != null) menus.close();
        if (exporter != null) exporter.close();
        CampCommand.rankUpConfirms.clear();
        if (storage != null) storage.close();
        INSTANCE = null;
    }

    public CampStorage getStorage() { return storage; }
    public ApplicationReminders getReminders() { return reminders; }
    public CampMenus getMenus() { return menus; }
    public CampExporter getExporter() { return exporter; }

    public void loadCamps() {
        camps.clear();
        for (Team team : scoreboard.getTeams()) {
            if (excepts.contains(team.getName())) continue;
            CampInfo camp = new CampInfo(this, team);
            storage.load(camp, getConfig().getInt("logs.preview-limit", 200));
            camps.add(camp);
        }
        logger.info("Loaded " + camps.size() + " camps");
    }

    public void createCamp(@Nullable UUID owner, String id, String name) {
        if (!id.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("阵营 ID 必须为 1 到 16 位字母、数字或下划线。");
        if (name.isBlank() || name.length() > 128) throw new IllegalArgumentException("阵营名称不能为空，且最多 128 个字符。");
        if (scoreboard.getTeam(id) != null || excepts.contains(id)) throw new IllegalArgumentException("阵营 ID 已存在或被排除。");
        Team team = scoreboard.registerNewTeam(id);
        team.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(name));
        CampInfo camp = new CampInfo(this, team);
        camps.add(camp);
        camp.log(owner, "创建阵营 " + id);
        if (Objects.isNull(owner)) return;
        camp.addMember(owner, owner);
        camp.setOwner(owner, owner);
    }

    public void disbandCamp(String id) {
        disbandCamp(null, id);
    }

    public void disbandCamp(@Nullable UUID handler, String id) {
        CampInfo camp = getCamp(id);
        if (camp == null) return;
        camp.log(handler, "解散阵营 " + id);
        camp.team.unregister();
        camps.remove(camp);
        storage.archive(id);
    }

    /** Clean old ranks and applications before the scoreboard moves a player to another team. */
    public void removeMembershipForMove(UUID uuid) {
        for (CampInfo camp : camps) {
            Member member = camp.getExistingMember(uuid);
            if (member != null && member.isOwner()) throw new IllegalArgumentException("请先转让或解散目标玩家拥有的阵营。");
        }
        for (CampInfo camp : camps) {
            Member member = camp.members.get(uuid);
            if (member == null || member.isBlackList()) continue;
            if (member.isMember()) camp.kickMember(null, uuid);
            else if (member.inIDLE()) camp.rejectMember(null, uuid);
        }
    }

    public CampInfo getCamp(String id) {
        for (CampInfo camp : camps) {
            if (camp.id.equals(id)) return camp;
        }
        return null;
    }

    public CampInfo getCamp(UUID player) {
        for (CampInfo camp : camps) {
            if (camp.getExistingMember(player) != null) return camp;
        }
        return null;
    }

    public Member getMember(UUID player) {
        if (!isCampMember(player)) return null;
        for (CampInfo camp : camps) {
            if (camp.getExistingMember(player) != null) return camp.getExistingMember(player);
        }
        return null;
    }

    public boolean hasCamp(String id) {
        return Objects.nonNull(getCamp(id));
    }

    public boolean isCampMember(UUID player){
        for (CampInfo camp : camps) {
            if (Objects.nonNull(camp.getExistingMember(player))) return true;
        }
        return false;
    }
}
