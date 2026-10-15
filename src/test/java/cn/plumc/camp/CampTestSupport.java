package cn.plumc.camp;

import cn.plumc.camp.camp.CampInfo;
import cn.plumc.camp.camp.Member;
import cn.plumc.camp.requests.ApplicationReminders;
import cn.plumc.camp.storage.CampStorage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public abstract class CampTestSupport {
    @TempDir protected Path directory;
    protected Camp plugin;
    protected CampStorage storage;
    protected static Server server;
    protected BukkitScheduler scheduler;
    private final Map<String, Team> entryTeams = new HashMap<>();
    private final Map<Team, Set<String>> teams = new HashMap<>();
    private final Map<UUID, OfflinePlayer> players = new HashMap<>();

    @BeforeEach void setup() throws Exception {
        if (server == null) {
            server = mock(Server.class);
            when(server.getLogger()).thenReturn(Logger.getLogger("Camp-tests"));
            when(server.getName()).thenReturn("Paper-test");
            when(server.getVersion()).thenReturn("1.18.2");
            when(server.getBukkitVersion()).thenReturn("1.18.2-R0.1");
            Bukkit.setServer(server);
        }
        reset(server);
        scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getWorlds()).thenReturn(List.of());
        when(server.getOfflinePlayer(any(UUID.class))).thenAnswer(call -> {
            UUID uuid = call.getArgument(0);
            OfflinePlayer existing = players.get(uuid);
            return existing != null ? existing : offline(uuid, "Player" + players.size());
        });
        when(server.getOfflinePlayer(anyString())).thenAnswer(call -> {
            String name = call.getArgument(0);
            return players.values().stream().filter(p -> name.equals(p.getName())).findFirst().orElseGet(() -> offline(UUID.nameUUIDFromBytes(name.getBytes()), name));
        });
        when(server.getOfflinePlayers()).thenAnswer(call -> players.values().toArray(OfflinePlayer[]::new));
        when(server.getOnlinePlayers()).thenReturn(List.of());
        when(server.getLogger()).thenReturn(Logger.getLogger("Camp-tests"));
        plugin = mock(Camp.class);
        plugin.camps = new ArrayList<>();
        plugin.logger = Logger.getLogger("Camp-tests");
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(plugin.logger);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getCamp(anyString())).thenAnswer(call -> plugin.camps.stream().filter(c -> c.id.equals(call.getArgument(0))).findFirst().orElse(null));
        when(plugin.hasCamp(anyString())).thenAnswer(call -> plugin.getCamp(call.getArgument(0, String.class)) != null);
        when(plugin.getCamp(any(UUID.class))).thenAnswer(call -> plugin.camps.stream().filter(c -> c.hasExistingMember(call.getArgument(0))).findFirst().orElse(null));
        when(plugin.isCampMember(any())).thenAnswer(call -> call.getArgument(0) != null && plugin.getCamp(call.getArgument(0, UUID.class)) != null);
        when(plugin.getMember(any())).thenAnswer(call -> {
            CampInfo camp = plugin.getCamp(call.getArgument(0, UUID.class));
            return camp == null ? null : camp.getExistingMember(call.getArgument(0));
        });
        storage = new CampStorage(directory.resolve("data"), plugin.logger);
        when(plugin.getStorage()).thenReturn(storage);
        when(plugin.getReminders()).thenReturn(mock(ApplicationReminders.class));
        Camp.INSTANCE = plugin;
    }

    @AfterEach void cleanup() {
        storage.close();
        Camp.INSTANCE = null;
        cn.plumc.camp.commands.CampCommand.rankUpConfirms.clear();
    }

    protected OfflinePlayer offline(UUID uuid, String name) {
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn(name);
        players.put(uuid, player);
        return player;
    }

    protected Player player(UUID uuid, String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn(name);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        players.put(uuid, player);
        return player;
    }

    protected Team team(String id, String... entries) {
        Team team = mock(Team.class);
        Set<String> data = new HashSet<>();
        teams.put(team, data);
        when(team.getName()).thenReturn(id);
        when(team.displayName()).thenReturn(Component.text(id));
        when(team.prefix()).thenReturn(Component.empty());
        when(team.suffix()).thenReturn(Component.empty());
        for (Team.Option option : Team.Option.values()) when(team.getOption(option)).thenReturn(Team.OptionStatus.ALWAYS);
        when(team.getEntries()).thenAnswer(call -> new HashSet<>(data));
        when(team.hasEntry(anyString())).thenAnswer(call -> data.contains(call.getArgument(0)));
        doAnswer(call -> {
            String entry = call.getArgument(0);
            Team old = entryTeams.put(entry, team);
            if (old != null) teams.get(old).remove(entry);
            data.add(entry);
            return null;
        }).when(team).addEntry(anyString());
        when(team.removeEntry(anyString())).thenAnswer(call -> {
            String entry = call.getArgument(0);
            boolean removed = data.remove(entry);
            entryTeams.remove(entry, team);
            return removed;
        });
        for (String entry : entries) team.addEntry(entry);
        return team;
    }

    protected CampInfo camp(String id, String... entries) {
        CampInfo camp = new CampInfo(plugin, team(id, entries));
        plugin.camps.add(camp);
        return camp;
    }

    protected Member member(CampInfo camp, OfflinePlayer player, boolean admin, boolean owner) {
        camp.team.addEntry(player.getName());
        Member member = new Member(camp.team, player);
        camp.members.put(member.uuid, member);
        if (admin) member.setAdmin(true);
        if (owner) member.changeOwner();
        return member;
    }
}
