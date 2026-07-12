package com.kuzhi.itemget.network;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ItemGetNetwork {
    private ItemGetNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(RequestRulesPacket.TYPE, RequestRulesPacket.STREAM_CODEC, RequestRulesPacket::handle);
        registrar.playToServer(SaveRulesPacket.TYPE, SaveRulesPacket.STREAM_CODEC, SaveRulesPacket::handle);
        registrar.playToClient(SyncRulesPacket.TYPE, SyncRulesPacket.STREAM_CODEC, SyncRulesPacket::handle);
        registrar.playToClient(ShowReminderPacket.TYPE, ShowReminderPacket.STREAM_CODEC, ShowReminderPacket::handle);
    }

    public static void requestRules() { PacketDistributor.sendToServer(new RequestRulesPacket()); }
    public static void saveRules(SaveRulesPacket packet) { PacketDistributor.sendToServer(packet); }
}
