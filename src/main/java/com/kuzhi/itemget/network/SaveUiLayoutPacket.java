package com.kuzhi.itemget.network;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.rule.UiLayoutStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveUiLayoutPacket(int x, int y) implements CustomPacketPayload {
    public static final Type<SaveUiLayoutPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "save_ui_layout"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveUiLayoutPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> { buf.writeVarInt(msg.x); buf.writeVarInt(msg.y); },
            buf -> new SaveUiLayoutPacket(buf.readVarInt(), buf.readVarInt()));
    @Override public Type<SaveUiLayoutPacket> type() { return TYPE; }
    public static void handle(SaveUiLayoutPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            int x = Math.max(-1, Math.min(4096, packet.x));
            int y = Math.max(-1, Math.min(4096, packet.y));
            UiLayoutStore.get(player.serverLevel()).setInventoryButton(x, y);
            PacketDistributor.sendToPlayer(player, new SyncUiLayoutPacket(x, y));
        }
    }
}
