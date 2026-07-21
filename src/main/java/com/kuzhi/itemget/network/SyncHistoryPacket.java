package com.kuzhi.itemget.network;

import com.kuzhi.itemget.ItemGet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncHistoryPacket(String json) implements CustomPacketPayload {
    public static final Type<SyncHistoryPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "sync_history"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncHistoryPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeUtf(msg.json, 1048576), buf -> new SyncHistoryPacket(buf.readUtf(1048576)));
    @Override public Type<SyncHistoryPacket> type() { return TYPE; }
    public static void handle(SyncHistoryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.kuzhi.itemget.client.ClientHooks.openHandbook(RuleJson.read(packet.json)));
    }
}
