package cn.plumc.camp.utils;

import cn.plumc.camp.Camp;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SenderChecker{
    private final CommandSender sender;
    public final boolean any;
    public final boolean all;

    public SenderChecker(CommandSender sender) {
        this.sender = sender;
        this.all = true;
        this.any = false;
    }

    public SenderChecker(CommandSender sender, SenderChecker old, boolean value) {
        this.sender = sender;
        this.all = old.all && value;
        this.any = old.any || value;
    }

    public SenderChecker op() {
        return new SenderChecker(sender, this, sender.isOp());
    }

    public SenderChecker nonop() {
        return new SenderChecker(sender, this, !sender.isOp());
    }

    public SenderChecker player() {
        return new SenderChecker(sender, this, sender instanceof Player);
    }

    public SenderChecker nonplayer() {
        return new SenderChecker(sender, this, !(sender instanceof Player));
    }


    public SenderChecker console() {
        return new SenderChecker(sender, this, sender instanceof ConsoleCommandSender);
    }

    public SenderChecker member() {
        return new SenderChecker(sender, this, sender instanceof Player player &&
                Camp.INSTANCE.isCampMember(player.getUniqueId()));
    }

    public SenderChecker nonmember() {
        return new SenderChecker(sender, this, sender instanceof Player player &&
                !Camp.INSTANCE.isCampMember(player.getUniqueId()));
    }

    public SenderChecker auth() {
        return new SenderChecker(sender, this, sender instanceof Player player &&
                Camp.INSTANCE.isCampMember(player.getUniqueId()) &&
                Camp.INSTANCE.getMember(player.getUniqueId()).hasAuthority());
    }

    public SenderChecker owner() {
        return new SenderChecker(sender, this, sender instanceof Player player &&
                Camp.INSTANCE.isCampMember(player.getUniqueId()) &&
                Camp.INSTANCE.getMember(player.getUniqueId()).isOwner());
    }

    public SenderChecker p(String permission) {
        return new SenderChecker(sender, this, sender.hasPermission(permission));
    }

    public SenderChecker np(String permission) {
        return new SenderChecker(sender, this, !sender.hasPermission(permission));
    }

    public SenderChecker camp(String camp) {
        return new SenderChecker(sender, this, Camp.INSTANCE.hasCamp(camp));
    }

    public Player toPlayer() {return sender instanceof Player player ? player : null;}

    public UUID toUUID() {return sender instanceof Player player ? player.getUniqueId() : null;}
}
