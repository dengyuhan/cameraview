
package com.google.android.cameraview.encoder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * 音频录制
 */
public class AudioRecorder {
    private static final String TAG = "AudioRecorder";

    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    private static final int DEFAULT_SAMPLE_RATE = 44100;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private static final int SAMPLES_PER_FRAME = 1024;
    private static final int FRAMES_PER_BUFFER = 20;

    private int bufferSizeInBytes;

    private AudioRecord mAudioRecord;

    private AudioEncoder audioEncoder;

    private boolean isRecording;

    public AudioRecorder(AudioEncoder encoder) {
        this(encoder, DEFAULT_SAMPLE_RATE);
    }

    public AudioRecorder(AudioEncoder encoder, int sampleRate) {
        audioEncoder = encoder;
        bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRate,
                CHANNEL_CONFIG,
                AUDIO_FORMAT);

        /*//todo understand this logic
        int bufferSize = SAMPLES_PER_FRAME * FRAMES_PER_BUFFER;
        if (bufferSize < bufferSizeInBytes) {
            bufferSize = ((bufferSizeInBytes / SAMPLES_PER_FRAME) + 1) * SAMPLES_PER_FRAME * 2;
        }*/

        mAudioRecord = new AudioRecord(AUDIO_SOURCE,
                sampleRate,
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
        int bufferReadResult = mAudioRecord.read(bytebuffer, SAMPLES_PER_FRAME);
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
