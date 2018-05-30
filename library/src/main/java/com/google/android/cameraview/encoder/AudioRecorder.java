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
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * @author: sq
 * @date: 2017/7/26
 * @corporation: 深圳市思迪信息科技有限公司
 * @description: 音频录制类
 * a wrapper around android's AudioRecord class
 * meant to record audio from microphone input
 * to be operated from the a HandlerThread (MediaRecorderThread)
 */
public class AudioRecorder {
    //    private static final String TAG = AudioRecorder.class.getSimpleName();
    private static final String TAG = "AudioRecorder";
    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private static final int SAMPLES_PER_FRAME = 1024;
    private static final int FRAMES_PER_BUFFER = 20;

    private int bufferSizeInBytes;

    private AudioRecord mAudioRecord;

    private AudioEncoder audioEncoder;

    private boolean isRecording;

    public AudioRecorder(AudioEncoder encoder) {
        audioEncoder = encoder;
        bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT);

        //todo understand this logic
        int bufferSize = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
        if (bufferSize < bufferSizeInBytes) {
            bufferSize = ((bufferSizeInBytes / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
        }

        mAudioRecord = new AudioRecord(AUDIO_SOURCE,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSizeInBytes);
        isRecording = false;
    }

    public void start() {
        mAudioRecord.startRecording();
        isRecording = true;
    }

    public void record() {
        final ByteBuffer bytebuffer = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME);
        int bufferReadResult;

        while (isRecording) {
            bytebuffer.clear();
            bufferReadResult = mAudioRecord.read(bytebuffer, SAMPLES_PER_FRAME);

            if (bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION || bufferReadResult ==
                    AudioRecord.ERROR_BAD_VALUE) {
                Log.d(TAG, "音频录制数据读取失败");
            } else if (bufferReadResult >= 0) {
                //Log.d(TAG, "bytes read "+bufferReadResult);
                // todo send this byte array to an audio encoder
                bytebuffer.position(bufferReadResult);
                bytebuffer.flip();
                byte[] bytes = new byte[bytebuffer.remaining()];
                bytebuffer.get(bytes);
                String packet = new String(bytes);
                //Log.d(TAG, packet);

                bytebuffer.position(bufferReadResult);
                bytebuffer.flip();
                audioEncoder.encode(bytebuffer, bufferReadResult, audioEncoder.getPTSUs());
            }
        }
    }

    public void sendEOS() {
        final ByteBuffer bytebuffer = ByteBuffer.allocateDirect(SAMPLES_PER_FRAME);
        int bufferReadResult;

        bufferReadResult = mAudioRecord.read(bytebuffer, SAMPLES_PER_FRAME);
        audioEncoder.encode(bytebuffer, 0, audioEncoder.getPTSUs());
    }

    public void stopRecording() {
        mAudioRecord.stop();
        mAudioRecord.release();
        sendEOS();
        //mAudioRecord = null;
        audioEncoder.stop();
        //possibly send an EOS to encoder.
    }

    public void setIsRecordingFalse() {
        isRecording = false;
    }


}
