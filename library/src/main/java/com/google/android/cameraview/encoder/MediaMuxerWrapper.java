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

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author: sq
 * @date: 2017/7/26
 * @corporation: 深圳市思迪信息科技有限公司
 * @description: 混合器，将音、视频进行混合，生成完整mp4文件
 */
public class MediaMuxerWrapper {
    private static final String TAG = MediaMuxerWrapper.class.getSimpleName();
    private MediaMuxer muxer;
    private boolean isMuxing;
    private MediaFormat audioFormat;
    private MediaFormat videoFormat;
    private int audioTrackIndex;
    private int videoTrackIndex;

    private int mNumTracksAdded = 0;
    private int TOTAL_NUM_TRACKS = 2;
    private boolean mMuxerStarted;

    public MediaMuxerWrapper(String outputFile) {

        try {
            muxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addAudioEncoder(AudioEncoder encoder) {
        audioFormat = encoder.getEncoder().getOutputFormat();
        audioTrackIndex = muxer.addTrack(audioFormat);
        Log.d(TAG, "添加音轨-->" + Thread.currentThread().getName());
    }

    public void addVideoEncoder(VideoEncoder encoder) {
        videoFormat = encoder.getEncoder().getOutputFormat();
        videoTrackIndex = muxer.addTrack(videoFormat);
        Log.d(TAG, "添加视频轨-->" + Thread.currentThread().getName());
    }

    public void startMuxing() {
        mNumTracksAdded++;
        if (mNumTracksAdded == TOTAL_NUM_TRACKS) {
            isMuxing = true;
            muxer.start();
            mMuxerStarted = true;
        }
    }

    public void stopMuxing() {
        if (isMuxing) {
            isMuxing = false;
            muxer.stop();
            muxer.release();
        }
    }

    public void muxAudio(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) {
        try {
            if (mMuxerStarted) {
                muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void muxVideo(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) {
        try {
            if (mMuxerStarted) {
                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}