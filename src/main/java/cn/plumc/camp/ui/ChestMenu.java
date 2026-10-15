package cn.plumc.camp.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A 54-slot menu: two intact glass borders, 27 content slots and 9 controls. */
public final class ChestMenu implements InventoryHolder {
    public static final int CONTENT_SIZE = 27;
    public final UUID viewer;
    public final Supplier<ChestMenu> parent;
    private final Inventory inventory;
    private final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();

    public ChestMenu(UUID viewer, Component title, Supplier<ChestMenu> parent) {
        this.viewer = viewer;
        this.parent = parent;
        inventory = Bukkit.createInventory(this, 54, title);
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        meta.lore(null);
        border.setItemMeta(meta);
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(45 + i, border);
        }
    }

    public void button(int slot, ItemStack icon, Consumer<ClickType> action) {
        if (slot < 9 || slot >= 45) throw new IllegalArgumentException("Menu controls must stay inside glass borders");
        inventory.setItem(slot, icon);
        if (action != null) actions.put(slot, action);
    }

    public void click(int slot, ClickType click) {
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
        Consumer<ClickType> action = actions.get(slot);
        if (action != null) action.accept(click);
    }

    @Override public @NotNull Inventory getInventory() { return inventory; }
}
