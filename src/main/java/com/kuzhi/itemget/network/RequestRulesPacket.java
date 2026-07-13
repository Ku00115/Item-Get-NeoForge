package com.kuzhi.itemget.network;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.rule.RuleStore;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.stream.Collectors;

public record RequestRulesPacket() implements CustomPacketPayload {
    public static final Type<RequestRulesPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "request_rules"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestRulesPacket> STREAM_CODEC = StreamCodec.unit(new RequestRulesPacket());
    @Override public Type<RequestRulesPacket> type() { return TYPE; }
    public static void handle(RequestRulesPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player,
                new SyncRulesPacket(RuleJson.write(RuleStore.get(player.serverLevel()).rules()), player.hasPermissions(2),
                        player.serverLevel().registryAccess().registryOrThrow(Registries.BIOME).keySet().stream().map(Object::toString).sorted().collect(Collectors.joining("\n")),
                        player.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().stream().map(Object::toString).sorted().collect(Collectors.joining("\n")),
                        player.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).entrySet().stream().map(e -> e.getKey().location() + "\t" + e.getValue().msgId()).sorted().collect(Collectors.joining("\n")),
                        player.server.getAdvancements().getAllAdvancements().stream().map(a -> a.id() + "\t" + a.value().display().map(display -> display.getTitle().getString()).orElse(a.id().toString())).sorted().collect(Collectors.joining("\n"))));
    }
}
