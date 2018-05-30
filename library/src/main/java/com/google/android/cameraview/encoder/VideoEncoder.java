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
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author: sq
 * @date: 2017/7/26
 * @corporation: 深圳市思迪信息科技有限公司
 * @description: 视频编、解码类
 */
public class VideoEncoder {
    //    private static final String TAG = VideoEncoder.class.getSimpleName();
    private static final String TAG = "VideoEncoder";
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 20;
    private static final int COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    private int bitRate;

    private static final int TIMEOUT_US = 10000;
    private MediaCodec videoEncoder;
    private MediaFormat videoFormat;
    private static VideoEncoder instance;
    private int mWidth;
    private int mHeight;

    private boolean isEncoding;

    private MediaCodec.BufferInfo bufferInfo;

    private MediaMuxerWrapper muxer;

    private long prevOutputPTSUs = 0;

    public VideoEncoder(MediaMuxerWrapper mux, int width, int height) {
        mWidth = width;
        mHeight = height;
        instance = this;
        muxer = mux;
        bufferInfo = new MediaCodec.BufferInfo();

        try {
            videoEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }


        bitRate = width * height * 3 / 2;
        videoFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FORMAT);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

//        audioFormat = MediaFormat.createAudioFormat(MIME_TYPE,
//                SAMPLE_RATE,
//                1);
//        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
//        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
//        //optional stuff
//        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
//        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel
// .AACObjectLC);
//        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE,BIT_RATE);
//
//        audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    public void start() {
        Log.d(TAG, "视频开始编码-->" + Thread.currentThread().getName());
        videoEncoder.start();
        isEncoding = true;
    }

    public void stop() {
        Log.d(TAG, "视频停止编码-->" + Thread.currentThread().getName());
        videoEncoder.stop();
        muxer.stopMuxing();
        isEncoding = false;
    }

    public void encode(byte[] input, int length, long presentationTimeUs) {
        //get input buffer
        if (isEncoding) {

            byte[] yuv420sp = new byte[mWidth * mHeight * 3 / 2];
            NV21ToNV12(input, yuv420sp, mWidth, mHeight);
            input = yuv420sp;

            final ByteBuffer[] inputBuffers = videoEncoder.getInputBuffers();

            //dequeue input buffer
            final int inputBufferIndex = videoEncoder.dequeueInputBuffer(TIMEOUT_US);
            if (inputBufferIndex >= 0) {
                final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();

                if (input != null) {
                    //copy ByteBuffer to input buffer
                    inputBuffer.put(input);
                }
                if (length <= 0) {
                    ////enqueue bytebuffer with EOS
                    videoEncoder.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    ////enqueue bytebuffer
                    videoEncoder.queueInputBuffer(inputBufferIndex, 0, input.length,
                            presentationTimeUs, 0);
                }
            } else {
                //Log.d(TAG, "输入缓冲区索引小于零");
            }
        }

        sendToMediaMuxer();
        //get outputByteBuffer
        //take data from outputByteBuffer
        //send to mediamuxer
    }

    public void sendToMediaMuxer() {
        if (videoEncoder == null) return;

        final ByteBuffer[] outputBuffers = videoEncoder.getOutputBuffers();

        final int outputBufferIndex = videoEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
        if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            muxer.addVideoEncoder(this);
            muxer.startMuxing();
        }
        if (outputBufferIndex >= 0) {
            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                // You shoud set output format to muxer here when you target Android4.3 or less
                // but MediaCodec#getOutputFormat can not call here(because
                // INFO_OUTPUT_FORMAT_CHANGED don't come yet)
                // therefor we should expand and prepare output format from buffer data.
                // This sample is for API>=18(>=Android 4.3), just ignore this flag here
                bufferInfo.size = 0;
            }
            final ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
            muxer.muxVideo(outputBuffer, bufferInfo);
            videoEncoder.releaseOutputBuffer(outputBufferIndex, false);
        } else {
            //Log.d(TAG, "输入缓冲区索引小于零");
        }

    }

    public MediaCodec getEncoder() {
        return videoEncoder;
    }

    public static VideoEncoder getInstance() {
        return instance;
    }

    public long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs) {
            result = (prevOutputPTSUs - result) + result;
        }
        prevOutputPTSUs = result;
        return result;
    }

    private void NV21ToNV12(byte[] nv21, byte[] nv12, int width, int height) {
        if (nv21 == null || nv12 == null) return;
        int framesize = width * height;
        int i = 0, j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for (i = 0; i < framesize; i++) {
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j - 1] = nv21[j + framesize];
        }
        for (j = 0; j < framesize / 2; j += 2) {
            nv12[framesize + j] = nv21[j + framesize - 1];
        }
    }

    // async style mediacodec >21 and polling based for <21
}
