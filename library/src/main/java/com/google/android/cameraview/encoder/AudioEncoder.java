
package com.google.android.cameraview.encoder;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioEncoder {
    private static final String TAG = "AudioEncoder";

    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int DEFAULT_CHANNEL_COUNT = 1;
    private static final int DEFAULT_SAMPLE_RATE = 44100;
    private static final int DEFAULT_BIT_RATE = 64000;
    private static final int TIMEOUT_US = 10000;

    private MediaCodec mAudioEncoder;
    private MediaFormat mAudioFormat;

    private boolean isEncoding;

    private MediaCodec.BufferInfo bufferInfo;

    private MediaMuxerWrapper muxer;

    private long prevOutputPTSUs = 0;

    public AudioEncoder(MediaMuxerWrapper mux) {
        this(mux, DEFAULT_SAMPLE_RATE, DEFAULT_BIT_RATE);
    }

    public AudioEncoder(MediaMuxerWrapper mux, int sampleRate, int bitRate) {
        muxer = mux;
        bufferInfo = new MediaCodec.BufferInfo();

        try {
            mAudioEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mAudioFormat = MediaFormat.createAudioFormat(MIME_TYPE,
                sampleRate,
                DEFAULT_CHANNEL_COUNT);
        mAudioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
        mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);

        mAudioEncoder.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    public void start() {
        Log.d(TAG, "音频开始编码--->" + Thread.currentThread().getName());
        mAudioEncoder.start();
        isEncoding = true;
    }

    public void stop() {
        Log.d(TAG, "音频停止编码编码--->" + Thread.currentThread().getName());
        mAudioEncoder.stop();
//        muxer.stopMuxing();
        isEncoding = false;
    }

    public void encode(ByteBuffer rawBuffer, int length, long presentationTimeUs) {
        //get input buffer
        if (isEncoding) {
            final ByteBuffer[] inputBuffers = mAudioEncoder.getInputBuffers();

            //dequeue input buffer
            final int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(TIMEOUT_US);
            if (inputBufferIndex >= 0) {
                final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();

                if (rawBuffer != null) {
                    //copy ByteBuffer to input buffer
                    inputBuffer.put(rawBuffer);
                }
                if (length <= 0) {
                    ////enqueue bytebuffer with EOS
                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    ////enqueue bytebuffer
                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, length, presentationTimeUs,
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
        if (mAudioEncoder == null) return;

        final ByteBuffer[] outputBuffers = mAudioEncoder.getOutputBuffers();

        final int outputBufferIndex = mAudioEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
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
            mAudioEncoder.releaseOutputBuffer(outputBufferIndex, false);
        } else {
            //Log.d(TAG, "输入缓冲区索引小于零");
        }

    }

    public MediaCodec getEncoder() {
        return mAudioEncoder;
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
