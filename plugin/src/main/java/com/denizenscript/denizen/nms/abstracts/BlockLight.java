package com.denizenscript.denizen.nms.abstracts;

import com.denizenscript.denizen.utilities.FoliaScheduler;
import com.denizenscript.denizen.utilities.blocks.ChunkCoordinate;
import com.denizenscript.denizen.utilities.packets.NetworkInterceptHelper;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BlockLight {

    public static final Map<Location, BlockLight> lightsByLocation = new HashMap<>();
    public static final Map<ChunkCoordinate, List<BlockLight>> lightsByChunk = new HashMap<>();

    public final Block block;
    public final ChunkCoordinate chunkCoord;
    public Chunk chunk;
    public final int originalLight;
    public int currentLight;
    public int cachedLight;
    public int intendedLevel;
    public ScheduledTask removeTask;
    public ScheduledTask updateTask;

    public Chunk getChunk() {
        chunk = Bukkit.getWorld(chunkCoord.worldName).getChunkAt(chunkCoord.x, chunkCoord.z);
        return chunk;
    }

    protected BlockLight(Location location, long ticks) {
        NetworkInterceptHelper.enable();
        this.block = location.getBlock();
        this.chunk = location.getChunk();
        this.chunkCoord = new ChunkCoordinate(chunk);
        this.originalLight = block.getLightFromBlocks();
        this.currentLight = originalLight;
        this.cachedLight = originalLight;
        this.intendedLevel = originalLight;
        this.removeLater(ticks);
    }

    public void removeLater(long ticks) {
        if (ticks > 0) {
            // runOnRegionDelayed: removing the block light resets/updates the block at its location (region-owned world
            // state), so it must run on that location's owning region thread after the given delay.
            this.removeTask = FoliaScheduler.runOnRegionDelayed(block.getLocation(), () -> {
                removeTask = null;
                removeLight(block.getLocation());
            }, ticks);
        }
    }

    public static void removeLight(Location location) {
        location = location.getBlock().getLocation();
        BlockLight blockLight = lightsByLocation.get(location);
        if (blockLight != null) {
            if (blockLight.updateTask != null) {
                blockLight.updateTask.cancel();
                blockLight.updateTask = null;
            }
            blockLight.reset(true);
            if (blockLight.removeTask != null) {
                blockLight.removeTask.cancel();
                blockLight.removeTask = null;
            }
            lightsByLocation.remove(location);
            List<BlockLight> lights = lightsByChunk.get(blockLight.chunkCoord);
            lights.remove(blockLight);
            if (lights.isEmpty()) {
                lightsByChunk.remove(blockLight.chunkCoord);
            }
        }
    }

    public void reset(boolean updateChunk) {
        this.update(originalLight, updateChunk);
    }

    // --- Folia task helpers. These return void so NMS subclasses (which compile against spigot-api and therefore
    // cannot see io.papermc.paper.threadedregions.scheduler.ScheduledTask) can schedule/cancel without referencing it. ---

    /** Schedules a one-shot task on the global region after a delay (ticks). */
    public static void scheduleLater(Runnable runnable, long delayTicks) {
        FoliaScheduler.runGlobalDelayed(runnable, delayTicks);
    }

    /** Stores a delayed update task on the global region, replacing any current handle. */
    public void setUpdateTaskLater(Runnable runnable, long delayTicks) {
        updateTask = FoliaScheduler.runGlobalDelayed(runnable, delayTicks);
    }

    /** Clears the stored update-task handle without cancelling (the task is already running/finished). */
    public void clearUpdateTask() {
        updateTask = null;
    }

    /** Cancels and clears both the remove and update task handles. */
    public void cancelTasks() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (removeTask != null) {
            removeTask.cancel();
            removeTask = null;
        }
    }

    public abstract void update(int lightLevel, boolean updateChunk);
}
