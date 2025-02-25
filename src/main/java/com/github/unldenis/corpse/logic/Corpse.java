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

package com.github.unldenis.corpse.logic;

import com.comphenix.protocol.*;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.wrappers.*;
import com.github.unldenis.corpse.*;
import com.github.unldenis.corpse.manager.*;
import com.github.unldenis.corpse.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.scoreboard.*;
import org.jetbrains.annotations.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

public class Corpse {

    protected final int id;

    protected final UUID uuid;

    protected final UUID playerUuid;

    protected final String name;

    protected final Location location;

    protected final WrappedGameProfile profile;

    private final Collection<Player> seeingPlayers = new CopyOnWriteArraySet<>();

    private final PacketLoader packetLoader;

    private final CorpsePool pool;

    private boolean armor = false;
    protected ItemStack[] armorContents;


    @ApiStatus.Internal
    public Corpse(
            @NotNull Location location,
            @NotNull WrappedGameProfile wrappedGameProfile,
            @Nullable ItemStack[] armorContents,
            @Nullable String name,
            @Nullable UUID playerUuid
    ) {
        pool = CorpsePool.getInstance();

        this.id = pool.getFreeEntityId();
        this.uuid = new UUID(new Random().nextLong(), 0);
        this.playerUuid = playerUuid;
        String rawName = name == null ? ProfileUtils.randomName() : name;
        this.name = "Corpse:"+rawName.substring(0,Math.min(rawName.length(),15));
        this.location = location;
        this.profile = new WrappedGameProfile(this.uuid, rawName);
        //set skin to profile WrappedGameProfile
        wrappedGameProfile.getProperties().get("textures").forEach(property -> profile.getProperties().put("textures", property));

        if(pool.isRenderArmor() && armorContents != null) {
            this.armorContents = armorContents.clone();
            this.armor = this.armorContents[0] != null || this.armorContents[1] != null || this.armorContents[2] != null || this.armorContents[3] != null;
        }
        //load packets
        packetLoader = new PacketLoader(this);
        packetLoader.load();

        //pool take care
        pool.takeCareOf(this);

        //remove eventually corpse after X seconds
        int time = pool.getTimeRemove();
        if(time > -1) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(CorpseP.getInstance(), ()-> pool.remove(this.id), 20L*time);
        }

    }

    public Corpse(@NotNull Player player) {
        this(player.getLocation(), WrappedGameProfile.fromPlayer(player), player.getInventory().getArmorContents(), player.getName(),player.getUniqueId());
    }

    public Corpse(
            @NotNull Location location,
            @NotNull OfflinePlayer offlinePlayer,
            @Nullable ItemStack[] armorContents
    ) {
        this(location, WrappedGameProfile.fromOfflinePlayer(offlinePlayer), armorContents, offlinePlayer.getName(),offlinePlayer.getUniqueId());
    }

    @ApiStatus.Internal
    public void show(@NotNull Player player) {
        this.seeingPlayers.add(player);

        if(!this.pool.isShowTags()) {
            hideNameTag(player);
        }
        sendPackets(player,
                this.packetLoader.getWrapperPlayerInfoAdd().get(),
                this.packetLoader.getWrapperNamedEntitySpawn().get(),
                this.packetLoader.getWrapperEntityMetadata().get()); // Set sleep

        if (VersionUtil.isBelow(VersionUtil.VersionEnum.V1_12)) {
            player.sendBlockChange(BedUtil.getBedLocation(location), Material.valueOf("BED_BLOCK"), (byte) BedUtil.yawToFacing(location.getYaw()));
            sendPackets(player,
                    this.packetLoader.getWrapperBed().get(),
                    this.packetLoader.getWrapperEntityTeleport().get());  // Set the correct height of the player lying down
        }

        if(armor) {
            sendPackets(player, this.packetLoader.getWrapperEntityEquipment().getMore());
        }

        Bukkit.getScheduler().runTaskLaterAsynchronously(
                CorpseP.getInstance(),
                () -> sendPackets(player, this.packetLoader.getWrapperPlayerInfoRemove().get()),
                2L);

    }

    @ApiStatus.Internal
    public void hide(@NotNull Player player) {
        sendPackets(player, this.packetLoader.getWrapperEntityDestroy().get());
        if(!this.pool.isShowTags()) {
            showNameTag(player);
        }
        this.seeingPlayers.remove(player);
    }

    public boolean isShownFor(@NotNull Player player) {
        return this.seeingPlayers.contains(player);
    }

    private static final String TEAM_NAME = "corpse-lib";

    private void hideNameTag(@NotNull Player player) {
        // hide nametag to player
        org.bukkit.scoreboard.Scoreboard scoreboard = player.getScoreboard();
        Team npcs = null;
        for(Team team : scoreboard.getTeams()) {
            if(team.getName().equals(Corpse.TEAM_NAME)) {
                npcs = team;
                break;
            }
        }
        if(npcs == null) {
            npcs = scoreboard.registerNewTeam(Corpse.TEAM_NAME);
        }
        npcs.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        npcs.addEntry(this.name);
    }

    private void showNameTag(@NotNull Player player) {
        // show nametag to player
        player.getScoreboard().getTeams()
                .stream()
                .filter(team -> team.getName().equals(Corpse.TEAM_NAME))
                .forEach(team -> team.removeEntry(this.name));
    }

    private void sendPackets(Player player, PacketContainer... packets) {
        for(PacketContainer packet: packets) {
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
    }

    public int getId() {
        return id;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public Location getLocation() {
        return location;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    @NotNull
    public Collection<Player> getSeeingPlayers() {
        return Collections.unmodifiableCollection(this.seeingPlayers);
    }

}
