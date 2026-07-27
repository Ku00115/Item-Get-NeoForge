package com.kuzhi.itemget.rule;

import com.google.gson.JsonObject;
import java.util.UUID;

public final class ReminderRule {
    public String id = UUID.randomUUID().toString();
    public String triggerType = TriggerType.ITEM_ACQUIRED.name();
    public JsonObject trigger = new JsonObject();
    public String title = "";
    public String subtitle = "";
    public boolean autoSubtitle = true;
    public String description = "";
    /** Optional handbook text used before the player unlocks this entry. */
    public String lockedTitle = "";
    public String lockedSubtitle = "";
    public String lockedDescription = "";
    /** Optional handbook metadata for addon-generated encyclopedias. */
    public String category = "";
    public String group = "";
    public int sort = 0;
    public String entryNumber = "";
    public String icon = "";
    /** Optional image icon name from config/item_get/images. */
    public String iconImage = "";
    /** Serialized ItemStack used by custom icons; keeps potion, enchantment and other NBT variants. */
    public String iconStack = "";
    /** Optional Ponder target id, usually an item or block registry id. */
    public String ponderTarget = "";
    /** JEI behavior: AUTO, USES, RECIPES, or OFF. */
    public String jeiMode = "AUTO";
    /** Optional JEI item target. Empty means use the trigger item when the trigger has one. */
    public String jeiTarget = "";
    public String displayStyle = "HORIZONTAL";
    public String sound = "item_get:item_acquired";
    /** Reserved for a future optional music system; currently hidden and not played. */
    public String music = "";
    public boolean pauseSingleplayer = true;
    public boolean enabled = true;
    public int triggerRevision = 1;

    public ReminderRule() {
        trigger.addProperty("item", "minecraft:diamond");
        trigger.addProperty("count", 1);
    }

    public String target() { return trigger.has("item") ? trigger.get("item").getAsString() : "minecraft:air"; }
    public String entityTarget() { return trigger.has("entity") ? trigger.get("entity").getAsString() : "minecraft:pig"; }
    public int threshold() { return Math.max(1, trigger.has("count") ? trigger.get("count").getAsInt() : 1); }
}
