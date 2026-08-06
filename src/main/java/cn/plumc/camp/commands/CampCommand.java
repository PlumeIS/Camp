package cn.plumc.camp.commands;

import cn.plumc.camp.Camp;
import cn.plumc.camp.camp.CampInfo;
import cn.plumc.camp.camp.Member;
import cn.plumc.camp.utils.SenderChecker;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class CampCommand implements TabCompleter, CommandExecutor {
    public static final String PREFIX = "&8[&aCamp&8]&f ";
    public static final String CAMP_ID_PATTERN = "\\w+";

    enum SubCommands{
        CREATE("create"),   // camp.create[default]
        DISBAND("disband"), // camp.disband[isOwner|op]
        LIST("list"),       // camp.list[default] ; camp.list.member[default]
        AUTH("auth"),       // camp.auth.rankup[isOwner|op] ; camp.auth.rankdown[isOwner|op]
        JOIN("join"),       // camp.join[!isMember] ; camp.join.other[op]
        LEAVE("leave"),     // camp.leave[!isMember] ; camp.leave.other[op]
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
            for (CampInfo camp : Camp.INSTANCE.camps) {
                success(sender, "| id: %s | 名称: %s | 人数: %s |".formatted(camp.id, camp.nameContent, camp.getMembers().size()));
                return true;
            }
        }
        if (args.length == 2) {
            if (checker.np("camp.list.member").nonop().all) return failure(sender, "权限不足。");
            if (!checker.camp(args[1]).all) return failure(sender, "无此阵营。");
            CampInfo camp = Camp.INSTANCE.getCamp(args[1]);
            success(sender, "========================================");
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
            return success(sender, "========================================");
        }
        return false;
    }

    public boolean auth(CommandSender sender, String[] args, SenderChecker checker) {
        return true;
    }

    public boolean join(CommandSender sender, String[] args, SenderChecker checker) {
        return true;
    }

    public boolean leave(CommandSender sender, String[] args, SenderChecker checker) {
        return true;
    }

    public boolean modify(CommandSender sender, String[] args, SenderChecker checker) {
        return true;
    }

    public boolean member(CommandSender sender, String[] args, SenderChecker checker) {
        return true;
    }

    public boolean help(CommandSender sender){
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
        return List.of();
    }
}
