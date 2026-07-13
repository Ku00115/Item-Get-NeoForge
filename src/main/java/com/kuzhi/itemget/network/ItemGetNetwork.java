package com.kuzhi.itemget.network;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ItemGetNetwork {
    private ItemGetNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("5");
        registrar.playToServer(RequestRulesPacket.TYPE, RequestRulesPacket.STREAM_CODEC, RequestRulesPacket::handle);
        registrar.playToServer(SaveRulesPacket.TYPE, SaveRulesPacket.STREAM_CODEC, SaveRulesPacket::handle);
        registrar.playToServer(RequestHistoryPacket.TYPE, RequestHistoryPacket.STREAM_CODEC, RequestHistoryPacket::handle);
        registrar.playToServer(RequestUiLayoutPacket.TYPE, RequestUiLayoutPacket.STREAM_CODEC, RequestUiLayoutPacket::handle);
        registrar.playToServer(SaveUiLayoutPacket.TYPE, SaveUiLayoutPacket.STREAM_CODEC, SaveUiLayoutPacket::handle);
        registrar.playToClient(SyncRulesPacket.TYPE, SyncRulesPacket.STREAM_CODEC, SyncRulesPacket::handle);
        registrar.playToClient(ShowReminderPacket.TYPE, ShowReminderPacket.STREAM_CODEC, ShowReminderPacket::handle);
        registrar.playToClient(SyncHistoryPacket.TYPE, SyncHistoryPacket.STREAM_CODEC, SyncHistoryPacket::handle);
        registrar.playToClient(SyncUiLayoutPacket.TYPE, SyncUiLayoutPacket.STREAM_CODEC, SyncUiLayoutPacket::handle);
    }

    public static void requestRules() { PacketDistributor.sendToServer(new RequestRulesPacket()); }
    public static void requestHistory() { PacketDistributor.sendToServer(new RequestHistoryPacket()); }
    public static void requestUiLayout() { PacketDistributor.sendToServer(new RequestUiLayoutPacket()); }
    public static void saveUiLayout(int x, int y) { PacketDistributor.sendToServer(new SaveUiLayoutPacket(x, y)); }
    public static void saveRules(SaveRulesPacket packet) { PacketDistributor.sendToServer(packet); }
}
