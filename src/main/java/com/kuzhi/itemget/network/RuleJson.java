package com.kuzhi.itemget.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kuzhi.itemget.rule.ReminderRule;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

final class RuleJson {
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    static final Type LIST = new TypeToken<ArrayList<ReminderRule>>() {}.getType();
    static String write(List<ReminderRule> rules) { return GSON.toJson(rules); }
    static List<ReminderRule> read(String json) {
        List<ReminderRule> result = GSON.fromJson(json, LIST);
        return result == null ? new ArrayList<>() : result;
    }
}
