/*
 * Copyright (c) 2017 Baidu, Inc. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.baidu.duer.dcs.androidapp;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.duer.dcs.R;
import com.baidu.duer.dcs.androidsystemimpl.PlatformFactoryImpl;
import com.baidu.duer.dcs.androidsystemimpl.webview.BaseWebView;
import com.baidu.duer.dcs.devicemodule.screen.ScreenDeviceModule;
import com.baidu.duer.dcs.devicemodule.screen.message.RenderVoiceInputTextPayload;
import com.baidu.duer.dcs.devicemodule.voiceinput.VoiceInputDeviceModule;
import com.baidu.duer.dcs.framework.DcsFramework;
import com.baidu.duer.dcs.framework.DeviceModuleFactory;
import com.baidu.duer.dcs.framework.IResponseListener;
import com.baidu.duer.dcs.http.HttpConfig;
import com.baidu.duer.dcs.oauth.api.IOauth;
import com.baidu.duer.dcs.oauth.api.OauthImpl;
import com.baidu.duer.dcs.systeminterface.IMediaPlayer;
import com.baidu.duer.dcs.systeminterface.IPlatformFactory;
import com.baidu.duer.dcs.systeminterface.IWakeUp;
import com.baidu.duer.dcs.util.CommonUtil;
import com.baidu.duer.dcs.util.FileUtil;
import com.baidu.duer.dcs.util.LogUtil;
import com.baidu.duer.dcs.util.NetWorkUtil;
import com.baidu.duer.dcs.wakeup.WakeUp;

import java.io.File;

/**
 * 主界面 activity
 * <p>
 * Created by zhangyan42@baidu.com on 2017/5/18.
 */
public class DcsSampleMainActivity extends Activity implements View.OnClickListener {
    public static final String TAG = DcsSampleMainActivity.class.getSimpleName();
    private Button mVoiceButton;
    private TextView mTextViewTimeStopListen;
    private TextView mTextViewRenderVoiceInputText;
    private Button mPauseOrPlayButton;
    private BaseWebView mWebView;
    private LinearLayout mTopLinearLayout;
    private DcsFramework mDcsFramework;
    private DeviceModuleFactory mDeviceModuleFactory;
    private IPlatformFactory mPlatformFactory;
    private boolean mIsPause = true;
    private long mStartTimeStopListen;
    private boolean mIsStopListenReceiving;
    private String mHtmlUrl;
    // 唤醒
    private WakeUp mWakeUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dcs_sample_activity_main);
        initView();
        initOauth();
        initFramework();
    }

    private void initView() {
        Button openLogBtn = (Button) findViewById(R.id.openLogBtn);
        openLogBtn.setOnClickListener(this);
        mVoiceButton = (Button) findViewById(R.id.voiceBtn);
        mVoiceButton.setOnClickListener(this);

        mTextViewTimeStopListen = (TextView) findViewById(R.id.id_tv_time_0);
        mTextViewRenderVoiceInputText = (TextView) findViewById(R.id.id_tv_RenderVoiceInputText);
        mTopLinearLayout = (LinearLayout) findViewById(R.id.topLinearLayout);

        mWebView = new BaseWebView(DcsSampleMainActivity.this.getApplicationContext());
        mWebView.setWebViewClientListen(new BaseWebView.WebViewClientListener() {
            @Override
            public BaseWebView.LoadingWebStatus shouldOverrideUrlLoading(WebView view, String url) {
                // 拦截处理不让其点击
                return BaseWebView.LoadingWebStatus.STATUS_TRUE;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {

            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!url.equals(mHtmlUrl) && !"about:blank".equals(mHtmlUrl)) {
                    mPlatformFactory.getWebView().linkClicked(url);
                }

                mHtmlUrl = url;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {

            }
        });
        mTopLinearLayout.addView(mWebView);

        Button mPreviousSongBtn = (Button) findViewById(R.id.previousSongBtn);
        mPauseOrPlayButton = (Button) findViewById(R.id.pauseOrPlayBtn);
        Button mNextSongBtn = (Button) findViewById(R.id.nextSongBtn);
        mPreviousSongBtn.setOnClickListener(this);
        mPauseOrPlayButton.setOnClickListener(this);
        mNextSongBtn.setOnClickListener(this);
    }

    private void initFramework() {
        mPlatformFactory = new PlatformFactoryImpl(this);
        mPlatformFactory.setWebView(mWebView);
        mDcsFramework = new DcsFramework(mPlatformFactory);
        mDeviceModuleFactory = mDcsFramework.getDeviceModuleFactory();

        mDeviceModuleFactory.createVoiceOutputDeviceModule();
        mDeviceModuleFactory.createVoiceInputDeviceModule();
        mDeviceModuleFactory.getVoiceInputDeviceModule().addVoiceInputListener(
                new VoiceInputDeviceModule.IVoiceInputListener() {
                    @Override
                    public void onStartRecord() {
                        LogUtil.d(TAG, "onStartRecord");
                        startRecording();
                    }

                    @Override
                    public void onFinishRecord() {
                        LogUtil.d(TAG, "onFinishRecord");
                        stopRecording();
                    }

                    @Override
                    public void onSucceed(int statusCode) {
                        LogUtil.d(TAG, "onSucceed-statusCode:" + statusCode);
                        if (statusCode != 200) {
                            stopRecording();
                            Toast.makeText(DcsSampleMainActivity.this,
                                    getResources().getString(R.string.voice_err_msg),
                                    Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }

                    @Override
                    public void onFailed(String errorMessage) {
                        LogUtil.d(TAG, "onFailed-errorMessage:" + errorMessage);
                        stopRecording();
                        Toast.makeText(DcsSampleMainActivity.this,
                                getResources().getString(R.string.voice_err_msg),
                                Toast.LENGTH_SHORT)
                                .show();
                    }
                });

        mDeviceModuleFactory.createAlertsDeviceModule();

        mDeviceModuleFactory.createAudioPlayerDeviceModule();
        mDeviceModuleFactory.getAudioPlayerDeviceModule().addAudioPlayListener(
                new IMediaPlayer.SimpleMediaPlayerListener() {
                    @Override
                    public void onPaused() {
                        super.onPaused();
                        mPauseOrPlayButton.setText(getResources().getString(R.string.audio_paused));
                        mIsPause = true;
                    }

                    @Override
                    public void onPlaying() {
                        super.onPlaying();
                        mPauseOrPlayButton.setText(getResources().getString(R.string.audio_playing));
                        mIsPause = false;
                    }

                    @Override
                    public void onCompletion() {
                        super.onCompletion();
                        mPauseOrPlayButton.setText(getResources().getString(R.string.audio_default));
                        mIsPause = false;
                    }

                    @Override
                    public void onStopped() {
                        super.onStopped();
                        mPauseOrPlayButton.setText(getResources().getString(R.string.audio_default));
                        mIsPause = true;
                    }
                });

        mDeviceModuleFactory.createSystemDeviceModule();
        mDeviceModuleFactory.createSpeakControllerDeviceModule();
        mDeviceModuleFactory.createPlaybackControllerDeviceModule();
        mDeviceModuleFactory.createScreenDeviceModule();
        mDeviceModuleFactory.getScreenDeviceModule()
                .addRenderVoiceInputTextListener(new ScreenDeviceModule.IRenderVoiceInputTextListener() {
                    @Override
                    public void onRenderVoiceInputText(RenderVoiceInputTextPayload payload) {
                        mTextViewRenderVoiceInputText.setText(payload.text);
                    }

                });
        // init唤醒
        mWakeUp = new WakeUp(mPlatformFactory.getWakeUp(),
                mPlatformFactory.getAudioRecord());
        mWakeUp.addWakeUpListener(wakeUpListener);
        // 开始录音，监听是否说了唤醒词
        mWakeUp.startWakeUp();
    }

    private IWakeUp.IWakeUpListener wakeUpListener = new IWakeUp.IWakeUpListener() {
        @Override
        public void onWakeUpSucceed() {
            Toast.makeText(DcsSampleMainActivity.this,
                    getResources().getString(R.string.wakeup_succeed),
                    Toast.LENGTH_SHORT)
                    .show();
            mVoiceButton.performClick();
        }
    };

    private void doUserActivity() {
        mDeviceModuleFactory.getSystemProvider().userActivity();
    }

    private void initOauth() {
        IOauth baiduOauth = new OauthImpl();
        HttpConfig.setAccessToken(baiduOauth.getAccessToken());
    }

    private void stopRecording() {
        mWakeUp.startWakeUp();
        mIsStopListenReceiving = false;
        mVoiceButton.setText(getResources().getString(R.string.stop_record));
        long t = System.currentTimeMillis() - mStartTimeStopListen;
        mTextViewTimeStopListen.setText(getResources().getString(R.string.time_record, t));
    }

    private void startRecording() {
        mWakeUp.stopWakeUp();
        mIsStopListenReceiving = true;
        mDeviceModuleFactory.getSystemProvider().userActivity();
        mVoiceButton.setText(getResources().getString(R.string.start_record));
        mTextViewTimeStopListen.setText("");
        mTextViewRenderVoiceInputText.setText("");
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.voiceBtn:
                if (!NetWorkUtil.isNetworkConnected(this)) {
                    Toast.makeText(this,
                            getResources().getString(R.string.err_net_msg),
                            Toast.LENGTH_SHORT).show();
                    mWakeUp.startWakeUp();
                    return;
                }
                if (CommonUtil.isFastDoubleClick()) {
                    return;
                }
                if (TextUtils.isEmpty(HttpConfig.getAccessToken())) {
                    startActivity(new Intent(DcsSampleMainActivity.this, DcsSampleOAuthActivity.class));
                    finish();
                    return;
                }
                if (!mDcsFramework.getDcsClient().isConnected()) {
                    mDcsFramework.getDcsClient().startConnect();
                    return;
                }
                if (mIsStopListenReceiving) {
                    mPlatformFactory.getVoiceInput().stopRecord();
                    mIsStopListenReceiving = false;
                    return;
                }
                mIsStopListenReceiving = true;
                mStartTimeStopListen = System.currentTimeMillis();
                mPlatformFactory.getVoiceInput().startRecord();
                doUserActivity();
                break;
            case R.id.openLogBtn:
                openAssignFolder(FileUtil.getLogFilePath());
                break;
            case R.id.previousSongBtn:
                mPlatformFactory.getPlayback().previous(nextPreResponseListener);
                doUserActivity();
                break;
            case R.id.nextSongBtn:
                mPlatformFactory.getPlayback().next(nextPreResponseListener);
                doUserActivity();
                break;
            case R.id.pauseOrPlayBtn:
                if (mIsPause) {
                    mPlatformFactory.getPlayback().play(playPauseResponseListener);
                } else {
                    mPlatformFactory.getPlayback().pause(playPauseResponseListener);
                }
                doUserActivity();
                break;
            default:
                break;
        }
    }

    private IResponseListener playPauseResponseListener = new IResponseListener() {
        @Override
        public void onSucceed(int statusCode) {
            if (statusCode == 204) {
                Toast.makeText(DcsSampleMainActivity.this,
                        getResources().getString(R.string.no_directive),
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }

        @Override
        public void onFailed(String errorMessage) {
            Toast.makeText(DcsSampleMainActivity.this,
                    getResources().getString(R.string.request_error),
                    Toast.LENGTH_SHORT)
                    .show();
        }
    };

    private IResponseListener nextPreResponseListener = new IResponseListener() {
        @Override
        public void onSucceed(int statusCode) {
            if (statusCode == 204) {
                Toast.makeText(DcsSampleMainActivity.this,
                        getResources().getString(R.string.no_audio),
                        Toast.LENGTH_SHORT)
                        .show();
            }
        }

        @Override
        public void onFailed(String errorMessage) {
            Toast.makeText(DcsSampleMainActivity.this,
                    getResources().getString(R.string.request_error),
                    Toast.LENGTH_SHORT)
                    .show();
        }
    };

    /**
     * 打开日志
     *
     * @param path 文件的绝对路径
     */
    private void openAssignFolder(String path) {
        File file = new File(path);
        if (!file.exists()) {
            Toast.makeText(DcsSampleMainActivity.this,
                    getResources().getString(R.string.no_log),
                    Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setDataAndType(Uri.fromFile(file), "text/plain");
        try {
            startActivity(Intent.createChooser(intent,
                    getResources().getString(R.string.open_file_title)));
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 先remove listener  停止唤醒,释放资源
        mWakeUp.removeWakeUpListener(wakeUpListener);
        mWakeUp.stopWakeUp();
        mWakeUp.releaseWakeUp();

        if (mDcsFramework != null) {
            mDcsFramework.release();
        }
        mWebView.setWebViewClientListen(null);
        mTopLinearLayout.removeView(mWebView);
        mWebView.removeAllViews();
        mWebView.destroy();
    }
}