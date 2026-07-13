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

public record RequestUiLayoutPacket() implements CustomPacketPayload {
    public static final Type<RequestUiLayoutPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "request_ui_layout"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestUiLayoutPacket> STREAM_CODEC = StreamCodec.unit(new RequestUiLayoutPacket());
    @Override public Type<RequestUiLayoutPacket> type() { return TYPE; }
    public static void handle(RequestUiLayoutPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            UiLayoutStore store = UiLayoutStore.get(player.serverLevel());
            PacketDistributor.sendToPlayer(player, new SyncUiLayoutPacket(store.inventoryButtonX(), store.inventoryButtonY()));
        }
    }
}
