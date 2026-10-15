package cn.plumc.camp.utils;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Locale;

/** The Minecraft team palette, shared by command validation, menus and exports. */
public final class TeamColors {
    private static final List<String> NAMES = List.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
            "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");
    private TeamColors() {}

    public static List<String> names() { return NAMES.stream().sorted().toList(); }

    public static NamedTextColor fromName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return NAMES.contains(normalized) ? NamedTextColor.NAMES.value(normalized) : null;
    }

    public static String name(TextColor color) {
        for (String name : NAMES) if (fromName(name).value() == color.value()) return name;
        throw new IllegalArgumentException("不支持的队伍颜色，仅支持 Minecraft 的 16 种队伍颜色。");
    }

    public static String name(Team team) { return team.hasColor() ? name(team.color()) : "white"; }
}
