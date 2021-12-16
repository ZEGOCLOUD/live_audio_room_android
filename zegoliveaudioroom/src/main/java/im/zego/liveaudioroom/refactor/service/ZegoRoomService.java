package im.zego.liveaudioroom.refactor.service;

import com.google.gson.Gson;
import im.zego.liveaudioroom.refactor.ZegoRoomManager;
import im.zego.liveaudioroom.refactor.ZegoZIMManager;
import im.zego.liveaudioroom.refactor.callback.ZegoOnlineRoomUsersCallback;
import im.zego.liveaudioroom.refactor.callback.ZegoRoomCallback;
import im.zego.liveaudioroom.refactor.constants.ZegoRoomConstants;
import im.zego.liveaudioroom.refactor.helper.ZegoRoomAttributesHelper;
import im.zego.liveaudioroom.refactor.listener.ZegoRoomServiceListener;
import im.zego.liveaudioroom.refactor.model.ZegoRoomInfo;
import im.zego.liveaudioroom.refactor.model.ZegoRoomUserRole;
import im.zego.zim.ZIM;
import im.zego.zim.callback.ZIMEventHandler;
import im.zego.zim.entity.ZIMRoomAdvancedConfig;
import im.zego.zim.entity.ZIMRoomAttributesUpdateInfo;
import im.zego.zim.entity.ZIMRoomInfo;
import im.zego.zim.enums.ZIMConnectionEvent;
import im.zego.zim.enums.ZIMConnectionState;
import im.zego.zim.enums.ZIMErrorCode;
import im.zego.zim.enums.ZIMRoomAttributesUpdateAction;
import java.util.HashMap;
import java.util.Set;
import org.json.JSONObject;

/**
 * Created by rocket_wang on 2021/12/14.
 */
public class ZegoRoomService extends ZIMEventHandler {

    private ZegoRoomServiceListener listener;
    // room info object
    public ZegoRoomInfo roomInfo;

    // create a room
    public void createRoom(String roomID, String roomName, final String token,
        final ZegoRoomCallback callback) {
        ZegoRoomManager.getInstance().userService.localUserInfo.setRole(ZegoRoomUserRole.Host);

        roomInfo = new ZegoRoomInfo();
        roomInfo.setRoomID(roomID);
        roomInfo.setRoomName(roomName);
        roomInfo.setHostID(ZegoRoomManager.getInstance().userService.localUserInfo.getUserID());
        roomInfo.setSeatNum(8);
        roomInfo.setTextMessageDisabled(false);
        roomInfo.setClosed(false);

        ZIMRoomInfo zimRoomInfo = new ZIMRoomInfo();
        zimRoomInfo.roomID = roomID;
        zimRoomInfo.roomName = roomName;

        HashMap<String, String> roomAttributes = new HashMap<>();
        roomAttributes.put(ZegoRoomConstants.KEY_ROOM_INFO, new Gson().toJson(roomInfo));
        ZIMRoomAdvancedConfig config = new ZIMRoomAdvancedConfig();
        config.roomAttributes = roomAttributes;

        ZegoZIMManager.getInstance().zim.createRoom(zimRoomInfo, config, (roomInfo, errorInfo) -> {
            if (errorInfo.code == ZIMErrorCode.SUCCESS) {
                //                speakerSeatManager.setupRTCModule(token, new SetupRTCModuleCallback() {
                //                    @Override
                //                    public void onConnectionState(ZegoLiveAudioRoomErrorCode error) {
                //
                //                    }
                //                });
            }
            if (callback != null) {
                callback.roomCallback(errorInfo.code.value());
            }
        });
    }

    // join a room
    public void joinRoom(String roomID, final String token, final ZegoRoomCallback callback) {
        ZegoRoomManager.getInstance().userService.localUserInfo.setRole(ZegoRoomUserRole.Listener);

        ZegoZIMManager.getInstance().zim.joinRoom(roomID, (roomInfo, errorInfo) -> {
            if (errorInfo.code == ZIMErrorCode.SUCCESS) {
                //                speakerSeatManager.setupRTCModule(token, new SetupRTCModuleCallback() {
                //                    @Override
                //                    public void onConnectionState(ZegoLiveAudioRoomErrorCode error) {
                //
                //                    }
                //                });
                ZegoSpeakerSeatService speakerSeatService = ZegoRoomManager
                    .getInstance().speakerSeatService;
                if (speakerSeatService != null) {
                    speakerSeatService.initRoomSeat();
                }
            }
            if (callback != null) {
                callback.roomCallback(errorInfo.code.value());
            }
        });
    }

    // leave the room
    public void leaveRoom(final ZegoRoomCallback callback) {
        ZegoSpeakerSeatService seatService = ZegoRoomManager.getInstance().speakerSeatService;
        if (seatService != null) {
            seatService.reset();
        }
        ZegoMessageService messageService = ZegoRoomManager.getInstance().messageService;
        if (messageService != null) {
            messageService.reset();
        }
        ZegoZIMManager.getInstance().zim.leaveRoom(roomInfo.getRoomID(), errorInfo -> {
            if (callback != null) {
                callback.roomCallback(errorInfo.code.value());
            }
        });
    }

    // query the number of chat rooms available online
    public void queryOnlineRoomUsers(final ZegoOnlineRoomUsersCallback callback) {
        ZegoZIMManager.getInstance().zim
            .queryRoomOnlineMemberCount(roomInfo.getRoomID(), (count, errorInfo) -> {
                if (callback != null) {
                    callback.userCountCallback(errorInfo.code.value(), count);
                }
            });
    }

    // disable text chat for all
    public void disableTextMessage(boolean isMuted, ZegoRoomCallback callback) {
        ZegoZIMManager.getInstance().zim.setRoomAttributes(
            ZegoRoomAttributesHelper.getRoomConfigByTextMessage(isMuted, roomInfo),
            roomInfo.getRoomID(),
            ZegoRoomAttributesHelper.getAttributesSetConfig(), errorInfo -> {
                if (callback != null) {
                    callback.roomCallback(errorInfo.code.value());
                }
            });
    }

    public void setListener(ZegoRoomServiceListener listener) {
        this.listener = listener;
    }

    @Override
    public void onRoomAttributesUpdated(ZIM zim, ZIMRoomAttributesUpdateInfo info, String roomID) {
        super.onRoomAttributesUpdated(zim, info, roomID);
        Set<String> keys = info.roomAttributes.keySet();
        for (String key : keys) {
            if (key.equals(ZegoRoomConstants.KEY_ROOM_INFO)) {
                if (info.action == ZIMRoomAttributesUpdateAction.SET) {
                    ZegoRoomInfo roomInfo = new Gson()
                        .fromJson(info.roomAttributes.get(key), ZegoRoomInfo.class);
                    this.roomInfo = roomInfo;
                    if (listener != null) {
                        listener.receiveRoomInfoUpdate(roomInfo);
                    }
                    //                    if (roomEventHandler != null) {
                    //                        roomEventHandler.onMuteAllMessage(nowIsMuted);
                    //                    }
                } else {
                    if (listener != null) {
                        listener.receiveRoomInfoUpdate(null);
                    }
                }
            }
        }
    }

    @Override
    public void onConnectionStateChanged(ZIM zim, ZIMConnectionState state,
        ZIMConnectionEvent event, JSONObject extendedData) {
        super.onConnectionStateChanged(zim, state, event, extendedData);
        if (listener != null) {
            listener.connectionStateChanged(state, event);
        }
    }
}