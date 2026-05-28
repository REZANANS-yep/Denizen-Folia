package com.denizenscript.denizen.utilities;

import com.denizenscript.denizen.Denizen;
import com.denizenscript.denizencore.utilities.debugging.Debug;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

/**
 * Central entry point for Folia's regionized scheduler API.
 * <p>
 * On Folia there is no single main thread. Work must be dispatched to the thread that owns the relevant data:
 * <ul>
 *     <li><b>Global region</b> - server-wide state (time, weather, game rules) and, in Denizen's case, the script
 *     engine tick itself. This is the one logical "main" thread the queue engine runs on.</li>
 *     <li><b>Region</b> (by {@link Location} or chunk) - blocks and unattached world state in a given area.</li>
 *     <li><b>Entity</b> - a specific entity (and whatever region currently owns it; Folia migrates this for us).</li>
 *     <li><b>Async</b> - off any tick thread, for I/O that must never touch world state directly.</li>
 * </ul>
 * Mutations of world/entity state from inside the queue engine (which runs on the global region) MUST be routed
 * through {@link #runOnEntity} / {@link #runOnRegion} or Folia will throw an {@code IllegalStateException}.
 */
public class FoliaScheduler {

    private static Plugin plugin() {
        return Denizen.getInstance();
    }

    /** Runs the task on the global region thread as soon as possible (next global tick if called off-thread). */
    public static ScheduledTask runGlobal(Runnable runnable) {
        return Bukkit.getGlobalRegionScheduler().run(plugin(), task -> runnable.run());
    }

    /** Runs the task on the global region thread after a delay (in ticks). A delay &lt;= 0 runs as soon as possible. */
    public static ScheduledTask runGlobalDelayed(Runnable runnable, long delayTicks) {
        if (delayTicks <= 0) {
            return runGlobal(runnable);
        }
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin(), task -> runnable.run(), delayTicks);
    }

    /** Repeats the task on the global region thread. Folia requires both delays to be at least 1 tick. */
    public static ScheduledTask runGlobalRepeating(Runnable runnable, long initialDelayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin(), task -> runnable.run(),
                Math.max(1, initialDelayTicks), Math.max(1, periodTicks));
    }

    /** Runs the task on the thread currently owning the given entity. No-op (retired callback) if the entity is gone. */
    public static void runOnEntity(Entity entity, Runnable runnable) {
        runOnEntity(entity, runnable, null);
    }

    /** Runs the task on the thread currently owning the given entity, invoking {@code retired} if the entity no longer exists.
     * Returns null if the entity was already retired. */
    public static ScheduledTask runOnEntity(Entity entity, Runnable runnable, Runnable retired) {
        return entity.getScheduler().run(plugin(), task -> runnable.run(), retired);
    }

    /** Runs the task on the entity's owning thread after a delay (ticks). Folia requires delay &gt;= 1 tick. */
    public static ScheduledTask runOnEntityDelayed(Entity entity, Runnable runnable, Runnable retired, long delayTicks) {
        return entity.getScheduler().runDelayed(plugin(), task -> runnable.run(), retired, Math.max(1, delayTicks));
    }

    /** Repeats the task on the entity's owning thread (Folia migrates ownership as the entity moves between regions). */
    public static ScheduledTask runOnEntityRepeating(Entity entity, Runnable runnable, Runnable retired, long initialDelayTicks, long periodTicks) {
        return entity.getScheduler().runAtFixedRate(plugin(), task -> runnable.run(), retired, Math.max(1, initialDelayTicks), Math.max(1, periodTicks));
    }

    /** Runs the task on the thread owning the region of the given location. */
    public static void runOnRegion(Location location, Runnable runnable) {
        Bukkit.getRegionScheduler().run(plugin(), location, task -> runnable.run());
    }

    /** Runs the task on the thread owning the region of the given chunk. */
    public static void runOnRegion(World world, int chunkX, int chunkZ, Runnable runnable) {
        Bukkit.getRegionScheduler().run(plugin(), world, chunkX, chunkZ, task -> runnable.run());
    }

    /** Runs the task on the owning region of the given location after a delay (ticks). Folia requires delay &gt;= 1 tick. */
    public static ScheduledTask runOnRegionDelayed(Location location, Runnable runnable, long delayTicks) {
        return Bukkit.getRegionScheduler().runDelayed(plugin(), location, task -> runnable.run(), Math.max(1, delayTicks));
    }

    /** Repeats the task on the owning region of the given location. */
    public static ScheduledTask runOnRegionRepeating(Location location, Runnable runnable, long initialDelayTicks, long periodTicks) {
        return Bukkit.getRegionScheduler().runAtFixedRate(plugin(), location, task -> runnable.run(), Math.max(1, initialDelayTicks), Math.max(1, periodTicks));
    }

    /** Runs the task off any tick thread, for blocking I/O. Must not touch world/entity state directly. */
    public static ScheduledTask runAsync(Runnable runnable) {
        return Bukkit.getAsyncScheduler().runNow(plugin(), task -> runnable.run());
    }

    /** Runs the task off any tick thread after a delay (given in ticks, converted to ms). */
    public static ScheduledTask runAsyncDelayed(Runnable runnable, long delayTicks) {
        return Bukkit.getAsyncScheduler().runDelayed(plugin(), task -> runnable.run(), Math.max(1, delayTicks) * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Repeats the task off any tick thread (delays given in ticks, converted to ms). */
    public static ScheduledTask runAsyncRepeating(Runnable runnable, long initialDelayTicks, long periodTicks) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin(), task -> runnable.run(), Math.max(1, initialDelayTicks) * 50L, Math.max(1, periodTicks) * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Runs the task immediately if the current thread already owns the given entity's region (so the call is a normal
     * synchronous operation), otherwise dispatches it to the entity's scheduler. Use this to wrap entity mutations
     * that may be invoked either from an event on the entity's own region thread or from the global engine tick.
     */
    public static void ensureEntity(Entity entity, Runnable runnable) {
        if (Bukkit.isOwnedByCurrentRegion(entity)) {
            runnable.run();
        }
        else {
            entity.getScheduler().run(plugin(), task -> runnable.run(), null);
        }
    }

    /**
     * Runs the task immediately if the current thread already owns the given location's region, otherwise dispatches
     * it to that region's scheduler. Use this to wrap block/location mutations.
     */
    public static void ensureRegion(Location location, Runnable runnable) {
        if (Bukkit.isOwnedByCurrentRegion(location)) {
            runnable.run();
        }
        else {
            Bukkit.getRegionScheduler().run(plugin(), location, task -> runnable.run());
        }
    }

    /** Cancels every Denizen-owned scheduled task across the global and async schedulers (used on disable). */
    public static void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin());
        Bukkit.getAsyncScheduler().cancelTasks(plugin());
    }

    /** When true, {@link #warnIfCrossRegion} logs a throttled warning + stack trace for off-region world reads.
     * Enable during Folia testing to enumerate the remaining raw cross-region read sites that need a snapshot or a ~waitable. */
    public static boolean diagnoseCrossRegion = false;

    private static long lastCrossRegionWarn = 0;

    /**
     * Diagnostic for the Folia read-hardening pass: if {@code diagnoseCrossRegion} is on and the current thread does
     * NOT own the given entity's region, logs a throttled warning with a stack trace identifying the read site.
     * Does not change behavior - the subsequent read may still throw; this just makes offenders visible during testing.
     */
    public static void warnIfCrossRegion(Entity entity, String what) {
        if (!diagnoseCrossRegion || entity == null || Bukkit.isOwnedByCurrentRegion(entity)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCrossRegionWarn < 1000) {
            return;
        }
        lastCrossRegionWarn = now;
        Debug.echoError("[Folia] Cross-region read '" + what + "' on entity off its owning region - needs a snapshot or ~waitable:");
        Debug.echoError(new RuntimeException("cross-region read stack"));
    }

    /** Location-based variant of {@link #warnIfCrossRegion(Entity, String)}. */
    public static void warnIfCrossRegion(Location location, String what) {
        if (!diagnoseCrossRegion || location == null || Bukkit.isOwnedByCurrentRegion(location)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCrossRegionWarn < 1000) {
            return;
        }
        lastCrossRegionWarn = now;
        Debug.echoError("[Folia] Cross-region read '" + what + "' on location off its owning region - needs a snapshot or ~waitable:");
        Debug.echoError(new RuntimeException("cross-region read stack"));
    }
}
