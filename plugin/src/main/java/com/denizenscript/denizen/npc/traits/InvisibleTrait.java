package com.denizenscript.denizen.npc.traits;

import com.denizenscript.denizen.utilities.FoliaScheduler;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.trait.ArmorStandTrait;
import net.citizensnpcs.util.NMS;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class InvisibleTrait extends Trait implements Listener {

    // <--[language]
    // @name Invisible Trait
    // @group NPC Traits
    // @description
    // The invisible trait will allow a NPC to remain invisible, even after a server restart.
    // It permanently applies the invisible potion effect.
    // Use '/npc invisible' or the 'invisible' script command to toggle this trait.
    //
    // Note that player-type NPCs must have '/npc playerlist' toggled to be turned invisible.
    // Once invisible, the player-type NPCs can be taken off the playerlist.
    // This only applies specifically to player-type NPCs.
    // Playerlist will be enabled automatically if not set in advance, but not automatically removed.
    // -->

    @Persist("")
    private boolean invisible = true;

    private static PotionEffect invis = new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false);

    public InvisibleTrait() {
        super("invisible");
    }

    public void setInvisible(boolean invisible) {
        this.invisible = invisible;
        if (npc.isSpawned() && npc.getEntity() instanceof LivingEntity) {
            setInvisible((LivingEntity) npc.getEntity(), npc, invisible);
        }
    }

    public static void setInvisible(LivingEntity ent, NPC npc, boolean invisible) {
        if (invisible) {
            setInvisible(ent, npc);
        }
        else {
            setVisible(ent, npc);
        }
    }

    public static void setVisible(LivingEntity ent, NPC npc) {
        if (ent.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            ent.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        if (ent.getType() == EntityType.ARMOR_STAND) {
            ((ArmorStand) ent).setVisible(true);
            if (npc != null) {
                npc.getOrAddTrait(ArmorStandTrait.class).setVisible(true);
            }
        }
    }

    public static void setInvisible(LivingEntity ent, NPC npc) {
        // Apply NPC Playerlist if necessary
        if (npc != null && ent.getType() == EntityType.PLAYER) {
            npc.data().setPersistent("removefromplayerlist", false);
            NMS.addOrRemoveFromPlayerList(ent, false);
        }
        if (ent.getType() == EntityType.ARMOR_STAND) {
            ((ArmorStand) ent).setVisible(false);
            if (npc != null) {
                npc.getOrAddTrait(ArmorStandTrait.class).setVisible(false);
            }
        }
        else {
            invis.apply(ent);
        }
    }

    private void setInvisible() {
        if (invisible && npc.isSpawned() && npc.getEntity() instanceof LivingEntity) {
            setInvisible((LivingEntity) npc.getEntity(), npc);
        }
    }

    public boolean isInvisible() {
        return invisible;
    }

    @Override
    public void onSpawn() {
        if (invisible) {
            setInvisible();
            // Workaround bug in Citizens on Paper servers - NPCs don't seem to actually spawn at startup til several ticks *after* the spawn event (2022/03/12)
            if (!npc.isSpawned()) {
                // Folia: poll spawn-state on the global thread (entity may not exist yet, so no entity scheduler available);
                // once spawned, route the entity-touching setInvisible onto the entity's owning thread. init 1, period 1 preserved.
                final io.papermc.paper.threadedregions.scheduler.ScheduledTask[] task = new io.papermc.paper.threadedregions.scheduler.ScheduledTask[1];
                final int[] ticks = {0};
                task[0] = FoliaScheduler.runGlobalRepeating(() -> {
                    if (ticks[0]++ > 80) {
                        if (task[0] != null) {
                            task[0].cancel();
                        }
                        return;
                    }
                    if (npc.isSpawned()) {
                        Entity ent = npc.getEntity();
                        if (ent != null) {
                            FoliaScheduler.runOnEntity(ent, this::setInvisible);
                        }
                        if (task[0] != null) {
                            task[0].cancel();
                        }
                    }
                }, 1, 1);
            }
        }
    }

    public boolean toggle() {
        setInvisible(!invisible);
        return invisible;
    }

    @Override
    public void onRemove() {
        setInvisible(false);
    }

    @Override
    public void onAttach() {
        setInvisible(invisible);
    }
}
