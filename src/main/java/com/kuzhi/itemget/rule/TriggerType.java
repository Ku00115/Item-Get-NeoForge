package com.kuzhi.itemget.rule;

/** New trigger types can be added without changing the stored rule shape. */
public enum TriggerType {
    ITEM_ACQUIRED("item_get.trigger.item_acquired"),
    ENTITY_KILLED("item_get.trigger.entity_killed"),
    HEALTH_AT("item_get.trigger.health_at"),
    HUNGER_AT("item_get.trigger.hunger_at"),
    EFFECT_GAINED("item_get.trigger.effect_gained"),
    WEATHER_IS("item_get.trigger.weather_is"),
    TIME_IS("item_get.trigger.time_is"),
    ENTER_BIOME("item_get.trigger.enter_biome"),
    ENTER_STRUCTURE("item_get.trigger.enter_structure"),
    DEATH_BY("item_get.trigger.death_by"),
    ADVANCEMENT_DONE("item_get.trigger.advancement_done"),
    OBSERVE_BLOCK("item_get.trigger.observe_block"),
    OBSERVE_ENTITY("item_get.trigger.observe_entity"),
    HOVER_ITEM("item_get.trigger.hover_item");

    public final String translationKey;
    TriggerType(String translationKey) { this.translationKey = translationKey; }

    public static TriggerType parse(String value) {
        try { return valueOf(value); }
        catch (Exception ignored) { return ITEM_ACQUIRED; }
    }
}
