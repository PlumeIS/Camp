package cn.plumc.camp.utils;

import cn.plumc.camp.Camp;
import cn.plumc.camp.camp.CampInfo;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SenderChecker{
    public CommandSender sender;
    public boolean result;

    public SenderChecker(CommandSender sender) {
        this.sender = sender;
        this.result = true;
    }

    public SenderChecker(CommandSender sender, boolean result) {
        this.sender = sender;
        this.result = result;
    }

    public SenderChecker op() {
        return new SenderChecker(sender, result && sender.isOp());
    }

    public SenderChecker nonop() {
        return new SenderChecker(sender, result && !sender.isOp());
    }

    public SenderChecker player() {
        return new SenderChecker(sender, result && sender instanceof Player);
    }

    public SenderChecker nonplayer() {
        return new SenderChecker(sender, result && !(sender instanceof Player));
    }


    public SenderChecker console() {
        return new SenderChecker(sender, result && sender instanceof ConsoleCommandSender);
    }

    public SenderChecker member() {
        return new SenderChecker(sender, result && sender instanceof Player player &&
                Camp.INSTANCE.isCampMember(player.getUniqueId()));
    }

    public SenderChecker nonmember() {
        return new SenderChecker(sender, result && sender instanceof Player player &&
                !Camp.INSTANCE.isCampMember(player.getUniqueId()));
    }

    public SenderChecker auth() {
        return new SenderChecker(sender, result && sender instanceof Player player &&
                Camp.INSTANCE.isCampMember(player.getUniqueId()) &&
                Camp.INSTANCE.getMember(player.getUniqueId()).hasAuthority());
    }

    public SenderChecker owner() {
        return new SenderChecker(sender, result && sender instanceof Player player &&
                Camp.INSTANCE.isCampMember(player.getUniqueId()) &&
                Camp.INSTANCE.getMember(player.getUniqueId()).isOwner());
    }

    public SenderChecker p(String permission) {
        return new SenderChecker(sender, result && (sender.hasPermission(permission)||sender.isOp()));
    }

    public SenderChecker np(String permission) {
        return new SenderChecker(sender, result && (!sender.hasPermission(permission)&&!sender.isOp()));
    }

    public SenderChecker camp(String camp) {
        return new SenderChecker(sender, result && Camp.INSTANCE.hasCamp(camp));
    }

    public Player toPlayer() {return sender instanceof Player player ? player : null;}

    public UUID toUUID() {return sender instanceof Player player ? player.getUniqueId() : null;}
}
