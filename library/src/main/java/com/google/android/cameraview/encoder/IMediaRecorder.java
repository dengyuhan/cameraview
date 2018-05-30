
package com.google.android.cameraview.encoder;

import android.media.CamcorderProfile;

/**
 * @author dengyuhan
 *         created 2018/5/30 15:52
 */
public interface IMediaRecorder {

    void setCamcorderProfile(CamcorderProfile profile, boolean recordAudio);

    void startRecording();

    void stopRecording();

    void release();

    boolean isRecording();
}
