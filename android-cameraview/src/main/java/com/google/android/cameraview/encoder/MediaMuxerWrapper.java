package com.google.android.cameraview.encoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaMuxerWrapper {
    private static final String TAG = MediaMuxerWrapper.class.getSimpleName();

    public static int VIDEO_TRACK_COUNT = 1;//只录视频的轨道数
    public static int TOTAL_TRACK_COUNT = 2;//音视频都录的轨道数

    private MediaMuxer mMediaMuxer;
    private boolean isMuxing;
    private MediaFormat mOutputAudioFormat;
    private MediaFormat mOutputVideoFormat;
    private int audioTrackIndex;
    private int videoTrackIndex;

    private int mNumTracksAdded = 0;
    private boolean mMuxerStarted;

    private int mTrackCount;

    public MediaMuxerWrapper(String outputFile, int trackCount) {
        this.mTrackCount = trackCount;
        try {
            mMediaMuxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addAudioEncoder(AudioEncoder encoder) {
        mOutputAudioFormat = encoder.getEncoder().getOutputFormat();
        audioTrackIndex = mMediaMuxer.addTrack(mOutputAudioFormat);
        Log.d(TAG, "添加音轨-->" + Thread.currentThread().getName());
    }

    public void addVideoEncoder(VideoEncoder encoder) {
        mOutputVideoFormat = encoder.getEncoder().getOutputFormat();
        videoTrackIndex = mMediaMuxer.addTrack(mOutputVideoFormat);
        Log.d(TAG, "添加视频轨-->" + Thread.currentThread().getName());
    }

    public void startMuxing() {
        mNumTracksAdded++;
        if (mNumTracksAdded == mTrackCount) {
            isMuxing = true;
            mMediaMuxer.start();
            mMuxerStarted = true;
        }
    }

    public void stopMuxing() {
        if (isMuxing) {
            isMuxing = false;
            mMediaMuxer.stop();
            mMediaMuxer.release();
        }
    }

    public void muxAudio(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) {
        try {
            if (mMuxerStarted) {
                mMediaMuxer.writeSampleData(audioTrackIndex, buffer, bufferInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void muxVideo(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) {
        try {
            if (mMuxerStarted) {
                mMediaMuxer.writeSampleData(videoTrackIndex, buffer, bufferInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}