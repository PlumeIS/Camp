package cn.plumc.camp.requests;

import cn.plumc.camp.Camp;
import cn.plumc.camp.camp.CampInfo;
import cn.plumc.camp.camp.Member;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;

public final class ApplicationReminders implements Listener {
    private final Camp plugin;
    private final Map<UUID, Long> days = new HashMap<>();

    public ApplicationReminders(Camp plugin) {
        this.plugin = plugin;
        plugin.getServer().getWorlds().forEach(w -> days.put(w.getUID(), Math.floorDiv(w.getFullTime(), 24000)));
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkDays, 20, 100);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (!player.isOnline()) return;
            for (CampInfo camp : plugin.camps) {
                Member member = camp.getExistingMember(player.getUniqueId());
                if (member != null && !member.name.equals(player.getName())) {
                    camp.team.removeEntry(member.name);
                    member.name = player.getName();
                    camp.team.addEntry(player.getName());
                }
            }
            notifyPlayer(player);
        }, 20);
    }

    private void checkDays() {
        Set<UUID> worlds = new HashSet<>();
        for (World world : plugin.getServer().getWorlds()) {
            worlds.add(world.getUID());
            long day = Math.floorDiv(world.getFullTime(), 24000);
            Long previous = days.put(world.getUID(), day);
            if (previous != null && previous != day) world.getPlayers().forEach(this::notifyPlayer);
        }
        days.keySet().retainAll(worlds);
    }

    public void notifyPlayer(Player player) {
        CampInfo camp = plugin.getCamp(player.getUniqueId());
        if (camp != null) notifyPlayer(player, camp);
    }

    public void notifyCamp(CampInfo camp) {
        plugin.getServer().getOnlinePlayers().forEach(player -> notifyPlayer(player, camp));
    }

    private void notifyPlayer(Player player, CampInfo camp) {
        Member admin = camp.getExistingMember(player.getUniqueId());
        if (admin == null || !admin.hasAuthority()) return;
        boolean accept = player.isOp() || player.hasPermission("camp.member.accept");
        boolean reject = player.isOp() || player.hasPermission("camp.member.reject");
        if (!accept && !reject) return;
        camp.members.values().stream().filter(Member::inIDLE).sorted(Comparator.comparing(m -> m.name)).forEach(member -> {
            Component message = Component.text("[Camp] " + camp.nameContent + " 收到 " + member.name + " 的加入申请\n理由："
                    + camp.applicationReasons.getOrDefault(member.uuid, "未填写理由") + " ", NamedTextColor.YELLOW);
            if (accept) message = message.append(button("[接受]", "accept", camp.id, member.uuid, NamedTextColor.GREEN));
            if (reject) message = message.append(Component.space()).append(button("[拒绝]", "reject", camp.id, member.uuid, NamedTextColor.RED));
            player.sendMessage(message);
        });
    }

    private Component button(String label, String action, String camp, UUID uuid, NamedTextColor color) {
        return Component.text(label, color).clickEvent(ClickEvent.runCommand("/camp member " + action + " " + camp + " " + uuid));
    }
}
