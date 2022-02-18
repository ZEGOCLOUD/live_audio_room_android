package im.zego.liveaudioroomdemo.feature.room;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AlertDialog.Builder;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.blankj.utilcode.util.ActivityUtils;
import com.blankj.utilcode.util.SizeUtils;
import com.blankj.utilcode.util.StringUtils;
import com.blankj.utilcode.util.ToastUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import im.zego.liveaudioroom.ZegoRoomManager;
import im.zego.liveaudioroom.constants.ZegoRoomErrorCode;
import im.zego.liveaudioroom.listener.ZegoRoomServiceListener;
import im.zego.liveaudioroom.listener.ZegoUserServiceListener;
import im.zego.liveaudioroom.model.ZegoRoomInfo;
import im.zego.liveaudioroom.model.ZegoRoomUserRole;
import im.zego.liveaudioroom.model.ZegoSpeakerSeatModel;
import im.zego.liveaudioroom.model.ZegoSpeakerSeatStatus;
import im.zego.liveaudioroom.model.ZegoTextMessage;
import im.zego.liveaudioroom.model.ZegoUserInfo;
import im.zego.liveaudioroom.service.ZegoGiftService;
import im.zego.liveaudioroom.service.ZegoMessageService;
import im.zego.liveaudioroom.service.ZegoRoomService;
import im.zego.liveaudioroom.service.ZegoSpeakerSeatService;
import im.zego.liveaudioroom.service.ZegoUserService;
import im.zego.liveaudioroomdemo.R;
import im.zego.liveaudioroomdemo.feature.BaseActivity;
import im.zego.liveaudioroomdemo.feature.login.UserLoginActivity;
import im.zego.liveaudioroomdemo.feature.room.adapter.MessageListAdapter;
import im.zego.liveaudioroomdemo.feature.room.adapter.SeatListAdapter;
import im.zego.liveaudioroomdemo.feature.room.dialog.IMInputDialog;
import im.zego.liveaudioroomdemo.feature.room.dialog.LoadingDialog;
import im.zego.liveaudioroomdemo.feature.room.dialog.MemberListDialog;
import im.zego.liveaudioroomdemo.feature.room.dialog.SendGiftDialog;
import im.zego.liveaudioroomdemo.feature.room.dialog.SettingsDialog;
import im.zego.liveaudioroomdemo.feature.room.enums.RoomGift;
import im.zego.liveaudioroomdemo.helper.DialogHelper;
import im.zego.liveaudioroomdemo.helper.PermissionHelper;
import im.zego.liveaudioroomdemo.helper.UserInfoHelper;
import im.zego.zim.enums.ZIMConnectionEvent;
import im.zego.zim.enums.ZIMConnectionState;

public class LiveAudioRoomActivity extends BaseActivity {

    private LoadingDialog loadingDialog;
    private AlertDialog inviteDialog;

    public static void startActivity(Context context) {
        Intent intent = new Intent(context, LiveAudioRoomActivity.class);
        context.startActivity(intent);
    }

    public static void startActivityForResult(Activity activity, int requestCode) {
        Intent intent = new Intent(activity, LiveAudioRoomActivity.class);
        activity.startActivityForResult(intent, requestCode);
    }

    private static final String TAG = "LiveAudioRoomActivity";

    private ConstraintLayout constraintLayout;
    private ImageView ivLogout;
    private TextView tvGiftToast;
    private ImageView ivIm;
    private ImageView ivMic;
    private ImageView ivMember;
    private ImageView ivSettings;
    private ImageView ivMore;
    private TextView tvRoomName;
    private TextView tvRoomID;
    private RecyclerView rvSeatList;
    private RecyclerView rvMessageList;
    private ImageView ivGift;
    private SeatListAdapter seatListAdapter;
    private MessageListAdapter messageListAdapter;

    private SettingsDialog settingsDialog;
    private SendGiftDialog giftDialog;
    private IMInputDialog imInputDialog;
    private MemberListDialog memberListDialog;

    private List<ZegoTextMessage> textMessageList = new ArrayList<>();
    private boolean isImDisabled = false;
    private Runnable hideGiftTips = () -> {
        tvGiftToast.setText("");
        tvGiftToast.setVisibility(View.INVISIBLE);
    };

//    private boolean isFirstIn = true;

    @Override
    protected int getStatusBarColor() {
        return R.color.milk_white;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);

        initUI();
        setListener();
        updateUI();
        initSDCallback();
    }

    private void initUI() {
        constraintLayout = findViewById(R.id.constraint_root_layout);
        ivLogout = findViewById(R.id.iv_logout);
        tvRoomName = findViewById(R.id.tv_room_name);
        tvRoomID = findViewById(R.id.tv_room_id);

        rvSeatList = findViewById(R.id.rv_seat_list);
        rvMessageList = findViewById(R.id.rv_message_list);
        tvGiftToast = findViewById(R.id.tv_gift_toast);

        ivIm = findViewById(R.id.iv_im);
        ivIm.setActivated(true);
        ivMic = findViewById(R.id.iv_mic);
        ivMember = findViewById(R.id.iv_member);
        ivGift = findViewById(R.id.iv_gift);
        ivSettings = findViewById(R.id.iv_settings);
        ivMore = findViewById(R.id.iv_more);
    }

    private void setListener() {
        ivLogout.setOnClickListener(v -> this.onBackPressed());
        ivIm.setOnClickListener(v -> {
            if (isImDisabled && !UserInfoHelper.isSelfOwner()) {
                ToastUtils.showShort(R.string.toast_disable_text_chat_tips);
            } else {
                imInputDialog = new IMInputDialog(this);
                imInputDialog.setOnSendListener(imText -> {
                    ZegoMessageService service = ZegoRoomManager.getInstance().messageService;
                    boolean textMessageDisabled = ZegoRoomManager.getInstance().roomService.roomInfo
                        .isTextMessageDisabled();
                    String userID = ZegoRoomManager.getInstance().userService.localUserInfo.getUserID();
                    String hostID = ZegoRoomManager.getInstance().roomService.roomInfo.getHostID();
                    if (userID.equals(hostID) || !textMessageDisabled) {
                        service.sendTextMessage(imText, errorCode -> {
                            if (errorCode == ZegoRoomErrorCode.SUCCESS) {
                                final ZegoTextMessage text = new ZegoTextMessage();
                                text.message = imText;
                                text.userID = getMyUserID();
                                textMessageList.add(text);
                                refreshMessageList();
                            } else {
                                ToastUtils.showShort(R.string.toast_send_message_error, errorCode);
                            }
                        });
                    } else {
                        ToastUtils.showShort(R.string.toast_disable_text_chat_tips);
                    }
                });
                imInputDialog.show();
            }
        });
        ivMic.setOnClickListener(v -> {
            PermissionHelper.requestRecordAudio(this, isAllGranted -> {
                if (isAllGranted) {
                    boolean bool = ivMic.isSelected();
                    ZegoRoomManager.getInstance().speakerSeatService.muteMic(bool, error -> {
                        if (error == ZegoRoomErrorCode.SUCCESS) {
                            ivMic.setSelected(!bool);
                        }
                    });
                }
            });
        });
        ivMember.setOnClickListener(v -> {
            ZegoUserService userService = ZegoRoomManager.getInstance().userService;
            List<ZegoUserInfo> userList = userService.getUserList();
            if (memberListDialog == null) {
                memberListDialog = new MemberListDialog(this, userList);
            }
            if (!memberListDialog.isShowing()) {
                memberListDialog.show();
            }
        });
        ivGift.setOnClickListener(v -> {
            giftDialog = new SendGiftDialog(this);
            giftDialog.setSendGiftListener((sendTo, giftID) -> {
                showGiftTips(sendTo, getMyUserID(), giftID);
            });
            giftDialog.show();
        });
        ivSettings.setOnClickListener(v -> {
            if (settingsDialog == null) {
                settingsDialog = new SettingsDialog(this);
            }
            settingsDialog.show();
        });
        ivMore.setOnClickListener(v -> speakerLeaveSeat());
    }

    private void updateMemberListDialog() {
        if (memberListDialog != null) {
            ZegoUserService userService = ZegoRoomManager.getInstance().userService;
            memberListDialog.updateUserList(userService.getUserList());
        }
    }

    private void requestRecordAudio() {
        PermissionHelper.requestRecordAudio(this, isAllGranted -> {
            ivMic.setSelected(isAllGranted);
            /**
             * If you do not grant microphone permissions, you need to turn off your seat microphone.
             */
            if (!isAllGranted) {
                ZegoRoomManager.getInstance().speakerSeatService.muteMic(true, error -> {
                });
            }
        });
    }

    private void updateUI() {
        seatListAdapter = new SeatListAdapter();
        rvSeatList.setAdapter(seatListAdapter);
        rvSeatList.setLayoutManager(new GridLayoutManager(this, 4));
        seatListAdapter.setOnSeatClickListener(this::onSpeakerSeatClicked);
        ZegoSpeakerSeatService seatService = ZegoRoomManager.getInstance().speakerSeatService;
        seatListAdapter.setSeatList(seatService.getSpeakerSeatList());

        messageListAdapter = new MessageListAdapter(textMessageList);
        rvMessageList.setAdapter(messageListAdapter);
        rvMessageList
            .setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));

        if (UserInfoHelper.isSelfOwner()) {
            /**
             * If it is Host, you need to let Host take the initiative to seat.
             */
            uiToOwner();
            ZegoRoomManager.getInstance().speakerSeatService.takeSeat(0, error -> {
                requestRecordAudio();
            });
        } else {
            uiToAudience();
        }

        ZegoRoomInfo roomInfo = ZegoRoomManager.getInstance().roomService.roomInfo;
        tvRoomName.setText(roomInfo.getRoomName());
        tvRoomID.setText(String.format("ID:%s", roomInfo.getRoomID()));

        if (textMessageList.isEmpty()) {
            ZegoUserInfo localUserInfo = ZegoRoomManager.getInstance().userService.localUserInfo;
            ZegoTextMessage textMessage = new ZegoTextMessage();
            textMessage.message = StringUtils
                .getString(R.string.room_page_joined_the_room, localUserInfo.getUserName());
            textMessageList.add(textMessage);
            refreshMessageList();
        }
    }

    private void onSpeakerSeatClicked(ZegoSpeakerSeatModel seatModel) {
        ZegoSpeakerSeatService seatService = ZegoRoomManager.getInstance().speakerSeatService;
        if (UserInfoHelper.isSelfOwner()) {
            /**
             * If the operator clicking on the seat position is Host, the business logic that should be processed needs to be judged according to the current state of the seat position:
             * 1. the seat position is not occupied, Host can lock the seat position
             * 2. the seat position is locked, Host can unlock the seat position
             * 3. the seat position is occupied, Host can remove the person from the seat position and lower the seat position
             */
            if (seatModel.status == ZegoSpeakerSeatStatus.Untaken) {
                DialogHelper.showToastDialog(LiveAudioRoomActivity.this,
                    StringUtils.getString(R.string.room_page_lock_seat), dialog -> {
                        if (seatModel.status == ZegoSpeakerSeatStatus.Untaken) {
                            seatService.convertClosedOpenSeat(true, seatModel.seatIndex, errorCode -> {
                                if (errorCode == ZegoRoomErrorCode.SUCCESS) {
                                    ZegoSpeakerSeatModel model = seatService.getSpeakerSeatList()
                                        .get(seatModel.seatIndex);
                                    seatListAdapter.updateUserInfo(model);
                                } else {
                                    ToastUtils.showShort(R.string.toast_lock_seat_fail, errorCode);
                                }
                            });
                        } else {
                            ToastUtils.showShort(R.string.toast_lock_seat_already_take_seat);
                        }
                    });
            } else if (seatModel.status == ZegoSpeakerSeatStatus.Closed) {
                DialogHelper.showToastDialog(LiveAudioRoomActivity.this,
                    StringUtils.getString(R.string.room_page_unlock_seat), dialog -> {
                        seatService.convertClosedOpenSeat(false, seatModel.seatIndex, errorCode -> {
                            if (errorCode == ZegoRoomErrorCode.SUCCESS) {
                                ZegoSpeakerSeatModel model = seatService.getSpeakerSeatList()
                                    .get(seatModel.seatIndex);
                                seatListAdapter.updateUserInfo(model);
                            }
                        });
                    });
            } else if (seatModel.status == ZegoSpeakerSeatStatus.Occupied) {
                if (Objects.equals(seatModel.userID, getMyUserID())) {
                    return;
                }
                if (seatModel.status == ZegoSpeakerSeatStatus.Occupied) {
                    String userName = ZegoRoomManager.getInstance().userService.getUserName(seatModel.userID);
                    DialogHelper.showToastDialog(LiveAudioRoomActivity.this,
                        StringUtils.getString(R.string.room_page_leave_speaker_seat), dialog -> {
                            DialogHelper.showAlertDialog(LiveAudioRoomActivity.this,
                                StringUtils.getString(R.string.room_page_leave_speaker_seat),
                                StringUtils.getString(R.string.dialog_warning_leave_seat_message, userName),
                                StringUtils.getString(R.string.dialog_confirm),
                                StringUtils.getString(R.string.dialog_cancel),
                                (alertDialog, which) -> {
                                    seatService.removeUserFromSeat(seatModel.seatIndex, errorCode -> {
                                        if (errorCode != ZegoRoomErrorCode.SUCCESS) {
                                            ToastUtils.showShort(R.string.toast_kickout_leave_seat_error,
                                                userName, errorCode);
                                        }
                                    });
                                },
                                (alertDialog, which) -> alertDialog.cancel()
                            );
                        });
                } else {
                    String userName = ZegoRoomManager.getInstance().userService.getUserName(seatModel.userID);
                    ToastUtils
                        .showShort(R.string.toast_kickout_leave_seat_error, userName, ZegoRoomErrorCode.NOT_IN_SEAT);
                }

            }
        } else {
            /**
             * If the operator clicking on the seat position is Listener, the business logic that should be processed needs to be judged according to the current state of the seat position:
             * 1. the seat position is not occupied, Listener can take the seat
             * 2. the seat position is locked, Listener can not take the seat
             * 3. the seat position is occupied, Listener can only leave self seat
             */
            if (seatModel.status == ZegoSpeakerSeatStatus.Untaken) {
                DialogHelper.showToastDialog(LiveAudioRoomActivity.this,
                    StringUtils.getString(R.string.room_page_take_seat), dialog -> {
                        if (seatModel.status == ZegoSpeakerSeatStatus.Untaken) {
                            boolean hasOnSeat = seatService.getSeatedUserList().contains(getMyUserID());
                            if (hasOnSeat) {
                                seatService.switchSeat(seatModel.seatIndex, errorCode -> {
                                    if (errorCode != ZegoRoomErrorCode.SUCCESS) {
                                        ToastUtils.showShort(StringUtils
                                            .getString(R.string.toast_take_speaker_seat_fail, errorCode));
                                    }
                                });
                            } else {
                                seatService.takeSeat(seatModel.seatIndex, errorCode -> {
                                    if (errorCode != ZegoRoomErrorCode.SUCCESS) {
                                        ToastUtils.showShort(R.string.toast_take_speaker_seat_fail,
                                            errorCode);
                                    } else {
                                        requestRecordAudio();
                                    }
                                });
                            }
                        } else {
                            ToastUtils.showShort(R.string.toast_take_speaker_seat_fail, ZegoRoomErrorCode.ERROR);
                        }
                    });
            } else if (seatModel.status == ZegoSpeakerSeatStatus.Closed) {
                ToastUtils.showShort(StringUtils.getString(R.string.the_wheat_position_has_been_locked));
            } else if (seatModel.status == ZegoSpeakerSeatStatus.Occupied) {
                if (getMyUserID().equals(seatModel.userID)) {
                    speakerLeaveSeat();
                } else {
                    ToastUtils.showShort(R.string.the_wheat_position_has_been_locked);
                }
            }
        }
    }

    /**
     * The business logic of leave seat: If it is a Host, you can leave any members on seat except yourself If it is a
     * Speaker, you can only leave yourself seat
     */
    private void speakerLeaveSeat() {
        DialogHelper.showToastDialog(LiveAudioRoomActivity.this,
            StringUtils.getString(R.string.room_page_leave_speaker_seat), dialog -> {
                DialogHelper.showAlertDialog(
                    LiveAudioRoomActivity.this,
                    StringUtils.getString(R.string.room_page_leave_speaker_seat),
                    StringUtils.getString(R.string.dialog_sure_to_leave_seat),
                    StringUtils.getString(R.string.dialog_confirm),
                    StringUtils.getString(R.string.dialog_cancel),
                    (alertDialog, which) -> {
                        ZegoSpeakerSeatService seatService = ZegoRoomManager.getInstance().speakerSeatService;
                        ZegoSpeakerSeatModel mySeatModel = null;
                        for (ZegoSpeakerSeatModel seatModel : seatService.getSpeakerSeatList()) {
                            if (Objects.equals(seatModel.userID, getMyUserID())) {
                                mySeatModel = seatModel;
                                break;
                            }
                        }
                        if (mySeatModel != null && mySeatModel.status == ZegoSpeakerSeatStatus.Occupied) {
                            seatService.leaveSeat(errorCode -> {
                                if (errorCode != ZegoRoomErrorCode.SUCCESS) {
                                    ToastUtils.showShort(R.string.toast_leave_seat_fail, errorCode);
                                }
                            });
                        } else {
                            ToastUtils.showShort(R.string.toast_leave_seat_fail, ZegoRoomErrorCode.NOT_IN_SEAT);
                        }
                    },
                    (alertDialog, which) -> alertDialog.cancel()
                );
            });
    }

    /**
     * Check if myself is a speaker
     */
    private boolean isSelfSpeaker() {
        ZegoSpeakerSeatService speakerSeatService = ZegoRoomManager.getInstance().speakerSeatService;
        List<ZegoSpeakerSeatModel> speakerSeatList = speakerSeatService.getSpeakerSeatList();
        ZegoUserInfo localUserInfo = ZegoRoomManager.getInstance().userService.localUserInfo;
        boolean isSpeaker = false;
        for (int i = 0; i < speakerSeatList.size(); i++) {
            ZegoSpeakerSeatModel speakerSeatModel = speakerSeatList.get(i);
            if (Objects.equals(localUserInfo.getUserID(), speakerSeatModel.userID)
                && speakerSeatModel.status == ZegoSpeakerSeatStatus.Occupied) {
                isSpeaker = true;
                break;
            }
        }
        return isSpeaker;
    }

    /**
     * When Host invites you to be a speaker, you will see this dialog: If you accept, you will take the seat and be a
     * speaker. If you decline, dialog will dismiss, and do nothing
     */
    private void showInviteDialog() {
        if (isSelfSpeaker()) {
            return;
        }
        if (inviteDialog == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(StringUtils.getString(R.string.dialog_invition_title));
            builder.setMessage(StringUtils.getString(R.string.dialog_invition_descrip));
            builder.setPositiveButton(StringUtils.getString(R.string.dialog_accept), (dialog, which) -> {
                if (isSelfSpeaker()) {
                    return;
                }
                ZegoSpeakerSeatService speakerSeatService = ZegoRoomManager.getInstance().speakerSeatService;
                List<ZegoSpeakerSeatModel> speakerSeatList = speakerSeatService.getSpeakerSeatList();
                int seatIndex = -1;
                for (int i = 0; i < speakerSeatList.size(); i++) {
                    ZegoSpeakerSeatModel model = speakerSeatList.get(i);
                    if (model.status == ZegoSpeakerSeatStatus.Untaken) {
                        seatIndex = model.seatIndex;
                        break;
                    }
                }
                if (seatIndex != -1) {
                    speakerSeatService.takeSeat(seatIndex, errorCode -> {
                        if (errorCode == ZegoRoomErrorCode.SUCCESS) {
                            requestRecordAudio();
                        } else {
                            ToastUtils.showShort(R.string.toast_take_speaker_seat_fail, errorCode);
                        }
                    });
                }
                dialog.dismiss();
            });
            builder.setNegativeButton(StringUtils.getString(R.string.dialog_refuse), (dialog, which) -> {
                dialog.dismiss();
            });
            inviteDialog = builder.create();
        }
        if (!inviteDialog.isShowing()) {
            inviteDialog.show();
        }
    }

    private void initSDCallback() {
        /**
         * Add GiftService Listener, listener to gifts sent
         */
        ZegoGiftService giftService = ZegoRoomManager.getInstance().giftService;
        giftService.setListener((giftID, fromUserID, toUserList) -> {
            showGiftTips(toUserList, fromUserID, giftID);
        });

        /**
         * Add MessageService Listener, listener to message sent
         */
        ZegoMessageService messageService = ZegoRoomManager.getInstance().messageService;
        messageService.setListener((textMessage) -> {
            textMessageList.add(textMessage);
            refreshMessageList();
        });

        /**
         * Add UserService Listener, listener to Events like room user join/leave, receiveTakeSeatInvitation, connectionStateChanged
         */
        ZegoUserService userService = ZegoRoomManager.getInstance().userService;
        userService.setListener(new ZegoUserServiceListener() {

            @Override
            public void onRoomUserJoin(List<ZegoUserInfo> userList) {
                boolean containsSelf = false;
                ZegoUserInfo localUserInfo = userService.localUserInfo;
                for (ZegoUserInfo userInfo : userList) {
                    if (Objects.equals(userInfo.getUserID(), localUserInfo.getUserID())) {
                        containsSelf = true;
                        break;
                    }
                }

                if (containsSelf) {
                    ZegoTextMessage textMessage = new ZegoTextMessage();
                    textMessage.message = StringUtils
                        .getString(R.string.room_page_joined_the_room, localUserInfo.getUserName());
                    textMessageList.add(textMessage);
                    refreshMessageList();
                } else {
                    for (ZegoUserInfo user : userList) {
                        ZegoTextMessage textMessage = new ZegoTextMessage();
                        textMessage.message = StringUtils
                            .getString(R.string.room_page_joined_the_room, user.getUserName());
                        textMessageList.add(textMessage);
                        refreshMessageList();
                    }
                }
                seatListAdapter.notifyDataSetChanged();
                updateMemberListDialog();
            }

            @Override
            public void onRoomUserLeave(List<ZegoUserInfo> userList) {
                for (ZegoUserInfo user : userList) {
                    ZegoTextMessage textMessage = new ZegoTextMessage();
                    textMessage.message = StringUtils
                        .getString(R.string.room_page_has_left_the_room, user.getUserName());
                    textMessageList.add(textMessage);
                    refreshMessageList();

                }
                seatListAdapter.notifyDataSetChanged();
                updateMemberListDialog();
            }

            @Override
            public void onReceiveTakeSeatInvitation() {
                showInviteDialog();
            }

            @Override
            public void onConnectionStateChanged(ZIMConnectionState state, ZIMConnectionEvent event) {
                Log.d(TAG, "onConnectionStateChanged() called with: state = [" + state + "], event = [" + event + "]");
                if (state == ZIMConnectionState.DISCONNECTED) {
                    dismissDialog(loadingDialog);
                    if (event == ZIMConnectionEvent.LOGIN_TIMEOUT) {
                        showDisconnectDialog();
                    } else {
                        if (event == ZIMConnectionEvent.SUCCESS) {
                            // disconnect because of room end
                            setResult(RESULT_OK);
                            finish();
                        } else if (event == ZIMConnectionEvent.KICKED_OUT) {
                            //disconnect because of multiple login,been kicked out
                            ToastUtils.showShort(R.string.toast_kickout_error);
                            ActivityUtils.finishToActivity(UserLoginActivity.class, false);
                        } else {
                            ToastUtils.showShort(StringUtils.getString(R.string.toast_disconnect_tips));
                            ActivityUtils.finishToActivity(UserLoginActivity.class, false);
                        }

                    }
                } else if (state == ZIMConnectionState.RECONNECTING) {
                    showLoadingDialog();
                } else if (state == ZIMConnectionState.CONNECTED) {
                    dismissDialog(loadingDialog);
                }
            }
        });

        /**
         * Add SpeakerSeatService Listener, listener to the speaker seat update events
         */
        ZegoSpeakerSeatService seatService = ZegoRoomManager.getInstance().speakerSeatService;
        seatService.setListener(model -> {
            ZegoUserInfo userInfo = userService.getUserInfo(getMyUserID());
            if (userInfo != null) {
                if (userInfo.getRole() == ZegoRoomUserRole.Speaker) {
                    uiToSpeaker();
                    for (ZegoSpeakerSeatModel seatModel : seatService.getSpeakerSeatList()) {
                        if (Objects.equals(seatModel.userID, getMyUserID())) {
                            ivMic.setSelected(seatModel.mic);
                        }
                    }
                } else if (userInfo.getRole() == ZegoRoomUserRole.Host) {
                    uiToOwner();
                } else {
                    uiToAudience();
                }
            }
            seatListAdapter.updateUserInfo(model);
            updateMemberListDialog();
            if (giftDialog != null && giftDialog.isShowing()) {
                giftDialog.updateList();
            }
        });

        /**
         * Add RoomService Listener, listener to the room info update events
         */
        ZegoRoomService roomService = ZegoRoomManager.getInstance().roomService;
        roomService.setListener(new ZegoRoomServiceListener() {
            @Override
            public void onReceiveRoomInfoUpdate(ZegoRoomInfo roomInfo) {
                Log.d(TAG, "onReceiveRoomInfoUpdate() called with: roomInfo = [" + roomInfo + "]");
                if (roomInfo == null) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    onUserMessageDisabled(roomInfo.isTextMessageDisabled());
//                    if (isFirstIn) {
//                        isFirstIn = false;
//                        return;
//                    }
                    if (!UserInfoHelper.isSelfOwner()) {
                        if (roomInfo.isTextMessageDisabled()) {
                            ToastUtils.showShort(R.string.toast_disable_text_chat_tips);
                        } else {
                            ToastUtils.showShort(R.string.toast_allow_text_chat_tips);
                        }
                    }
                }
            }
        });
        onUserMessageDisabled(roomService.roomInfo.isTextMessageDisabled());
    }

    private void showDisconnectDialog() {
        AlertDialog.Builder builder2 = new Builder(LiveAudioRoomActivity.this);
        builder2.setTitle(R.string.network_connect_failed_title);
        builder2.setMessage(R.string.network_connect_failed);
        builder2.setCancelable(false);
        builder2.setPositiveButton(R.string.dialog_confirm, (dialog1, which1) -> {
            ActivityUtils.finishToActivity(UserLoginActivity.class, false);
        });
        if (!LiveAudioRoomActivity.this.isFinishing()) {
            AlertDialog alertDialog = builder2.create();
            alertDialog.setCanceledOnTouchOutside(false);
            alertDialog.show();
        }
    }

    private void onUserMessageDisabled(boolean disable) {
        isImDisabled = disable;
        if (imInputDialog != null) {
            imInputDialog.updateSendButtonState(!disable);
        }
        if (!UserInfoHelper.isSelfOwner()) {
            ivIm.setActivated(!disable);
        }
    }

    private void uiToOwner() {
        ivIm.setVisibility(View.VISIBLE);
        ivMic.setVisibility(View.VISIBLE);
        ivMember.setVisibility(View.VISIBLE);
        ivGift.setVisibility(View.VISIBLE);
        ivSettings.setVisibility(View.VISIBLE);
        ivMore.setVisibility(View.GONE);
    }

    private void uiToSpeaker() {
        ivIm.setVisibility(View.VISIBLE);
        ivMic.setVisibility(View.VISIBLE);
        ivMember.setVisibility(View.GONE);
        ivGift.setVisibility(View.VISIBLE);
        ivSettings.setVisibility(View.GONE);
        ivMore.setVisibility(View.VISIBLE);
    }

    private void uiToAudience() {
        ivIm.setVisibility(View.VISIBLE);
        ivMic.setVisibility(View.GONE);
        ivMember.setVisibility(View.GONE);
        ivGift.setVisibility(View.VISIBLE);
        ivSettings.setVisibility(View.GONE);
        ivMore.setVisibility(View.GONE);
    }

    private void refreshMessageList() {
        messageListAdapter.notifyItemInserted(textMessageList.size());
        rvMessageList.scrollToPosition(messageListAdapter.getItemCount() - 1);

        int measuredHeight = rvMessageList.getMeasuredHeight();
        int maxHeight = constraintLayout.getMeasuredHeight() - (rvSeatList.getBottom() + SizeUtils.dp2px(55F + 30F));
        if (maxHeight > 0) {
            ConstraintSet constraintSet = new ConstraintSet();
            constraintSet.clone(constraintLayout);
            constraintSet.constrainMaxHeight(R.id.rv_message_list, maxHeight);
            constraintSet.applyTo(constraintLayout);
        }
        if (maxHeight > 0 && measuredHeight >= maxHeight) {
            ConstraintSet constraintSet = new ConstraintSet();
            constraintSet.clone(constraintLayout);
            constraintSet.clear(R.id.tv_gift_toast, ConstraintSet.BOTTOM);
            constraintSet.connect(R.id.tv_gift_toast, ConstraintSet.TOP, R.id.rv_message_list, ConstraintSet.TOP);
            constraintSet.applyTo(constraintLayout);
        }
    }

    /**
     * When user send gifts, you will see this gift showing on UI
     */
    private void showGiftTips(List<String> toUserIDList, String fromUserID, String giftID) {
        String giftName = "";
        for (RoomGift value : RoomGift.values()) {
            if (value.getId().equals(giftID)) {
                giftName = getString(value.getName());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < toUserIDList.size(); i++) {
            String userID = toUserIDList.get(i);
            String name = ZegoRoomManager.getInstance().userService.getUserName(userID);
            sb.append(name);
            if (i != toUserIDList.size() - 1) {
                sb.append(",");
            }
        }
        String fromUserName = ZegoRoomManager.getInstance().userService.getUserName(fromUserID);
        String giftTips = getString(R.string.room_page_received_gift_tips, sb.toString(),
            fromUserName);
        SpannableString string = new SpannableString(giftTips);
        ForegroundColorSpan yellowSpan = new ForegroundColorSpan(
            ContextCompat.getColor(this, R.color.yellow)
        );

        int indexOfGiftName = giftTips.indexOf(giftName);
        string.setSpan(yellowSpan, indexOfGiftName,
            indexOfGiftName + giftName.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);

        tvGiftToast.setVisibility(View.VISIBLE);
        tvGiftToast.setText(string);
        tvGiftToast.removeCallbacks(hideGiftTips);
        tvGiftToast.postDelayed(hideGiftTips, 10_000L);
    }

    public void showExitDialog() {
        DialogHelper.showAlertDialog(LiveAudioRoomActivity.this,
            StringUtils.getString(R.string.room_page_destroy_room),
            StringUtils.getString(R.string.dialog_sure_to_destroy_room),
            StringUtils.getString(R.string.dialog_confirm),
            StringUtils.getString(R.string.dialog_cancel),
            (dialog, which) -> {
                dialog.dismiss();
                finish();
            },
            (dialog, which) -> dialog.dismiss()
        );
    }

    private String getMyUserID() {
        ZegoUserInfo localUserInfo = ZegoRoomManager.getInstance().userService.localUserInfo;
        return localUserInfo.getUserID();
    }

    @Override
    public void onBackPressed() {
        if (UserInfoHelper.isSelfOwner()) {
            showExitDialog();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * When this activity destroyed, we need dismiss all showing Dialog and let self leave room
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissDialog(settingsDialog);
        dismissDialog(giftDialog);
        dismissDialog(imInputDialog);
        dismissDialog(memberListDialog);
        dismissDialog(loadingDialog);
        dismissDialog(inviteDialog);
        ZegoRoomManager.getInstance().roomService.leaveRoom(error -> {
        });
    }

    private void dismissDialog(Dialog dialog) {
        if (loadingDialog != null) {
            loadingDialog.dismiss();
        }
    }

    private void showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = new LoadingDialog(this);
        }
        loadingDialog.updateText(R.string.network_reconnect);
        loadingDialog.show();
    }
}