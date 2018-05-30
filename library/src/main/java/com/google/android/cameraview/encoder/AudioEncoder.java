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

import android.media.AudioFormat;
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
 * @description: 音频编、解码类
 */
public class AudioEncoder {
    //    private static final String TAG = AudioEncoder.class.getSimpleName();
    private static final String TAG = "AudioEncoder";

    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int SAMPLE_RATE = 44100;
    private static final int BIT_RATE = 64000;
    private static final int TIMEOUT_US = 10000;
    private MediaCodec audioEncoder;
    private MediaFormat audioFormat;

    private boolean isEncoding;

    private MediaCodec.BufferInfo bufferInfo;

    private MediaMuxerWrapper muxer;

    private long prevOutputPTSUs = 0;

    public AudioEncoder(MediaMuxerWrapper mux) {
        muxer = mux;
        bufferInfo = new MediaCodec.BufferInfo();

        try {
            audioEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        audioFormat = MediaFormat.createAudioFormat(MIME_TYPE,
                SAMPLE_RATE,
                1);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        //optional stuff
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);

        audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    public void start() {
        Log.d(TAG, "音频开始编码--->" + Thread.currentThread().getName());
        audioEncoder.start();
        isEncoding = true;
    }

    public void stop() {
        Log.d(TAG, "音频停止编码编码--->" + Thread.currentThread().getName());
        audioEncoder.stop();
//        muxer.stopMuxing();
        isEncoding = false;
    }

    public void encode(ByteBuffer rawBuffer, int length, long presentationTimeUs) {
        //get input buffer
        if (isEncoding) {
            final ByteBuffer[] inputBuffers = audioEncoder.getInputBuffers();

            //dequeue input buffer
            final int inputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_US);
            if (inputBufferIndex >= 0) {
                final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();

                if (rawBuffer != null) {
                    //copy ByteBuffer to input buffer
                    inputBuffer.put(rawBuffer);
                }
                if (length <= 0) {
                    ////enqueue bytebuffer with EOS
                    audioEncoder.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    ////enqueue bytebuffer
                    audioEncoder.queueInputBuffer(inputBufferIndex, 0, length, presentationTimeUs,
                            0);
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
        if (audioEncoder == null) return;

        final ByteBuffer[] outputBuffers = audioEncoder.getOutputBuffers();

        final int outputBufferIndex = audioEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
        if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            muxer.addAudioEncoder(this);
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
            muxer.muxAudio(outputBuffer, bufferInfo);
            audioEncoder.releaseOutputBuffer(outputBufferIndex, false);
        } else {
            //Log.d(TAG, "输入缓冲区索引小于零");
        }

    }

    public MediaCodec getEncoder() {
        return audioEncoder;
    }

    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs) {
            result = (prevOutputPTSUs - result) + result;
        }
        prevOutputPTSUs = result;
        return result;
    }

    // async style mediacodec >21 and polling based for <21
}
