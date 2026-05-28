package com.denizenscript.denizen.npc.traits;

import com.denizenscript.denizen.utilities.FoliaScheduler;
import com.denizenscript.denizen.nms.abstracts.ProfileEditor;
import com.denizenscript.denizen.utilities.packets.NetworkInterceptHelper;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import org.bukkit.Location;

import java.util.UUID;

public class MirrorTrait extends Trait {

    @Persist("")
    public boolean mirror = true;

    public MirrorTrait() {
        super("mirror");
    }

    public void respawn() {
        // Folia: outer step despawns the existing NPC entity -> run on its owning thread (no-op if already gone)
        if (npc.getEntity() == null) {
            return;
        }
        FoliaScheduler.runOnEntity(npc.getEntity(), () -> {
            if (npc.isSpawned()) {
                Location loc = npc.getStoredLocation().clone();
                npc.despawn(DespawnReason.PENDING_RESPAWN);
                // Folia: re-spawn happens at the stored location's region (entity no longer exists post-despawn)
                FoliaScheduler.runOnRegion(loc, () -> {
                    npc.spawn(loc);
                });
            }
        });
    }

    public void mirrorOn() {
        NetworkInterceptHelper.enable();
        UUID uuid = npc.getMinecraftUniqueId();
        if (!ProfileEditor.mirrorUUIDs.contains(uuid)) {
            ProfileEditor.mirrorUUIDs.add(uuid);
            respawn();
        }
    }

    public void mirrorOff() {
        UUID uuid = npc.getMinecraftUniqueId();
        if (ProfileEditor.mirrorUUIDs.contains(uuid)) {
            ProfileEditor.mirrorUUIDs.remove(uuid);
            respawn();
        }
    }

    public void enableMirror() {
        mirror = true;
        mirrorOn();
    }

    public void disableMirror() {
        mirror = false;
        mirrorOff();
    }

    @Override
    public void onSpawn() {
        if (mirror) {
            mirrorOn();
        }
    }

    @Override
    public void onRemove() {
        if (mirror) {
            mirrorOff();
        }
    }

    @Override
    public void onAttach() {
        if (mirror) {
            mirrorOn();
        }
    }
}
