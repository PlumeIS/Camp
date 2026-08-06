package cn.plumc.camp;

import cn.plumc.camp.camp.CampInfo;
import cn.plumc.camp.camp.Member;
import cn.plumc.camp.commands.CampCommand;
import net.kyori.adventure.text.Component;
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

    public Camp() {
        if (Objects.isNull(INSTANCE)) INSTANCE = this;
        logger = getLogger();
    }

    @Override
    public void onEnable() {
        excepts = getConfig().getStringList("excepts");
        scoreboard = getServer().getScoreboardManager().getMainScoreboard();
        getServer().getPluginCommand("camp").setExecutor(new CampCommand());
        loadCamps();
    }

    public void loadCamps() {
        camps.clear();
        for (Team team : scoreboard.getTeams()) {
            if (excepts.contains(team.getName())) continue;
            camps.add(new CampInfo(this, team));
        }
        logger.info("Loaded " + camps.size() + " camps");
    }

    public void createCamp(@Nullable UUID owner, String id, String name) {
        Team team = scoreboard.registerNewTeam(id);
        team.displayName(Component.text(name));
        CampInfo camp = new CampInfo(this, team);
        camps.add(camp);
        if (Objects.isNull(owner)) return;
        camp.addMember(owner, owner);
        camp.setOwner(owner, owner);
    }

    public void disbandCamp(String id) {
        scoreboard.getTeam(id).unregister();
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
