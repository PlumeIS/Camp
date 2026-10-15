package cn.plumc.camp;

import cn.plumc.camp.camp.*;
import cn.plumc.camp.commands.CampCommand;
import cn.plumc.camp.export.CampExporter;
import cn.plumc.camp.requests.ApplicationReminders;
import cn.plumc.camp.utils.SenderChecker;
import cn.plumc.camp.utils.TeamColors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CampBehaviorTest extends CampTestSupport {
    @Test void nonOperatorsCannotModifyColorEvenWithPermissionAndOwnerOrAdminRank() {
        CampCommand commands = new CampCommand();
        for (int rank = 1; rank <= 3; rank++) {
            Player actor = player(UUID.randomUUID(), "Actor" + rank);
            CampInfo camp = camp("rank" + rank);
            member(camp, actor, rank >= 2, rank == 3);
            assertTrue(actor.hasPermission("camp.modify.color"));
            commands.modify(actor, new String[]{"modify", "color", "red"}, new SenderChecker(actor));
            commands.modify(actor, new String[]{"modify", "color", camp.id, "red"}, new SenderChecker(actor));
            verify(camp.team, never()).color(any(net.kyori.adventure.text.format.NamedTextColor.class));
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.WHITE, camp.color);
            assertTrue(camp.tempLogs.isEmpty());
        }
    }

    @Test void operatorCanModifyColorOfAnotherCampWithoutMembership() {
        Player operator = player(UUID.randomUUID(), "Operator");
        when(operator.isOp()).thenReturn(true);
        CampInfo camp = camp("target");
        new CampCommand().modify(operator, new String[]{"modify", "color", "target", "red"}, new SenderChecker(operator));
        verify(camp.team).color(net.kyori.adventure.text.format.NamedTextColor.RED);
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.RED, camp.color);
        assertEquals(1, camp.tempLogs.size());
    }

    @Test void loadsLegacyRanksAndApplicationsWithoutRewritingEntries() {
        UUID owner = UUID.randomUUID(), applicant = UUID.randomUUID();
        offline(owner, "Owner"); offline(applicant, "Applicant");
        String[] entries = {"Owner", Member.Permissions.OWNER.to(owner), Member.Permissions.IDLE.to(applicant), "camp.invite_rule.accept_key.secret", "camp.attribute.example"};
        CampInfo camp = camp("legacy", entries);
        assertEquals(new HashSet<>(Arrays.asList(entries)), camp.team.getEntries());
        assertTrue(camp.getExistingMember(owner).isOwner());
        assertTrue(camp.members.get(applicant).inIDLE());
        assertEquals("secret", InviteRule.getAcceptKey(camp.team));
        assertFalse(Member.Permissions.isPermission("permission.idle.not-a-uuid"));
    }

    @Test void identicalRulesAndBlacklistStatesCoexistAcrossCamps() {
        CampInfo a = camp("a"), b = camp("b");
        InviteRule.set(a.team, InviteRule.ACCEPT_ALL, null);
        InviteRule.set(b.team, InviteRule.ACCEPT_ALL, null);
        assertEquals(InviteRule.ACCEPT_ALL, InviteRule.get(a.team));
        assertEquals(InviteRule.ACCEPT_ALL, InviteRule.get(b.team));
        InviteRule.set(a.team, InviteRule.ACCEPT_KEY, "same.key");
        InviteRule.set(b.team, InviteRule.ACCEPT_KEY, "same.key");
        assertEquals("same.key", InviteRule.getAcceptKey(a.team));
        assertEquals("same.key", InviteRule.getAcceptKey(b.team));
        UUID blocked = UUID.randomUUID();
        assertTrue(a.setBlackList(null, blocked, true));
        assertTrue(b.setBlackList(null, blocked, true));
        assertTrue(a.inBlackList(blocked));
        assertTrue(b.inBlackList(blocked));
        assertTrue(a.setBlackList(null, blocked, false));
        assertFalse(a.inBlackList(blocked));
        assertTrue(b.inBlackList(blocked));
        assertThrows(IllegalArgumentException.class, () -> InviteRule.set(a.team, InviteRule.ACCEPT_KEY, ""));
        assertEquals("same.key", InviteRule.getAcceptKey(a.team));
    }

    @Test void normalAdminCanClickReminderAcceptAndRejectUsingUuidAndExplicitCamp() {
        Player admin = player(UUID.randomUUID(), "Admin");
        CampInfo camp = camp("a");
        member(camp, admin, true, false);
        UUID accept = UUID.randomUUID(), reject = UUID.randomUUID();
        offline(accept, "Accept"); offline(reject, "Reject");
        assertTrue(camp.inviteMember(accept, accept, "一起建造"));
        assertTrue(camp.inviteMember(reject, reject, "临时加入"));
        ApplicationReminders reminders = new ApplicationReminders(plugin);
        reminders.notifyPlayer(admin);
        ArgumentCaptor<Component> messages = ArgumentCaptor.forClass(Component.class);
        verify(admin, times(2)).sendMessage(messages.capture());
        assertTrue(messages.getAllValues().stream().anyMatch(c -> PlainTextComponentSerializer.plainText().serialize(c).contains("一起建造")));
        List<ClickEvent> clicks = new ArrayList<>();
        messages.getAllValues().forEach(c -> collectClicks(c, clicks));
        assertTrue(clicks.stream().anyMatch(c -> c.value().equals("/camp member accept a " + accept)));
        CampCommand command = new CampCommand();
        command.member(admin, new String[]{"member", "accept", "a", accept.toString()}, new SenderChecker(admin));
        command.member(admin, new String[]{"member", "reject", "a", reject.toString()}, new SenderChecker(admin));
        assertTrue(camp.hasExistingMember(accept));
        assertFalse(camp.members.containsKey(reject));
        assertTrue(camp.applicationReasons.isEmpty());
    }

    @Test void reminderHonorsEachActionPermissionAndAdminMembership() {
        Player admin = player(UUID.randomUUID(), "Admin");
        CampInfo camp = camp("a");
        member(camp, admin, true, false);
        UUID pending = UUID.randomUUID();
        camp.inviteMember(null, pending, "reason");
        when(admin.hasPermission("camp.member.accept")).thenReturn(false);
        ApplicationReminders reminders = new ApplicationReminders(plugin);
        reminders.notifyPlayer(admin);
        ArgumentCaptor<Component> message = ArgumentCaptor.forClass(Component.class);
        verify(admin).sendMessage(message.capture());
        List<ClickEvent> clicks = new ArrayList<>(); collectClicks(message.getValue(), clicks);
        assertEquals(1, clicks.size());
        assertTrue(clicks.get(0).value().contains(" reject "));
        clearInvocations(admin);
        when(admin.hasPermission("camp.member.reject")).thenReturn(false);
        reminders.notifyPlayer(admin);
        verify(admin, never()).sendMessage(any(Component.class));
        Player outsider = player(UUID.randomUUID(), "Other");
        reminders.notifyPlayer(outsider);
        verify(outsider, never()).sendMessage(any(Component.class));
    }

    @Test void pendingApplicantCannotBeAcceptedAfterJoiningAnotherCamp() {
        UUID uuid = UUID.randomUUID();
        Player player = player(uuid, "Mover");
        CampInfo a = camp("a"), b = camp("b");
        a.inviteMember(null, uuid, "reason");
        member(b, player, false, false);
        assertFalse(a.acceptMember(null, uuid));
        assertTrue(a.members.get(uuid).inIDLE());
        assertFalse(a.hasExistingMember(uuid));
    }

    @Test void outsidersCannotManageExplicitCampAndNoNullPointerOccurs() {
        Player outsider = player(UUID.randomUUID(), "Other");
        CampInfo own = camp("own"), target = camp("target");
        member(own, outsider, true, false);
        UUID pending = UUID.randomUUID(); target.inviteMember(null, pending, "reason");
        assertDoesNotThrow(() -> new CampCommand().member(outsider, new String[]{"member", "accept", "target", pending.toString()}, new SenderChecker(outsider)));
        assertTrue(target.members.get(pending).inIDLE());
    }

    @Test void joinAndDayChangeTriggerRemindersOnlyOncePerDayTransition() {
        Player admin = player(UUID.randomUUID(), "Admin");
        CampInfo camp = camp("a"); member(camp, admin, true, false);
        camp.inviteMember(null, UUID.randomUUID(), "reason");
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getFullTime()).thenReturn(23999L);
        when(world.getPlayers()).thenReturn(List.of(admin));
        when(server.getWorlds()).thenReturn(List.of(world));
        ArgumentCaptor<Runnable> poll = ArgumentCaptor.forClass(Runnable.class);
        ApplicationReminders reminders = new ApplicationReminders(plugin);
        verify(scheduler).runTaskTimer(eq(plugin), poll.capture(), eq(20L), eq(100L));
        poll.getValue().run();
        verify(admin, never()).sendMessage(any(Component.class));
        when(world.getFullTime()).thenReturn(24000L);
        poll.getValue().run(); poll.getValue().run();
        verify(admin, times(1)).sendMessage(any(Component.class));
        reminders.onJoin(new PlayerJoinEvent(admin, Component.empty()));
        ArgumentCaptor<Runnable> join = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), join.capture(), eq(20L));
        join.getValue().run();
        verify(admin, times(2)).sendMessage(any(Component.class));
    }

    @Test void promotionConfirmationCannotBeClearedByAnOlderTimeout() {
        Player owner = player(UUID.randomUUID(), "Owner");
        CampInfo camp = camp("a"); member(camp, owner, true, true);
        Player a = player(UUID.randomUUID(), "AdminA"), b = player(UUID.randomUUID(), "AdminB");
        member(camp, a, true, false); member(camp, b, true, false);
        CampCommand commands = new CampCommand();
        commands.auth(owner, new String[]{"auth", "rankup", a.getUniqueId().toString()}, new SenderChecker(owner));
        commands.auth(owner, new String[]{"auth", "rankup", b.getUniqueId().toString()}, new SenderChecker(owner));
        ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(2)).runTaskLater(eq(plugin), timeout.capture(), eq(400L));
        timeout.getAllValues().get(0).run();
        assertEquals(b.getUniqueId(), CampCommand.rankUpConfirms.get(owner.getUniqueId()).target());
        commands.auth(owner, new String[]{"auth", "rankup", b.getUniqueId().toString()}, new SenderChecker(owner));
        assertTrue(camp.getExistingMember(b.getUniqueId()).isOwner());
        assertFalse(camp.getExistingMember(owner.getUniqueId()).isOwner());
        timeout.getAllValues().get(1).run();
        assertTrue(CampCommand.rankUpConfirms.isEmpty());
    }

    @Test void snapshotIncludesEmptyCampsPendingStatesAndOnlyCampAttributes() {
        CampInfo empty = camp("empty", "camp.attribute.custom.value");
        CampInfo a = camp("a");
        member(a, player(UUID.randomUUID(), "Owner"), true, true);
        UUID pending = UUID.randomUUID(); a.inviteMember(null, pending, "because");
        a.setBlackList(null, UUID.randomUUID(), true);
        try (CampExporter exporter = new CampExporter(plugin)) {
            var snapshot = exporter.snapshot();
            assertEquals(4, snapshot.size());
            assertTrue(snapshot.stream().anyMatch(row -> row.cells().get(5).equals("空阵营")
                    && row.cells().get(14).equals("名称: empty\nID: empty\n颜色: white\n加入规则: 默认/申请")));
            for (var row : snapshot) {
                String attributes = row.cells().get(14);
                assertFalse(attributes.contains("camp.attribute.custom.value"));
                assertFalse(attributes.contains("友伤"));
                assertFalse(attributes.contains("隐身"));
                assertFalse(attributes.contains("前缀"));
                assertFalse(attributes.contains("后缀"));
                assertFalse(attributes.contains("COLLISION_RULE"));
            }
            assertTrue(empty.team.hasEntry("camp.attribute.custom.value"));
            assertTrue(snapshot.stream().anyMatch(row -> row.cells().get(6).equals("3")));
            assertTrue(snapshot.stream().anyMatch(row -> row.cells().get(5).equals("黑名单")));
            assertTrue(snapshot.stream().anyMatch(row -> row.cells().get(13).equals("because")));
        }
    }

    private static void collectClicks(Component component, List<ClickEvent> events) {
        if (component.clickEvent() != null) events.add(component.clickEvent());
        component.children().forEach(c -> collectClicks(c, events));
    }

    @Test void operatorSelfApplicationAllowsFreeTextAndDoesNotInviteTheNamedPlayer() {
        Player operator = player(UUID.randomUUID(), "Operator"); when(operator.isOp()).thenReturn(true);
        Player known = player(UUID.randomUUID(), "Known");
        CampInfo a = camp("a");
        CampCommand commands = new CampCommand();
        commands.join(operator, new String[]{"join", "a", "--self", "Known"}, new SenderChecker(operator));
        assertTrue(a.members.get(operator.getUniqueId()).inIDLE());
        assertFalse(a.members.containsKey(known.getUniqueId()));
        a.rejectMember(null, operator.getUniqueId());
        commands.join(operator, new String[]{"join", "a", "--self", "申请一起建造，多字理由"}, new SenderChecker(operator));
        assertEquals("申请一起建造，多字理由", a.applicationReasons.get(operator.getUniqueId()));
    }

    @Test void explicitCampGuardsPreventStaleLeaveAndRankOperations() {
        Player operator = player(UUID.randomUUID(), "Operator"); when(operator.isOp()).thenReturn(true);
        Player target = player(UUID.randomUUID(), "Target");
        CampInfo a = camp("a"), b = camp("b");
        member(b, operator, false, false); member(b, target, false, false);
        CampCommand commands = new CampCommand();
        commands.auth(operator, new String[]{"auth", "rankup", "a", target.getUniqueId().toString()}, new SenderChecker(operator));
        commands.leave(operator, new String[]{"leave", "a"}, new SenderChecker(operator));
        assertFalse(b.getExistingMember(target.getUniqueId()).isAdmin());
        assertTrue(b.hasExistingMember(operator.getUniqueId()));
    }

    @Test void legacyKeyNamedLikeRuleAndExplicitCampNamedLikeRuleBothWork() {
        Player owner = player(UUID.randomUUID(), "Owner");
        CampInfo own = camp("own"); member(own, owner, true, true);
        CampCommand commands = new CampCommand();
        commands.modify(owner, new String[]{"modify", "rule", "accept_key", "default"}, new SenderChecker(owner));
        assertEquals("default", InviteRule.getAcceptKey(own.team));
        Player operator = player(UUID.randomUUID(), "Operator"); when(operator.isOp()).thenReturn(true);
        CampInfo namedRule = camp("accept_key");
        commands.modify(operator, new String[]{"modify", "rule", "--camp", "accept_key", "accept_key", "accept_all"}, new SenderChecker(operator));
        assertEquals("accept_all", InviteRule.getAcceptKey(namedRule.team));
        assertEquals("default", InviteRule.getAcceptKey(own.team));
    }

    @Test void forcedMoveClearsOldRanksAndCannotMoveOwner() {
        Player mover = player(UUID.randomUUID(), "Mover");
        CampInfo a = camp("a"), b = camp("b");
        member(a, mover, true, false);
        UUID moverId = mover.getUniqueId();
        doCallRealMethod().when(plugin).removeMembershipForMove(moverId);
        assertTrue(b.addMember(null, mover.getUniqueId()));
        assertFalse(a.members.containsKey(mover.getUniqueId()));
        assertFalse(a.team.hasEntry(Member.Permissions.ADMIN.to(mover.getUniqueId())));
        assertTrue(b.hasExistingMember(mover.getUniqueId()));
        assertFalse(b.getExistingMember(mover.getUniqueId()).isAdmin());
        b.setOwner(null, mover.getUniqueId());
        assertThrows(IllegalArgumentException.class, () -> a.addMember(null, mover.getUniqueId()));
        assertTrue(b.getExistingMember(mover.getUniqueId()).isOwner());
    }

    @Test void publicSnapshotContainsOnlySixPublicFieldsAndOfficialMembers() {
        CampInfo camp = camp("public"), empty = camp("empty");
        member(camp, player(UUID.randomUUID(), "Owner"), true, true);
        member(camp, player(UUID.randomUUID(), "Admin"), true, false);
        member(camp, player(UUID.randomUUID(), "Member"), false, false);
        UUID pending = UUID.randomUUID(), banned = UUID.randomUUID();
        offline(pending, "Pending"); offline(banned, "Banned");
        camp.inviteMember(null, pending, "private-reason");
        camp.setBlackList(null, banned, true);
        camp.setAcceptKey(null, "private-key");
        when(camp.team.hasColor()).thenReturn(true);
        when(camp.team.color()).thenReturn(net.kyori.adventure.text.format.NamedTextColor.RED);
        try (CampExporter exporter = new CampExporter(plugin)) {
            var snapshot = exporter.snapshots();
            assertEquals(6, snapshot.allRows().size());
            assertEquals(4, snapshot.publicRows().size());
            String all = snapshot.allRows().toString();
            String publicData = snapshot.publicRows().toString();
            assertTrue(all.contains("private-key"));
            assertTrue(all.contains("private-reason"));
            assertFalse(publicData.contains("private-key"));
            assertFalse(publicData.contains("private-reason"));
            assertFalse(publicData.contains("Pending"));
            assertFalse(publicData.contains("Banned"));
            assertFalse(publicData.contains(pending.toString()));
            for (var row : snapshot.publicRows()) assertEquals(6, row.cells().size());
            var official = snapshot.publicRows().stream().filter(row -> row.cells().get(0).equals("public")).toList();
            assertEquals(List.of("拥有者", "管理员", "成员"), official.stream().map(row -> row.cells().get(5)).toList());
            for (var row : official) {
                assertEquals("red", row.cells().get(2));
                assertEquals("填写密钥加入", row.cells().get(3));
                assertEquals(0xff5555, row.rgb());
            }
            assertTrue(snapshot.publicRows().stream().anyMatch(row -> row.cells().get(0).equals(empty.id) && row.cells().get(4).isEmpty()));
        }
    }

    @Test void colorsMatchMinecraftPaletteAndRejectHexAndFormattingCodes() {
        Set<String> vanilla = Arrays.stream(org.bukkit.ChatColor.values()).filter(org.bukkit.ChatColor::isColor)
                .map(color -> color.name().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        assertEquals(vanilla, new HashSet<>(TeamColors.names()));
        assertEquals(16, TeamColors.names().size());
        for (String name : TeamColors.names()) assertEquals(name, TeamColors.name(TeamColors.fromName(name)));
        Player operator = player(UUID.randomUUID(), "Operator"); when(operator.isOp()).thenReturn(true);
        CampInfo camp = camp("target"); CampCommand commands = new CampCommand();
        for (String invalid : List.of("#ff5555", "ff5555", "reset", "bold", "rainbow"))
            commands.modify(operator, new String[]{"modify", "color", "target", invalid}, new SenderChecker(operator));
        verify(camp.team, never()).color(any(net.kyori.adventure.text.format.NamedTextColor.class));
        assertTrue(camp.tempLogs.isEmpty());
        commands.modify(operator, new String[]{"modify", "color", "target", "RED"}, new SenderChecker(operator));
        assertTrue(camp.tempLogs.values().iterator().next().endsWith("修改阵营颜色为 red"));
        assertEquals("red", TeamColors.name(net.kyori.adventure.text.format.TextColor.color(0xff5555)));
    }

    @Test void exportCommandWritesOneFullAndOnePublicWorkbook() throws Exception {
        CampInfo camp = camp("a"); member(camp, player(UUID.randomUUID(), "Member"), false, false);
        Player operator = player(UUID.randomUUID(), "Operator");
        try (CampExporter exporter = new CampExporter(plugin)) { exporter.export(operator); }
        try (var files = java.nio.file.Files.list(directory.resolve("exports"))) {
            List<String> names = files.map(file -> file.getFileName().toString()).sorted().toList();
            String day = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            assertEquals(List.of("camps-" + day + "-1.xlsx", "camps-all-" + day + "-1.xlsx"), names);
        }
    }
}
