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

package com.github.unldenis.corpse;

import com.github.unldenis.corpse.command.*;
import com.github.unldenis.corpse.data.*;
import com.github.unldenis.corpse.logic.*;
import com.github.unldenis.corpse.manager.*;
import org.bukkit.configuration.file.*;
import org.bukkit.plugin.java.*;
import org.bukkit.scheduler.*;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public class CorpseP extends JavaPlugin {

    private static CorpseP instance;

    private DataManager configYml;
    private CorpsePool pool;

    private Path data = getDataFolder().toPath().resolve("data.json");

    @Override
    public void onEnable() {
        CorpseP.instance = this;

        //register commands
        this.getCommand("spawncorpse").setExecutor(new SpawnCorpseCommand());
        this.getCommand("removecorpse").setExecutor(new RemoveCorpseCommand());

        //load config
        configYml = new DataManager(this, "config.yml");

        //load instance
        pool = CorpsePool.getInstance();

        // load data
        saveResource("data.json", false);
        try {
            pool.loadCorpses(data.toFile());
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        //register events
        getServer().getPluginManager().registerEvents(new EventListener(), this);
    }

    @Override
    public void onDisable() {
        BukkitTask task = pool.getTickTask();
        if (task != null) {
            task.cancel();
        }
        for (Corpse c : pool.getCorpses()) {
            c.getSeeingPlayers()
                    .forEach(c::hide);
        }

        try {
            pool.saveCorpses(data.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    public static CorpseP getInstance() {
        return instance;
    }

    @NotNull
    public FileConfiguration getConfigYml() {
        return configYml.getConfig();
    }
}
