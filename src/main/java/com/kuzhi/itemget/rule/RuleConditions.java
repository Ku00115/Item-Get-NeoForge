package com.kuzhi.itemget.rule;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class RuleConditions {
    public static final String CONDITIONS = "conditions";
    public static final String LOGIC = "condition_logic";

    private RuleConditions() {}

    public record Entry(TriggerType type, JsonObject data, boolean inverted, int index) {}

    public static List<Entry> entries(ReminderRule rule) {
        List<Entry> out = new ArrayList<>();
        if (rule.trigger != null && rule.trigger.has(CONDITIONS) && rule.trigger.get(CONDITIONS).isJsonArray()) {
            JsonArray array = rule.trigger.getAsJsonArray(CONDITIONS);
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                JsonObject data = object.has("data") && object.get("data").isJsonObject() ? object.getAsJsonObject("data").deepCopy() : new JsonObject();
                out.add(new Entry(TriggerType.parse(text(object, "type", TriggerType.ITEM_ACQUIRED.name())), data, false, out.size()));
            }
        }
        if (out.isEmpty()) out.add(new Entry(TriggerType.parse(rule.triggerType), legacyData(rule), false, 0));
        return out;
    }

    public static String logic(ReminderRule rule) {
        if (rule.trigger != null && rule.trigger.has(LOGIC) && "OR".equalsIgnoreCase(rule.trigger.get(LOGIC).getAsString())) return "OR";
        return "AND";
    }

    public static void write(ReminderRule rule, List<Entry> entries, String logic) {
        if (entries == null || entries.isEmpty()) return;
        Entry first = entries.get(0);
        rule.triggerType = first.type().name();
        rule.trigger = first.data().deepCopy();
        if (entries.size() == 1 && !"OR".equalsIgnoreCase(logic)) return;
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            JsonObject object = new JsonObject();
            object.addProperty("type", entry.type().name());
            object.add("data", entry.data().deepCopy());
            array.add(object);
        }
        rule.trigger.add(CONDITIONS, array);
        rule.trigger.addProperty(LOGIC, "OR".equalsIgnoreCase(logic) ? "OR" : "AND");
    }

    private static JsonObject legacyData(ReminderRule rule) {
        JsonObject data = rule.trigger == null ? new JsonObject() : rule.trigger.deepCopy();
        data.remove(CONDITIONS);
        data.remove(LOGIC);
        data.remove("condition_preview");
        return data;
    }

    private static String text(JsonObject object, String key, String fallback) {
        return object.has(key) ? object.get(key).getAsString() : fallback;
    }
}
