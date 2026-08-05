package cn.plumc.camp;

import cn.plumc.camp.camp.CampInfo;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Camp extends JavaPlugin {
    public static Camp INSTANCE;

    private List<String> excepts;
    private Scoreboard scoreboard;
    public List<CampInfo> camps = new ArrayList<>();

    public Camp() {
        if (Objects.isNull(INSTANCE)) INSTANCE = this;
    }

    @Override
    public void onEnable() {
        excepts = getConfig().getStringList("excepts");
        scoreboard = getServer().getScoreboardManager().getMainScoreboard();
        loadCamps();
    }

    public void loadCamps() {
        camps.clear();
        for (Team team : scoreboard.getTeams()) {
            if (!excepts.contains(team.getName())) continue;
            camps.add(new CampInfo(this, team));
        }
    }

    public CampInfo createCamp(UUID owner, String id, String name) {
        Team team = scoreboard.registerNewTeam(id);
        team.displayName(Component.text(name));
        CampInfo camp = new CampInfo(this, team);
        camps.add(camp);
        camp.addMember(owner, owner);
        camp.setOwner(owner, owner);
        return camp;
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

    public boolean isCampMember(UUID player){
        for (CampInfo camp : camps) {
            if (Objects.nonNull(camp.getExistingMember(player))) return true;
        }
        return false;
    }
}
