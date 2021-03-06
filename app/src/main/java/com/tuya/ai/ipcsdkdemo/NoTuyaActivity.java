package com.tuya.ai.ipcsdkdemo;

import android.Manifest;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.tuya.ai.ipcsdkdemo.audio.FileAudioCapture;
import com.tuya.ai.ipcsdkdemo.video.VideoCapture;
import com.tuya.edge.atop.AtopFacade;
import com.tuya.edge.enums.QrcodeEnum;
import com.tuya.edge.init.EdgeNetConfigManager;
import com.tuya.edge.init.MediaParamConfigCallback;
import com.tuya.edge.model.vo.NetQrcodeVO;
import com.tuya.edge.utils.AESUtils;
import com.tuya.smart.aiipc.base.permission.PermissionUtil;
import com.tuya.smart.aiipc.ipc_sdk.api.Common;
import com.tuya.smart.aiipc.ipc_sdk.api.IDeviceManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IMediaTransManager;
import com.tuya.smart.aiipc.ipc_sdk.api.IParamConfigManager;
import com.tuya.smart.aiipc.ipc_sdk.service.IPCServiceManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class NoTuyaActivity extends AppCompatActivity {

    private static final String TAG = "NoTuyaActivity";

    private SurfaceView surfaceView;

    private VideoCapture videoCapture;

    private FileAudioCapture fileAudioCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_no_tuya);

        findViewById(R.id.call).setOnClickListener(v -> {
            IMediaTransManager mediaTransManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);

            try {
                InputStream fileStream = getAssets().open("donghua.jpg");

                byte[] buffer = new byte[2048];
                int bytesRead;
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                while ((bytesRead = fileStream.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                byte[] file = output.toByteArray();

                mediaTransManager.sendDoorBellCallForPress(file, Common.NOTIFICATION_CONTENT_TYPE_E.NOTIFICATION_CONTENT_JPEG);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    //??????????????????????????????
                    String secret = "";

                    //????????????id,??????????????????id??????
                    String cid = "";
                    String basePath = getFilesDir().getPath() + "/";
                    String recordPath = getFilesDir().getPath() + "/";

                    //???????????????Map
                    String t = "";
                    String a = "";
                    String key = "";

                    //??????????????????
                    NetQrcodeVO netQrcodeVO = AtopFacade.getInstance().queryQrcodeInfo(a, key, cid);
                    //???????????????????????????
                    String qrcodeInfo = AESUtils.decrypt(netQrcodeVO.getData(), secret);

                    //??????qrcodeMap;
                    Map<String, String> qrcodeMap = JSON.parseObject(qrcodeInfo, new TypeReference<HashMap<String, String>>() {
                    });
                    qrcodeMap.put(QrcodeEnum.TOKEN.getCode(), t);

                    //??????????????????
                    Properties properties = new Properties();
                    properties.put("dc_userInfo", "com.tuya.ai.ipcsdkdemo.edge.TenementReceiveEventImpl");
                    properties.put("dc_door", "com.tuya.ai.ipcsdkdemo.edge.DoorReceiveEventImpl");
                    properties.put("dc_talk", "com.tuya.ai.ipcsdkdemo.edge.TalkReceiveEventImpl");
                    //??????????????????
                    //  properties.put("dc_faceInfo","com.tuya.ai.ipcsdkdemo.edge.FaceImageReceiveEventImpl");
                    // ???????????????
                    //   properties.put("dn_cardInfo","com.tuya.ai.ipcsdkdemo.edge.CardReceiveEventImpl");
                    // ?????????????????????
                    //  properties.put("dc_qrCodeInfo","com.tuya.ai.ipcsdkdemo.edge.QcCodeReceiveEventImpl");

                    PermissionUtil.check(NoTuyaActivity.this, new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WAKE_LOCK,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.CAMERA
                    }, () -> initSDK(NoTuyaActivity.this, cid, qrcodeMap, basePath, recordPath, properties, new MediaParamConfigCallback() {
                        public void initMediaParamConfig() {
                            LoadParamConfig();
                        }
                    }));
                } catch (Exception ex) {

                }
            }
        }).start();
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param ctx                 ?????????
     * @param cid                 ????????????id
     * @param qrcodeMap           ???????????????Map
     * @param basePath            ????????????????????????????????????SDK???????????????
     * @param recordPath          ??????????????????????????????????????????
     * @param properties          ??????????????????
     * @param paramConfigCallBack
     */
    private void initSDK(Context ctx, String cid, Map<String, String> qrcodeMap, String basePath, String recordPath,
                         Properties properties, MediaParamConfigCallback paramConfigCallBack) {

        EdgeNetConfigManager.getInstance().initSDK(ctx, cid, qrcodeMap, basePath, recordPath, properties, "1", paramConfigCallBack);

        runOnUiThread(() -> findViewById(R.id.call).setEnabled(true));

        //??????????????????id
        IDeviceManager deviceManager = (IDeviceManager) IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.DEVICE_SERVICE);
        String deviceId = deviceManager.getDeviceId();

        //?????????????????????
        videoCapture = new VideoCapture(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN);
        videoCapture.startVideoCapture();

        //???????????????????????????
        fileAudioCapture = new FileAudioCapture(ctx);
        fileAudioCapture.startFileCapture();

        IMediaTransManager mediaTransManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_TRANS_SERVICE);
        mediaTransManager.setDoorBellCallStatusCallback(status -> {
            /**
             * ??????????????????????????????
             * status = -1 ????????????
             * status = 0 ??????
             * status = 1 ??????
             * status = 2 ???????????????
             * {@link Common.DoorBellCallStatus}
             * */
            Log.d(TAG, "doorbell back: " + status);

        });
    }

    /**
     * ?????????????????????
     */
    private void LoadParamConfig() {
        IParamConfigManager configManager = IPCServiceManager.getInstance().getService(IPCServiceManager.IPCService.MEDIA_PARAM_SERVICE);

        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_WIDTH, 1280);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_HEIGHT, 720);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_FRAME_RATE, 24);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_I_FRAME_INTERVAL, 2);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_VIDEO_MAIN, Common.ParamKey.KEY_VIDEO_BIT_RATE, 1024000);

        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_CHANNEL_NUM, 1);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_SAMPLE_RATE, 8000);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_SAMPLE_BIT, 16);
        configManager.setInt(Common.ChannelIndex.E_CHANNEL_AUDIO, Common.ParamKey.KEY_AUDIO_FRAME_RATE, 25);
    }
}