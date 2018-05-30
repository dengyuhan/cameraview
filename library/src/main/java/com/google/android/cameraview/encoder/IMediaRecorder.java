
package com.google.android.cameraview.encoder;

import android.media.CamcorderProfile;

/**
 * @author dengyuhan
 *         created 2018/5/30 15:52
 */
public interface IMediaRecorder {
    void setupMediaRecorder(String outputPath, boolean recordAudio, CamcorderProfile profile);

    void startRecording();

    void stopRecording();

    void release();

    boolean isRecording();
}
