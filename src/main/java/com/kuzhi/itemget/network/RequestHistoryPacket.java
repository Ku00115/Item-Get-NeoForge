package com.kuzhi.itemget.network;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.rule.ReminderRule;
import com.kuzhi.itemget.rule.RuleStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record RequestHistoryPacket() implements CustomPacketPayload {
    public static final Type<RequestHistoryPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "request_history"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestHistoryPacket> STREAM_CODEC = StreamCodec.unit(new RequestHistoryPacket());
    @Override public Type<RequestHistoryPacket> type() { return TYPE; }
    public static void handle(RequestHistoryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, new SyncHistoryPacket(RuleJson.write(history(player))));
        });
    }

    private static List<ReminderRule> history(ServerPlayer player) {
        List<ReminderRule> out = new ArrayList<>();
        CompoundTag data = player.getPersistentData().getCompound("item_get_data");
        ListTag saved = data.getList("history", Tag.TAG_STRING);
        for (Tag tag : saved) try {
            ReminderRule rule = RuleJson.GSON.fromJson(tag.getAsString(), ReminderRule.class);
            if (rule != null) out.add(rule);
        } catch (RuntimeException ignored) {}
        CompoundTag shown = data.getCompound("shown");
        for (ReminderRule rule : RuleStore.get(player.serverLevel()).rules()) {
            if (shown.contains(rule.id) && shown.getInt(rule.id) == Math.max(1, rule.triggerRevision) && !contains(out, rule)) out.add(rule);
        }
        return out;
    }

    private static boolean contains(List<ReminderRule> rules, ReminderRule target) {
        int revision = Math.max(1, target.triggerRevision);
        for (ReminderRule rule : rules) if (rule.id.equals(target.id) && Math.max(1, rule.triggerRevision) == revision) return true;
        return false;
    }
}
