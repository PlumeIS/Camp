package cn.plumc.camp.ui;

import cn.plumc.camp.Camp;
import cn.plumc.camp.camp.CampInfo;
import cn.plumc.camp.camp.InviteRule;
import cn.plumc.camp.camp.Member;
import cn.plumc.camp.commands.CampCommand;
import cn.plumc.camp.utils.TeamColors;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class CampMenus implements Listener, AutoCloseable {
    private record Entry(ItemStack icon, Consumer<ClickType> action) {}
    private record Input(Consumer<String> accept, Supplier<ChestMenu> back) {}
    private final Camp plugin;
    private final CampCommand commands;
    private final Map<UUID, ChestMenu> sessions = new HashMap<>();
    private final Map<UUID, Input> inputs = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerProfile> profiles = new LinkedHashMap<>();
    private final Set<UUID> fetchingProfiles = new HashSet<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public CampMenus(Camp plugin, CampCommand commands) { this.plugin = plugin; this.commands = commands; }

    public void open(Player player, String campId) {
        inputs.remove(player.getUniqueId());
        show(player, campId == null ? root(player, 0) : members(player, campId, 0, () -> root(player, 0)));
    }

    private void show(Player player, ChestMenu menu) {
        if (!player.isOnline()) return;
        player.openInventory(menu.getInventory());
        sessions.put(player.getUniqueId(), menu);
    }

    private ChestMenu page(Player player, String title, List<Entry> entries, int requested,
                           IntFunction<ChestMenu> rebuild, Supplier<ChestMenu> parent) {
        int pages = Math.max(1, (entries.size() + ChestMenu.CONTENT_SIZE - 1) / ChestMenu.CONTENT_SIZE);
        int index = Math.max(0, Math.min(requested, pages - 1));
        ChestMenu menu = new ChestMenu(player.getUniqueId(), Component.text(title + " " + (index + 1) + "/" + pages), parent);
        for (int offset = 0; offset < ChestMenu.CONTENT_SIZE && index * ChestMenu.CONTENT_SIZE + offset < entries.size(); offset++) {
            Entry entry = entries.get(index * ChestMenu.CONTENT_SIZE + offset);
            menu.button(9 + offset, entry.icon(), entry.action());
        }
        if (entries.isEmpty()) menu.button(22, item(Material.PAPER, "暂无记录"), null);
        menu.button(36, item(Material.ARROW, parent == null ? "关闭" : "返回上一级"), c -> {
            if (parent == null) player.closeInventory(); else show(player, parent.get());
        });
        if (index > 0) menu.button(37, item(Material.ARROW, "上一页"), c -> show(player, rebuild.apply(index - 1)));
        if (index + 1 < pages) menu.button(38, item(Material.ARROW, "下一页"), c -> show(player, rebuild.apply(index + 1)));
        menu.button(39, item(Material.COMPASS, "返回首页"), c -> show(player, root(player, 0)));
        menu.button(44, item(Material.CLOCK, "刷新"), c -> show(player, rebuild.apply(index)));
        return menu;
    }

    private ChestMenu root(Player player, int index) {
        List<Entry> entries = new ArrayList<>();
        if (allowed(player, "camp.list")) plugin.camps.stream().sorted(Comparator.comparing(c -> c.id)).forEach(camp -> {
            List<String> lore = new ArrayList<>(campAttributes(camp, false));
            lore.addAll(List.of("成员: " + camp.getMembers().size(), "左键：成员预览", "右键：临时日志预览"));
            ItemStack icon = item(Material.WHITE_BANNER, plain(camp.name), lore.toArray(String[]::new));
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(camp.name.color(camp.color).decoration(TextDecoration.ITALIC, false));
            icon.setItemMeta(meta);
            entries.add(new Entry(icon, click -> {
                if (click == ClickType.RIGHT) show(player, logs(player, camp.id, 0, () -> root(player, index)));
                else show(player, members(player, camp.id, 0, () -> root(player, index)));
            }));
        });
        ChestMenu menu = page(player, "阵营预览", entries, index, p -> root(player, p), null);
        if (allowed(player, "camp.create") && (player.isOp() || !plugin.isCampMember(player.getUniqueId())))
            menu.button(40, item(Material.EMERALD, "创建阵营"), c -> prompt(player, "阵营 ID（1-16 位字母/数字/下划线）", id -> {
                if (!id.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("阵营 ID 格式无效。");
                prompt(player, "阵营名称（支持 & 颜色）", name -> execute(player, new String[]{"create", id, name}, () -> root(player, index)), () -> root(player, index));
            }, () -> root(player, index)));
        if (allowed(player, "camp.export")) menu.button(41, item(Material.MAP, "导出全部阵营为 Excel"), c -> execute(player, new String[]{"export"}, () -> root(player, index)));
        CampInfo own = plugin.getCamp(player.getUniqueId());
        if (own != null) menu.button(42, item(Material.NETHER_STAR, "我的阵营"), c -> show(player, management(player, own.id, () -> root(player, index))));
        return menu;
    }

    private ChestMenu missing(Player player) { return root(player, 0); }

    private ChestMenu members(Player player, String id, int index, Supplier<ChestMenu> parent) {
        CampInfo camp = plugin.getCamp(id);
        if (camp == null) return missing(player);
        if (!allowed(player, "camp.list.member")) { commands.failure(player, "权限不足。"); return missing(player); }
        List<Entry> entries = camp.getMembers().values().stream().sorted(Comparator.comparingInt(CampMenus::rank).reversed().thenComparing(m -> m.name))
                .map(member -> new Entry(head(member.player, member.rank(), member.player.isOnline() ? "在线" : "离线", "点击查看可用操作"),
                        c -> show(player, memberActions(player, id, member.uuid, () -> members(player, id, index, parent))))).toList();
        ChestMenu menu = page(player, "成员 · " + plain(camp.name), entries, index, p -> members(player, id, p, parent), parent);
        menu.button(40, item(Material.BOOK, "阵营操作与属性"), c -> show(player, management(player, id, () -> members(player, id, index, parent))));
        menu.button(41, item(Material.WRITABLE_BOOK, "临时日志"), c -> show(player, logs(player, id, 0, () -> members(player, id, index, parent))));
        if (admin(player, camp)) menu.button(42, item(Material.PAPER, "待处理申请"), c -> show(player, states(player, id, false, 0, () -> members(player, id, index, parent))));
        return menu;
    }

    private ChestMenu logs(Player player, String id, int index, Supplier<ChestMenu> parent) {
        CampInfo camp = plugin.getCamp(id);
        if (camp == null) return missing(player);
        if (!allowed(player, "camp.list")) { commands.failure(player, "权限不足。"); return missing(player); }
        List<Entry> entries = camp.tempLogs.entrySet().stream().sorted(Map.Entry.<Long, String>comparingByKey().reversed())
                .map(log -> new Entry(item(Material.PAPER, TIME.format(Instant.ofEpochMilli(log.getKey())), log.getValue()), null)).toList();
        return page(player, "临时日志 · " + plain(camp.name), entries, index, p -> logs(player, id, p, parent), parent);
    }

    private ChestMenu states(Player player, String id, boolean blacklist, int index, Supplier<ChestMenu> parent) {
        CampInfo camp = plugin.getCamp(id);
        if (camp == null) return missing(player);
        if (!admin(player, camp)) { commands.failure(player, "你不是该阵营的管理员。"); return missing(player); }
        List<Entry> entries = camp.members.values().stream().filter(m -> blacklist ? m.isBlackList() : m.inIDLE())
                .sorted(Comparator.comparing(m -> m.name)).map(member -> new Entry(head(member.player, member.rank(),
                        "理由: " + camp.applicationReasons.getOrDefault(member.uuid, "未填写理由"), "点击处理"),
                        c -> show(player, memberActions(player, id, member.uuid, () -> states(player, id, blacklist, index, parent))))).toList();
        return page(player, blacklist ? "黑名单" : "待处理申请", entries, index, p -> states(player, id, blacklist, p, parent), parent);
    }

    private ChestMenu management(Player player, String id, Supplier<ChestMenu> parent) {
        CampInfo camp = plugin.getCamp(id);
        if (camp == null) return missing(player);
        Supplier<ChestMenu> back = () -> management(player, id, parent);
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(item(Material.BOOK, plain(camp.name), campAttributes(camp, false).toArray(String[]::new)), null));
        if (!plugin.isCampMember(player.getUniqueId()) && allowed(player, "camp.join")) entries.add(new Entry(item(Material.EMERALD, "加入 / 申请加入", camp.inviteRule.info), c -> {
            if (camp.inviteRule == InviteRule.ACCEPT_ALL) execute(player, new String[]{"join", id, "--self"}, back);
            else prompt(player, camp.inviteRule == InviteRule.ACCEPT_KEY ? "加入密钥" : "申请理由", value -> execute(player, new String[]{"join", id, "--self", value}, back), back);
        }));
        if (camp.hasExistingMember(player.getUniqueId()) && allowed(player, "camp.leave")) entries.add(new Entry(item(Material.OAK_DOOR, "离开阵营"), c -> confirm(player, "离开阵营", () -> execute(player, new String[]{"leave", id}, () -> root(player, 0)), back)));
        if (camp.hasExistingMember(player.getUniqueId()) && allowed(player, "camp.member.invite")) entries.add(new Entry(item(Material.PLAYER_HEAD, "邀请在线玩家"), c -> show(player, players(player, id, "invite", true, 0, back))));
        if (admin(player, camp)) {
            entries.add(new Entry(item(Material.PAPER, "处理加入申请"), c -> show(player, states(player, id, false, 0, back))));
            entries.add(new Entry(item(Material.BARRIER, "黑名单"), c -> show(player, states(player, id, true, 0, back))));
            if (allowed(player, "camp.member.ban")) entries.add(new Entry(item(Material.REDSTONE, "选择玩家拉黑"), c -> show(player, players(player, id, "ban", false, 0, back))));
            if (allowed(player, "camp.modify.name")) entries.add(new Entry(item(Material.NAME_TAG, "修改名称"), c -> prompt(player, "新阵营名称", value -> execute(player, modifyArgs(player, "name", id, value), back), back)));
            if (player.isOp()) entries.add(new Entry(item(Material.RED_DYE, "修改颜色"), c -> show(player, colors(player, id, back))));
            if (allowed(player, "camp.rule")) entries.add(new Entry(item(Material.COMPARATOR, "修改加入规则"), c -> show(player, rules(player, id, back))));
        }
        Member actor = camp.getExistingMember(player.getUniqueId());
        if ((player.isOp() || (actor != null && actor.isOwner())) && allowed(player, "camp.disband"))
            entries.add(new Entry(item(Material.TNT, "解散阵营"), c -> confirm(player, "解散 " + id, () -> execute(player, new String[]{"disband", id}, () -> root(player, 0)), back)));
        if (player.isOp() && allowed(player, "camp.join.other")) entries.add(new Entry(item(Material.LEAD, "强制添加玩家"), c -> show(player, players(player, id, "add", false, 0, back))));
        return page(player, "阵营管理", entries, 0, p -> management(player, id, parent), parent);
    }

    private ChestMenu memberActions(Player player, String id, UUID uuid, Supplier<ChestMenu> parent) {
        CampInfo camp = plugin.getCamp(id);
        if (camp == null) return missing(player);
        Member target = camp.members.get(uuid);
        if (target == null) { commands.failure(player, "该记录已被处理。"); return parent.get(); }
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(head(target.player, "UUID: " + uuid, "权限: " + target.rank()), null));
        if (admin(player, camp)) {
            if (target.inIDLE()) {
                entries.add(new Entry(item(Material.PAPER, "申请理由", camp.applicationReasons.getOrDefault(uuid, "未填写理由")), null));
                addAction(entries, player, "accept", id, uuid, "接受申请", Material.LIME_DYE, parent, false);
                addAction(entries, player, "reject", id, uuid, "拒绝申请", Material.RED_DYE, parent, true);
            }
            if (target.isBlackList()) addAction(entries, player, "pardon", id, uuid, "解除黑名单", Material.MILK_BUCKET, parent, true);
            else if (!target.isOwner()) {
                addAction(entries, player, "ban", id, uuid, "加入黑名单", Material.BARRIER, parent, true);
                if (target.isMember()) addAction(entries, player, "kick", id, uuid, "踢出成员", Material.IRON_BOOTS, parent, true);
            }
        }
        Member actor = camp.getExistingMember(player.getUniqueId());
        if (target.isMember() && !target.isOwner() && (player.isOp() || (actor != null && actor.isOwner()))) {
            if (allowed(player, "camp.auth.rankup")) entries.add(new Entry(item(Material.GOLD_INGOT, target.isAdmin() ? "转让拥有者" : "提升为管理员"), c -> {
                String[] args = {"auth", "rankup", id, uuid.toString()};
                if (target.isAdmin()) {
                    commands.onCommand(player, Objects.requireNonNull(plugin.getCommand("camp")), "camp", args);
                    CampCommand.Promotion pending = CampCommand.rankUpConfirms.get(player.getUniqueId());
                    if (pending != null && pending.target().equals(uuid) && pending.campId().equals(id))
                        confirm(player, "转让拥有者（20 秒内确认）", () -> execute(player, args, parent), parent);
                    else show(player, parent.get());
                } else execute(player, args, parent);
            }));
            if (target.isAdmin() && allowed(player, "camp.auth.rankdown")) entries.add(new Entry(item(Material.IRON_INGOT, "罢免管理员"), c -> execute(player, new String[]{"auth", "rankdown", id, uuid.toString()}, parent)));
        }
        if (target.isMember() && !target.isOwner() && player.isOp() && allowed(player, "camp.leave.other"))
            entries.add(new Entry(item(Material.OAK_DOOR, "移出阵营（OP）"), c -> confirm(player, "移出成员", () -> execute(player, new String[]{"leave", id, uuid.toString()}, parent), parent)));
        return page(player, "玩家 · " + target.name, entries, 0, p -> memberActions(player, id, uuid, parent), parent);
    }

    private void addAction(List<Entry> entries, Player player, String action, String id, UUID uuid,
                           String label, Material material, Supplier<ChestMenu> parent, boolean confirm) {
        if (!allowed(player, "camp.member." + action)) return;
        entries.add(new Entry(item(material, label), c -> {
            Runnable run = () -> execute(player, new String[]{"member", action, id, uuid.toString()}, parent);
            if (confirm) confirm(player, label, run, parent); else run.run();
        }));
    }

    private ChestMenu players(Player player, String id, String action, boolean onlineOnly, int index, Supplier<ChestMenu> parent) {
        CampInfo camp = plugin.getCamp(id);
        if (camp == null) return missing(player);
        Map<UUID, OfflinePlayer> known = new HashMap<>();
        if (!onlineOnly) for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) known.put(offline.getUniqueId(), offline);
        Bukkit.getOnlinePlayers().forEach(p -> known.put(p.getUniqueId(), p));
        List<Entry> entries = known.values().stream().sorted(Comparator.comparing(p -> p.getName() == null ? p.getUniqueId().toString() : p.getName()))
                .map(target -> new Entry(head(target, "点击选择"), c -> {
                    if (action.equals("add")) execute(player, new String[]{"join", id, "--player", target.getUniqueId().toString()}, parent);
                    else if (action.equals("invite")) execute(player, new String[]{"member", "invite", id, target.getUniqueId().toString()}, parent);
                    else confirm(player, "拉黑玩家", () -> execute(player, new String[]{"member", action, id, target.getUniqueId().toString()}, parent), parent);
                })).toList();
        ChestMenu menu = page(player, "选择玩家", entries, index, p -> players(player, id, action, onlineOnly, p, parent), parent);
        if (!onlineOnly) menu.button(40, item(Material.NAME_TAG, "输入玩家名 / UUID"), c -> prompt(player, "玩家名或 UUID", value -> {
            OfflinePlayer target = CampCommand.resolvePlayer(value);
            if (action.equals("add")) execute(player, new String[]{"join", id, "--player", target.getUniqueId().toString()}, parent);
            else execute(player, new String[]{"member", action, id, target.getUniqueId().toString()}, parent);
        }, parent));
        return menu;
    }

    private ChestMenu colors(Player player, String id, Supplier<ChestMenu> parent) {
        List<Entry> entries = TeamColors.names().stream().map(color ->
                new Entry(colorIcon(color), c -> execute(player, modifyArgs(player, "color", id, color), parent))).toList();
        return page(player, "选择颜色", entries, 0, p -> colors(player, id, parent), parent);
    }

    private ItemStack colorIcon(String color) {
        // Team colors and wool dyes use different palettes; choose a matching hue and tint the exact color name.
        Material wool = switch (color) {
            case "black" -> Material.BLACK_WOOL;
            case "dark_blue" -> Material.BLUE_WOOL;
            case "blue", "aqua" -> Material.LIGHT_BLUE_WOOL;
            case "dark_green" -> Material.GREEN_WOOL;
            case "green" -> Material.LIME_WOOL;
            case "dark_aqua" -> Material.CYAN_WOOL;
            case "dark_red", "red" -> Material.RED_WOOL;
            case "dark_purple" -> Material.PURPLE_WOOL;
            case "light_purple" -> Material.MAGENTA_WOOL;
            case "gold" -> Material.ORANGE_WOOL;
            case "gray" -> Material.LIGHT_GRAY_WOOL;
            case "dark_gray" -> Material.GRAY_WOOL;
            case "yellow" -> Material.YELLOW_WOOL;
            case "white" -> Material.WHITE_WOOL;
            default -> throw new IllegalArgumentException("未知队伍颜色: " + color);
        };
        ItemStack icon = item(wool, color);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(Component.text(color, TeamColors.fromName(color)).decoration(TextDecoration.ITALIC, false));
        icon.setItemMeta(meta);
        return icon;
    }

    private ChestMenu rules(Player player, String id, Supplier<ChestMenu> parent) {
        List<Entry> entries = new ArrayList<>();
        for (InviteRule rule : InviteRule.values()) entries.add(new Entry(item(Material.COMPARATOR, rule.info), c -> {
            String value = rule.name().toLowerCase(Locale.ROOT);
            if (rule == InviteRule.ACCEPT_KEY) prompt(player, "新的加入密钥", key -> {
                List<String> args = new ArrayList<>(Arrays.asList(modifyArgs(player, "rule", id, value)));
                args.add(key);
                execute(player, args.toArray(String[]::new), parent);
            }, parent);
            else execute(player, modifyArgs(player, "rule", id, value), parent);
        }));
        return page(player, "加入规则", entries, 0, p -> rules(player, id, parent), parent);
    }

    private String[] modifyArgs(Player player, String field, String id, String value) {
        if (field.equals("rule")) return new String[]{"modify", "rule", "--camp", id, value};
        return new String[]{"modify", field, id, value};
    }

    private void confirm(Player player, String label, Runnable yes, Supplier<ChestMenu> parent) {
        ChestMenu menu = page(player, "确认操作", List.of(
                new Entry(item(Material.LIME_CONCRETE, "确认：" + label), c -> yes.run()),
                new Entry(item(Material.RED_CONCRETE, "取消"), c -> {
                    CampCommand.rankUpConfirms.remove(player.getUniqueId());
                    show(player, parent.get());
                })), 0, p -> parent.get(), parent);
        show(player, menu);
    }

    private void execute(Player player, String[] args, Supplier<ChestMenu> back) {
        // Share all validation and permission checks with the command path, including stale menus.
        commands.onCommand(player, Objects.requireNonNull(plugin.getCommand("camp")), "camp", args);
        show(player, back.get());
    }

    private void prompt(Player player, String label, Consumer<String> accept, Supplier<ChestMenu> back) {
        player.closeInventory();
        Input input = new Input(accept, back);
        inputs.put(player.getUniqueId(), input);
        player.sendMessage(Component.text("[Camp] 请在聊天中输入" + label + "；输入 cancel 取消（120 秒内有效）。", NamedTextColor.YELLOW));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (inputs.remove(player.getUniqueId(), input) && player.isOnline()) {
                commands.failure(player, "输入已超时。");
                show(player, back.get());
            }
        }, 2400);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Input input = inputs.get(player.getUniqueId());
        if (input == null) return;
        event.setCancelled(true);
        String value = plain(event.message()).strip();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !inputs.remove(player.getUniqueId(), input)) return;
            if (value.equalsIgnoreCase("cancel") || value.equals("取消")) { show(player, input.back().get()); return; }
            if (value.isBlank() || value.length() > 512) { commands.failure(player, "输入为空或超过 512 字符。"); show(player, input.back().get()); return; }
            try { input.accept().accept(value); }
            catch (IllegalArgumentException e) { commands.failure(player, e.getMessage()); show(player, input.back().get()); }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ChestMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.viewer.equals(player.getUniqueId())) return;
        if (event.getRawSlot() < 9 || event.getRawSlot() >= 45) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && sessions.get(player.getUniqueId()) == menu && player.getOpenInventory().getTopInventory() == menu.getInventory())
                menu.click(event.getRawSlot(), event.getClick());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ChestMenu) event.setCancelled(true);
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ChestMenu menu) sessions.remove(event.getPlayer().getUniqueId(), menu);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        inputs.remove(uuid);
        CampCommand.rankUpConfirms.remove(uuid);
    }

    private ItemStack head(OfflinePlayer target, String... lore) {
        ItemStack head = item(Material.PLAYER_HEAD, target.getName() == null ? target.getUniqueId().toString() : target.getName(), lore);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = profiles.get(target.getUniqueId());
        if (profile == null) {
            Player online = target.getPlayer();
            profile = online != null ? online.getPlayerProfile() : Bukkit.createProfile(target.getUniqueId(), target.getName());
            profiles.put(target.getUniqueId(), profile);
            if (profiles.size() > 512) profiles.remove(profiles.keySet().iterator().next());
        }
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        if (!profile.hasTextures() && fetchingProfiles.add(target.getUniqueId())) {
            PlayerProfile pending = Bukkit.createProfile(profile.getId(), profile.getName());
            pending.setProperties(profile.getProperties());
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try { pending.complete(true); }
                catch (RuntimeException e) { plugin.getLogger().log(Level.FINE, "Cannot load skin " + target.getUniqueId(), e); }
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    fetchingProfiles.remove(target.getUniqueId());
                    if (!pending.hasTextures()) return;
                    profiles.put(target.getUniqueId(), pending);
                    if (profiles.size() > 512) profiles.remove(profiles.keySet().iterator().next());
                    for (ChestMenu menu : sessions.values()) for (int slot = 9; slot < 45; slot++) {
                        ItemStack icon = menu.getInventory().getItem(slot);
                        if (icon == null || !(icon.getItemMeta() instanceof SkullMeta skull)) continue;
                        PlayerProfile shown = skull.getPlayerProfile();
                        if (shown != null && target.getUniqueId().equals(shown.getId())) {
                            skull.setPlayerProfile(pending);
                            icon.setItemMeta(skull);
                            menu.getInventory().setItem(slot, icon);
                        }
                    }
                });
            });
        }
        return head;
    }

    private static ItemStack item(Material material, String name, String... lines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) for (String paragraph : line.split("\\R", -1)) {
            for (int start = 0; start < Math.max(1, paragraph.length()); start += 44)
                lore.add(Component.text(paragraph.substring(start, Math.min(start + 44, paragraph.length())), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
        return item;
    }

    public static List<String> campAttributes(CampInfo camp, boolean includeSecrets) {
        // Display only properties managed by Camp commands; native Team settings and raw entries stay in storage.
        List<String> attrs = new ArrayList<>(List.of("名称: " + plain(camp.team.displayName()), "ID: " + camp.id,
                "颜色: " + TeamColors.name(camp.team),
                "加入规则: " + camp.inviteRule.info));
        if (camp.inviteRule == InviteRule.ACCEPT_KEY) {
            String key = InviteRule.getAcceptKey(camp.team);
            attrs.add("加入密钥: " + (includeSecrets && key != null ? key : "已设置"));
        }
        return attrs;
    }

    private static boolean allowed(Player player, String permission) { return player.isOp() || player.hasPermission(permission); }
    private static boolean admin(Player player, CampInfo camp) {
        Member actor = camp.getExistingMember(player.getUniqueId());
        return player.isOp() || (actor != null && actor.hasAuthority());
    }
    private static int rank(Member member) { return member.isOwner() ? 3 : member.isAdmin() ? 2 : 1; }
    private static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }

    @Override public void close() {
        inputs.clear();
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getOpenInventory().getTopInventory().getHolder() instanceof ChestMenu) player.closeInventory();
        }
        sessions.clear();
        profiles.clear();
    }
}
