package im.zego.liveaudioroom.listener;

import im.zego.liveaudioroom.model.ZegoRoomInfo;
import im.zego.zim.enums.ZIMConnectionEvent;
import im.zego.zim.enums.ZIMConnectionState;

/**
 * Created by rocket_wang on 2021/12/14.
 */
public interface ZegoRoomServiceListener {
    // room info update
    void onReceiveRoomInfoUpdate(ZegoRoomInfo roomInfo);

    void onConnectionStateChanged(ZIMConnectionState state, ZIMConnectionEvent event);
}