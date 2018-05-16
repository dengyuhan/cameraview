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

package com.google.android.cameraview.demo;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.ColorDrawable;
import android.media.CamcorderProfile;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.cameraview.AspectRatio;
import com.google.android.cameraview.CameraView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.RuntimePermissions;


/**
 * This demo app saves the taken picture to a constant file.
 * $ adb pull /sdcard/Android/data/com.google.android.cameraview.demo/files/Pictures/picture.jpg
 */
@RuntimePermissions
public class MainActivity extends AppCompatActivity implements
        AspectRatioFragment.Listener {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private static final String FRAGMENT_DIALOG = "dialog";

    private static final int[] FLASH_OPTIONS = {
            CameraView.FLASH_AUTO,
            CameraView.FLASH_OFF,
            CameraView.FLASH_ON,
    };

    private static final int[] FLASH_ICONS = {
            R.drawable.ic_flash_auto,
            R.drawable.ic_flash_off,
            R.drawable.ic_flash_on,
    };

    private static final int[] FLASH_TITLES = {
            R.string.flash_auto,
            R.string.flash_off,
            R.string.flash_on,
    };

    private int mCurrentFlash;

    CameraView mCameraView;
    TextView mRecordingTimeView;
    ImageView mChangeModeView;
    ImageView mShutterView;

    private Disposable mRecordingDisposable;

    private boolean mModeTakePicture = true;//true拍照,false录像

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.take_picture:
                    if (mCameraView != null) {
                        if (mModeTakePicture) {
                            mCameraView.takePicture();
                        } else {
                            if (mCameraView.isRecording()) {
                                stopRecording();
                            } else {
                                MainActivityPermissionsDispatcher.onGrantedRecordAudioWithCheck(
                                        MainActivity.this);
                            }
                        }
                    }
                    break;
                case R.id.change_mode:
                    if (mCameraView != null) {
                        changeCameraMode(!mModeTakePicture);
                    }
                    break;
            }
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCameraView = (CameraView) findViewById(R.id.camera);
        if (mCameraView != null) {
            mCameraView.setAspectRatio(AspectRatio.of(16, 9));
            mCameraView.setAutoFocus(true);
            mCameraView.addCallback(mCallback);
        }
        MainActivityPermissionsDispatcher.onGrantedCameraWithCheck(this);

        mRecordingTimeView = findViewById(R.id.tv_recording_time);
        mChangeModeView = findViewById(R.id.change_mode);
        mChangeModeView.setOnClickListener(mOnClickListener);
        mShutterView = findViewById(R.id.take_picture);
        mShutterView.setOnClickListener(mOnClickListener);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainActivityPermissionsDispatcher.onGrantedCameraWithCheck(this);
    }

    @Override
    protected void onPause() {
        mCameraView.stop();
        super.onPause();
    }

    @NeedsPermission(Manifest.permission.CAMERA)
    void onGrantedCamera() {
        mCameraView.start();
    }

    @OnPermissionDenied(Manifest.permission.CAMERA)
    void onDeniedCamera() {
        Toast.makeText(this, R.string.camera_permission_not_granted,
                Toast.LENGTH_SHORT).show();
    }

    @NeedsPermission({Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void onGrantedRecordAudio() {
        Toast.makeText(MainActivity.this, "开始录像",
                Toast.LENGTH_SHORT).show();
        File file = new File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "video.mp4");
        startRecordingTimer();
        mCameraView.record(file.getAbsolutePath(), -1, -1, true,
                CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
    }

    @OnPermissionDenied(Manifest.permission.RECORD_AUDIO)
    void onDeniedRecordAudio() {
        Toast.makeText(this, R.string.record_audio_permission_not_granted,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.aspect_ratio:
                FragmentManager fragmentManager = getSupportFragmentManager();
                if (mCameraView != null
                        && fragmentManager.findFragmentByTag(FRAGMENT_DIALOG) == null) {
                    final Set<AspectRatio> ratios = mCameraView.getSupportedAspectRatios();
                    final AspectRatio currentRatio = mCameraView.getAspectRatio();
                    AspectRatioFragment.newInstance(ratios, currentRatio)
                            .show(fragmentManager, FRAGMENT_DIALOG);
                }
                return true;
            case R.id.switch_flash:
                if (mCameraView != null) {
                    mCurrentFlash = (mCurrentFlash + 1) % FLASH_OPTIONS.length;
                    item.setTitle(FLASH_TITLES[mCurrentFlash]);
                    item.setIcon(FLASH_ICONS[mCurrentFlash]);
                    mCameraView.setFlash(FLASH_OPTIONS[mCurrentFlash]);
                }
                return true;
            case R.id.switch_camera:
                if (mCameraView != null) {
                    int facing = mCameraView.getFacing();
                    mCameraView.setFacing(facing == CameraView.FACING_FRONT ?
                            CameraView.FACING_BACK : CameraView.FACING_FRONT);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onAspectRatioSelected(@NonNull AspectRatio ratio) {
        if (mCameraView != null) {
            Toast.makeText(this, ratio.toString(), Toast.LENGTH_SHORT).show();
            mCameraView.setAspectRatio(ratio);
        }
    }

    private Observable<File> pictureTakenObservable(final byte[] data) {
        Toast.makeText(this, R.string.picture_taken, Toast.LENGTH_SHORT)
                .show();
        return Observable.create(new ObservableOnSubscribe<File>() {
            @Override
            public void subscribe(ObservableEmitter<File> emitter) throws Exception {
                File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "picture.jpg");
                OutputStream os = null;
                try {
                    os = new FileOutputStream(file);
                    os.write(data);
                    os.close();
                    emitter.onNext(file);
                } catch (IOException e) {
                    Log.w(TAG, "Cannot write to " + file, e);
                    emitter.onError(e);
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e) {
                            // Ignore
                            emitter.onError(e);
                        }
                    }
                }
                emitter.onComplete();
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }


    private void asyncPictureTakenObservable(final byte[] data) {
        pictureTakenObservable(data)
                .subscribe(
                        new Consumer<File>() {
                            @Override
                            public void accept(File file) throws Exception {
                                Toast.makeText(MainActivity.this, file.getAbsolutePath(),
                                        Toast.LENGTH_SHORT).show();
                                MediaScannerConnection.scanFile(MainActivity.this,
                                        new String[]{file.getAbsolutePath()}, new String[]{""},
                                        null);

                            }
                        },
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) throws Exception {
                                Toast.makeText(MainActivity.this, R.string.picture_taken_failed,
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
    }

    public void startRecordingTimer() {
        mRecordingTimeView.setVisibility(View.VISIBLE);
        final SimpleDateFormat format = new SimpleDateFormat("mm:ss");
        stopRecordingTimer();
        mRecordingDisposable = Observable.interval(0, 1,
                TimeUnit.SECONDS)
                .map(new Function<Long, Long>() {
                    @Override
                    public Long apply(Long aLong) throws Exception {
                        return aLong + 1;
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long seconds) throws Exception {
                        String time = format.format(seconds * 1000);
                        mRecordingTimeView.setText(time);
                    }
                });
    }

    private void stopRecording() {
        Toast.makeText(MainActivity.this, "停止录像",
                Toast.LENGTH_SHORT).show();
        mCameraView.stopRecording();
        stopRecordingTimer();
        mRecordingTimeView.setVisibility(View.GONE);
    }

    private void stopRecordingTimer() {
        if (mRecordingDisposable != null && !mRecordingDisposable.isDisposed()) {
            mRecordingDisposable.dispose();
        }
    }

    private CameraView.Callback mCallback
            = new CameraView.Callback() {

        @Override
        public void onCameraOpened(CameraView cameraView) {
            Log.d(TAG, "onCameraOpened");
        }

        @Override
        public void onCameraClosed(CameraView cameraView) {
            Log.d(TAG, "onCameraClosed");
        }

        @Override
        public void onPictureTaken(CameraView cameraView, final byte[] data) {
            Log.d(TAG, "onPictureTaken " + data.length);
            asyncPictureTakenObservable(data);
        }

    };


    private void changeCameraMode(boolean takePicture) {
        mModeTakePicture = takePicture;

        final int icon =
                mModeTakePicture ? R.drawable.icon_recording : R.drawable.icon_take_picture;
        mChangeModeView.setImageResource(icon);

        final ColorDrawable drawable = (ColorDrawable) mShutterView.getDrawable();
        int colorRes = mModeTakePicture ? R.color.whiteTranslucent : R.color.redTranslucent;
        final int targetColor = getResources().getColor(colorRes);
        ValueAnimator colorAnimator = ValueAnimator.ofInt(drawable.getColor(), targetColor);
        colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int color = (int) animation.getAnimatedValue();
                mShutterView.setImageDrawable(new ColorDrawable(color));
            }
        });
        colorAnimator.setEvaluator(new ArgbEvaluator());
        colorAnimator.setDuration(500);
        colorAnimator.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // NOTE: delegate the permission handling to generated method
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode,
                grantResults);
    }

}
