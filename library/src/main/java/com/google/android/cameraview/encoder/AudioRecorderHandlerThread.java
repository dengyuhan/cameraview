/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.cameraview.encoder;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.google.android.cameraview.utils.YUVRotateUtil;

/**
 * @author: sq
 * @date: 2017/7/26
 * @corporation: 深圳市思迪信息科技有限公司
 * @description: 音、视频编解码线程
 */
public class AudioRecorderHandlerThread extends HandlerThread implements Handler.Callback {
    //    private static final String TAG = AudioRecorderHandlerThread.class.getSimpleName();
    private static final String TAG = "AudioRecorderThread";

    /* Handler associated with this HandlerThread*/
    private Handler mRecorderHandler;

    /* Reference to a handler from the thread that started this HandlerThread */
    private Handler mCallback;

    private static final int MSG_RECORDING_START = 100;
    private static final int MSG_RECORDING_STOP = 101;
    private static final int MSG_ENCODE_VIDEO = 104;

    /* AudioRecord object to record audio from microphone input */
    private AudioRecorder audioRecorder;

    /* AudioEncoder object to take recorded ByteBuffer from the AudioRecord object*/
    private AudioEncoder audioEncoder;

    /* VideoEncoder object to take recorded ByteBuffer from the Camera object*/
    public VideoEncoder videoEncoder;

    /* MediaMuxerWrapper object to add encoded data to a MediaMuxer which converts it to .mp4*/
    private MediaMuxerWrapper mediaMuxerWrapper;


    public AudioRecorderHandlerThread(String outputFile) {
        super("RecorderThread");
        mediaMuxerWrapper = new MediaMuxerWrapper(outputFile);
        audioEncoder = new AudioEncoder(mediaMuxerWrapper);
        audioRecorder = new AudioRecorder(audioEncoder);
        videoEncoder = new VideoEncoder(mediaMuxerWrapper, 1080, 1920);
        start();
    }

    public void setCallback(Handler cb) {
        mCallback = cb;
    }

    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();
        mRecorderHandler = new Handler(getLooper(), this);
    }

    @Override
    public boolean handleMessage(Message message) {
        switch (message.what) {
            case MSG_RECORDING_START:
                handleStartRecording();
                break;
            case MSG_RECORDING_STOP:
                handleStopRecording();
                break;
            case MSG_ENCODE_VIDEO:
                handleEncodeFrame((byte[]) message.obj, message.arg1, message.arg2);
                break;
        }
        return true;
    }

    private void handleEncodeFrame(byte[] data, int width, int height) {
        Log.d("handleEncodeFrame-->",
                data.length + " " + width + " " + height + " " + Thread.currentThread().getName());
        final byte[] tempData = YUVRotateUtil.rotateYUV420Degree90(data, width, height);

        videoEncoder.encode(tempData, tempData.length,
                videoEncoder.getPTSUs());
    }

    private void handleStartRecording() {
        Log.d(TAG, "recording start message received");
        //mCallback.sendMessage(Message.obtain(null, Messages.MSG_RECORDING_START_CALLBACK));
        audioRecorder.start();
        audioEncoder.start();
        videoEncoder.start();
        audioRecorder.record();
    }

    private void handleStopRecording() {
        //mCallback.sendMessage(Message.obtain(null, Messages.MSG_RECORDING_STOP_CALLBACK));
        audioRecorder.stopRecording();
        videoEncoder.stop();
    }

    public void startRecording() {
        Message msg = Message.obtain(null, MSG_RECORDING_START);
        mRecorderHandler.sendMessage(msg);
    }

    public void stopRecording() {
        audioRecorder.setIsRecordingFalse();
        Message msg = Message.obtain(null, MSG_RECORDING_STOP);
        mRecorderHandler.sendMessage(msg);
    }

    public VideoEncoder getVideoEncoder() {
        return videoEncoder;
    }


    public void sendEncodeFrame(byte[] data, int width, int height) {
        handleEncodeFrame(data, width, height);
        /*if (mRecorderHandler == null) {
            return;
        }
        Message msg = Message.obtain(null, MSG_ENCODE_VIDEO, width, height, data);
        mRecorderHandler.sendMessage(msg);*/
    }
}
