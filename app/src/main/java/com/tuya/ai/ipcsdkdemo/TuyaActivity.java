package com.tuya.ai.ipcsdkdemo;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;

import com.tuya.ai.ipcsdkdemo.audio.FileAudioCapture;
import com.tuya.ai.ipcsdkdemo.video.VideoCapture;
import com.tuya.edge.init.EdgeNetConfigManager;
import com.tuya.edge.mqtt.MqEventCallBack;
import com.tuya.smart.aiipc.base.permission.PermissionUtil;
import com.tuya.smart.aiipc.ipc_sdk.IPCSDK;
import com.tuya.smart.aiipc.ipc_sdk.api.Common;
import com.tuya.smart.aiipc.ipc_sdk.api.IMediaTransManager;
import com.tuya.smart.aiipc.ipc_sdk.api.INetConfigManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IParamConfigManager;
import com.tuya.smart.aiipc.ipc_sdk.callback.DPEvent;
import com.tuya.smart.aiipc.ipc_sdk.callback.NetConfigCallback;
import com.tuya.smart.aiipc.ipc_sdk.service.IPCServiceManager;
import com.tuya.smart.aiipc.netconfig.ConfigProvider;
import com.tuya.smart.aiipc.netconfig.mqtt.TuyaNetConfig;
import com.tuya.smart.aiipc.trans.IPCLog;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Properties;

public class TuyaActivity extends AppCompatActivity {

    private static final String TAG = "TuyaActivity";

    private VideoCapture videoCapture;

    private FileAudioCapture fileAudioCapture;

    public static final String pid = "";

    public static final String uid = "";

    public static final String key = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tuya);
        findViewById(R.id.reset).setOnClickListener(v -> IPCServiceManager.getInstance().reset());

        PermissionUtil.check(TuyaActivity.this, new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WAKE_LOCK,
                Manifest.permission.CAMERA,
        }, () -> {
            initSDK();
            startConfig();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        IPCSDK.closeWriteLog();
    }

    private void initSDK() {
        IPCSDK.initSDK(this);
        IPCSDK.openWriteLog(this, "/sdcard/tuya_log/ipc/", 3);
        LoadParamConfig();

        IPCServiceManager.getInstance().setResetHandler(isHardward -> restart());

        //开始配网，目前支持蓝牙\二维码\MQTT
        INetConfigManager iNetConfigManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.NET_CONFIG_SERVICE);
        SurfaceView surfaceView = findViewById(R.id.surface);

        iNetConfigManager.setPID(pid);
        iNetConfigManager.setAuthorKey(key);
        iNetConfigManager.setUserId(uid);

        iNetConfigManager.config(INetConfigManager.QR_OUTPUT, surfaceView.getHolder());

        iNetConfigManager.config(INetConfigManager.QR_PARAMETERS, (INetConfigManager.OnParameterSetting) (p, camera) -> {
            camera.setDisplayOrientation(90);
        });

        ConfigProvider.enableMQTT(false);

        TuyaNetConfig.setDebug(true);
    }

    private void startConfig() {

        IPCServiceManager.getInstance().setDnsHandler(hostname -> {
            String hostAddr = null;
            try {
                hostAddr = InetAddress.getByName(hostname).getHostAddress();
            } catch (UnknownHostException e) {
                e.printStackTrace();
                Log.e(TAG, "DNSHandler: " + e.getMessage());
            }

            Log.e(TAG, "getIP: " + hostAddr);
            return hostAddr;
        });

        INetConfigManager iNetConfigManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.NET_CONFIG_SERVICE);


        NetConfigCallback netConfigCallback = new NetConfigCallback() {

            /**
             * 配网失败，建议提示用户重新操作
             * @param type 配网类型 ConfigProvider.TYPE_XX
             * @param msg 错误信息
             */
            @Override
            public void onNetConnectFailed(int type, String msg) {
                IPCLog.w(TAG, String.format(Locale.getDefault(), "onNetConnectFailed: %d %s", type, msg));
            }

            /**
             * 配网准备阶段失败，建议重试
             * @param type 配网类型 ConfigProvider.TYPE_XX
             * @param msg 错误信息
             */
            @Override
            public void onNetPrepareFailed(int type, String msg) {
                IPCLog.w(TAG, "onNetPrepareFailed: " + type + " / " + msg);
                iNetConfigManager.retry(type);
            }

            @Override
            public void configOver(boolean first, String token) {
                IPCLog.w(TAG, "configOver: token" + token);

                //实现类的配置
                Properties properties = new Properties();
                properties.put("dc_userInfo", "com.tuya.ai.ipcsdkdemo.edge.TenementReceiveEventImpl");
                properties.put("dc_door", "com.tuya.ai.ipcsdkdemo.edge.DoorReceiveEventImpl");
                properties.put("dc_talk", "com.tuya.ai.ipcsdkdemo.edge.TalkReceiveEventImpl");

                EdgeNetConfigManager.getInstance().initSDK(TuyaActivity.this, token, pid, uid, key, "/sdcard/", "/sdcard/", properties, new MqEventCallBack() {

                    @Override
                    public void mqEvent(DPEvent[] events) {
                        //自定义dp处理
                    }
                });

                //视频流（相机）
                videoCapture = new VideoCapture(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN);
                videoCapture.startVideoCapture();

                //音频流（本地文件）
                fileAudioCapture = new FileAudioCapture(TuyaActivity.this);
                fileAudioCapture.startFileCapture();

                IMediaTransManager mediaTransManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);
                mediaTransManager.setDoorBellCallStatusCallback(status -> {
                    /**
                     * 门铃呼叫报警接听状态
                     * status = -1 未知状态
                     * status = 0 接听
                     * status = 1 挂断
                     * status = 2 通话中心跳
                     * {@link Common.DoorBellCallStatus}
                     * */
                    Log.d(TAG, "doorbell back: " + status);

                });
            }

            @Override
            public void startConfig() {
                IPCLog.w(TAG, "startConfig: ");
            }

            @Override
            public void recConfigInfo() {
                IPCLog.w(TAG, "recConfigInfo: ");
            }
        };

        iNetConfigManager.configNetInfo(netConfigCallback);

    }

    private void restart() {
        Intent mStartActivity = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (mStartActivity != null) {
            int mPendingIntentId = 123456;
            PendingIntent mPendingIntent = PendingIntent.getActivity(this, mPendingIntentId
                    , mStartActivity, PendingIntent.FLAG_CANCEL_CURRENT);
            AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
            Runtime.getRuntime().exit(0);
        }
    }

    private void LoadParamConfig() {
        IParamConfigManager configManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_PARAM_SERVICE);

        /**
         * 主码流参数配置
         * */
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_WIDTH, 1280);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_HEIGHT, 720);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_FRAME_RATE, 30);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_I_FRAME_INTERVAL, 2);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_BIT_RATE, 1024000);


        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_WIDTH, 1280);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_HEIGHT, 720);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_FRAME_RATE, 15);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_I_FRAME_INTERVAL, 2);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_SUB, Common.ParamKey.KEY_VIDEO_BIT_RATE, 512000);

        /**
         * 音频流参数
         * */
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_CHANNEL_NUM, 1);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_SAMPLE_RATE, 8000);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_SAMPLE_BIT, 16);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_FRAME_RATE, 25);
    }
}