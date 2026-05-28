package com.denizenscript.denizen.npc.traits;

import com.denizenscript.denizen.utilities.FoliaScheduler;
import com.denizenscript.denizen.scripts.commands.entity.RenameCommand;
import com.denizenscript.denizen.utilities.packets.NetworkInterceptHelper;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.UUID;

public class MirrorNameTrait extends Trait {

    @Persist("")
    public boolean mirror = true;

    public UUID mirroredUUID = null;

    public MirrorNameTrait() {
        super("mirrorname");
    }

    public void respawn() {
        if (!npc.isSpawned() || npc.getEntity().getType() != EntityType.PLAYER) {
            return;
        }
        // Folia: outer step despawns the existing NPC entity -> run on its owning thread (guarded isSpawned above)
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
        if (!npc.isSpawned()) {
            return;
        }
        mirroredUUID = npc.getEntity().getUniqueId();
        RenameCommand.RenameData renamer = new RenameCommand.RenameData();
        renamer.nameFunction = Player::getName;
        RenameCommand.addDynamicRename(npc.getEntity(), null, renamer);
    }

    public void mirrorOff() {
        if (mirroredUUID == null) {
            return;
        }
        if (RenameCommand.customNames.remove(mirroredUUID) != null && npc.isSpawned() && npc.getEntity().getType() == EntityType.PLAYER) {
            respawn();
        }
    }

    public void enableMirror() {
        mirror = true;
        mirrorOn();
        if (npc.isSpawned() && npc.getEntity().getType() == EntityType.PLAYER) {
            respawn();
        }
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
