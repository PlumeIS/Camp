package cn.plumc.camp;

import cn.plumc.camp.commands.CampCommand;
import cn.plumc.camp.ui.CampMenus;
import cn.plumc.camp.ui.ChestMenu;
import io.papermc.paper.event.player.AsyncChatEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CampMenusTest extends CampTestSupport {
    private Player viewer;
    private CampMenus menus;
    private InventoryView view;
    private final AtomicReference<Inventory> shown = new AtomicReference<>();
    private final List<Runnable> nextTick = new ArrayList<>();

    @BeforeEach void setupInventory() {
        ItemFactory factory = mock(ItemFactory.class);
        when(server.getItemFactory()).thenReturn(factory);
        when(factory.getItemMeta(any(Material.class))).thenAnswer(call -> {
            ItemMeta meta = call.getArgument(0) == Material.PLAYER_HEAD ? mock(SkullMeta.class) : mock(ItemMeta.class);
            AtomicReference<List<Component>> lore = new AtomicReference<>();
            AtomicReference<Component> name = new AtomicReference<>();
            doAnswer(c -> { lore.set(c.getArgument(0)); return null; }).when(meta).lore(any());
            when(meta.lore()).thenAnswer(c -> lore.get());
            doAnswer(c -> { name.set(c.getArgument(0)); return null; }).when(meta).displayName(any());
            when(meta.displayName()).thenAnswer(c -> name.get());
            when(meta.clone()).thenReturn(meta);
            if (meta instanceof SkullMeta skull) {
                AtomicReference<PlayerProfile> profile = new AtomicReference<>();
                doAnswer(c -> { profile.set(c.getArgument(0)); return null; }).when(skull).setPlayerProfile(any());
                when(skull.getPlayerProfile()).thenAnswer(c -> profile.get());
            }
            return meta;
        });
        when(factory.isApplicable(any(ItemMeta.class), any(Material.class))).thenReturn(true);
        when(factory.asMetaFor(any(ItemMeta.class), any(Material.class))).thenAnswer(call -> call.getArgument(0));
        when(factory.updateMaterial(any(ItemMeta.class), any(Material.class))).thenAnswer(call -> call.getArgument(1));
        when(server.createInventory(any(InventoryHolder.class), eq(54), any(Component.class))).thenAnswer(call -> {
            Inventory inventory = mock(Inventory.class);
            ItemStack[] slots = new ItemStack[54];
            when(inventory.getHolder()).thenReturn(call.getArgument(0));
            when(inventory.getSize()).thenReturn(54);
            when(inventory.getItem(anyInt())).thenAnswer(c -> slots[c.getArgument(0, Integer.class)]);
            doAnswer(c -> { slots[c.getArgument(0, Integer.class)] = c.getArgument(1); return null; }).when(inventory).setItem(anyInt(), any());
            return inventory;
        });
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.hasTextures()).thenReturn(true);
        when(server.createProfile(any(UUID.class), any())).thenReturn(profile);
        when(plugin.getCommand("camp")).thenReturn(mock(PluginCommand.class));
        viewer = player(UUID.randomUUID(), "Viewer");
        when(viewer.getPlayerProfile()).thenReturn(profile);
        view = mock(InventoryView.class);
        when(view.getTopInventory()).thenAnswer(call -> shown.get());
        when(view.getPlayer()).thenReturn(viewer);
        when(viewer.getOpenInventory()).thenReturn(view);
        when(viewer.openInventory(any(Inventory.class))).thenAnswer(call -> { shown.set(call.getArgument(0)); return view; });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> { nextTick.add(call.getArgument(1)); return null; });
        menus = new CampMenus(plugin, new CampCommand());
    }

    @Test void maximumChestKeepsBothGlassRowsLoreFreeAndPaginatesWithParentReturn() {
        for (int i = 0; i < 30; i++) camp(String.format("camp%02d", i));
        menus.open(viewer, null);
        Inventory first = shown.get();
        assertEquals(54, first.getSize());
        for (int i = 0; i < 9; i++) {
            assertEquals(Material.GRAY_STAINED_GLASS_PANE, first.getItem(i).getType());
            assertNull(first.getItem(i).getItemMeta().lore());
            assertEquals(Material.GRAY_STAINED_GLASS_PANE, first.getItem(45 + i).getType());
            assertNull(first.getItem(45 + i).getItemMeta().lore());
        }
        click(38, ClickType.LEFT);
        assertNotSame(first, shown.get());
        Inventory second = shown.get();
        assertNotNull(second.getItem(9));
        assertNull(second.getItem(12));
        click(9, ClickType.LEFT); // member preview
        assertNotSame(second, shown.get());
        click(36, ClickType.LEFT); // actual parent page
        assertNotNull(shown.get().getItem(37));
        click(37, ClickType.LEFT);
        click(9, ClickType.RIGHT); // log preview
        assertNotSame(first, shown.get());
        click(36, ClickType.LEFT);
        assertNotNull(shown.get().getItem(38));
    }

    @Test void shiftNumberDoubleClickBottomInventoryAndDragCannotMoveMenuItems() {
        camp("a"); menus.open(viewer, null);
        Inventory menu = shown.get();
        for (ClickType click : List.of(ClickType.SHIFT_LEFT, ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.DROP)) {
            InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 9, click, InventoryAction.MOVE_TO_OTHER_INVENTORY);
            menus.onClick(event);
            assertTrue(event.isCancelled());
            tick();
            assertSame(menu, shown.get());
        }
        InventoryClickEvent bottom = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 54, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        menus.onClick(bottom); assertTrue(bottom.isCancelled());
        InventoryDragEvent drag = new InventoryDragEvent(view, new ItemStack(Material.STONE), new ItemStack(Material.STONE), false, Map.of(9, new ItemStack(Material.STONE)));
        menus.onDrag(drag); assertTrue(drag.isCancelled());
    }

    @Test void staleQueuedClickCannotOpenPagesAfterAnotherMenuWasOpened() {
        camp("a"); menus.open(viewer, null);
        InventoryClickEvent click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 9, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        menus.onClick(click);
        menus.open(viewer, null);
        Inventory current = shown.get();
        tick();
        assertSame(current, shown.get());
    }

    private void click(int slot, ClickType type) {
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        menus.onClick(event);
        assertTrue(event.isCancelled());
        tick();
    }

    private void tick() {
        List<Runnable> tasks = new ArrayList<>(nextTick);
        nextTick.clear(); tasks.forEach(Runnable::run);
    }

    @Test void chatCreationPreservesTextAndReturnsToMenu() {
        menus.open(viewer, null);
        click(40, ClickType.LEFT);
        chat("new_camp");
        chat("测试 阵营 &a名称");
        verify(plugin).createCamp(viewer.getUniqueId(), "new_camp", "测试 阵营 &a名称");
        assertNotNull(shown.get());
    }

    @Test void reopeningMenuCancelsAlreadyQueuedChatInput() {
        menus.open(viewer, null);
        click(40, ClickType.LEFT);
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(viewer);
        when(event.message()).thenReturn(Component.text("new_camp"));
        menus.onChat(event);
        verify(event).setCancelled(true);
        menus.open(viewer, null);
        Inventory reopened = shown.get();
        tick();
        assertSame(reopened, shown.get());
        verify(plugin, never()).createCamp(any(), anyString(), anyString());
    }

    @Test void permissionRevokedWhileConfirmationIsOpenPreventsLeave() {
        var camp = camp("a"); member(camp, viewer, false, false);
        menus.open(viewer, "a");
        click(40, ClickType.LEFT); // camp management
        click(10, ClickType.LEFT); // leave confirmation
        when(viewer.hasPermission("camp.leave")).thenReturn(false);
        click(9, ClickType.LEFT);
        assertTrue(camp.hasExistingMember(viewer.getUniqueId()));
    }

    @Test void colorButtonIsHiddenForNonOperatorOwnerAndVisibleForOperator() {
        var camp = camp("a"); member(camp, viewer, true, true);
        assertTrue(viewer.hasPermission("camp.modify.color"));
        menus.open(viewer, "a");
        click(40, ClickType.LEFT);
        assertFalse(java.util.stream.IntStream.range(9, 36).anyMatch(slot -> {
            ItemStack icon = shown.get().getItem(slot);
            return icon != null && icon.getType() == Material.RED_DYE;
        }));
        when(viewer.isOp()).thenReturn(true);
        menus.open(viewer, "a");
        click(40, ClickType.LEFT);
        assertTrue(java.util.stream.IntStream.range(9, 36).anyMatch(slot -> {
            ItemStack icon = shown.get().getItem(slot);
            return icon != null && icon.getType() == Material.RED_DYE;
        }));
        int colorButton = java.util.stream.IntStream.range(9, 36).filter(slot -> {
            ItemStack icon = shown.get().getItem(slot);
            return icon != null && icon.getType() == Material.RED_DYE;
        }).findFirst().orElseThrow();
        click(colorButton, ClickType.LEFT);
        int whiteCount = 0;
        int redSlot = -1;
        for (int slot = 9; slot < 25; slot++) {
            ItemStack icon = shown.get().getItem(slot);
            assertNotNull(icon);
            assertTrue(icon.getType().name().endsWith("_WOOL"));
            if (icon.getType() == Material.WHITE_WOOL) whiteCount++;
            Component name = icon.getItemMeta().displayName();
            String color = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
            assertEquals(cn.plumc.camp.utils.TeamColors.fromName(color), name.color());
            if (color.equals("red")) redSlot = slot;
        }
        assertEquals(1, whiteCount);
        assertTrue(redSlot >= 9);
        assertEquals(Material.RED_WOOL, shown.get().getItem(redSlot).getType());
        click(redSlot, ClickType.LEFT);
        verify(camp.team).color(net.kyori.adventure.text.format.NamedTextColor.RED);
    }

    private void chat(String text) {
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(viewer);
        when(event.message()).thenReturn(Component.text(text));
        menus.onChat(event);
        verify(event).setCancelled(true);
        tick();
    }
}
