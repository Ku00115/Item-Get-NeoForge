package com.kuzhi.itemget.network;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.client.ClientHooks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncObserverRulesPacket(String json) implements CustomPacketPayload {
    public static final Type<SyncObserverRulesPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "sync_observer_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncObserverRulesPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeUtf(msg.json, 1048576),
            buf -> new SyncObserverRulesPacket(buf.readUtf(1048576)));
    @Override public Type<SyncObserverRulesPacket> type() { return TYPE; }
    public static void handle(SyncObserverRulesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.syncObserverRules(RuleJson.read(packet.json)));
    }
}
