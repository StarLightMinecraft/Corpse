package com.github.unldenis.corpse.logic;

import com.github.unldenis.corpse.manager.CorpsePool;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class EventListener implements Listener {

    private final CorpsePool pool = CorpsePool.getInstance();

    @EventHandler
    private void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        final Location loc = Optional.ofNullable(e.getClickedBlock())
                .map(Block::getLocation)
                .orElse(e.getPlayer().getLocation());
        CorpsePool.getInstance().getCorpses().stream()
                .filter(it -> it.isShownFor(e.getPlayer())
                        && it.getLocation().getWorld() == loc.getWorld()
                        && it.getLocation().distanceSquared(loc) < 4)
                .min(Comparator.comparingDouble(it -> it.getLocation().distanceSquared(loc)))
                .ifPresent(it -> {
                    CorpseActionInventoryHolder holder = new CorpseActionInventoryHolder(it, e.getPlayer());
                    Inventory inv = Bukkit.createInventory(holder, InventoryType.CHEST, ChatColor.RED + "操作此尸体");
                    ItemStack removeItem = new ItemStack(Material.SUNFLOWER);
                    ItemMeta removeItemMeta = removeItem.getItemMeta();
                    assert removeItemMeta != null;
                    removeItemMeta.setDisplayName("埋葬尸体");
                    removeItemMeta.setLore(new ArrayList<String>() {{
                        add(ChatColor.RESET + "//又一位开拓者倒在了新大陆上，他将永远成为这片大地的一部分。//");
                        add(ChatColor.RESET + "-");
                        add(ChatColor.RESET + "尸体将被" + ChatColor.BOLD + "埋葬");
                    }});
                    removeItem.setItemMeta(removeItemMeta);
                    inv.setItem(11, removeItem);
                    ItemStack eatItem = new ItemStack(Material.BEEF);
                    ItemMeta eatItemMeta = removeItem.getItemMeta();
                    assert eatItemMeta != null;
                    eatItemMeta.setDisplayName("食用尸体");
                    eatItemMeta.setLore(new ArrayList<String>() {{
                        add(ChatColor.RESET + "//人类道德的残片冲击着你的意志，但生存的本能迫使你这么做……//");
                        add(ChatColor.RESET + "-");
                        add(ChatColor.RESET + "尸体将被" + ChatColor.BOLD + "食用");
                        add(ChatColor.RESET + "饱食度得到一定" + ChatColor.BOLD + "恢复");
                        add(ChatColor.RESET + "然而你将付出一定" + ChatColor.BOLD + "代价" + ChatColor.RESET + "……");
                    }});
                    eatItem.setItemMeta(eatItemMeta);
                    inv.setItem(14, eatItem);
                    holder.setInventory(inv);

                    e.getPlayer().openInventory(inv);
                });
    }

    @EventHandler
    private void onClickInventory(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof CorpseActionInventoryHolder)) return;
        e.setCancelled(true);
        CorpseActionInventoryHolder holder = (CorpseActionInventoryHolder) e.getInventory().getHolder();
        if (e.getSlot() == 11) {
            holder.getPlayer().playSound(holder.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1, 1);
            holder.getPlayer().sendMessage(ChatColor.RED + "安息吧，不知名的朋友...");
            pool.remove(holder.corpse.getId());
        } else if (e.getSlot() == 14) {
            holder.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 5 * 20, 0));
            holder.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 120 * 20, 2));
            holder.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 20 * 20, 3));
            holder.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 20, 1));
            holder.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 20, 1));
            holder.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 20, 1));
            holder.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 20, 3));
            holder.getPlayer().playSound(holder.getPlayer().getLocation(), Sound.ENTITY_PLAYER_BURP, 1, 1);
            pool.remove(holder.corpse.getId());
        }
        holder.getPlayer().closeInventory();
    }

    static class CorpseActionInventoryHolder implements InventoryHolder {

        public CorpseActionInventoryHolder(Corpse corpse, Player player) {
            this.corpse = corpse;
            this.player = player;
        }

        public Player getPlayer() {
            return player;
        }

        public Corpse getCorpse() {
            return corpse;
        }

        private final Corpse corpse;
        private final Player player;
        private Inventory inventory;

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @NotNull
        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

}
