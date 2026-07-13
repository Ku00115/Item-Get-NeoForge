package com.kuzhi.itemget.network;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.client.ClientHooks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncUiLayoutPacket(int x, int y) implements CustomPacketPayload {
    public static final Type<SyncUiLayoutPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "sync_ui_layout"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncUiLayoutPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> { buf.writeVarInt(msg.x); buf.writeVarInt(msg.y); },
            buf -> new SyncUiLayoutPacket(buf.readVarInt(), buf.readVarInt()));
    @Override public Type<SyncUiLayoutPacket> type() { return TYPE; }
    public static void handle(SyncUiLayoutPacket packet, IPayloadContext context) { ClientHooks.setInventoryButtonPosition(packet.x, packet.y); }
}
