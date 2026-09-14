package cn.plumc.camp.commands;

import cn.plumc.camp.Camp;
import cn.plumc.camp.camp.CampInfo;
import cn.plumc.camp.camp.InviteRule;
import cn.plumc.camp.camp.Member;
import cn.plumc.camp.utils.SenderChecker;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;

public class CampCommand implements TabCompleter, CommandExecutor {
    public static final String PREFIX = "&8[&aCamp&8]&f ";
    public static final String CAMP_ID_PATTERN = "\\w+";

    public static final HashMap<UUID, UUID> rankUpConfirms = new HashMap<>();

    enum SubCommands{
        CREATE("create"),   // camp.create[default]
        DISBAND("disband"), // camp.disband[isOwner|op]
        LIST("list"),       // camp.list[default] ; camp.list.member[default]
        AUTH("auth"),       // camp.auth.rankup[isOwner|op] ; camp.auth.rankdown[isOwner|op]
        JOIN("join"),       // camp.join[!isMember] ; camp.join.other[op]
        LEAVE("leave"),     // camp.leave[isMember] ; camp.leave.other[op]
        MODIFY("modify"),   // camp.modify.name[isAdmin|op] ; camp.modify.color[isAdmin|op] ; camp.rule[isAdmin|op]
        MEMBER("member"),   // camp.member.invite[isMember] ; camp.member.accept[isAdmin|op] ; camp.member.reject[isAdmin|op] ; camp.member.kick[isAdmin|op] ; camp.member.ban[isAdmin|op]; camp.member.pardon[isAdmin|op]
        NULL("null");

        public final String command;
        SubCommands(String command) {
            this.command = command;
        }

        @NotNull
        public static SubCommands fromString(String command) {
            for (SubCommands subCommand : values()) {
                if (subCommand.command.equalsIgnoreCase(command)) {
                    return subCommand;
                }
            }
            return NULL;
        }

        public List<String> all(){
            return Arrays.stream(values()).filter(c -> !c.equals(NULL)).map(c -> c.command).toList();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {return help(sender);}

        SenderChecker senderChecker = new SenderChecker(sender);
        try {
            return switch (SubCommands.fromString(args[0])) {
                case CREATE -> create(sender, args, senderChecker);
                case DISBAND -> disband(sender, args, senderChecker);
                case LIST -> list(sender, args, senderChecker);
                case AUTH -> auth(sender, args, senderChecker);
                case JOIN -> join(sender, args, senderChecker);
                case LEAVE -> leave(sender, args, senderChecker);
                case MODIFY -> modify(sender, args, senderChecker);
                case MEMBER -> member(sender, args, senderChecker);
                case NULL -> help(sender);
            };
        } catch (Exception e) {
            Camp.INSTANCE.logger.log(Level.WARNING, e.getMessage());
            return failure(sender, "执行命令时出现意外错误");
        }

    }

    public boolean create(CommandSender sender, String[] args, SenderChecker checker) {
        if (checker.op().console().all) {
            if (args.length != 3 || !String.valueOf(args[1]).matches(CAMP_ID_PATTERN)) return failure(sender, "参数错误。");
            Camp.INSTANCE.createCamp(null, args[1], args[2]);
            return success(sender, "阵营已创建。");
        }
        if (checker.nonplayer().all) return failure(sender, "此命令必须由玩家执行。");
        if (checker.member().all) return failure(sender, "你已在一个阵营内。");
        if (args.length != 3 || !args[1].matches(CAMP_ID_PATTERN)) return failure(sender, "参数错误。");
        if (checker.np("camp.create").nonop().all) return failure(sender, "权限不足。");

        Camp.INSTANCE.createCamp(checker.toUUID(), args[1], args[2]);
        return success(sender, "阵营已创建。");
    }

    public boolean disband(CommandSender sender, String[] args, SenderChecker checker) {
        if (args.length >= 2 && checker.op().all) {
            if (!Camp.INSTANCE.hasCamp(args[1])) return failure(sender, "无此阵营。");
            Camp.INSTANCE.disbandCamp(args[1]);
            return success(sender, "阵营已解散。");
        }
        if (checker.nonplayer().all) return failure(sender, "此命令必须由玩家执行。");
        if (checker.nonmember().all) return failure(sender, "你不处于任何一个阵营。");
        if (!checker.owner().all) return failure(sender, "你不是当前阵营的拥有者。");
        if (checker.np("camp.disband").nonop().all) return failure(sender, "权限不足。");

        Camp.INSTANCE.disbandCamp(Camp.INSTANCE.getCamp(checker.toUUID()).id);
        return success(sender, "阵营已解散。");
    }

    public boolean list(CommandSender sender, String[] args, SenderChecker checker) {
        if (args.length == 1) {
            if (checker.np("camp.list").nonop().all) return failure(sender, "权限不足。");
            if (Camp.INSTANCE.camps.isEmpty()) return success(sender, "当前没有任何阵营。");
            for (CampInfo camp : Camp.INSTANCE.camps) {
                success(sender, "| id: %s | 名称: %s | 人数: %s |".formatted(camp.id, camp.nameContent, camp.getMembers().size()));
            }
            return true;
        }
        if (args.length == 2) {
            if (checker.np("camp.list.member").nonop().all) return failure(sender, "权限不足。");
            if (!checker.camp(args[1]).all) return failure(sender, "无此阵营。");
            CampInfo camp = Camp.INSTANCE.getCamp(args[1]);
            success(sender, ChatColor.GRAY+"========================================");
            success(sender, "阵营: %s".formatted(camp.nameContent));
            int counter = 0;
            StringBuilder sb = new StringBuilder();
            for (Member member : camp.getMembers().values()) {
                sb.append(member.player.isOnline() ? ChatColor.GREEN : ChatColor.GRAY).append("●");
                sb.append(ChatColor.WHITE).append(member.name).append(" ");
                counter++;
                if (counter >= 2) {
                    success(sender, sb.toString());
                    sb = new StringBuilder();
                }
            }
            success(sender, sb.toString());
            return success(sender, ChatColor.GRAY+"========================================");
        }
        return false;
    }

    public boolean auth(CommandSender sender, String[] args, SenderChecker checker) {
        if (!checker.member().op().any) return failure(sender, "你不处于任何一个阵营。");
        if (args.length != 3) return failure(sender, "参数错误。");
        OfflinePlayer target = Bukkit.getServer().getOfflinePlayer(args[2]);
        if (!Camp.INSTANCE.isCampMember(target.getUniqueId())) return failure(sender, "目标不处于任何一个阵营。");
        CampInfo camp = Camp.INSTANCE.getCamp(target.getUniqueId());
        if (!(checker.op().all || (checker.player().all && camp.hasExistingMember(checker.toUUID())))) return failure(sender, "你与目标不处于同一阵营。");
        if (!(checker.op().all || camp.getExistingMember(checker.toUUID()).isOwner())) return failure(sender, "你不是阵营拥有者。");
        Member member = camp.getExistingMember(target.getUniqueId());
        UUID handler = checker.player().all ? checker.toUUID() : UUID.nameUUIDFromBytes(Bukkit.getConsoleSender().getName().getBytes());
        if (args[1].equalsIgnoreCase("rankup")) {
            if (checker.nonop().all && member.uuid.equals(handler)) return failure(sender, "你不能提升自己的权限。");
            if (checker.np("camp.auth.rankup").nonop().all) return failure(sender, "权限不足。");
            if (!member.isAdmin()) {
                camp.setAdmin(handler, member.uuid, true);
                return success(sender, "%s 已成为管理员。".formatted(member.name));
            }
            else {
                if (rankUpConfirms.containsKey(handler) && rankUpConfirms.get(handler).equals(member.uuid)) {
                    camp.setOwner(handler, member.uuid);
                    rankUpConfirms.remove(handler);
                    return success(sender, "%s 已成为新的阵营拥有者。".formatted(member.name));
                } else {
                    rankUpConfirms.put(handler, member.uuid);
                    Bukkit.getScheduler().runTaskLater(Camp.INSTANCE, ()->{
                        if (rankUpConfirms.containsKey(handler)) success(sender, ChatColor.GRAY+"交换已取消");
                        rankUpConfirms.remove(handler);
                    }, 20*20);
                    return success(sender, ChatColor.YELLOW+"正在交换拥有者，请在20s内再次输入重复命令以确认交换。");
                }
            }
        }
        if (args[1].equalsIgnoreCase("rankdown")) {
            if (checker.nonop().all && member.uuid.equals(handler)) return failure(sender, "你不能降低自己的权限，请使用/camp auth rankup <target>转让拥有者。");
            if (checker.np("camp.auth.rankdown").nonop().all) return failure(sender, "权限不足。");
            if (member.isAdmin()) {
                camp.setAdmin(handler, member.uuid, false);
                return success(sender, "%s 已被罢免。".formatted(member.name));
            }
            else return failure(sender, "目标不是管理员。");
        }
        return failure(sender, "参数错误。");
    }

    /**
     * camp.join[!isMember] : 玩家主动加入阵营,按阵营加入规则处理;
     * camp.join.other[op] : op 可将其他玩家直接加入阵营(绕过加入规则)。
     */
    public boolean join(CommandSender sender, String[] args, SenderChecker checker) {
        // op 使用 camp.join.other: /camp join <campId> <player>
        if (args.length >= 3 && checker.op().all && checker.p("camp.join.other").all) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            // args[2] 是已知玩家(且非自己)时视为替他人加入,否则按玩家主动加入解析(密钥/附言)
            if ((target.hasPlayedBefore() || target.isOnline()) && !target.getUniqueId().equals(checker.toUUID())) {
                if (!checker.camp(args[1]).all) return failure(sender, "无此阵营。");
                CampInfo camp = Camp.INSTANCE.getCamp(args[1]);
                if (camp.hasExistingMember(target.getUniqueId())) return failure(sender, "该玩家已在该阵营内。");
                if (camp.inBlackList(target.getUniqueId())) return failure(sender, "该玩家在该阵营的黑名单中。");
                camp.addMember(handlerUUID(checker), target.getUniqueId());
                return success(sender, "已将 %s 加入阵营 %s。".formatted(target.getName(), camp.nameContent));
            }
        }

        // 玩家主动加入
        if (checker.nonplayer().all) return failure(sender, "此命令必须由玩家执行。");
        if (checker.member().all) return failure(sender, "你已在一个阵营内。");
        if (checker.np("camp.join").nonop().all) return failure(sender, "权限不足。");
        if (args.length < 2 || args.length > 3 || !args[1].matches(CAMP_ID_PATTERN)) return failure(sender, "参数错误。");
        if (!checker.camp(args[1]).all) return failure(sender, "无此阵营。");
        CampInfo camp = Camp.INSTANCE.getCamp(args[1]);
        if (camp.inBlackList(checker.toUUID())) return failure(sender, "你在该阵营的黑名单中。");
        if (hasPendingApplication(checker.toUUID())) return failure(sender, "你已有待处理的申请。");

        switch (camp.inviteRule) {
            case ACCEPT_ALL -> {
                camp.addMember(checker.toUUID(), checker.toUUID());
                return success(sender, "你已加入阵营 %s。".formatted(camp.nameContent));
            }
            case ACCEPT_KEY -> {
                if (args.length != 3) return failure(sender, "该阵营需要密钥加入: /camp join <campId> <密钥>");
                if (!args[2].equals(InviteRule.getAcceptKey(camp.team))) return failure(sender, "密钥错误。");
                camp.addMember(checker.toUUID(), checker.toUUID());
                return success(sender, "你已加入阵营 %s。".formatted(camp.nameContent));
            }
            default -> {
                camp.inviteMember(checker.toUUID(), checker.toUUID(), args.length == 3 ? args[2] : null);
                return success(sender, "你已向阵营 %s 提交申请,等待管理员处理。".formatted(camp.nameContent));
            }
        }
    }

    /**
     * camp.leave[isMember] : 成员离开当前阵营,拥有者需先转让或解散;
     * camp.leave.other[op] : op 可将其他玩家移出指定阵营。
     */
    public boolean leave(CommandSender sender, String[] args, SenderChecker checker) {
        // op 使用 camp.leave.other: /camp leave <campId> <player>
        if (args.length >= 3 && checker.op().all && checker.p("camp.leave.other").all) {
            if (!checker.camp(args[1]).all) return failure(sender, "无此阵营。");
            CampInfo camp = Camp.INSTANCE.getCamp(args[1]);
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            Member member = camp.getExistingMember(target.getUniqueId());
            if (member == null) return failure(sender, "该玩家不在该阵营内。");
            if (member.isOwner()) return failure(sender, "该玩家是阵营拥有者,请先解散阵营或转让拥有者。");
            camp.kickMember(handlerUUID(checker), target.getUniqueId());
            return success(sender, "%s 已离开阵营 %s。".formatted(target.getName(), camp.nameContent));
        }

        if (checker.nonplayer().all) return failure(sender, "此命令必须由玩家执行。");
        if (checker.nonmember().all) return failure(sender, "你不处于任何一个阵营。");
        if (checker.np("camp.leave").nonop().all) return failure(sender, "权限不足。");
        CampInfo camp = Camp.INSTANCE.getCamp(checker.toUUID());
        Member member = camp.getExistingMember(checker.toUUID());
        if (member.isOwner()) {
            if (camp.getMembers().size() > 1) return failure(sender, "你是阵营拥有者,请先转让拥有者或解散阵营。");
            Camp.INSTANCE.disbandCamp(camp.id);
            return success(sender, "你是最后一个成员,阵营已解散。");
        }
        camp.kickMember(checker.toUUID(), checker.toUUID());
        return success(sender, "你已离开阵营 %s。".formatted(camp.nameContent));
    }

    /**
     * camp.modify.name[isAdmin|op] : /camp modify name <name> (op: <campId> <name>)
     * camp.modify.color[isAdmin|op] : /camp modify color <color> (op: <campId> <color>)
     * camp.rule[isAdmin|op] : /camp modify rule <default|accept_all|accept_key> [key] (op: <campId> <rule> [key])
     */
    public boolean modify(CommandSender sender, String[] args, SenderChecker checker) {
        if (args.length < 2) return failure(sender, "参数错误。");
        return switch (args[1].toLowerCase()) {
            case "name" -> modifyName(sender, args, checker);
            case "color" -> modifyColor(sender, args, checker);
            case "rule" -> modifyRule(sender, args, checker);
            default -> failure(sender, "未知的修改项: name / color / rule");
        };
    }

    private boolean modifyName(CommandSender sender, String[] args, SenderChecker checker) {
        CampInfo camp;
        String name;
        if (checker.op().all && args.length == 4) {
            if (!checker.camp(args[2]).all) return failure(sender, "无此阵营。");
            camp = Camp.INSTANCE.getCamp(args[2]);
            name = args[3];
        } else {
            if (checker.nonplayer().all) return failure(sender, "此命令必须由玩家执行。");
            if (checker.nonmember().all) return failure(sender, "你不处于任何一个阵营。");
            if (args.length != 3) return failure(sender, "参数错误。");
            camp = Camp.INSTANCE.getCamp(checker.toUUID());
            name = args[2];
        }
        if (!checkCampAdmin(sender, checker, camp, "camp.modify.name")) return true;
        Component displayName = LegacyComponentSerializer.legacyAmpersand().deserialize(name);
        camp.team.displayName(displayName);
        camp.name = displayName;
        camp.nameContent = name;
        camp.log(handlerUUID(checker), "修改阵营名为 %s".formatted(name));
        return success(sender, "阵营名称已修改为 %s。".formatted(name));
    }

    private boolean modifyColor(CommandSender sender, String[] args, SenderChecker checker) {
        CampInfo camp;
        String colorName;
        if (checker.op().all && args.length == 4) {
            if (!checker.camp(args[2]).all) return failure(sender, "无此阵营。");
            camp = Camp.INSTANCE.getCamp(args[2]);
            colorName = args[3];
        } else {
            if (checker.nonplayer().all) return failure(sender, "此命令必须由玩家执行。");
            if (checker.nonmember().all) return failure(sender, "你不处于任何一个阵营。");
            if (args.length != 3) return failure(sender, "参数错误。");
            camp = Camp.INSTANCE.getCamp(checker.toUUID());
            colorName = args[2];
        }
        if (!checkCampAdmin(sender, checker, camp, "camp.modify.color")) return true;
        NamedTextColor color = NamedTextColor.NAMES.value(colorName.toLowerCase());
        if (color == null) return failure(sender, "未知颜色,仅支持 16 种基础颜色名。");
        camp.team.color(color);
        camp.color = color;
        camp.log(handlerUUID(checker), "修改阵营颜色为 %s".formatted(color.toString()));
        return success(sender, "阵营颜色已修改为 %s。".formatted(color.toString()));
    }

    private boolean modifyRule(CommandSender sender, String[] args, SenderChecker checker) {
        CampInfo camp;
        int ruleIndex;
        // op 形式: /camp modify rule <campId> <rule> [key];4 个参数时若 args[2] 是规则名则按玩家形式解析
        if (checker.op().all && args.length >= 4 && (args.length >= 5 || checker.camp(args[2]).all || !isRule(args[2]))) {
            if (!checker.camp(args[2]).all) return failure(sender, "无此阵营。");
            camp = Camp.INSTANCE.getCamp(args[2]);
            ruleIndex = 3;
        } else {
            if (checker.nonplayer().all) return failure(sender, "此命令必须由玩家执行。");
            if (checker.nonmember().all) return failure(sender, "你不处于任何一个阵营。");
            if (args.length != 3 && args.length != 4) return failure(sender, "参数错误。");
            camp = Camp.INSTANCE.getCamp(checker.toUUID());
            ruleIndex = 2;
        }
        if (!checkCampAdmin(sender, checker, camp, "camp.rule")) return true;
        UUID handler = handlerUUID(checker);
        switch (args[ruleIndex].toLowerCase()) {
            case "default" -> {
                if (args.length != ruleIndex + 1) return failure(sender, "参数错误。");
                camp.setInviteRule(handler, InviteRule.DEFAULT);
                return success(sender, "加入规则已修改为: %s。".formatted(InviteRule.DEFAULT.info));
            }
            case "accept_all" -> {
                if (args.length != ruleIndex + 1) return failure(sender, "参数错误。");
                camp.setInviteRule(handler, InviteRule.ACCEPT_ALL);
                return success(sender, "加入规则已修改为: %s。".formatted(InviteRule.ACCEPT_ALL.info));
            }
            case "accept_key" -> {
                if (args.length != ruleIndex + 2) return failure(sender, "参数错误,需要提供密钥。");
                String key = args[ruleIndex + 1];
                camp.setAcceptKey(handler, key);
                return success(sender, "加入规则已修改为: %s : %s。".formatted(InviteRule.ACCEPT_KEY.info, key));
            }
            default -> {return failure(sender, "未知规则: default / accept_all / accept_key");}
        }
    }

    /**
     * camp.member.invite[isMember] : 向在线玩家发送可点击的邀请消息,对方点击后执行 /camp join <campId>;
     * camp.member.accept/reject/kick/ban/pardon[isAdmin|op] : 成员管理。
     */
    public boolean member(CommandSender sender, String[] args, SenderChecker checker) {
        if (args.length < 2) return failure(sender, "参数错误。");
        return switch (args[1].toLowerCase()) {
            case "invite" -> memberInvite(sender, args, checker);
            case "accept" -> memberManage(sender, args, checker, "accept", "camp.member.accept");
            case "reject" -> memberManage(sender, args, checker, "reject", "camp.member.reject");
            case "kick" -> memberManage(sender, args, checker, "kick", "camp.member.kick");
            case "ban" -> memberManage(sender, args, checker, "ban", "camp.member.ban");
            case "pardon" -> memberManage(sender, args, checker, "pardon", "camp.member.pardon");
            default -> failure(sender, "未知的成员操作: invite / accept / reject / kick / ban / pardon");
        };
    }

    private boolean memberInvite(CommandSender sender, String[] args, SenderChecker checker) {
        if (checker.nonplayer().all) return failure(sender, "此命令必须由玩家执行。");
        if (checker.nonmember().all) return failure(sender, "你不处于任何一个阵营。");
        if (checker.np("camp.member.invite").nonop().all) return failure(sender, "权限不足。");
        if (args.length != 3) return failure(sender, "参数错误。");
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) return failure(sender, "目标玩家不在线。");
        if (target.getUniqueId().equals(checker.toUUID())) return failure(sender, "不能邀请自己。");
        CampInfo camp = Camp.INSTANCE.getCamp(checker.toUUID());
        if (camp.hasExistingMember(target.getUniqueId())) return failure(sender, "该玩家已在你所在的阵营内。");
        if (camp.inBlackList(target.getUniqueId())) return failure(sender, "该玩家在你所在阵营的黑名单中。");

        Component message = LegacyComponentSerializer.legacySection()
                .deserialize((PREFIX + "&e%s &f邀请你加入阵营 &e%s &f".formatted(sender.getName(), camp.nameContent)).replace("&", "§"))
                .append(LegacyComponentSerializer.legacySection()
                        .deserialize("&a[点击加入]".replace("&", "§"))
                        .clickEvent(ClickEvent.runCommand("/camp join " + camp.id))
                        .hoverEvent(HoverEvent.showText(Component.text("点击加入阵营 " + camp.id))));
        ((Audience) target).sendMessage(message);
        return success(sender, "已向 %s 发送邀请,对方点击聊天框中的 [点击加入] 即可。".formatted(target.getName()));
    }

    private boolean memberManage(CommandSender sender, String[] args, SenderChecker checker, String action, String permission) {
        CampInfo camp;
        String playerName;
        if (checker.op().all && args.length == 4) {
            if (!checker.camp(args[2]).all) return failure(sender, "无此阵营。");
            camp = Camp.INSTANCE.getCamp(args[2]);
            playerName = args[3];
        } else {
            if (checker.nonplayer().all) return failure(sender, "此命令必须由玩家执行。");
            if (checker.nonmember().all) return failure(sender, "你不处于任何一个阵营。");
            if (args.length != 3) return failure(sender, "参数错误。");
            camp = Camp.INSTANCE.getCamp(checker.toUUID());
            playerName = args[2];
        }
        if (!checkCampAdmin(sender, checker, camp, permission)) return true;
        UUID handler = handlerUUID(checker);
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = targetPlayer.getUniqueId();
        Member member = camp.getExistingMember(uuid);
        switch (action) {
            case "accept" -> {
                if (member != null) return failure(sender, "该玩家已是阵营成员。");
                if (!camp.acceptMember(handler, uuid)) return failure(sender, "该玩家没有待处理的申请。");
                return success(sender, "%s 已加入阵营。".formatted(targetPlayer.getName()));
            }
            case "reject" -> {
                if (member != null) return failure(sender, "该玩家已是阵营成员。");
                if (!camp.rejectMember(handler, uuid)) return failure(sender, "该玩家没有待处理的申请。");
                return success(sender, "%s 的申请已被拒绝。".formatted(targetPlayer.getName()));
            }
            case "kick" -> {
                if (member == null) return failure(sender, "该玩家不在该阵营内。");
                if (member.isOwner()) return failure(sender, "不能踢出阵营拥有者。");
                camp.kickMember(handler, uuid);
                return success(sender, "%s 已被踢出阵营。".formatted(targetPlayer.getName()));
            }
            case "ban" -> {
                if (member != null && member.isOwner()) return failure(sender, "不能拉黑阵营拥有者。");
                if (camp.inBlackList(uuid)) return failure(sender, "该玩家已在黑名单中。");
                camp.setBlackList(handler, uuid, true);
                return success(sender, "%s 已被加入黑名单。".formatted(targetPlayer.getName()));
            }
            case "pardon" -> {
                if (!camp.inBlackList(uuid)) return failure(sender, "该玩家不在黑名单中。");
                camp.setBlackList(handler, uuid, false);
                return success(sender, "%s 已被移出黑名单。".formatted(targetPlayer.getName()));
            }
        }
        return failure(sender, "未知操作。");
    }

    public boolean help(CommandSender sender){
        success(sender, "&e========= &aCamp 阵营插件 &e=========");
        success(sender, "&f/camp create <id> <名称> &7- 创建阵营");
        success(sender, "&f/camp disband [阵营id] &7- 解散阵营(拥有者/op)");
        success(sender, "&f/camp list [阵营id] &7- 查看阵营列表 / 成员列表");
        success(sender, "&f/camp auth rankup|rankdown <玩家> &7- 任命/罢免管理员,再次 rankup 转让拥有者");
        success(sender, "&f/camp join <阵营id> [密钥|附言] &7- 加入阵营 / 提交申请");
        success(sender, "&f/camp leave &7- 离开当前阵营");
        success(sender, "&f/camp modify name|color|rule <值> [密钥] &7- 修改阵营名称/颜色/加入规则(管理员)");
        success(sender, "&f/camp member invite <玩家> &7- 邀请在线玩家加入");
        success(sender, "&f/camp member accept|reject <玩家> &7- 接受/拒绝加入申请(管理员)");
        success(sender, "&f/camp member kick|ban|pardon <玩家> &7- 踢出/拉黑/解除拉黑(管理员)");
        return true;
    }

    public boolean failure(CommandSender sender, String message) {
        sender.sendMessage((PREFIX + ChatColor.RED + message).replace("&", "§"));
        return true;
    }

    public boolean success(CommandSender sender, String message) {
        sender.sendMessage((PREFIX + message).replace("&", "§"));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> campIds = Camp.INSTANCE.camps.stream().map(c -> c.id).toList();
        List<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        List<String> colorNames = NamedTextColor.NAMES.keys().stream().toList();

        if (args.length == 1) {
            return filter(Arrays.stream(SubCommands.values()).filter(c -> !c.equals(SubCommands.NULL)).map(c -> c.command).toList(), args[0]);
        }

        return switch (SubCommands.fromString(args[0])) {
            case DISBAND -> sender.isOp() && args.length == 2 ? filter(campIds, args[1]) : List.of();
            case LIST -> args.length == 2 ? filter(campIds, args[1]) : List.of();
            case AUTH -> switch (args.length) {
                case 2 -> filter(List.of("rankup", "rankdown"), args[1]);
                case 3 -> filter(playerNames, args[2]);
                default -> List.of();
            };
            case JOIN -> switch (args.length) {
                case 2 -> filter(campIds, args[1]);
                case 3 -> sender.isOp() ? filter(playerNames, args[2]) : List.of();
                default -> List.of();
            };
            case LEAVE -> sender.isOp() ? switch (args.length) {
                case 2 -> filter(campIds, args[1]);
                case 3 -> filter(playerNames, args[2]);
                default -> List.of();
            } : List.of();
            case MODIFY -> switch (args.length) {
                case 2 -> filter(List.of("name", "color", "rule"), args[1]);
                case 3 -> {
                    if (args[1].equalsIgnoreCase("rule")) yield filter(List.of("default", "accept_all", "accept_key"), args[2]);
                    if (args[1].equalsIgnoreCase("name")) yield sender.isOp() ? filter(campIds, args[2]) : List.of();
                    if (args[1].equalsIgnoreCase("color")) yield filter(colorNames, args[2]);
                    yield List.of();
                }
                case 4 -> args[1].equalsIgnoreCase("color") ? filter(colorNames, args[3]) : List.of();
                default -> List.of();
            };
            case MEMBER -> switch (args.length) {
                case 2 -> filter(List.of("invite", "accept", "reject", "kick", "ban", "pardon"), args[1]);
                case 3 -> filter(playerNames, args[2]);
                case 4 -> sender.isOp() ? filter(playerNames, args[3]) : List.of();
                default -> List.of();
            };
            default -> List.of();
        };
    }

    private List<String> filter(List<String> options, String token) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(token.toLowerCase())).toList();
    }

    /** 是否为有效的加入规则名。 */
    private boolean isRule(String value) {
        return value.equalsIgnoreCase("default") || value.equalsIgnoreCase("accept_all") || value.equalsIgnoreCase("accept_key");
    }

    /** 校验执行者是否为该阵营管理员(或 op)并持有指定权限,失败时已发送原因。 */
    private boolean checkCampAdmin(CommandSender sender, SenderChecker checker, CampInfo camp, String permission) {
        if (checker.nonplayer().nonop().all) { failure(sender, "此命令必须由玩家执行。"); return false; }
        if (!(checker.op().all || (checker.player().all && camp.getExistingMember(checker.toUUID()).hasAuthority()))) {
            failure(sender, "你不是该阵营的管理员。");
            return false;
        }
        if (checker.np(permission).nonop().all) { failure(sender, "权限不足。"); return false; }
        return true;
    }

    /** 获取操作者 UUID:玩家为自身,控制台为固定 UUID。 */
    private UUID handlerUUID(SenderChecker checker) {
        if (checker.player().all) return checker.toUUID();
        return UUID.nameUUIDFromBytes(Bukkit.getConsoleSender().getName().getBytes());
    }

    /** 玩家是否已在任意阵营存在待处理的加入申请(IDLE)。 */
    private boolean hasPendingApplication(UUID player) {
        for (CampInfo camp : Camp.INSTANCE.camps) {
            Member member = camp.members.get(player);
            if (member != null && member.inIDLE()) return true;
        }
        return false;
    }
}
