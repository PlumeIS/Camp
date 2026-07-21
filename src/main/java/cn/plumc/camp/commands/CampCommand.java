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
        CREATE("create"),
        DISBAND("disband"),
        LIST("list"),
        AUTH("auth"),
        JOIN("join"),
        LEAVE("leave"),
        MODIFY("modify"),
        MEMBER("member");
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
