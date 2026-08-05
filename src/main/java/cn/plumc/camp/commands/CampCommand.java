package cn.plumc.camp.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class CampCommand implements TabCompleter, CommandExecutor {
    enum SubCommands{
        CREATE("create"),   // camp.create[default]
        DISBAND("disband"), // camp.disband[isAdmin|op]
        LIST("list"),       // camp.list[default] ; camp.list.camp[isMember]
        AUTH("auth"),       // camp.auth.rankup[isOwner|op] ; camp.auth.rankdown[isOwner|op]
        JOIN("join"),       // camp.join[!isMember] ; camp.join.other[op]
        LEAVE("leave"),     // camp.leave[!isMember] ; camp.leave.other[op]
        MODIFY("modify"),   // camp.modify.name[isAdmin|op] ; camp.modify.color[isAdmin|op] ; camp.rule[isAdmin|op]
        MEMBER("member");   // camp.invite[isMember] ; camp.accept[isAdmin|op] ; camp.reject[isAdmin|op] ; camp.kick[isAdmin|op] ; camp.ban[isAdmin|op]; camp.pardon[isAdmin|op]
        final String command;
        SubCommands(String command) {
            this.command = command;
        }
        public SubCommands fromString(String command) {
            for (SubCommands subCommand : values()) {
                if (subCommand.command.equalsIgnoreCase(command)) {
                    return subCommand;
                }
            }
            return null;
        }
        public List<String> all(){
            return Arrays.stream(values()).map(c -> c.command).toList();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return List.of();
    }
}
