
package com.google.android.cameraview.encoder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoEncoder {
    //    private static final String TAG = VideoEncoder.class.getSimpleName();
    private static final String TAG = "VideoEncoder";

    private static final String MIME_TYPE = "video/avc";
    private static final int DEFAULT_FRAME_RATE = 24;
    private static final int DEFAULT_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;

    private static final int TIMEOUT_US = 10000;

    private MediaCodec mVideoCodec;
    private MediaFormat mOutputFormat;
    private int mWidth;
    private int mHeight;

    private boolean isEncoding;

    private MediaCodec.BufferInfo mBufferInfo;

    private MediaMuxerWrapper mMediaMuxer;

    private long prevOutputPTSUs = 0;

    public VideoEncoder(MediaMuxerWrapper muxer, int width, int height) {
        this(muxer, width, height, DEFAULT_FRAME_RATE, getDefaultVideoBitRate(width, height));
    }

    public VideoEncoder(MediaMuxerWrapper muxer, int width, int height, int frameRate,
            int bitRate) {
        this(muxer, width, height, frameRate, bitRate, DEFAULT_COLOR_FORMAT);
    }

    public VideoEncoder(MediaMuxerWrapper muxer, int width, int height, int frameRate, int bitRate,
            int colorFormat) {
        this.mWidth = width;
        this.mHeight = height;
        this.mBufferInfo = new MediaCodec.BufferInfo();
        this.mMediaMuxer = muxer;

        try {
            mVideoCodec = MediaCodec.createEncoderByType(MIME_TYPE);

            mOutputFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
            mOutputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            mOutputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            mOutputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
            mOutputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            mVideoCodec.configure(mOutputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        Log.d(TAG, "视频开始编码-->" + Thread.currentThread().getName());
        mVideoCodec.start();
        isEncoding = true;
    }

    public void stop() {
        Log.d(TAG, "视频停止编码-->" + Thread.currentThread().getName());
        mVideoCodec.stop();
        mMediaMuxer.stopMuxing();
        isEncoding = false;
    }

    public void encode(byte[] input, int length, long presentationTimeUs) {
        //get input buffer
        if (isEncoding) {

            byte[] yuv420sp = new byte[mWidth * mHeight * 3 / 2];
            NV21ToNV12(input, yuv420sp, mWidth, mHeight);
            input = yuv420sp;

            final ByteBuffer[] inputBuffers = mVideoCodec.getInputBuffers();

            //dequeue input buffer
            final int inputBufferIndex = mVideoCodec.dequeueInputBuffer(TIMEOUT_US);
            if (inputBufferIndex >= 0) {
                final ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();

                //copy ByteBuffer to input buffer
                inputBuffer.put(input);

                if (length <= 0) {
                    ////enqueue bytebuffer with EOS
                    mVideoCodec.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    ////enqueue bytebuffer
                    mVideoCodec.queueInputBuffer(inputBufferIndex, 0, input.length,
                            presentationTimeUs, 0);
                }
            } else {
                //Log.d(TAG, "输入缓冲区索引小于零");
            }

            sendToMediaMuxer();
        }
    }

    public void sendToMediaMuxer() {
        if (mVideoCodec == null) return;

        final ByteBuffer[] outputBuffers = mVideoCodec.getOutputBuffers();

        final int outputBufferIndex = mVideoCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_US);

        if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            mMediaMuxer.addVideoEncoder(this);
            mMediaMuxer.startMuxing();
        } else if (outputBufferIndex >= 0) {
            if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                // You shoud set output format to muxer here when you target Android4.3 or less
                // but MediaCodec#getOutputFormat can not call here(because
                // INFO_OUTPUT_FORMAT_CHANGED don't come yet)
                // therefor we should expand and prepare output format from buffer data.
                // This sample is for API>=18(>=Android 4.3), just ignore this flag here
                mBufferInfo.size = 0;
            }
            final ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
            mMediaMuxer.muxVideo(outputBuffer, mBufferInfo);
            mVideoCodec.releaseOutputBuffer(outputBufferIndex, false);
        }
    }

    public MediaCodec getEncoder() {
        return mVideoCodec;
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

    /**
     * 获取默认比特率
     */
    public static int getDefaultVideoBitRate(int width, int height) {
        return width * height * 3;
    }
}
