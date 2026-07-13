package com.kuzhi.itemget.client.screen;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

final class TranslatedText {
    private TranslatedText() {}

    static String resolve(String text) {
        if (text == null || text.isBlank()) return "";
        String key = text.trim();
        return I18n.exists(key) ? I18n.get(key) : text;
    }

    static Component component(String text) {
        return Component.literal(resolve(text));
    }
}
