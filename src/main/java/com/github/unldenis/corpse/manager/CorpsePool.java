/*
 * Corpse - Dead bodies in bukkit
 * Copyright (C) unldenis <https://github.com/unldenis>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.unldenis.corpse.manager;

import com.github.unldenis.corpse.*;
import com.github.unldenis.corpse.api.CorpseAPI;
import com.github.unldenis.corpse.logic.*;
import com.google.common.collect.*;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.bukkit.*;
import org.bukkit.configuration.file.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class CorpsePool implements Listener {

    private static CorpsePool instance;

    private static final Random RANDOM = new Random();
    private final CorpseP plugin;

    //config options
    private final double spawnDistance;
    private final int timeRemove;
    private final boolean onDeath;
    private final boolean showTags;
    private final boolean renderArmor;


    private final Map<Integer, Corpse> corpseMap = new ConcurrentHashMap<>();

    private BukkitTask tickTask;

    @ApiStatus.Internal
    private CorpsePool() {
        this.plugin = CorpseP.getInstance();

        FileConfiguration config = plugin.getConfigYml();
        this.spawnDistance = Math.pow(config.getInt("corpse-distance"), 2);
        this.timeRemove = config.getInt("corpse-time");
        this.onDeath = config.getBoolean("on-death");
        this.showTags = config.getBoolean("show-tags");
        this.renderArmor = config.getBoolean("render-armor");

        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.corpseTick();
    }

    private void corpseTick() {
        tickTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this.plugin, () -> {
            for (Player player : ImmutableList.copyOf(Bukkit.getOnlinePlayers())) {
                for (Corpse corpse : this.corpseMap.values()) {
                    Location holoLoc = corpse.getLocation();
                    Location playerLoc = player.getLocation();
                    boolean isShown = corpse.isShownFor(player);

                    if (!holoLoc.getWorld().equals(playerLoc.getWorld()) && isShown) {
                        corpse.hide(player);
                        continue;
                    } else if (!holoLoc.getWorld()
                            .isChunkLoaded(holoLoc.getBlockX() >> 4, holoLoc.getBlockZ() >> 4) && isShown) {
                        corpse.hide(player);
                        continue;
                    }
                    boolean inRange = holoLoc.distanceSquared(playerLoc) <= this.spawnDistance;

                    if (!inRange && isShown) {
                        corpse.hide(player);
                    } else if (inRange && !isShown) {
                        corpse.show(player);
                    }
                }
            }
        }, 20, 2);
    }

    @NotNull
    public Optional<Corpse> getCorpse(int entityId) {
        return Optional.ofNullable(this.corpseMap.get(entityId));
    }

    @NotNull
    public Optional<Corpse> getCorpse(String name) {
        return this.getCorpses()
                .stream()
                .filter(corpse -> corpse.getName().equals(name))
                .findFirst();
    }

    public void remove(int entityId) {
        this.getCorpse(entityId).ifPresent(corpse -> {
            this.corpseMap.remove(entityId);
            corpse.getSeeingPlayers()
                    .forEach(corpse::hide);
        });
    }

    public int getFreeEntityId() {
        int id;

        do {
            id = RANDOM.nextInt(Integer.MAX_VALUE);
        } while (this.corpseMap.containsKey(id));

        return id;
    }

    @NotNull
    public Collection<Corpse> getCorpses() {
        return Collections.unmodifiableCollection(this.corpseMap.values());
    }

    public void takeCareOf(@NotNull Corpse corpse) {
        // Prevent two corpses with same name and showTags is enabled
        if (this.showTags) {
            this.getCorpse(corpse.getName()).ifPresent(c -> {
                this.corpseMap.remove(c.getId());
                c.getSeeingPlayers()
                        .forEach(c::hide);
            });
        }
        this.corpseMap.put(corpse.getId(), corpse);
    }

    public int getTimeRemove() {
        return timeRemove;
    }

    public boolean isRenderArmor() {
        return renderArmor;
    }

    public boolean isShowTags() {
        return showTags;
    }

    @Nullable
    public BukkitTask getTickTask() {
        return tickTask;
    }

    @EventHandler
    public void handleQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        this.corpseMap.values().stream()
                .filter(corpse -> corpse.isShownFor(player))
                .forEach(corpse -> corpse.hide(player));
    }

    @EventHandler
    public void handleRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        this.corpseMap.values().stream()
                .filter(corpse -> corpse.isShownFor(player))
                .forEach(corpse -> corpse.hide(player));
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void handleDeath(PlayerDeathEvent event) {
        //event.setDeathMessage(null);
        if (onDeath) {
            new Corpse(event.getEntity());
        }
    }

    @NotNull
    public static synchronized CorpsePool getInstance() {
        if (instance == null) {
            instance = new CorpsePool();
        }
        return instance;
    }

    public void loadCorpses(File data) throws FileNotFoundException {
        new Gson().fromJson(Files.newReader(data, StandardCharsets.UTF_8), JsonArray.class).forEach(json -> {
            JsonObject obj = json.getAsJsonObject();
            OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(obj.get("uuid").getAsString()));
            Location location = Location.deserialize(new Gson().fromJson(obj.get("location"), new TypeToken<Map<String, Object>>() {
            }.getType()));
            Corpse corpse = CorpseAPI.getInstance().spawnCorpse(player, location);
        });
    }

    public void saveCorpses(File data) throws IOException {
        try (BufferedWriter writer = Files.newWriter(data, StandardCharsets.UTF_8)) {
            JsonArray array = new JsonArray();
            this.getCorpses().forEach(it->{
                JsonObject obj = new JsonObject();
                obj.addProperty("uuid", it.getPlayerUuid().toString());
                obj.add("location", new Gson().toJsonTree(it.getLocation().serialize()));
                array.add(obj);
            });
            writer.write(new Gson().toJson(array));
        }
    }

}
