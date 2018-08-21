
package com.google.android.cameraview.encoder;

import android.media.CamcorderProfile;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.google.android.cameraview.utils.YUVUtils;

public class MediaRecorderThread extends HandlerThread implements Handler.Callback,
        IMediaRecorder {
    //    private static final String TAG = MediaRecorderThread.class.getSimpleName();
    private static final String TAG = "MediaRecorderThread";

    /* Handler associated with this HandlerThread*/
    private Handler mRecorderHandler;

    /* Reference to a handler from the thread that started this HandlerThread */
    private Handler mCallback;

    private static final int MSG_RECORDING_START = 100;
    private static final int MSG_RECORDING_STOP = 101;
    private static final int MSG_SETUP_MEDIARECORDER = 103;
    private static final int MSG_ENCODE_VIDEO = 104;

    /* AudioRecord object to record audio from microphone input */
    private AudioRecorder mAudioRecorder;

    /* AudioEncoder object to take recorded ByteBuffer from the AudioRecord object*/
    private AudioEncoder mAudioEncoder;

    /* VideoEncoder object to take recorded ByteBuffer from the Camera object*/
    public VideoEncoder mVideoEncoder;

    /* MediaMuxerWrapper object to add encoded data to a MediaMuxer which converts it to .mp4*/
    private MediaMuxerWrapper mMediaMuxer;

    private boolean mRecording;

    private boolean mRecordAudio;


    public MediaRecorderThread() {
        super("RecorderThread");
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
            case MSG_SETUP_MEDIARECORDER:
                final Bundle data = message.getData();
                boolean recordAudio = data.getBoolean("record_audio");
                String outputPath = data.getString("output_path");
                handleSetupMediaRecorder(outputPath, recordAudio, (CamcorderProfile) message.obj);
                break;
            case MSG_ENCODE_VIDEO:
                handleEncodeFrame((byte[]) message.obj, message.arg1, message.arg2);
                break;
        }
        return true;
    }

    private void handleSetupMediaRecorder(String outputPath, boolean recordAudio,
            CamcorderProfile profile) {
        this.mRecordAudio = recordAudio;
        Log.d("CamcorderProfile---->",
                profileToString(profile) + "--" + Thread.currentThread().getName());

        this.mMediaMuxer = new MediaMuxerWrapper(outputPath,
                recordAudio ? MediaMuxerWrapper.TOTAL_TRACK_COUNT
                        : MediaMuxerWrapper.VIDEO_TRACK_COUNT);

        //编码器的宽高调换
        int width = profile.videoFrameHeight;
        int height = profile.videoFrameWidth;
        /*mVideoEncoder = new VideoEncoder(mMediaMuxer, width, height,
                profile.videoFrameRate, profile.videoBitRate);*/
        mVideoEncoder = new VideoEncoder(mMediaMuxer, width, height);
        if (mRecordAudio) {
            //音频配置
            /*mAudioEncoder = new AudioEncoder(mMediaMuxer, profile.audioSampleRate,
                    profile.audioBitRate);*/
            mAudioEncoder = new AudioEncoder(mMediaMuxer);
            //mAudioRecorder = new AudioRecorder(mAudioEncoder, profile.audioSampleRate);
            mAudioRecorder = new AudioRecorder(mAudioEncoder);
        }
    }

    private void handleEncodeFrame(byte[] data, int width, int height) {
        //Log.d("handleEncodeFrame-->",data.length + " " + width + " " + height + " " + Thread
        // .currentThread().getName());
        final byte[] tempData = YUVUtils.rotateYUV420Degree90(data, width, height);
        if (tempData != null) {
            mVideoEncoder.encode(tempData, tempData.length,
                    mVideoEncoder.getPTSUs());
        }

    }


    private void handleStartRecording() {
        mRecording = true;
        //mCallback.sendMessage(Message.obtain(null, Messages.MSG_RECORDING_START_CALLBACK));
        if (mRecordAudio) {
            mAudioRecorder.start();
            mAudioEncoder.start();
        }
        mVideoEncoder.start();
        if (mRecordAudio) {
            mAudioRecorder.record();
        }
    }

    private void handleStopRecording() {
        mRecording = false;
        //mCallback.sendMessage(Message.obtain(null, Messages.MSG_RECORDING_STOP_CALLBACK));
        if (mRecordAudio) {
            mAudioRecorder.stopRecording();
        }
        mVideoEncoder.stop();
    }

    @Override
    public void setupMediaRecorder(String outputPath, boolean recordAudio,
            CamcorderProfile profile) {
        Message msg = Message.obtain(null, MSG_SETUP_MEDIARECORDER, profile);
        Bundle data = new Bundle();
        data.putBoolean("record_audio", recordAudio);
        data.putString("output_path", outputPath);
        msg.setData(data);
        mRecorderHandler.sendMessage(msg);
    }

    @Override
    public void startRecording() {
        Message msg = Message.obtain(null, MSG_RECORDING_START);
        mRecorderHandler.sendMessage(msg);
    }

    @Override
    public void stopRecording() {
        if (mRecordAudio) {
            mAudioRecorder.setIsRecordingFalse();
        }
        Message msg = Message.obtain(null, MSG_RECORDING_STOP);
        mRecorderHandler.sendMessage(msg);
    }

    @Override
    public void release() {

    }

    @Override
    public boolean isRecording() {
        return mRecording;
    }

    public VideoEncoder getVideoEncoder() {
        return mVideoEncoder;
    }


    public void sendEncodeFrame(byte[] data, int width, int height) {
        handleEncodeFrame(data, width, height);
        /*
        if (mRecorderHandler == null) {
            return;
        }
        Message msg = Message.obtain(null, MSG_ENCODE_VIDEO, width, height, data);
        mRecorderHandler.sendMessage(msg);
        */
    }


    private String profileToString(CamcorderProfile profile) {
        return "[视频 - 宽:" + profile.videoFrameWidth + ",高:" + profile.videoFrameHeight + ",比特率:"
                + profile.videoBitRate + ",帧率:" + profile.videoFrameRate + "]\n[音频 - 采样率:" +
                profile.audioSampleRate + ",比特率:" + profile.audioBitRate + ",通道数:"
                + profile.audioChannels + "]";
    }
}
