package com.github.unldenis.corpse.logic;

import com.github.unldenis.corpse.manager.CorpsePool;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Comparator;
import java.util.Optional;

public class EventListener implements Listener {

    private final CorpsePool pool = CorpsePool.getInstance();

    @EventHandler
    private void onRightClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // Sneaking for remove only, or eat it.
        final boolean mode = e.getPlayer().isSneaking();
        final Location loc = Optional.ofNullable(e.getClickedBlock())
                .map(Block::getLocation)
                .orElse(e.getPlayer().getLocation());
        CorpsePool.getInstance().getCorpses().stream()
                .filter(it -> it.isShownFor(e.getPlayer())
                        && it.getLocation().getWorld() == loc.getWorld()
                        && it.getLocation().distanceSquared(loc) < 4)
                .min(Comparator.comparingDouble(it -> it.getLocation().distanceSquared(loc)))
                .ifPresent(it -> {
                    if (mode) {
                        e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1, 1);
                        e.getPlayer().sendMessage(ChatColor.RED + "安息吧，不知名的朋友...");
                    } else {
                        e.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 5 * 20, 0));
                        e.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 120 * 20, 2));
                        e.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 20 * 20, 3));
                        e.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 20, 1));
                        e.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 20 * 20, 1));
                        e.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 20, 1));
                        e.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 20, 3));
                        e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_PLAYER_BURP, 1, 1);
                    }
                    pool.remove(it.getId());
                });
    }

}
