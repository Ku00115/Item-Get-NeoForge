package com.kuzhi.itemget.rule;

import com.google.gson.JsonObject;
import java.util.UUID;

public final class ReminderRule {
    public String id = UUID.randomUUID().toString();
    public String triggerType = TriggerType.ITEM_ACQUIRED.name();
    public JsonObject trigger = new JsonObject();
    public String title = "";
    public String description = "";
    public String icon = "";
    /** Serialized ItemStack used by custom icons; keeps potion, enchantment and other NBT variants. */
    public String iconStack = "";
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
