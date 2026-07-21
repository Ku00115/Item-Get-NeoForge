package com.kuzhi.itemget.network;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.ServerEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TriggerObservedPacket(String ruleId) implements CustomPacketPayload {
    public static final Type<TriggerObservedPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "trigger_observed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TriggerObservedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeUtf(msg.ruleId, 128),
            buf -> new TriggerObservedPacket(buf.readUtf(128)));
    @Override public Type<TriggerObservedPacket> type() { return TYPE; }
    public static void handle(TriggerObservedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> { if (context.player() instanceof ServerPlayer player) ServerEvents.triggerObserved(player, packet.ruleId); });
    }
}
