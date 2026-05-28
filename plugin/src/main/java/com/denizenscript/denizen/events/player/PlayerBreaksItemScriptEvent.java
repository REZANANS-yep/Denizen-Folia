package com.denizenscript.denizen.events.player;

import com.denizenscript.denizen.objects.EntityTag;
import com.denizenscript.denizen.objects.ItemTag;
import com.denizenscript.denizen.utilities.FoliaScheduler;
import com.denizenscript.denizen.utilities.implementation.BukkitScriptEntryData;
import com.denizenscript.denizen.events.BukkitScriptEvent;
import com.denizenscript.denizen.utilities.inventory.SlotHelper;
import com.denizenscript.denizencore.objects.ObjectTag;
import com.denizenscript.denizencore.objects.core.ElementTag;
import com.denizenscript.denizencore.scripts.ScriptEntryData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerBreaksItemScriptEvent extends BukkitScriptEvent implements Listener {

    // <--[event]
    // @Events
    // player breaks held item
    // player breaks held <item>
    //
    // @Regex ^on player breaks held [^\s]+$
    //
    // @Group Player
    //
    // @Location true
    //
    // @Cancellable true
    //
    // @Triggers when a player breaks the item they are holding.
    //
    // @Context
    // <context.item> returns the item that broke.
    // <context.slot> returns the slot of the item that broke.
    //
    // @Player Always.
    //
    // -->

    public PlayerBreaksItemScriptEvent() {
    }

    public ItemTag item;
    public PlayerItemBreakEvent event;

    @Override
    public boolean couldMatch(ScriptPath path) {
        if (!path.eventLower.startsWith("player breaks held")) {
            return false;
        }
        if (!couldMatchItem(path.eventArgLowerAt(3))) {
            return false;
        }
        return true;
    }

    @Override
    public boolean matches(ScriptPath path) {
        if (!path.tryArgObject(3, item)) {
            return false;
        }
        if (!runInCheck(path, event.getPlayer().getLocation())) {
            return false;
        }
        return super.matches(path);
    }

    @Override
    public ScriptEntryData getScriptEntryData() {
        return new BukkitScriptEntryData(event.getPlayer());
    }

    @Override
    public ObjectTag getContext(String name) {
        if (name.equals("item")) {
            return item;
        }
        else if (name.equals("slot")) {
            return new ElementTag(SlotHelper.slotForItem(event.getPlayer().getInventory(), item.getItemStack()) + 1);
        }
        return super.getContext(name);
    }

    @Override
    public void cancellationChanged() {
        if (cancelled) { // Hacked-in cancellation helper
            final Player player = event.getPlayer();
            final ItemStack itemstack = event.getBrokenItem();
            itemstack.setAmount(itemstack.getAmount() + 1);
            // runOnEntityDelayed(1): restores the broken item and refreshes this player's inventory (player entity state),
            // so it runs on the player's owning region thread after 1 tick
            FoliaScheduler.runOnEntityDelayed(player, () -> {
                itemstack.setDurability(itemstack.getType().getMaxDurability());
                player.updateInventory();
            }, null, 1);
        }
        super.cancellationChanged();
    }

    @EventHandler
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
        if (EntityTag.isNPC(event.getPlayer())) {
            return;
        }
        item = new ItemTag(event.getBrokenItem());
        this.event = event;
        fire(event);
    }
}
