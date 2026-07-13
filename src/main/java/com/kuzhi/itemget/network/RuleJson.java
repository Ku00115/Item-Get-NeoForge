package com.kuzhi.itemget.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kuzhi.itemget.rule.ReminderRule;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class RuleJson {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public static final Type LIST = new TypeToken<ArrayList<ReminderRule>>() {}.getType();
    public static String write(List<ReminderRule> rules) { return GSON.toJson(rules); }
    public static List<ReminderRule> read(String json) {
        List<ReminderRule> result = GSON.fromJson(json, LIST);
        return result == null ? new ArrayList<>() : result;
    }
}
