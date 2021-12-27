package im.zego.liveaudioroom.service;

import android.text.TextUtils;
import android.util.Log;
import com.google.gson.Gson;
import im.zego.liveaudioroom.ZegoRoomManager;
import im.zego.liveaudioroom.ZegoZIMManager;
import im.zego.liveaudioroom.callback.ZegoOnlineRoomUsersCallback;
import im.zego.liveaudioroom.callback.ZegoRoomCallback;
import im.zego.liveaudioroom.constants.ZegoRoomConstants;
import im.zego.liveaudioroom.helper.ZegoRoomAttributesHelper;
import im.zego.liveaudioroom.listener.ZegoRoomServiceListener;
import im.zego.liveaudioroom.model.ZegoRoomInfo;
import im.zego.liveaudioroom.model.ZegoRoomUserRole;
import im.zego.liveaudioroom.model.ZegoUserInfo;
import im.zego.zegoexpress.ZegoExpressEngine;
import im.zego.zegoexpress.constants.ZegoUpdateType;
import im.zego.zegoexpress.entity.ZegoRoomConfig;
import im.zego.zegoexpress.entity.ZegoStream;
import im.zego.zegoexpress.entity.ZegoUser;
import im.zego.zim.ZIM;
import im.zego.zim.entity.ZIMRoomAdvancedConfig;
import im.zego.zim.entity.ZIMRoomAttributesUpdateInfo;
import im.zego.zim.entity.ZIMRoomInfo;
import im.zego.zim.enums.ZIMConnectionEvent;
import im.zego.zim.enums.ZIMConnectionState;
import im.zego.zim.enums.ZIMErrorCode;
import im.zego.zim.enums.ZIMRoomAttributesUpdateAction;
import im.zego.zim.enums.ZIMRoomEvent;
import im.zego.zim.enums.ZIMRoomState;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;

/**
 * Created by rocket_wang on 2021/12/14.
 */
public class ZegoRoomService {

    private ZegoRoomServiceListener listener;
    // room info object
    public ZegoRoomInfo roomInfo = new ZegoRoomInfo();

    private static final String TAG = "ZegoRoomService";

    // create a room
    public void createRoom(String roomID, String roomName, final String token,
        final ZegoRoomCallback callback) {
        ZegoUserInfo localUserInfo = ZegoRoomManager.getInstance().userService.localUserInfo;
        localUserInfo.setRole(ZegoRoomUserRole.Host);

        ZegoRoomInfo createRoomInfo = new ZegoRoomInfo();
        createRoomInfo.setRoomID(roomID);
        createRoomInfo.setRoomName(roomName);
        createRoomInfo.setHostID(localUserInfo.getUserID());
        createRoomInfo.setSeatNum(8);
        createRoomInfo.setTextMessageDisabled(false);
        createRoomInfo.setClosed(false);

        ZIMRoomInfo zimRoomInfo = new ZIMRoomInfo();
        zimRoomInfo.roomID = roomID;
        zimRoomInfo.roomName = roomName;

        HashMap<String, String> roomAttributes = new HashMap<>();
        roomAttributes.put(ZegoRoomConstants.KEY_ROOM_INFO, new Gson().toJson(createRoomInfo));
        ZIMRoomAdvancedConfig config = new ZIMRoomAdvancedConfig();
        config.roomAttributes = roomAttributes;

        ZegoZIMManager.getInstance().zim.createRoom(zimRoomInfo, config, (roomInfo, errorInfo) -> {
            if (errorInfo.code == ZIMErrorCode.SUCCESS) {
                loginRTCRoom(roomID, token, localUserInfo);
                initRoomSeat();
            }
            if (callback != null) {
                callback.roomCallback(errorInfo.code.value());
            }
        });
    }

    // join a room
    public void joinRoom(String roomID, final String token, final ZegoRoomCallback callback) {
        ZegoUserInfo localUserInfo = ZegoRoomManager.getInstance().userService.localUserInfo;
        localUserInfo.setRole(ZegoRoomUserRole.Listener);

        ZegoZIMManager.getInstance().zim.joinRoom(roomID, (roomInfo, errorInfo) -> {
            if (errorInfo.code == ZIMErrorCode.SUCCESS) {
                loginRTCRoom(roomID, token, localUserInfo);
                this.roomInfo.setRoomID(roomInfo.baseInfo.roomID);
                this.roomInfo.setRoomName(roomInfo.baseInfo.roomName);
            }
            if (callback != null) {
                callback.roomCallback(errorInfo.code.value());
            }
        });
    }

    private void initRoomSeat() {
        ZegoSpeakerSeatService speakerSeatService = ZegoRoomManager.getInstance().speakerSeatService;
        if (speakerSeatService != null) {
            speakerSeatService.initRoomSeat();
        }
    }

    private void loginRTCRoom(String roomID, String token, ZegoUserInfo localUserInfo) {
        ZegoUser user = new ZegoUser(localUserInfo.getUserID(), localUserInfo.getUserName());
        ZegoRoomConfig roomConfig = new ZegoRoomConfig();
        roomConfig.token = token;
        ZegoExpressEngine.getEngine().loginRoom(roomID, user, roomConfig);
        ZegoExpressEngine.getEngine().startSoundLevelMonitor(500);
    }

    // leave the room
    public void leaveRoom(final ZegoRoomCallback callback) {
        ZegoSpeakerSeatService seatService = ZegoRoomManager.getInstance().speakerSeatService;
        if (seatService != null) {
            seatService.leaveSeat(errorCode -> {

            });
            seatService.reset();
        }
        ZegoMessageService messageService = ZegoRoomManager.getInstance().messageService;
        if (messageService != null) {
            messageService.reset();
        }
        ZegoUserService userService = ZegoRoomManager.getInstance().userService;
        if (userService != null) {
            userService.reset();
        }
        ZegoGiftService giftService = ZegoRoomManager.getInstance().giftService;
        if (giftService != null) {
            giftService.reset();
        }
        reset();

        ZegoExpressEngine.getEngine().stopSoundLevelMonitor();
        ZegoExpressEngine.getEngine().stopPublishingStream();

        ZegoExpressEngine.getEngine().logoutRoom(roomInfo.getRoomID());

        ZegoZIMManager.getInstance().zim.leaveRoom(roomInfo.getRoomID(), errorInfo -> {
            Log.d(TAG, "leaveRoom() called with: errorInfo = [" + errorInfo.code + "]" + errorInfo.message);
            if (callback != null) {
                callback.roomCallback(errorInfo.code.value());
            }
        });
    }

    void reset() {
        roomInfo.setRoomName("");
        roomInfo.setSeatNum(0);
        roomInfo.setHostID("");
        listener = null;
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
                Log.d(TAG, "disableTextMessage() called with: isMuted = [" + isMuted);
                if (callback != null) {
                    callback.roomCallback(errorInfo.code.value());
                }
            });
    }

    public void setListener(ZegoRoomServiceListener listener) {
        this.listener = listener;
    }

    /**
     * @param zim
     * @param info
     * @param roomID
     */
    public void onRoomAttributesUpdated(ZIM zim, ZIMRoomAttributesUpdateInfo info, String roomID) {
        Log.d(TAG,
            "onRoomAttributesUpdated() called with: info.action = [" + info.action + "], info.roomAttributes = ["
                + info.roomAttributes + "], roomID = [" + roomID + "]");
        if (info.action == ZIMRoomAttributesUpdateAction.SET) {
            Set<String> keys = info.roomAttributes.keySet();
            for (String key : keys) {
                if (key.equals(ZegoRoomConstants.KEY_ROOM_INFO)) {
                    ZegoRoomInfo roomInfo = new Gson().fromJson(info.roomAttributes.get(key), ZegoRoomInfo.class);
                    boolean firstInit = (this.roomInfo.getSeatNum() == 0);
                    Log.d(TAG, "onRoomAttributesUpdated: firstInit " + firstInit);
                    this.roomInfo = roomInfo;
                    if (firstInit) {
                        initRoomSeat();
                    }
                    if (listener != null) {
                        listener.onReceiveRoomInfoUpdate(roomInfo);
                    }
                }
            }
        } else {
            if (listener != null) {
                listener.onReceiveRoomInfoUpdate(null);
            }
        }
    }

    public void onRoomStateChanged(ZIM zim, ZIMRoomState state, ZIMRoomEvent event, JSONObject extendedData,
        String roomID) {
        Log.d(TAG, "onRoomStateChanged() called with: zim = [" + zim + "], state = [" + state + "], event = [" + event
            + "], extendedData = [" + extendedData + "], roomID = [" + roomID + "]");
        if (state == ZIMRoomState.CONNECTED) {
            boolean newInRoom = (this.roomInfo.getSeatNum() == 0);
            if (!newInRoom && !TextUtils.isEmpty(roomID)) {
                ZegoZIMManager.getInstance().zim.queryRoomAllAttributes(roomID, (roomAttributes, errorInfo) -> {
                    boolean hostLeft = errorInfo.getCode() == ZIMErrorCode.SUCCESS
                        && !roomAttributes.keySet().contains(ZegoRoomConstants.KEY_ROOM_INFO);
                    boolean roomNotExisted = errorInfo.getCode() == ZIMErrorCode.ROOM_NOT_EXIST;
                    if (hostLeft || roomNotExisted) {
                        if (listener != null) {
                            listener.onReceiveRoomInfoUpdate(null);
                        }
                    }
                });
            }
        } else if (state == ZIMRoomState.DISCONNECTED) {
            if (listener != null) {
                listener.onReceiveRoomInfoUpdate(null);
            }
        }
    }

    public void onConnectionStateChanged(ZIM zim, ZIMConnectionState state, ZIMConnectionEvent event,
        JSONObject extendedData) {
        Log.d(TAG,
            "onConnectionStateChanged() called with: zim = [" + zim + "], state = [" + state + "], event = ["
                + event + "], extendedData = [" + extendedData + "]");
        if (listener != null) {
            listener.onConnectionStateChanged(state, event);
        }

    }

    public void onRoomStreamUpdate(String roomID, ZegoUpdateType updateType, List<ZegoStream> streamList) {
        for (ZegoStream zegoStream : streamList) {
            if (updateType == ZegoUpdateType.ADD) {
                ZegoExpressEngine.getEngine().startPlayingStream(zegoStream.streamID, null);
            } else {
                ZegoExpressEngine.getEngine().stopPlayingStream(zegoStream.streamID);
            }
        }
    }
}