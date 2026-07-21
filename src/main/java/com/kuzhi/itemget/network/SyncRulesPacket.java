package com.kuzhi.itemget.network;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.client.ClientHooks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncRulesPacket(String json, boolean editable, String biomes, String structures, String damageTypes, String advancements) implements CustomPacketPayload {
    public static final Type<SyncRulesPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "sync_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRulesPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> { buf.writeUtf(msg.json, 1048576); buf.writeBoolean(msg.editable); buf.writeUtf(msg.biomes, 1048576); buf.writeUtf(msg.structures, 1048576); buf.writeUtf(msg.damageTypes, 1048576); buf.writeUtf(msg.advancements, 1048576); },
            buf -> new SyncRulesPacket(buf.readUtf(1048576), buf.readBoolean(), buf.readUtf(1048576), buf.readUtf(1048576), buf.readUtf(1048576), buf.readUtf(1048576)));
    @Override public Type<SyncRulesPacket> type() { return TYPE; }
    public static void handle(SyncRulesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientHooks.openManager(RuleJson.read(packet.json), packet.editable, packet.biomes, packet.structures, packet.damageTypes, packet.advancements));
    }
}
