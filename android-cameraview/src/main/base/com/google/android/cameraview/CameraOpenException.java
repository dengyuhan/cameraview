package com.google.android.cameraview;

/**
 * @author dengyuhan
 * created 2019/4/29 19:39
 */
public class CameraOpenException extends RuntimeException {
    public CameraOpenException() {
    }

    public CameraOpenException(String message) {
        super(message);
    }

    public CameraOpenException(String message, Throwable cause) {
        super(message, cause);
    }

    public CameraOpenException(Throwable cause) {
        super(cause);
    }

}
