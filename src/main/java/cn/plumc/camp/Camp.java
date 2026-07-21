package cn.plumc.camp;

import cn.plumc.camp.camp.CampInfo;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Camp extends JavaPlugin {
    public static Camp INSTANCE;

    public List<CampInfo> camps = new ArrayList<>();

    public Camp() {
        if (Objects.isNull(INSTANCE)) INSTANCE = this;
    }

    @Override
    public void onEnable() {
        for (Team team : getServer().getScoreboardManager().getMainScoreboard().getTeams()) {
            camps.add(new CampInfo(this, team));
        }
    }
}
