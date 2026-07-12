package com.kuzhi.itemget.network;

import com.kuzhi.itemget.ItemGet;
import com.kuzhi.itemget.client.ClientHooks;
import com.kuzhi.itemget.rule.ReminderRule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShowReminderPacket(String json) implements CustomPacketPayload {
    public static final Type<ShowReminderPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ItemGet.MOD_ID, "show_reminder"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShowReminderPacket> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> buf.writeUtf(msg.json, 65535), buf -> new ShowReminderPacket(buf.readUtf(65535)));
    public ShowReminderPacket(ReminderRule rule) { this(RuleJson.GSON.toJson(rule)); }
    @Override public Type<ShowReminderPacket> type() { return TYPE; }
    public static void handle(ShowReminderPacket packet, IPayloadContext context) { ClientHooks.show(RuleJson.GSON.fromJson(packet.json, ReminderRule.class)); }
}
