package cn.plumc.camp.export;

import cn.plumc.camp.Camp;
import cn.plumc.camp.camp.CampInfo;
import cn.plumc.camp.camp.Member;
import cn.plumc.camp.ui.CampMenus;
import cn.plumc.camp.utils.TeamColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class CampExporter implements AutoCloseable {
    public record Snapshot(List<CampWorkbook.Row> allRows, List<CampWorkbook.Row> publicRows) {
        public Snapshot { allRows = List.copyOf(allRows); publicRows = List.copyOf(publicRows); }
    }
    private final Camp plugin;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Camp-export");
        thread.setDaemon(true);
        return thread;
    });

    public CampExporter(Camp plugin) { this.plugin = plugin; }

    public void export(CommandSender sender) {
        if (!running.compareAndSet(false, true)) {
            sender.sendMessage(Component.text("[Camp] 已有导出任务正在执行。", NamedTextColor.YELLOW));
            return;
        }
        Snapshot snapshot;
        try { snapshot = snapshots(); }
        catch (RuntimeException e) { running.set(false); throw e; }
        Path directory = plugin.getDataFolder().toPath().resolve("exports");
        LocalDate date = LocalDate.now();
        sender.sendMessage(Component.text("[Camp] 开始导出 " + plugin.camps.size() + " 个阵营…", NamedTextColor.YELLOW));
        writer.submit(() -> {
            try {
                CampExportFiles.Pair files = CampExportFiles.write(directory, date, snapshot.allRows(), snapshot.publicRows());
                report(sender, "[Camp] 完整表已导出: " + files.all(), NamedTextColor.GREEN);
                report(sender, "[Camp] 玩家公示表已导出: " + files.publicFile(), NamedTextColor.GREEN);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Cannot export camps", e);
                report(sender, "[Camp] 导出失败: " + e.getMessage(), NamedTextColor.RED);
            } finally { running.set(false); }
        });
    }

    /** Team API access stays on the server thread; only this immutable snapshot reaches the writer. */
    public List<CampWorkbook.Row> snapshot() {
        return snapshots().allRows();
    }

    public Snapshot snapshots() {
        List<CampWorkbook.Row> rows = new ArrayList<>();
        List<CampWorkbook.Row> publicRows = new ArrayList<>();
        plugin.camps.stream().sorted(Comparator.comparing(c -> c.id)).forEach(camp -> {
            List<Member> members = camp.members.values().stream().filter(m -> m.isMember() || m.inIDLE() || m.isBlackList())
                    .sorted(Comparator.comparingInt((Member m) -> m.isOwner() ? 3 : m.isAdmin() ? 2 : m.isMember() ? 1 : 0).reversed().thenComparing(m -> m.name)).toList();
            if (members.isEmpty()) rows.add(row(camp, null));
            else members.forEach(member -> rows.add(row(camp, member)));
            List<Member> official = members.stream().filter(Member::isMember).toList();
            if (official.isEmpty()) publicRows.add(publicRow(camp, null));
            else official.forEach(member -> publicRows.add(publicRow(camp, member)));
        });
        return new Snapshot(rows, publicRows);
    }

    private CampWorkbook.Row row(CampInfo camp, Member member) {
        String attrs = String.join("\n", CampMenus.campAttributes(camp, true));
        String name = PlainTextComponentSerializer.plainText().serialize(camp.team.displayName());
        int rgb = camp.team.hasColor() ? camp.team.color().value() : 0xffffff;
        return new CampWorkbook.Row(rgb, List.of(camp.id, name, TeamColors.name(camp.team),
                member == null ? "" : member.name, member == null ? "" : member.uuid.toString(),
                member == null ? "空阵营" : member.isBlackList() ? "黑名单" : member.inIDLE() ? "待审核" : "成员",
                member == null ? "" : member.isOwner() ? "3" : member.isAdmin() ? "2" : member.isMember() ? "1" : "0",
                flag(member != null && member.isOwner()), flag(member != null && member.isAdmin()), flag(member != null && member.isMember()),
                flag(member != null && member.inIDLE()), flag(member != null && member.isBlackList()), camp.inviteRule.info,
                member != null && member.inIDLE() ? camp.applicationReasons.getOrDefault(member.uuid, "未填写理由") : "", attrs));
    }

    private CampWorkbook.Row publicRow(CampInfo camp, Member member) {
        String condition = switch (camp.inviteRule) {
            case DEFAULT -> "申请加入，需管理员审核";
            case ACCEPT_ALL -> "自由加入";
            case ACCEPT_KEY -> "填写密钥加入";
        };
        int rgb = camp.team.hasColor() ? camp.team.color().value() : 0xffffff;
        return new CampWorkbook.Row(rgb, List.of(camp.id, PlainTextComponentSerializer.plainText().serialize(camp.team.displayName()),
                TeamColors.name(camp.team), condition, member == null ? "" : member.name,
                member == null ? "" : member.isOwner() ? "拥有者" : member.isAdmin() ? "管理员" : "成员"));
    }

    private static String flag(boolean value) { return value ? "是" : "否"; }

    private void report(CommandSender sender, String message, NamedTextColor color) {
        plugin.getLogger().info(message);
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage(Component.text(message, color)));
    }

    @Override public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) plugin.getLogger().severe("Export writer did not finish within 30 seconds.");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
