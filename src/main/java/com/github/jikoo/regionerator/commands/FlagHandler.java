/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.commands;

import com.github.jikoo.planarwrappers.util.Coords;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.util.yaml.Config;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class for handling logic related to flag management commands.
 */
public class FlagHandler {

	private final @NotNull Regionerator plugin;

	public FlagHandler(@NotNull Regionerator plugin) {
		this.plugin = plugin;
	}

	public void handleFlags(@NotNull CommandSender sender, String @NotNull [] args, boolean flag) {
		List<ChunkPosition> chunks = getSelectedArea(sender, args);
		if (chunks == null) {
			// More descriptive errors are handled when selecting chunks
			return;
		}

		if (chunks.isEmpty()) {
			sender.sendMessage("No chunks selected for (un)flagging!");
			return;
		}

		String worldName = chunks.get(0).name();
		boolean invalid = true;
		for (String world : plugin.config().enabledWorlds()) {
			if (world.equalsIgnoreCase(worldName)) {
				invalid = false;
				// Re-assign so case will match when editing values
				worldName = world;
				break;
			}
		}
		if (invalid) {
			sender.sendMessage("No world \"" + worldName + "\" is enabled for regeneration.");
			return;
		}

		for (ChunkPosition chunk : chunks) {
			if (flag) {
				plugin.getFlagger().flagChunksInRadius(worldName, chunk.chunkX(), chunk.chunkZ(), 0, Config.FLAG_ETERNAL);
			} else {
				plugin.getFlagger().unflagChunk(worldName, chunk.chunkX(), chunk.chunkZ());
			}
		}

		sender.sendMessage("Edited flags successfully!");
	}

	private @Nullable List<ChunkPosition> getSelectedArea(CommandSender sender, String @NotNull [] args) {
		if (args.length < 3 && !(sender instanceof Player)) {
			sender.sendMessage("Console usage: /regionerator (un)flag <world> <chunk X> <chunk Z>");
			sender.sendMessage("Chunk coordinates = regular coordinates / 16");
			return null;
		}

		// Flag chunks by chunk coordinates
		if (args.length > 2) {

			String worldName;
			if (args.length > 3) {
				worldName = args[1];
			} else if (sender instanceof Player) {
				worldName = ((Player) sender).getWorld().getName();
			} else {
				sender.sendMessage("Unable to parse world.");
				sender.sendMessage("/regionerator (un)flag [world] <chunk X> <chunk Z>");
				return null;
			}

			int chunkX;
			int chunkZ;
			try {
				chunkX = Integer.parseInt(args[args.length - 2]);
				chunkZ = Integer.parseInt(args[args.length - 1]);
			} catch (NumberFormatException e) {
				sender.sendMessage("/regionerator (un)flag [world] <chunk X> <chunk Z>");
				return null;
			}
			// This looks silly, but it's necessary to make the compiler happy
			return Collections.singletonList(new ChunkPosition(worldName, chunkX, chunkZ));
		}

		// Safe cast: prior 2 blocks remove all non-players.
		Player player = (Player) sender;

		// Flag current chunk
		if (args.length < 2) {
			Location location = player.getLocation();
			return Collections.singletonList(new ChunkPosition(
					player.getWorld().getName(),
					Coords.blockToChunk(location.getBlockX()),
					Coords.blockToChunk(location.getBlockZ())));
		}

		// 2 args guaranteed, safe
		args[1] = args[1].toLowerCase();
		if (!args[1].equals("selection")) {
			sender.sendMessage("/regionerator (un)flag - (un)flag current chunk");
			sender.sendMessage("/regionerator (un)flag [world] <chunk X> <chunk Z>");
			sender.sendMessage("/regionerator (un)flag selection - unflag WorldEdit selection");
			return null;
		}

		try {
			Class.forName("com.sk89q.worldedit.bukkit.WorldEditPlugin");
		} catch (ClassNotFoundException e) {
			sender.sendMessage("WorldEdit must be enabled to (un)flag selection!");
			return null;
		}

		WorldEditPlugin worldedit = getWE();

		if (worldedit == null) {
			sender.sendMessage("WorldEdit must be enabled to (un)flag selection!");
			return null;
		}

		LocalSession session = worldedit.getSession(player);

		if (session == null || session.getSelectionWorld() == null) {
			sender.sendMessage("You must select an area with WorldEdit to (un)flag!");
			return null;
		}

		Region selection = null;
		try {
			selection = session.getSelection(session.getSelectionWorld());
		} catch (Exception ignored) {
			// Ignored - we return anyway.
		}

		if (selection == null) {
			sender.sendMessage("You must select an area with WorldEdit to (un)flag!");
			return null;
		}

		ArrayList<ChunkPosition> chunks = new ArrayList<>();
		String worldName = session.getSelectionWorld().getName();
		int maxChunkX = Coords.blockToChunk(selection.getMaximumPoint().getBlockX());
		int maxChunkZ = Coords.blockToChunk(selection.getMaximumPoint().getBlockZ());

		for (int minChunkX = Coords.blockToChunk(selection.getMinimumPoint().getBlockX());
			minChunkX <= maxChunkX; minChunkX++) {
			for (int minChunkZ = Coords.blockToChunk(selection.getMinimumPoint().getBlockZ());
				minChunkZ <= maxChunkZ; minChunkZ++) {
				chunks.add(new ChunkPosition(worldName, minChunkX, minChunkZ));
			}
		}

		return chunks;
	}

	private @Nullable WorldEditPlugin getWE() {
		if (!Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
			return null;
		}

		return WorldEditPlugin.getPlugin(WorldEditPlugin.class);
	}

	private record ChunkPosition(String name, int chunkX, int chunkZ) {}

}
