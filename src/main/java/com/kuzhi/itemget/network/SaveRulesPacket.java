package com.kuzhi.itemget.network;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.rule.ReminderRule;
import com.kuzhi.itemget.rule.RuleStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.*;

public record SaveRulesPacket(String json) implements CustomPacketPayload {
    public static final Type<SaveRulesPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "save_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveRulesPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeUtf(msg.json, 1048576), buf -> new SaveRulesPacket(buf.readUtf(1048576)));
    @Override public Type<SaveRulesPacket> type() { return TYPE; }

    public static void handle(SaveRulesPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender) || !sender.hasPermissions(2)) return;
        RuleStore store = RuleStore.get(sender.serverLevel());
        List<ReminderRule> updated = RuleJson.read(packet.json);
        Map<String, ReminderRule> old = new HashMap<>(); store.rules().forEach(rule -> old.put(rule.id, rule));
        Set<String> changed = new HashSet<>();
        for (ReminderRule rule : updated) {
            ReminderRule before = old.get(rule.id);
            boolean triggerChanged = before == null || !Objects.equals(before.triggerType, rule.triggerType) || !Objects.equals(before.trigger, rule.trigger);
            if (triggerChanged) changed.add(rule.id);
            rule.triggerRevision = before == null ? Math.max(1, rule.triggerRevision) : triggerChanged ? Math.max(1, before.triggerRevision) + 1 : Math.max(1, before.triggerRevision);
        }
        store.replace(updated);
        if (!changed.isEmpty()) for (ServerPlayer player : sender.server.getPlayerList().getPlayers()) {
            CompoundTag data = player.getPersistentData().getCompound("item_get_data");
            CompoundTag shown = data.getCompound("shown"), totals = data.getCompound("totals"), states = data.getCompound("condition_states");
            changed.forEach(id -> { shown.remove(id); totals.remove(id); states.remove(id); });
            data.put("shown", shown); data.put("totals", totals); data.put("condition_states", states); player.getPersistentData().put("item_get_data", data);
        }
    }
}
