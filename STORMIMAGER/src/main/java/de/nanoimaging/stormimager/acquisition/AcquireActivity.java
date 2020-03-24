package de.nanoimaging.stormimager.acquisition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import de.nanoimaging.stormimager.R;

import static de.nanoimaging.stormimager.acquisition.CaptureRequestEx.HUAWEI_DUAL_SENSOR_MODE;
import static org.opencv.core.Core.norm;
import static org.opencv.imgcodecs.Imgcodecs.imwrite;

/**
 * Created by Bene on 26.09.2015.
 */

public class AcquireActivity extends Activity implements FragmentCompat.OnRequestPermissionsResultCallback, AcquireSettings.NoticeDialogListener {

    /**
     * MQTT related stuff
     */
    MqttAndroidClient mqttAndroidClient;

    String myIPAddress = "192.168.43.88";             // IP for the MQTT Broker, format tcp://ipaddress
    public static final String topic_lens_z = "lens/right/z";
    public static final String topic_lens_x = "lens/right/x";
    public static final String topic_laser = "laser/red";
    public static final String topic_lens_sofi_z = "lens/right/sofi/z";
    public static final String topic_lens_sofi_x = "lens/right/sofi/x";
    public static final String topic_state = "state";
    String STATE_CALIBRATION = "state_calib";       // STate signal sent to ESP for light signal
    String STATE_WAIT = "state_wait";               // STate signal sent to ESP for light signal
    String STATE_RECORD = "state_record";           // STate signal sent to ESP for light signal

    final String MQTT_USER = "username";
    final String MQTT_PASS = "pi";
    final String MQTT_CLIENTID = "STORMimager";

    SharedPreferences.Editor editor = null;

    // Global MQTT Values
    int MQTT_SLEEP = 250;                       // wait until next thing should be excuted

    /**
     * GUI related stuff
     */
    public DialogFragment settingsDialogFragment;       // For the pop-up window for external settings
    String TAG = "STORMimager_AcquireActivity";         // TAG for the APP

    // Save settings for later
    private final String PREFERENCE_FILE_KEY = "myAppPreference";

    /**
     * Whether the app is recording video now
     */
    public boolean mIsRecordingVideo;                   // State if camera is recording
    public boolean isCameraBusy = false;                // State if camera is busy
    boolean is_measurement = false;                     // State if measurement is performed
    boolean is_findcoupling = false;                    // State if coupling is performed

    // Camera parameters
    String global_isoval = "0";                         // global iso-value
    String global_expval = "0";                         // global exposure time in ms
    private String[] isovalues;                         // array to store available iso values
    private String[] texpvalues;                        // array to store available exposure times
    int val_iso_index = 3;                              // Slider value for
    int val_texp_index = 10;

    // Acquisition parameters
    int val_period_measurement = 6 * 10;                // time between measurements in seconds
    int val_duration_measurement = 5;                   // duration for one measurement in seconds
    int val_nperiods_calibration = 10 * 10;             // number of measurements for next recalibraiont

    // settings for coupling
    double val_mean_max = 0;                            // for coupling intensity
    int i_search_maxintensity = 0;                      // global counter for number of search steps
    int val_lens_x_maxintensity = 0;                    // lens-position for maximum intensity
    int val_lens_x_global_old = 0;                      // last lens position before optimization
    int ROI_SIZE = 512;                                 // region which gets cropped to measure the coupling efficiencey
    boolean is_findcoupling_coarse = true;              // State if coupling is in fine mode
    boolean is_findcoupling_fine = false;               // State if coupling is in coarse mode

    // File IO parameters
    File myVideoFileName = new File("");
    boolean isRaw = false;
    int mImageFormat = ImageFormat.JPEG;
    ByteBuffer buffer = null; // for the processing
    Bitmap global_bitmap = null;
    // (default) global file paths
    String mypath_measurements = Environment.getExternalStorageDirectory() + "/STORMimager/";
    String myfullpath_measurements = mypath_measurements;
    private MediaRecorder mMediaRecorder;               // MediaRecorder
    private File mCurrentFile;
    int global_framerate = 20;
    int global_cameraquality = CamcorderProfile.QUALITY_1080P;

    /**
     * HARDWARE Settings for MQTT related values
     */
    int PWM_RES = (int) (Math.pow(2, 15)) - 1;          // bitrate of the PWM signal 15 bit

    int val_lens_x_global = 0;                          // global position for the x-lens
    int val_lens_z_global = 0;                          // global position for the z-lens
    int val_laser_red_global = 0;                       // global value for the laser

    int val_sofi_amplitude_z = 20; // amplitude of the lens in each periode
    int val_sofi_amplitude_x = 20; // amplitude of the lens in each periode

    boolean is_SOFI_x = false;
    boolean is_SOFI_z = false;

    /*
     GUI-Settings
     */
    ToggleButton acquireSettingsSOFIToggle;
    private Button btn_x_lens_plus;
    private Button btn_x_lens_minus;
    private Button btnStartMeasurement;
    private Button btnStopMeasurement;
    private Button btnSetup;
    private Button btnCapture;

    private SeekBar seekbar_iso;
    private SeekBar seekbar_shutter;
    private SeekBar seekBarLensX;
    private SeekBar seekBarLensZ;
    private SeekBar seekBarLaser;
    private TextView textView_iso;
    private TextView textView_shutter;
    private TextView textViewLensX;
    private TextView textViewLensZ;
    private TextView textViewLaser;
    private TextView textViewGuiText;

    private ProgressBar acquireProgressBar;



    /**
     * CAMERA-Related stuff
     */
    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    /**
     * Request code for camera permissions.
     */
    private static final int REQUEST_CAMERA_PERMISSIONS = 1;
    /**
     * Permissions required to take a picture.
     */
    private static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
    };
    /**
     * Timeout for the pre-capture sequence.
     */
    private static final long PRECAPTURE_TIMEOUT_MS = 1000;
    /**
     * Tolerance when comparing aspect ratios.
     */
    private static final double ASPECT_RATIO_TOLERANCE = 0.005;
    //private CaptureRequest.Builder mPreviewRequestBuilder;
    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;
    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    /**
     * Camera state: Device is closed.
     */
    private static final int STATE_CLOSED = 0;
    /**
     * Camera state: Device is opened, but is not capturing.
     */
    private static final int STATE_OPENED = 1;
    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 2;
    /**
     * Camera state: Waiting for 3A convergence before capturing a photo.
     */
    private static final int STATE_WAITING_FOR_3A_CONVERGENCE = 3;

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
    }

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    /**
     * A counter for tracking corresponding {@link CaptureRequest}s and {@link CaptureResult}s
     * across the {@link CameraCaptureSession} capture callbacks.
     */
    private final AtomicInteger mRequestCounter = new AtomicInteger();
    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);
    /**
     * A lock protecting camera state.
     */
    private final Object mCameraStateLock = new Object();
    /**
     * Request ID to {@link ImageSaver.ImageSaverBuilder} mapping for in-progress RAW captures.
     */
    private final TreeMap<Integer, ImageSaver.ImageSaverBuilder> mRawResultQueue = new TreeMap<>();
    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnRawImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image == null) return;

            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            int nRowStride = image.getPlanes()[0].getRowStride();
            int nPixelStride = image.getPlanes()[0].getPixelStride();
            image.close();
            try {
                String output = "";
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    };
    /**
     * A {@link Handler} for showing {@link Toast}s on the UI thread.
     */
    private final Handler mMessageHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Toast.makeText(AcquireActivity.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
        }
    };


    private Image mRawLastReceivedImage = null;
    ImageReader.OnImageAvailableListener mImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        Log.e(TAG, "Null image returned YUV1");
                        return;
                    }

                    if (mRawLastReceivedImage != null) {
                        mRawLastReceivedImage.close();
                    }

                    Image.Plane plane0 = img.getPlanes()[0];
                    buffer = plane0.getBuffer();

                    final byte[] DateBuf;
                    if (buffer.hasArray()) {
                        DateBuf = buffer.array();
                    } else {
                        DateBuf = new byte[buffer.capacity()];
                        buffer.get(DateBuf);
                    }

                    mRawLastReceivedImage = img;
                    Log.d(TAG, "mImageListener RECIEVE img!!!");
                }
            };
    /**
     * An {@link OrientationEventListener} used to determine when device rotation has occurred.
     * This is mainly necessary for when the device is rotated by 180 degrees, in which case
     * onCreate or onConfigurationChanged is not called as the view dimensions remain the same,
     * but the orientation of the has changed, and thus the preview rotation must be updated.
     */
    private OrientationEventListener mOrientationListener;
    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;
    /**
     * An additional thread for running tasks that shouldn't block the UI.  This is used for all
     * callbacks from the {@link CameraDevice} and {@link CameraCaptureSession}s.
     */
    private HandlerThread mBackgroundThread;
    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;
    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;
    /**
     * A reference to the open {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;
    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;
    /**
     * The {@link CameraCharacteristics} for the currently configured camera device.
     */
    private CameraCharacteristics mCharacteristics;

    // *********************************************************************************************
    // State protected by mCameraStateLock.
    //
    // The following state is used across both the UI and background threads.  Methods with "Locked"
    // in the name expect mCameraStateLock to be held while calling.
    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;
    /**
     * A reference counted holder wrapping the {@link ImageReader} that handles RAW image captures.
     * This is used to allow us to clean up the {@link ImageReader} when all background tasks using
     * its {@link Image}s have completed.
     */
    private RefCountedAutoCloseable<ImageReader> mImageReader;
    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * RAW image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            dequeueAndSaveImage(mRawResultQueue, mImageReader);
        }

    };
    private RefCountedAutoCloseable<ImageReader> mJPEGImageReader;
    /**
     * Whether or not the currently configured camera device is fixed-focus.
     */
    private boolean mNoAFRun = true;
    /**
     * Number of pending user requests to capture a photo.
     */
    private int mPendingUserCaptures = 0;
    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;
    /**
     * The state of the camera device.
     *
     * @see #mPreCaptureCallback
     */
    private int mState = STATE_CLOSED;
    /**
     * Timer to use with pre-capture sequence to ensure a timely capture if 3A convergence is
     * taking too long.
     */
    private long mCaptureTimer;
    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events for the preview and
     * pre-capture sequence.
     */
    private CameraCaptureSession.CaptureCallback mPreCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        private void process(CaptureResult result) {
            synchronized (mCameraStateLock) {
                switch (mState) {
                    case STATE_PREVIEW: {
                        // We have nothing to do when the camera preview is running normally.
                        break;
                    }
                    case STATE_WAITING_FOR_3A_CONVERGENCE: {
                        boolean readyToCapture = true;
                        if (!mNoAFRun) {
                            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                            if (afState == null) {
                                break;
                            }

                            // If auto-focus has reached locked state, we are ready to capture
                            if (true) {
                                readyToCapture = true;
                            } else {
                                readyToCapture =
                                        (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                                afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
                            }
                        }

                        // If we are running on an non-legacy device, we should also wait until
                        // auto-exposure and auto-white-balance have converged as well before
                        // taking a picture.
                        if (!isLegacyLocked()) {
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            Integer awbState = result.get(CaptureResult.CONTROL_AWB_STATE);
                            if (aeState == null || awbState == null) {
                                break;
                            }

                            readyToCapture = readyToCapture &&
                                    aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED &&
                                    awbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED;
                        }

                        // If we haven't finished the pre-capture sequence but have hit our maximum
                        // wait timeout, too bad! Begin capture anyway.
                        if (!readyToCapture && hitTimeoutLocked()) {
                            Log.w(TAG, "Timed out waiting for pre-capture sequence to complete.");
                            readyToCapture = true;
                        }

                        if (readyToCapture && mPendingUserCaptures > 0) {
                            // Capture once for each user tap of the "Picture" button.
                            while (mPendingUserCaptures > 0) {
                                captureStillPictureLocked();
                                mPendingUserCaptures--;
                            }
                            // After this, the camera will go back to the normal state of preview.
                            mState = STATE_PREVIEW;
                        }
                    }
                }
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            process(result);
        }

    };
    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events of a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            synchronized (mCameraStateLock) {
                mPreviewSize = null;
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {

            if (is_findcoupling & !isCameraBusy) {
                //Log.i(TAG, "Lens Calibration in progress");
                global_bitmap = mTextureView.getBitmap();
                global_bitmap = Bitmap.createBitmap(global_bitmap, 0, 0, global_bitmap.getWidth(), global_bitmap.getHeight(), mTextureView.getTransform(null), true);

                // START THREAD AND ALIGN THE LENS
                if (is_findcoupling_coarse) {
                    run_calibration_thread_coarse thread_1 = new run_calibration_thread_coarse("CoarseThread");
                } else if (is_findcoupling_fine) {
                    run_calibration_thread_fine thread_2 = new run_calibration_thread_fine("FineThread");
                }


            }
        }
    };
    /**
     * {@link CameraDevice.StateCallback} is called when the currently active {@link CameraDevice}
     * changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here if
            // the TextureView displaying this has been set up.
            synchronized (mCameraStateLock) {
                mState = STATE_OPENED;
                mCameraOpenCloseLock.release();
                mCameraDevice = cameraDevice;

                // Start the preview session if the TextureView has been set up already.
                if (mPreviewSize != null && mTextureView.isAvailable()) {
                    createCameraPreviewSessionLocked();
                }
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            synchronized (mCameraStateLock) {
                mState = STATE_CLOSED;
                mCameraOpenCloseLock.release();
                cameraDevice.close();
                mCameraDevice = null;
            }
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            Log.e(TAG, "Received camera device error: " + error);
            synchronized (mCameraStateLock) {
                mState = STATE_CLOSED;
                mCameraOpenCloseLock.release();
                cameraDevice.close();
                mCameraDevice = null;
            }

        }

    };
    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles the still JPEG and RAW capture
     * request.
     */
    private final CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                                     long timestamp, long frameNumber) {
            String currentDateTime = generateTimestamp();
            File imageFile = null;

            // setting the filepath
            String mytimestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
            if (isRaw)
                imageFile = new File(myfullpath_measurements, mytimestamp + ".DNG");
            else imageFile = new File(myfullpath_measurements, mytimestamp + ".JPG");

            // Look up the ImageSaverBuilder for this request and update it with the file name
            // based on the capture start time.
            ImageSaver.ImageSaverBuilder rawBuilder;
            int requestId = (int) request.getTag();
            synchronized (mCameraStateLock) {
                rawBuilder = mRawResultQueue.get(requestId);
            }

            if (rawBuilder != null) rawBuilder.setFile(imageFile);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            int requestId = (int) request.getTag();
            ImageSaver.ImageSaverBuilder rawBuilder;
            StringBuilder sb = new StringBuilder();

            // Look up the ImageSaverBuilder for this request and update it with the CaptureResult
            synchronized (mCameraStateLock) {
                rawBuilder = mRawResultQueue.get(requestId);


                if (rawBuilder != null) {
                    rawBuilder.setResult(result);
                    sb.append("Saving RAW as: ");
                    sb.append(rawBuilder.getSaveLocation());
                }

                // If we have all the results necessary, save the image to a file in the background.
                handleCompletionLocked(requestId, rawBuilder, mRawResultQueue);

                finishedCaptureLocked();
            }

            showToast(sb.toString());
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                                    CaptureFailure failure) {
            int requestId = (int) request.getTag();
            synchronized (mCameraStateLock) {
                mRawResultQueue.remove(requestId);
                finishedCaptureLocked();
            }
            showToast("Capture failed!");
        }

    };

    public AcquireActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    //**********************************************************************************************

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e("Camera2Raw", "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    /**
     * Generate a string containing a formatted timestamp with the current date and time.
     *
     * @return a {@link String} representing a time.
     */
    private static String generateTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US);
        return sdf.format(new Date());
    }

    /**
     * Cleanup the given {@link OutputStream}.
     *
     * @param outputStream the stream to close.
     */
    private static void closeOutput(OutputStream outputStream) {
        if (null != outputStream) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Return true if the given array contains the given integer.
     *
     * @param modes array to check.
     * @param mode  integer to get for.
     * @return true if the array contains the given integer, otherwise false.
     */
    private static boolean contains(int[] modes, int mode) {
        if (modes == null) {
            return false;
        }
        for (int i : modes) {
            if (i == mode) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if the two given {@link Size}s have the same aspect ratio.
     *
     * @param a first {@link Size} to compare.
     * @param b second {@link Size} to compare.
     * @return true if the sizes have the same aspect ratio, otherwise false.
     */
    private static boolean checkAspectsEqual(Size a, Size b) {
        double aAspect = a.getWidth() / (double) a.getHeight();
        double bAspect = b.getWidth() / (double) b.getHeight();
        return Math.abs(aAspect - bAspect) <= ASPECT_RATIO_TOLERANCE;
    }

    /**
     * Rotation need to transform from the camera sensor orientation to the device's current
     * orientation.
     *
     * @param c                 the {@link CameraCharacteristics} to query for the camera sensor
     *                          orientation.
     * @param deviceOrientation the current device orientation relative to the native device
     *                          orientation.
     * @return the total rotation from the sensor orientation to the current device orientation.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static int sensorToDeviceRotation(CameraCharacteristics c, int deviceOrientation) {
        int sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Get device orientation in degrees
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);

        // Reverse device orientation for front-facing cameras
        if (c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            deviceOrientation = -deviceOrientation;
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (sensorOrientation + deviceOrientation + 360) % 360;
    }

    // taken from killerink/freedcam
    public static long getMilliSecondStringFromShutterString(String shuttervalue) {
        float a;
        if (shuttervalue.contains("/")) {
            String[] split = shuttervalue.split("/");
            a = Float.parseFloat(split[0]) / Float.parseFloat(split[1]) * 1000000f;
        } else
            a = Float.parseFloat(shuttervalue) * 1000000f;
        a = Math.round(a);
        return (long) a;
    }


    //**********************************************************************************************
    //  Method onCreate
    //**********************************************************************************************
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_acquire);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Initialize OpenCV using external library for now //TODO use internal!
        OpenCVLoader.initDebug();
        //OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);

        // Load previously saved settings and set GUIelements
        SharedPreferences sharedPref = this.getSharedPreferences(
                PREFERENCE_FILE_KEY, Context.MODE_PRIVATE);
        editor = sharedPref.edit();
        setGUIelements(sharedPref);

        // build the pop-up settings activity
        settingsDialogFragment = new AcquireSettings();

        // start MQTT
        initialConfig();
        if (isNetworkAvailable()) {
            Toast.makeText(this, "Connecting MQTT", Toast.LENGTH_SHORT).show();
            initialConfig();
        } else
            Toast.makeText(this, "We don't have network", Toast.LENGTH_SHORT).show();

        // *****************************************************************************************
        //  Camera STUFF
        //******************************************************************************************
        // Setup a new OrientationEventListener.  This is used to handle rotation events like a
        // 180 degree rotation that do not normally trigger a call to onCreate to do view re-layout
        // or otherwise cause the preview TextureView's size to change.
        mTextureView = (AutoFitTextureView) this.findViewById(R.id.texture);
        mOrientationListener = new OrientationEventListener(this,
                SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (mTextureView != null && mTextureView.isAvailable()) {
                    configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
                }
            }
        };

        // Create the ISO-List
        List<String> isolist = new ArrayList<>();
        isolist.add(String.valueOf(100));
        isolist.add(String.valueOf(200));
        isolist.add(String.valueOf(300));
        isolist.add(String.valueOf(1000));
        isolist.add(String.valueOf(1500));
        isolist.add(String.valueOf(2000));
        isolist.add(String.valueOf(3200));
        isolist.add(String.valueOf(6400));
        isolist.add(String.valueOf(12800));
        isovalues = new String[isolist.size()];
        isolist.toArray(isovalues);

        // Create the Shutter-List
        texpvalues = "1/100000,1/6000,1/4000,1/2000,1/1000,1/500,1/250,1/125,1/60,1/30,1/15,1/8,1/4,1/2,2,4,8,15,30,32".split(",");

        /**
        GUI-STUFF
         */
        btn_x_lens_plus = findViewById(R.id.button_x_lens_plus);
        btn_x_lens_minus = findViewById(R.id.button_x_lens_minus);
        btnCapture = findViewById(R.id.btnCapture);
        btnSetup = findViewById(R.id.btnSetup);
        btnStartMeasurement = findViewById(R.id.btnStart);
        btnStopMeasurement = findViewById(R.id.btnStop);

        textViewGuiText = findViewById(R.id.textViewGuiText);
        textView_shutter = findViewById(R.id.textView_shutter);
        textView_iso = findViewById(R.id.textView_iso);

        acquireProgressBar = (ProgressBar) findViewById(R.id.acquireProgressBar);
        acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up



        /*
        Seekbar for the ISO-Setting
         */
        seekbar_iso = findViewById(R.id.seekBar_iso);
        seekbar_iso.setVisibility(View.GONE);
        seekbar_shutter = findViewById(R.id.seekBar_shutter);
        seekbar_shutter.setVisibility(View.GONE);

        seekbar_iso.setMax(isovalues.length - 1);
        seekbar_iso.setProgress(val_iso_index); // 50x16=800
        textView_iso.setText("Iso:" + isovalues[val_iso_index]);

        seekbar_iso.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mPreviewRequestBuilder != null && fromUser) {
                    editor.putString("val_iso_index", String.valueOf(progress));
                    editor.commit();

                    textView_iso.setText("Iso:" + isovalues[progress]);
                    global_isoval = isovalues[progress];
                    setIso(global_isoval);
                    try {
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        /*
        Seekbar for the ISO-Setting
         */
        seekbar_shutter.setMax(texpvalues.length - 1);
        seekbar_shutter.setProgress(val_texp_index); // == 1/30
        textView_shutter.setText("Shutter:" + texpvalues[val_texp_index]);

        seekbar_shutter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mPreviewRequestBuilder != null && fromUser) {
                    editor.putString("val_texp_index", String.valueOf(progress));
                    editor.commit();

                    global_expval = texpvalues[progress];
                    textView_shutter.setText("Shutter:" + texpvalues[progress]);
                    setExposureTime(texpvalues[progress]);
                    try {
                        mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        /*
        Seekbar for Lens in X-direction
         */
        seekBarLensX = (SeekBar) findViewById(R.id.seekBarLensX);
        seekBarLensX.setMax(PWM_RES);
        seekBarLensX.setProgress(0);

        textViewLensX = (TextView) findViewById(R.id.textViewLensX);
        String text_lens_x_pre = "Lens (X): ";
        textViewLensX.setText(text_lens_x_pre + seekBarLensX.getProgress());

        seekBarLensX.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        val_lens_x_global = progress;
                        setLensX(val_lens_x_global);
                        textViewLensX.setText(text_lens_x_pre + String.format("%.2f", val_lens_x_global * 1.));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        textViewLensX.setText(text_lens_x_pre + String.format("%.2f", val_lens_x_global * 1.));
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        textViewLensX.setText(text_lens_x_pre + String.format("%.2f", val_lens_x_global * 1.));
                    }
                }
        );


        /*
        Seekbar for Lens in X-direction
         */
        seekBarLensZ = (SeekBar) findViewById(R.id.seekBarLensZ);
        seekBarLensZ.setMax(PWM_RES);
        seekBarLensZ.setProgress(val_lens_z_global);

        textViewLensZ = (TextView) findViewById(R.id.textViewLensZ);
        String text_lens_z_pre = "Lens (Z): ";
        textViewLensZ.setText(text_lens_z_pre + seekBarLensZ.getProgress());

        seekBarLensZ.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        val_lens_z_global = progress;
                        setLensZ(val_lens_z_global);
                        textViewLensZ.setText(text_lens_z_pre + val_lens_z_global * 1.);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        textViewLensZ.setText(text_lens_z_pre + val_lens_z_global * 1.);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        textViewLensZ.setText(text_lens_z_pre + val_lens_z_global * 1.);
                    }
                }

        );

        /*
        Seekbar for Lens in X-direction
         */
        seekBarLaser = (SeekBar) findViewById(R.id.seekBarLaser);
        seekBarLaser.setMax(PWM_RES);
        seekBarLaser.setProgress(0);

        textViewLaser = (TextView) findViewById(R.id.textViewLaser);
        String text_laser_pre = "Laser (I): ";
        textViewLaser.setText(text_laser_pre + seekBarLaser.getProgress());

        seekBarLaser.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        val_laser_red_global = progress;
                        setLaser(val_laser_red_global);
                        textViewLaser.setText(text_laser_pre + String.format("%.2f", val_laser_red_global * 1.));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        textViewLaser.setText(text_laser_pre + String.format("%.2f", val_laser_red_global * 1.));
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        textViewLaser.setText(text_laser_pre + String.format("%.2f", val_laser_red_global * 1.));
                    }
                }
        );


        //Create second surface with another holder (holderTransparent) for drawing the rectangle
        SurfaceView transparentView = (SurfaceView) findViewById(R.id.TransparentView);
        transparentView.setBackgroundColor(Color.TRANSPARENT);
        transparentView.setZOrderOnTop(true);    // necessary
        SurfaceHolder holderTransparent = transparentView.getHolder();
        holderTransparent.setFormat(PixelFormat.TRANSPARENT);
        //TODO holderTransparent.addCallback(callBack);



        /*
        Assign GUI-Elements to actions
         */
        acquireSettingsSOFIToggle = this.findViewById(R.id.btnSofi);
        acquireSettingsSOFIToggle.setText("SOFI (x): 0");
        acquireSettingsSOFIToggle.setTextOn("SOFI (x): 1");
        acquireSettingsSOFIToggle.setTextOff("SOFI (x): 0");

        btnSetup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openSettingsDialog();
            }
        });


        //******************* SOFI-Mode  ********************************************//
        // This is to let the lens vibrate by a certain amount
        acquireSettingsSOFIToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.i(TAG, "Checked");
                    // turn on fluctuation
                    publishMessage(topic_lens_sofi_z, String.valueOf(val_sofi_amplitude_z));
                } else {
                    publishMessage(topic_lens_sofi_z, String.valueOf(0));
                }
            }

        });

        //******************* Move X ++ ********************************************//
        btn_x_lens_plus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                val_lens_x_global = val_lens_x_global+10;
                setLensX(val_lens_x_global);
                textViewLensX.setText(text_lens_x_pre + String.format("%.2f", val_lens_x_global * 1.));
                seekBarLensX.setProgress(val_lens_x_global);
            }

        });

        //******************* Move X -- ********************************************//
        btn_x_lens_minus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                val_lens_x_global = val_lens_x_global-10;
                setLensX(val_lens_x_global);
                textViewLensX.setText(text_lens_x_pre + String.format("%.2f", val_lens_x_global * 1.));
                seekBarLensX.setProgress(val_lens_x_global);
            }

        });

        //******************* Make snapshot ********************************************//
        btnCapture.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //
                //Log.i(TAG, "Set is_find_coupling to true!");
                //if (!is_findcoupling) is_findcoupling = true;
                //takePicture(); //TODO integrate
                showToast("Taking a snapshot.");
                // turn on the laser
                setLaser(val_laser_red_global);
                Log.i(TAG, "Lens Calibration in progress");
                String my_gui_text = "Lens Calibration in progress";
                is_findcoupling = true;
                is_measurement = false;
                textViewGuiText.setText(my_gui_text);
                setState(STATE_CALIBRATION);
            }
        });

        //******************* Start MEasurement ********************************************//
        btnStartMeasurement.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(!is_measurement&!is_findcoupling){
                    is_measurement = true;
                    new run_sofimeasurement().execute();
                }
            }
        });

        //******************* Stop Measurement ********************************************//
        btnStopMeasurement.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                is_measurement = false;
                is_findcoupling = false;
                is_findcoupling_coarse = false;
                is_findcoupling_coarse = true;
            }
        });

    }


    //**********************************************************************************************
    //  Method OnPause
    //**********************************************************************************************
    @Override
    protected void onPause() {
        super.onPause();
        Log.e(TAG, "onPause");

        if (mOrientationListener != null) {
            mOrientationListener.disable();
        }
        closeCamera();
        super.onPause();
        stopBackgroundThread();
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        openCamera();
        if (mTextureView.isAvailable()) {
            configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
        if (mOrientationListener != null && mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }


        // SET CAMERA PARAMETERS FROM GUI
        try {
            global_isoval = isovalues[val_iso_index];
            setIso(global_isoval);
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            global_expval = texpvalues[val_texp_index];
            setExposureTime(global_expval);
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreCaptureCallback, mBackgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSIONS) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    showMissingPermissionError();
                    return;
                }
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Sets up state related to camera that is needed before opening a {@link CameraDevice}.
     */
    private boolean setUpCameraOutputs() {
        Activity activity = AcquireActivity.this;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            ErrorDialog.buildErrorDialog("This device doesn't support Camera2 API.").
                    show(getFragmentManager(), "dialog");
            return false;
        }
        try {
            // Find a CameraDevice that supports RAW captures, and configure state.
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We only use a camera that supports RAW in this sample.
                if (!contains(characteristics.get(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES),
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // For still image captures, we use the largest available size.

                Size largestSize = new Size(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);
                if (isRaw) {
                    largestSize = Collections.max(
                            Arrays.asList(map.getOutputSizes(ImageFormat.RAW_SENSOR)),//RAW_SENSOR)),
                            new CompareSizesByArea());

                    largestSize = new Size(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);
                } else {
                    largestSize = new Size(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);
                    Size[] sizeList = map.getOutputSizes(ImageFormat.YUV_420_888);
                    for (Size size : sizeList) {
                        if (size.getWidth() * size.getHeight() > 1000000)
                            continue;
                        else {
                            largestSize = size;
                            break;
                        }
                    }
                }


                synchronized (mCameraStateLock) {
                    // Set up ImageReaders for JPEG and RAW outputs.  Place these in a reference
                    // counted wrapper to ensure they are only closed when all background tasks
                    // using them are finished.


                    // RAW or JPEG
                    if (isRaw)
                        mImageFormat = ImageFormat.RAW_SENSOR;
                    else
                        mImageFormat = ImageFormat.JPEG;

                    if (mImageReader == null || mImageReader.getAndRetain() == null) {
                        mImageReader = new RefCountedAutoCloseable<>(
                                ImageReader.newInstance(largestSize.getWidth(),
                                        largestSize.getHeight(), mImageFormat, /*maxImages*/ 5));
                    }

                    mImageReader.get().setOnImageAvailableListener(
                            mOnRawImageAvailableListener, mBackgroundHandler);


                    mCharacteristics = characteristics;
                    mCameraId = cameraId;
                }
                return true;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // If we found no suitable cameras for capturing RAW, warn the user.
        ErrorDialog.buildErrorDialog("This device doesn't support capturing RAW photos").
                show(getFragmentManager(), "dialog");
        return false;
    }

    /**
     * Opens the camera specified by {@link #mCameraId}.
     */
    @SuppressWarnings("MissingPermission")
    private void openCamera() {
        if (!setUpCameraOutputs()) {
            return;
        }
        if (!hasAllPermissionsGranted()) {
            requestCameraPermissions();
            return;
        }

        Activity activity = AcquireActivity.this;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            // Wait for any previously running session to finish.
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            String cameraId;
            Handler backgroundHandler;
            synchronized (mCameraStateLock) {
                cameraId = mCameraId;
                backgroundHandler = mBackgroundHandler;
            }

            // Attempt to open the camera. mStateCallback will be called on the background handler's
            // thread when this succeeds or fails.
            manager.openCamera(cameraId, mStateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
        mMediaRecorder = new MediaRecorder();
    }

    /**
     * Requests permissions necessary to use camera and save pictures.
     */
    private void requestCameraPermissions() {

        ActivityCompat.requestPermissions(AcquireActivity.this, CAMERA_PERMISSIONS, REQUEST_CAMERA_PERMISSIONS);


    }

    /**
     * Tells whether all the necessary permissions are granted to this app.
     *
     * @return True if all the required permissions are granted.
     */
    private boolean hasAllPermissionsGranted() {
        for (String permission : CAMERA_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(AcquireActivity.this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Shows that this app really needs the permission and finishes the app.
     */
    private void showMissingPermissionError() {
        Activity activity = AcquireActivity.this;
        if (activity != null) {
            Toast.makeText(activity, R.string.request_permission, Toast.LENGTH_SHORT).show();
            activity.finish();
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            synchronized (mCameraStateLock) {

                seekbar_iso.setVisibility(View.GONE);
                seekbar_shutter.setVisibility(View.GONE);
                // Reset state and clean up resources used by the camera.
                // Note: After calling this, the ImageReaders will be closed after any background
                // tasks saving Images from these readers have been completed.
                mPendingUserCaptures = 0;
                mState = STATE_CLOSED;
                if (null != mCaptureSession) {
                    mCaptureSession.close();
                    mCaptureSession = null;
                }
                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }
                if (null != mImageReader) {
                    mImageReader.close();
                    mImageReader = null;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        synchronized (mCameraStateLock) {
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            synchronized (mCameraStateLock) {
                mBackgroundHandler = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void createCameraPreviewSessionLocked() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface,
                    mImageReader.get().getSurface()), new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            synchronized (mCameraStateLock) {
                                // The camera is already closed
                                if (null == mCameraDevice) {
                                    return;
                                }

                                try {
                                    setup3AControlsLocked(mPreviewRequestBuilder);
                                    // Finally, we start displaying the camera preview.
                                    cameraCaptureSession.setRepeatingRequest(
                                            mPreviewRequestBuilder.build(),
                                            mPreCaptureCallback, mBackgroundHandler);
                                    mState = STATE_PREVIEW;
                                } catch (CameraAccessException | IllegalStateException e) {
                                    e.printStackTrace();
                                    return;
                                }
                                // When the session is ready, we start displaying the preview.
                                mCaptureSession = cameraCaptureSession;
                                Log.i(TAG, "mCaptureSession was created");
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed to configure camera.");
                        }
                    }, mBackgroundHandler
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        seekbar_iso.post(new Runnable() {
            @Override
            public void run() {
                seekbar_shutter.setVisibility(View.VISIBLE);
                seekbar_iso.setVisibility(View.VISIBLE);
            }
        });

    }

    private void setExposureTime(String val) {
        int msexpo = (int) getMilliSecondStringFromShutterString(val);

        Rational exporat;
        if (msexpo > 1000000) {
            exporat = new Rational(msexpo / 1000000, 1);
        } else
            exporat = new Rational(1, (int) (0.5D + 1.0E9F / msexpo));

        mPreviewRequestBuilder.set(CaptureRequestEx.HUAWEI_SENSOR_EXPOSURE_TIME, msexpo);
        //mPreviewRequestBuilder.set(CaptureRequestEx.HUAWEI_PROF_EXPOSURE_TIME, exporat);
    }

    private void setIso(String iso) {
        mPreviewRequestBuilder.set(CaptureRequestEx.HUAWEI_SENSOR_ISO_VALUE, Integer.parseInt(iso));
    }

    private void setup3AControlsLocked(CaptureRequest.Builder builder) {
        // Enable auto-magical 3A run by camera device
        builder.set(CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_AUTO);

        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);

        // If there is an auto-magical flash control mode available, use it, otherwise default to
        // the "on" mode, which is guaranteed to always be available.
        if (contains(mCharacteristics.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES),
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        }

        // If there is an auto-magical white balance control mode available, use it.
        if (contains(mCharacteristics.get(
                CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES),
                CaptureRequest.CONTROL_AWB_MODE_AUTO)) {
            // Allow AWB to run auto-magically if this device supports this
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
        }

        builder.set(CaptureRequestEx.HUAWEI_PROFESSIONAL_MODE, CaptureRequestEx.HUAWEI_PROFESSIONAL_MODE_ENABLED);
        Log.i(TAG, "set DUAL");
        builder.set(HUAWEI_DUAL_SENSOR_MODE, (byte) 2);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);

        setExposureTime(global_expval);
        setIso(global_isoval);
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = AcquireActivity.this;
        synchronized (mCameraStateLock) {
            if (null == mTextureView || null == activity) {
                return;
            }

            StreamConfigurationMap map = mCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            // For still image captures, we always use the largest available size.
            Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.RAW_SENSOR)),
                    new CompareSizesByArea());

            // Find the rotation of the device relative to the native device orientation.
            int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            Point displaySize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

            // Find the rotation of the device relative to the camera sensor's orientation.
            int totalRotation = sensorToDeviceRotation(mCharacteristics, deviceRotation);

            // Swap the view dimensions for calculation as needed if they are rotated relative to
            // the sensor.
            boolean swappedDimensions = totalRotation == 90 || totalRotation == 270;
            int rotatedViewWidth = viewWidth;
            int rotatedViewHeight = viewHeight;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            if (swappedDimensions) {
                rotatedViewWidth = viewHeight;
                rotatedViewHeight = viewWidth;
                maxPreviewWidth = displaySize.y;
                maxPreviewHeight = displaySize.x;
            }

            // Preview should not be larger than display size and 1080p.
            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                maxPreviewWidth = MAX_PREVIEW_WIDTH;
            }

            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
            }

            // Find the best preview size for these view dimensions and configured JPEG size.
            Size previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    rotatedViewWidth, rotatedViewHeight, maxPreviewWidth, maxPreviewHeight,
                    largest);

            if (swappedDimensions) {
                mTextureView.setAspectRatio(
                        previewSize.getHeight(), previewSize.getWidth());
            } else {
                mTextureView.setAspectRatio(
                        previewSize.getWidth(), previewSize.getHeight());
            }

            // Find rotation of device in degrees (reverse device orientation for front-facing
            // cameras).
            int rotation = (mCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_FRONT) ?
                    (360 + ORIENTATIONS.get(deviceRotation)) % 360 :
                    (360 - ORIENTATIONS.get(deviceRotation)) % 360;

            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();

            // Initially, output stream images from the Camera2 API will be rotated to the native
            // device orientation from the sensor's orientation, and the TextureView will default to
            // scaling these buffers to fill it's view bounds.  If the aspect ratios and relative
            // orientations are correct, this is fine.
            //
            // However, if the device orientation has been rotated relative to its native
            // orientation so that the TextureView's dimensions are swapped relative to the
            // native device orientation, we must do the following to ensure the output stream
            // images are not incorrectly scaled by the TextureView:
            //   - Undo the scale-to-fill from the output buffer's dimensions (i.e. its dimensions
            //     in the native device orientation) to the TextureView's dimension.
            //   - Apply a scale-to-fill from the output buffer's rotated dimensions
            //     (i.e. its dimensions in the current device orientation) to the TextureView's
            //     dimensions.
            //   - Apply the rotation from the native device orientation to the current device
            //     rotation.
            if (Surface.ROTATION_90 == deviceRotation || Surface.ROTATION_270 == deviceRotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scale = Math.max(
                        (float) viewHeight / previewSize.getHeight(),
                        (float) viewWidth / previewSize.getWidth());
                matrix.postScale(scale, scale, centerX, centerY);

            }
            matrix.postRotate(rotation, centerX, centerY);

            mTextureView.setTransform(matrix);

            // Start or restart the active capture session if the preview was initialized or
            // if its aspect ratio changed significantly.
            if (mPreviewSize == null || !checkAspectsEqual(previewSize, mPreviewSize)) {
                mPreviewSize = previewSize;
                if (mState != STATE_CLOSED) {
                    createCameraPreviewSessionLocked();
                }
            }
        }
    }

    // Utility classes and methods:
    // *********************************************************************************************

    /**
     * Initiate a still image capture.
     * <p/>
     * This function sends a capture request that initiates a pre-capture sequence in our state
     * machine that waits for auto-focus to finish, ending in a "locked" state where the lens is no
     * longer moving, waits for auto-exposure to choose a good exposure value, and waits for
     * auto-white-balance to converge.
     */
    private void takePicture() {
        synchronized (mCameraStateLock) {
            mPendingUserCaptures++;

            // If we already triggered a pre-capture sequence, or are in a state where we cannot
            // do this, return immediately.
            if (mState != STATE_PREVIEW) {
                return;
            }

            try {
                // Trigger an auto-focus run if camera is capable. If the camera is already focused,
                // this should do nothing.
                if (!mNoAFRun) {
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                            CameraMetadata.CONTROL_AF_TRIGGER_START);
                }

                // If this is not a legacy device, we can also trigger an auto-exposure metering
                // run.
                if (!isLegacyLocked()) {
                    // Tell the camera to lock focus.
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                            CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                }

                // Update state machine to wait for auto-focus, auto-exposure, and
                // auto-white-balance (aka. "3A") to converge.
                mState = STATE_WAITING_FOR_3A_CONVERGENCE;

                // Start a timer for the pre-capture sequence.
                startTimerLocked();

                // Replace the existing repeating request with one with updated 3A triggers.
                mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                        mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Send a capture request to the camera device that initiates a capture targeting the JPEG and
     * RAW outputs.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void captureStillPictureLocked() {
        try {
            final Activity activity = AcquireActivity.this;
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            captureBuilder.addTarget(mImageReader.get().getSurface());

            // Use the same AE and AF modes as the preview.
            setup3AControlsLocked(captureBuilder);

            // Set orientation.
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

            // Set request tag to easily track results in callbacks.
            captureBuilder.setTag(mRequestCounter.getAndIncrement());

            CaptureRequest request = captureBuilder.build();
            //captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, CURRENT_ZOOM);


            // Create an ImageSaverBuilder in which to collect results, and add it to the queue
            // of active requests.
            ImageSaver.ImageSaverBuilder rawBuilder = new ImageSaver.ImageSaverBuilder(activity)
                    .setCharacteristics(mCharacteristics);

            mRawResultQueue.put((int) request.getTag(), rawBuilder);

            mCaptureSession.capture(request, mCaptureCallback, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called after a RAW/JPEG capture has completed; resets the AF trigger state for the
     * pre-capture sequence.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void finishedCaptureLocked() {
        try {
            // Reset the auto-focus trigger in case AF didn't run quickly enough.
            if (!mNoAFRun) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);

                mCaptureSession.capture(mPreviewRequestBuilder.build(), mPreCaptureCallback,
                        mBackgroundHandler);

                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                        CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieve the next {@link Image} from a reference counted {@link ImageReader}, retaining
     * that {@link ImageReader} until that {@link Image} is no longer in use, and set this
     * {@link Image} as the result for the next request in the queue of pending requests.  If
     * all necessary information is available, begin saving the image to a file in a background
     * thread.
     *
     * @param pendingQueue the currently active requests.
     * @param reader       a reference counted wrapper containing an {@link ImageReader} from which
     *                     to acquire an image.
     */
    private void dequeueAndSaveImage(TreeMap<Integer, ImageSaver.ImageSaverBuilder> pendingQueue,
                                     RefCountedAutoCloseable<ImageReader> reader) {
        synchronized (mCameraStateLock) {
            Map.Entry<Integer, ImageSaver.ImageSaverBuilder> entry =
                    pendingQueue.firstEntry();
            ImageSaver.ImageSaverBuilder builder = entry.getValue();

            // Increment reference count to prevent ImageReader from being closed while we
            // are saving its Images in a background thread (otherwise their resources may
            // be freed while we are writing to a file).
            if (reader == null || reader.getAndRetain() == null) {
                Log.e(TAG, "Paused the activity before we could save the image," +
                        " ImageReader already closed.");
                pendingQueue.remove(entry.getKey());
                return;
            }

            Image image;
            try {
                image = reader.get().acquireNextImage();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Too many images queued for saving, dropping image for request: " +
                        entry.getKey());
                pendingQueue.remove(entry.getKey());
                return;
            }

            builder.setRefCountedReader(reader).setImage(image);

            handleCompletionLocked(entry.getKey(), builder, pendingQueue);
        }
    }

    public void openSettingsDialog() {
        settingsDialogFragment.show(getFragmentManager(), "acquireSettings");
    }

    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {

    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show.
     */
    private void showToast(String text) {
        // We show a Toast by sending request message to mMessageHandler. This makes sure that the
        // Toast is shown on the UI thread.
        Message message = Message.obtain();
        message.obj = text;
        mMessageHandler.sendMessage(message);
    }

    /**
     * If the given request has been completed, remove it from the queue of active requests and
     * send an {@link ImageSaver} with the results from this request to a background thread to
     * save a file.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @param requestId the ID of the {@link CaptureRequest} to handle.
     * @param builder   the {@link ImageSaver.ImageSaverBuilder} for this request.
     * @param queue     the queue to remove this request from, if completed.
     */
    private void handleCompletionLocked(int requestId, ImageSaver.ImageSaverBuilder builder,
                                        TreeMap<Integer, ImageSaver.ImageSaverBuilder> queue) {
        if (builder == null) return;
        ImageSaver saver = builder.buildIfComplete();
        if (saver != null) {
            queue.remove(requestId);
            AsyncTask.THREAD_POOL_EXECUTOR.execute(saver);
        }
    }

    /**
     * Check if we are using a device that only supports the LEGACY hardware level.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @return true if this is a legacy device.
     */
    private boolean isLegacyLocked() {
        return mCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ==
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
    }

    /**
     * Start the timer for the pre-capture sequence.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     */
    private void startTimerLocked() {
        mCaptureTimer = SystemClock.elapsedRealtime();
    }

    /**
     * Check if the timer for the pre-capture sequence has been hit.
     * <p/>
     * Call this only with {@link #mCameraStateLock} held.
     *
     * @return true if the timeout occurred.
     */
    private boolean hitTimeoutLocked() {
        return (SystemClock.elapsedRealtime() - mCaptureTimer) > PRECAPTURE_TIMEOUT_MS;
    }

    private void setUpMediaRecorder() throws IOException {
        final Activity activity = this; //getActivity();
        if (null == activity) {
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        /**
         * create video output file
         */
        mCurrentFile = myVideoFileName;
        /**
         * set output file in media recorder
         */
        mMediaRecorder.setOutputFile(mCurrentFile.getAbsolutePath());
        CamcorderProfile profile = CamcorderProfile.get(global_cameraquality);
        mMediaRecorder.setVideoFrameRate(global_framerate);
        mMediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mMediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setAudioEncodingBitRate(profile.audioBitRate);
        mMediaRecorder.setAudioSamplingRate(profile.audioSampleRate);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        mMediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));

        mMediaRecorder.prepare();
    }


    private void closePreviewSession() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }

    /**
     * Start the camera preview.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // Use the same AE and AF  modes as the preview.
            setup3AControlsLocked(mPreviewRequestBuilder);

            Surface previewSurface = new Surface(texture);
            mPreviewRequestBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCaptureSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Activity activity = AcquireActivity.this;
                            if (null != activity) {
                                Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();

            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            // Use the same AE and AF  modes as the preview.
            setup3AControlsLocked(mPreviewRequestBuilder);


            List<Surface> surfaces = new ArrayList<>();
            /**
             * Surface for the camera preview set up
             */
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewRequestBuilder.addTarget(previewSurface);
            //MediaRecorder setup for surface
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewRequestBuilder.addTarget(recorderSurface);
            // Start a capture session

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                        mCaptureSession = cameraCaptureSession;
                        updatePreview();
                        runOnUiThread(() -> {
                            mIsRecordingVideo = true;
                            // Start recording
                            try {
                                //mMediaRecorder.prepare();
                                mMediaRecorder.start();
                            } catch (IllegalStateException e) {
                                Log.e(TAG, String.valueOf(e));
                            }
                        });
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                        Log.e(TAG, "onConfigureFailed: Failed");
                    }
                }, mBackgroundHandler);
            }

        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the camera preview. {@link startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewRequestBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    public void stopRecordingVideo() throws Exception {
        // UI
        mIsRecordingVideo = false;
        try {
            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        startPreview();
    }

    private void saveFile(byte[] Data, int w, int h, int type) {
        String filename = "";
        String filetype = "";
        try {
            switch (type) {
                case 0:
                    filetype = "JPG";
                    break;
                case 1:
                    filetype = "yuv";
                    break;
                case 2:
                    filetype = "raw";
                    break;
                default:
                    Log.w(TAG, "unknow file type");
            }

            filename = String.format("/sdcard/DCIM/Camera/SNAP_%dx%d_%d.%s", w, h, System.currentTimeMillis(), filetype);
            File file;
            while (true) {
                file = new File(filename);
                if (file.createNewFile()) {
                    break;
                }
            }

            long t0 = SystemClock.uptimeMillis();
            OutputStream os = new FileOutputStream(file);
            os.write(Data);
            os.flush();
            os.close();
            long t1 = SystemClock.uptimeMillis();

            Log.d(TAG, String.format("Wrote data(%d) %d bytes as %s in %.3f seconds;%s", type,
                    Data.length, file, (t1 - t0) * 0.001, filename));
        } catch (IOException e) {
            Log.e(TAG, "Error creating new file: ", e);
        }
    }

    private void initialConfig() {
        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), "tcp://" + myIPAddress, MQTT_CLIENTID);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String myIPAddress) {

                if (reconnect) {
                    //addToHistory("Reconnected to : " + myIPAddress);
                    // Because Clean Session is true, we need to re-subscribe
                    // subscribeToTopic();
                } else {
                    //addToHistory("Connected to: " + myIPAddress);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                //addToHistory("The Connection was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                //addToHistory("Incoming message: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setUserName(MQTT_USER);
        mqttConnectOptions.setPassword(MQTT_PASS.toCharArray());
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);

        try {
            //addToHistory("Connecting to " + myIPAddress);
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);

                    publishMessage("A phone has connected.", "");
                    // subscribeToTopic();

                    Toast.makeText(AcquireActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    //addToHistory("Failed to connect to: " + myIPAddress);
                    Toast.makeText(AcquireActivity.this, "Connection attemp failed", Toast.LENGTH_SHORT).show();
                }
            });


        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public void publishMessage(String pub_topic, String publishMessage) {

        Log.i(TAG, pub_topic + " " + publishMessage);
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(publishMessage.getBytes());
            mqttAndroidClient.publish(pub_topic, message);
            //addToHistory("Message Published");
            if (!mqttAndroidClient.isConnected()) {
                //addToHistory(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
            Log.i(TAG, "Message sent: " + pub_topic + message);
        } catch (MqttException e) {
            Toast.makeText(this, "Error while sending data", Toast.LENGTH_SHORT).show();
            //System.err.println("Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopConnection() {
        try {
            mqttAndroidClient.close();
            Toast.makeText(AcquireActivity.this, "Connection closed - on purpose?", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            Toast.makeText(AcquireActivity.this, "Something went wrong - propbably no connection established?", Toast.LENGTH_SHORT).show();
            Log.e(TAG, String.valueOf(e));
        }
    }

    // -------------------------------
    // ------ MQTT STUFFF -----------
    //- ------------------------------

    public void setIPAddress(String mIPaddress) {
        myIPAddress = mIPaddress;
        editor.putString("myIPAddress", String.valueOf(myIPAddress));
        editor.commit();
    }

    public void setSOFIX(boolean misSOFI_X, int mvalSOFIX) {
        val_sofi_amplitude_x = mvalSOFIX;
        is_SOFI_x = misSOFI_X;
        publishMessage(topic_lens_sofi_x, String.valueOf(val_sofi_amplitude_x));
        editor.putString("val_sofi_amplitude_x", String.valueOf(val_sofi_amplitude_x));
        editor.commit();
    }

    public void setSOFIZ(boolean misSOFI_Z, int mvalSOFIZ) {
        val_sofi_amplitude_z = mvalSOFIZ;
        is_SOFI_z = misSOFI_Z;
        publishMessage(topic_lens_sofi_z, String.valueOf(val_sofi_amplitude_z));
        editor.putString("val_sofi_amplitude_z", String.valueOf(val_sofi_amplitude_z));
        editor.commit();
    }

    public void setValSOFIX(int mval_sofi_amplitude_x) {
        val_sofi_amplitude_x = mval_sofi_amplitude_x;
        // Save the IP address for next start
        editor.putString("mval_sofi_amplitude_x", String.valueOf(mval_sofi_amplitude_x));
        editor.commit();
    }

    public void setValSOFIZ(int mval_sofi_amplitude_z) {
        val_sofi_amplitude_z = mval_sofi_amplitude_z;
        // Save the IP address for next start
        editor.putString("mval_sofi_amplitude_z", String.valueOf(mval_sofi_amplitude_z));
        editor.commit();
    }

    public void setValDurationMeas(int mval_duration_measurement) {
        val_duration_measurement = mval_duration_measurement;
        // Save the IP address for next start
        editor.putString("val_duration_measurement", String.valueOf(mval_duration_measurement));
        editor.commit();
    }

    public void setValPeriodMeas(int mval_period_measurement) {
        val_period_measurement = mval_period_measurement;
        // Save the IP address for next start
        editor.putString("val_period_measurement", String.valueOf(mval_period_measurement));
        editor.commit();
    }

    public void setNValPeriodCalibration(int mval_period_calibration) {
        val_nperiods_calibration = mval_period_calibration;
        // Save the IP address for next start
        editor.putString("val_nperiods_calibration", String.valueOf(mval_period_calibration));
        editor.commit();
    }

    void MQTT_Reconnect(String mIP) {

        myIPAddress = mIP;
        Toast.makeText(AcquireActivity.this, "IP-Address set to: " + myIPAddress, Toast.LENGTH_SHORT).show();
        stopConnection();
        initialConfig();

        // Save the IP address for next start
        editor.putString("myIPAddress", myIPAddress);
        editor.commit();
    }

    protected String wifiIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endianif needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e("WIFIIP", "Unable to get host address.");
            ipAddressString = null;
        }

        return ipAddressString;
    }

    // SOME I/O thingys
    public int lin2qudratic(int input, int mymax) {
        double normalizedval = (double) input / (double) mymax;
        double quadraticval = Math.pow(normalizedval, 2);
        int laserintensitypow = (int) (quadraticval * (double) mymax);
        return laserintensitypow;
    }

    public void setLaser(int laserintensity) {
        if (laserintensity < PWM_RES && laserintensity>=0 ) {
            if (laserintensity ==  0)laserintensity=1;
            publishMessage(topic_laser, String.valueOf(lin2qudratic(laserintensity, PWM_RES)));
            // Wait until the command was actually sent
            if(is_findcoupling){
                try {
                    Thread.sleep(MQTT_SLEEP);
                } catch (Exception e) {
                    Log.e(TAG, String.valueOf(e));
                }
            }
        }
    }

    public void setState(String mystate) {
        publishMessage(topic_state, mystate);
    }



    void setLensX(int lensposition) {
        if ((lensposition < PWM_RES) && (lensposition >=0)) {
            if (lensposition ==  0)lensposition=1;
            publishMessage(topic_lens_x, String.valueOf(lin2qudratic(lensposition, PWM_RES)));
            // Wait until the command was actually sent
            if(is_findcoupling){
            try {
                Thread.sleep(MQTT_SLEEP);
            } catch (Exception e) {
                Log.e(TAG, String.valueOf(e));
            }
            }
            editor.putString("val_lens_x_global", String.valueOf(lensposition));
            editor.commit();


        }
    }

    void setLensZ(int lensposition) {
        if (lensposition < PWM_RES && lensposition >= 0) {
            if (lensposition ==  0)lensposition=1;
            publishMessage(topic_lens_z, String.valueOf(lin2qudratic(lensposition, PWM_RES)));
            // Wait until the command was actually sent
            if(is_findcoupling){
                try {
                    Thread.sleep(MQTT_SLEEP);
                } catch (Exception e) {
                    Log.e(TAG, String.valueOf(e));
                }
            }
            editor.putString("val_lens_z_global", String.valueOf(lensposition));
            editor.commit();

        }
    }

    /**
     * Runnable that saves an {@link Image} into the specified {@link File}, and updates
     * {@link android.provider.MediaStore} to include the resulting file.
     * <p/>
     * This can be constructed through an {@link ImageSaverBuilder} as the necessary image and
     * result information becomes available.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The image to save.
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        /**
         * The CaptureResult for this image capture.
         */
        private final CaptureResult mCaptureResult;

        /**
         * The CameraCharacteristics for this camera device.
         */
        private final CameraCharacteristics mCharacteristics;

        /**
         * The Context to use when updating MediaStore with the saved images.
         */
        private final Context mContext;

        /**
         * A reference counted wrapper for the ImageReader that owns the given image.
         */
        private final RefCountedAutoCloseable<ImageReader> mReader;

        private ImageSaver(Image image, File file, CaptureResult result,
                           CameraCharacteristics characteristics, Context context,
                           RefCountedAutoCloseable<ImageReader> reader) {
            mImage = image;
            mFile = file;
            mCaptureResult = result;
            mCharacteristics = characteristics;
            mContext = context;
            mReader = reader;
        }

        @Override
        public void run() {
            boolean success = false;
            int format = mImage.getFormat();
            switch (format) {
                case ImageFormat.JPEG: {
                    ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(mFile + ".jpg");
                        output.write(bytes);
                        success = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        closeOutput(output);
                    }
                    break;
                }
                case ImageFormat.RAW_SENSOR: {
                    DngCreator dngCreator = new DngCreator(mCharacteristics, mCaptureResult);
                    FileOutputStream output = null;
                    try {
                        output = new FileOutputStream(mFile + ".jpg");
                        dngCreator.writeImage(output, mImage);
                        success = true;
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        mImage.close();
                        closeOutput(output);
                    }
                    break;
                }
                default: {
                    Log.e("Camera2Raw", "Cannot save image, unexpected image format:" + format);
                    break;
                }
            }

            // Decrement reference count to allow ImageReader to be closed to free up resources.
            mReader.close();

            // If saving the file succeeded, update MediaStore.
            if (success) {
                MediaScannerConnection.scanFile(mContext, new String[]{mFile.getPath()},
                        /*mimeTypes*/null, new MediaScannerConnection.MediaScannerConnectionClient() {
                            @Override
                            public void onMediaScannerConnected() {
                                // Do nothing
                            }

                            @Override
                            public void onScanCompleted(String path, Uri uri) {
                                Log.i("Camera2Raw", "Scanned " + path + ":");
                                Log.i("Camera2Raw", "-> uri=" + uri);
                            }
                        });
            }
        }

        /**
         * Builder class for constructing {@link ImageSaver}s.
         * <p/>
         * This class is thread safe.
         */
        public static class ImageSaverBuilder {
            private Image mImage;
            private File mFile;
            private CaptureResult mCaptureResult;
            private CameraCharacteristics mCharacteristics;
            private Context mContext;
            private RefCountedAutoCloseable<ImageReader> mReader;

            /**
             * Construct a new ImageSaverBuilder using the given {@link Context}.
             *
             * @param context a {@link Context} to for accessing the
             *                {@link android.provider.MediaStore}.
             */
            public ImageSaverBuilder(final Context context) {
                mContext = context;
            }

            public synchronized ImageSaverBuilder setRefCountedReader(
                    RefCountedAutoCloseable<ImageReader> reader) {
                if (reader == null) throw new NullPointerException();

                mReader = reader;
                return this;
            }

            public synchronized ImageSaverBuilder setImage(final Image image) {
                if (image == null) throw new NullPointerException();
                mImage = image;
                return this;
            }

            public synchronized ImageSaverBuilder setFile(final File file) {
                if (file == null) throw new NullPointerException();
                mFile = file;
                return this;
            }

            public synchronized ImageSaverBuilder setResult(final CaptureResult result) {
                if (result == null) throw new NullPointerException();
                mCaptureResult = result;
                return this;
            }

            public synchronized ImageSaverBuilder setCharacteristics(
                    final CameraCharacteristics characteristics) {
                if (characteristics == null) throw new NullPointerException();
                mCharacteristics = characteristics;
                return this;
            }

            public synchronized ImageSaver buildIfComplete() {
                if (!isComplete()) {
                    return null;
                }
                return new ImageSaver(mImage, mFile, mCaptureResult, mCharacteristics, mContext,
                        mReader);
            }

            public synchronized String getSaveLocation() {
                return (mFile == null) ? "Unknown" : mFile.toString();
            }

            private boolean isComplete() {
                return mImage != null && mFile != null && mCaptureResult != null
                        && mCharacteristics != null;
            }
        }
    }

    /**
     * Comparator based on area of the given {@link Size} objects.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * A dialog fragment for displaying non-recoverable errors; this {@ling Activity} will be
     * finished once the dialog has been acknowledged by the user.
     */
    public static class ErrorDialog extends DialogFragment {

        private String mErrorMessage;

        public ErrorDialog() {
            mErrorMessage = "Unknown error occurred!";
        }

        // Build a dialog with a custom message (Fragments require default constructor).
        public static ErrorDialog buildErrorDialog(String errorMessage) {
            ErrorDialog dialog = new ErrorDialog();
            dialog.mErrorMessage = errorMessage;
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(mErrorMessage)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }

    /**
     * A wrapper for an {@link AutoCloseable} object that implements reference counting to allow
     * for resource management.
     */
    public static class RefCountedAutoCloseable<T extends AutoCloseable> implements AutoCloseable {
        private T mObject;
        private long mRefCount = 0;

        /**
         * Wrap the given object.
         *
         * @param object an object to wrap.
         */
        public RefCountedAutoCloseable(T object) {
            if (object == null) throw new NullPointerException();
            mObject = object;
        }

        /**
         * Increment the reference count and return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        public synchronized T getAndRetain() {
            if (mRefCount < 0) {
                return null;
            }
            mRefCount++;
            return mObject;
        }

        /**
         * Return the wrapped object.
         *
         * @return the wrapped object, or null if the object has been released.
         */
        public synchronized T get() {
            return mObject;
        }

        /**
         * Decrement the reference count and release the wrapped object if there are no other
         * users retaining this object.
         */
        @Override
        public synchronized void close() {
            if (mRefCount >= 0) {
                mRefCount--;
                if (mRefCount < 0) {
                    try {
                        mObject.close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        mObject = null;
                    }
                }
            }
        }
    }

    /**
     * A dialog that explains about the necessary permissions.
     */
    public static class PermissionConfirmationDialog extends DialogFragment {

        public static PermissionConfirmationDialog newInstance() {
            return new PermissionConfirmationDialog();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent, CAMERA_PERMISSIONS,
                                    REQUEST_CAMERA_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    getActivity().finish();
                                }
                            })
                    .create();
        }


    }

    private class run_sofimeasurement extends AsyncTask<Void, Void, Void> {

        String my_gui_text = "";
        long t = 0;
        int i_meas = 0;
        int n_meas = 20;
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
        String mypath = mypath_measurements + timestamp + "/";
        File myDir = new File(mypath);

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // Create Folder
            if (!myDir.exists()) {
                if (!myDir.mkdirs()) {
                    return; //Cannot make directory
                }
            }

            // Make sure laser is not set to zero
            if (val_laser_red_global == 0) {
                val_laser_red_global = 2000;
            }

            // Set some GUI components
            acquireProgressBar.setVisibility(View.VISIBLE); // Make invisible at first, then have it pop up
            acquireProgressBar.setMax(val_nperiods_calibration);

            Toast.makeText(AcquireActivity.this, "Start Measurements", Toast.LENGTH_SHORT).show();

        }

        @Override
        protected void onProgressUpdate(Void... params) {
            acquireProgressBar.setProgress(i_meas);

            // some GUI interaction
            textViewGuiText.setText(my_gui_text);
            btnStartMeasurement.setEnabled(false);

            // Update GUI
            String text_lens_x_pre = "Lens (X): ";
            textViewLensX.setText(text_lens_x_pre + String.format("%.2f", val_lens_x_global * 1.));
            seekBarLensX.setProgress(val_lens_x_global);

            String text_laser_pre = "Laser: ";
            textViewLaser.setText(text_laser_pre + String.format("%.2f", val_laser_red_global * 1.));
            seekBarLaser.setProgress(val_laser_red_global);
        }

        void mSleep(int sleepVal) {
            try {
                Thread.sleep(sleepVal);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {

            // Wait for the data to propigate down the chain
            t = SystemClock.elapsedRealtime();

            // Start with a video measurement for XXX-seconds
            i_meas = 1;
            while (is_measurement) {
                // Do recalibration every  10 measurements
                // do lens calibration every n-th step
                if ((i_meas % val_nperiods_calibration) == 0) {
                    // turn on the laser
                    setLaser(val_laser_red_global);
                    Log.i(TAG, "Lens Calibration in progress");
                    my_gui_text = "Lens Calibration in progress";
                    is_findcoupling = true;
                    is_measurement = false;
                    i_meas++;
                    publishProgress();
                    setState(STATE_CALIBRATION);
                }
                else if(!is_findcoupling&is_measurement) {// if no coupling has to be done -> measure!
                    setState(STATE_RECORD);
                    // Once in a while update the GUI
                    my_gui_text = "Measurement: " + String.valueOf(i_meas ) + '/' + String.valueOf(n_meas);
                    publishProgress();

                    // set lens to correct position
                    setLensX(val_lens_x_global);

                    // determine the path for the video
                    myVideoFileName = new File(mypath + File.separator + "VID_" + String.valueOf(i_meas) + ".mp4");
                    Log.i(TAG, "Saving file here:" + String.valueOf(myVideoFileName));

                    // turn on the laser
                    setLaser(val_laser_red_global);

                    // start video-capture
                    if (!mIsRecordingVideo) {
                        startRecordingVideo();
                    }

                    // turn on fluctuation
                    publishMessage(topic_lens_sofi_z, String.valueOf(val_sofi_amplitude_z));
                    mSleep(val_duration_measurement * 1000); //Let AEC stabalize if it's on

                    // turn off fluctuation
                    publishMessage(topic_lens_sofi_z, String.valueOf(0));
                    mSleep(500); //Let AEC stabalize if it's on

                    // stop video-capture
                    if (mIsRecordingVideo) {
                        try {
                            stopRecordingVideo();
                            //prepareViews();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    // turn off the laser
                    setLaser(1);

                    //TODO : Dirty hack for now since we don't have a proper laser
                    mSleep(200); //Let AEC stabalize if it's on
                    setLensX(1); // Heavily detune the lens to reduce phototoxicity

                    // Once in a while update the GUI
                    my_gui_text = "Waiting for next measurements. "+String.valueOf(val_nperiods_calibration-i_meas)+"/"+String.valueOf(val_nperiods_calibration)+"left until recalibration";
                    publishProgress();
                    setState(STATE_WAIT);

                    // only perform the measurements if the camera is not looking for best coupling
                    i_meas++;

                    for(int iwait = 0; iwait<val_period_measurement*10; iwait++){
                        if(!is_measurement)break;
                        my_gui_text = "Waiting: "+String.valueOf(iwait/10) + "/" +String.valueOf(val_period_measurement)+"s";
                        publishProgress();
                        mSleep(100);
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            // Set some GUI components
            acquireProgressBar.setVisibility(View.GONE); // Make invisible at first, then have it pop up
            textViewGuiText.setText("Done Measurements.");
            btnStartMeasurement.setEnabled(true);

            // Switch off laser
            setLaser(0);

            // free memory
            is_findcoupling = false;
            Toast.makeText(AcquireActivity.this, "Stop Measurements", Toast.LENGTH_SHORT).show();
            System.gc();
        }
    }

    class run_calibration_thread_coarse implements Runnable {

        Thread mythread;
        // to stop the thread
        private boolean exit;
        private String name;

        run_calibration_thread_coarse(String threadname) {
            name = threadname;
            mythread = new Thread(this, name);
            exit = false;
            mythread.start(); // Starting the thread
        }

        // execution of thread starts from run() method
        public void run() {

            try {
                isCameraBusy = true;

                // convert the Bitmap coming from the camera frame to MAT
                Mat src = new Mat();
                Mat dst = new Mat();

                Utils.bitmapToMat(global_bitmap, src);
                Imgproc.cvtColor(src, dst, Imgproc.COLOR_BGRA2BGR);





                // reset the lens's position in the first iteration by some value
                    if (i_search_maxintensity == 0) {
                    val_lens_x_global_old = val_lens_x_global; // Save the value for later
                    val_lens_x_global = 0;
                    val_mean_max = 0;
                    val_lens_x_maxintensity = 0;
                    setLensX(val_lens_x_global);
                }
                else {
                        int i_mean = (int)measureCoupling(dst, ROI_SIZE, 9);
                        Log.i(TAG, "Coupling (fine) @ "+String.valueOf(i_search_maxintensity)+" is "+String.valueOf(i_mean)+"with max: "+String.valueOf(val_mean_max));
                    if (i_mean > val_mean_max) {
                        // Save the position with maximum intensity
                        val_mean_max = i_mean;
                        val_lens_x_maxintensity = val_lens_x_global;
                    }

                }
                // break if algorithm reaches the maximum of lens positions
                if (val_lens_x_global > PWM_RES) {
                    // if maximum number of search iteration is reached, break
                    if (val_lens_x_maxintensity == 0) {
                        val_lens_x_maxintensity = val_lens_x_global_old;
                    }
                    val_lens_x_global = val_lens_x_maxintensity;
                    setLensX(val_lens_x_global);

                    i_search_maxintensity = 0;
                    exit = true;
                    is_findcoupling_coarse = false;
                    is_findcoupling_fine = true;
                    Log.i(TAG, "My final Mean/STDV (coarse) is:" + String.valueOf(val_mean_max) + "@" + String.valueOf(val_lens_x_maxintensity));

                } else {
                    // increase the lens position
                    val_lens_x_global = val_lens_x_global + 400;
                    setLensX(val_lens_x_global);
                    // free memory
                    System.gc();
                    src.release();
                    dst.release();
                    global_bitmap.recycle();
                }
                // release camera
                isCameraBusy = false;

                i_search_maxintensity++;

            }
            catch(Exception v){
                    System.out.println(v);
                }

                System.out.println(name + " Stopped.");
            }

            // for stopping the thread
            public void stop ()
            {
                exit = true;
            }
        }

        class run_calibration_thread_fine implements Runnable {

            Thread mythread;
            // to stop the thread
            private boolean exit;
            private String name;

            run_calibration_thread_fine(String threadname) {
                name = threadname;
                mythread = new Thread(this, name);
                exit = false;
                mythread.start(); // Starting the thread
            }

            // execution of thread starts from run() method
            public void run() {

                try {
                    isCameraBusy = true;

                    // convert the Bitmap coming from the camera frame to MAT
                    Mat src = new Mat();
                    Mat dst = new Mat();
                    Utils.bitmapToMat(global_bitmap, src);
                    Imgproc.cvtColor(src, dst, Imgproc.COLOR_BGRA2BGR);

                    // reset the lens's position in the first iteration by some value
                    if (i_search_maxintensity == 0) {
                        val_lens_x_global_old = val_lens_x_global; // Save the value for later
                        val_lens_x_global = val_lens_x_global - 200;
                    }
                    i_search_maxintensity++;


                    int i_mean = (int)measureCoupling(dst, ROI_SIZE, 9);
                    Log.i(TAG, "Coupling (coarse) @ "+String.valueOf(i_search_maxintensity)+" is "+String.valueOf(i_mean)+"with max: "+String.valueOf(val_mean_max));
                    if (i_mean > val_mean_max) {
                        // Save the position with maximum intensity
                        val_mean_max = i_mean;
                        val_lens_x_maxintensity = val_lens_x_global;
                    }


                    // break if algorithm reaches the maximum of lens positions
                    if (val_lens_x_global > val_lens_x_global_old + 200) {
                        // if maximum number of search iteration is reached, break
                        if (val_lens_x_maxintensity == 0) {
                            val_lens_x_maxintensity = val_lens_x_global_old;
                        }
                        val_lens_x_global = val_lens_x_maxintensity;
                        setLensX(val_lens_x_global);
                        is_findcoupling = false;
                        i_search_maxintensity = 0;
                        exit = true;
                        is_findcoupling_fine = false;
                        is_findcoupling_coarse = true;
                        Log.i(TAG, "My final Mean/STDV (fine) is:" + String.valueOf(val_mean_max) + "@" + String.valueOf(val_lens_x_maxintensity));
                        setLaser(0);
                    }

                    // increase the lens position
                    val_lens_x_global = val_lens_x_global + 10;
                    setLensX(val_lens_x_global);

                    // free memory
                    System.gc();
                    src.release();
                    dst.release();
                    global_bitmap.recycle();

                    isCameraBusy = false;


                } catch (Exception v) {
                    System.out.println(v);
                }

                System.out.println(name + " Stopped.");
            }

            // for stopping the thread
            public void stop() {
                exit = true;
            }
        }


    double measureCoupling(Mat inputmat, int mysize, int ksize){
        // reserve some memory
        MatOfDouble tmp_mean = new MatOfDouble();
        MatOfDouble tmp_std = new MatOfDouble();

        // crop the matrix
        // We want only the center part assuming the illuminated wave guide is in the center
        Rect roi = new Rect((int)inputmat.width()/2-mysize/2, (int)inputmat.height()/2-mysize/2, mysize, mysize);
        Mat dst_cropped = new Mat(inputmat, roi);
        // Median filter the image
        Imgproc.medianBlur(dst_cropped, dst_cropped, ksize);

        Core.meanStdDev(dst_cropped, tmp_mean, tmp_std);
        double mystd = Core.mean(tmp_std).val[0];

        /*
        // Estimate Entropy in Image
        double i_mean = Core.mean(dst_cropped).val[0];

        Mat dst_tmp = new Mat();
        dst_cropped.convertTo(dst_cropped, CV_32F);

        Core.pow(dst_cropped, 2., dst_tmp);

        double myL2norm = Core.norm(dst_cropped,Core.NORM_L2);
        double myL1norm = Core.norm(dst_cropped,Core.NORM_L1);
        //double myMinMaxnorm = Core.norm(dst_cropped,Core.NORM_MINMAX);
        //Core.MinMaxLocResult myMinMax = Core.minMaxLoc(dst_cropped);
        int mymin = 0;//(int) myMinMax.minVal;
        int mymax = 0;//(int) myMinMax.maxVal;
        */

        //imwrite(Environment.getExternalStorageDirectory() + "/STORMimager/mytest"+String.valueOf(i_search_maxintensity)+"_mean_" + String.valueOf(i_mean) + "_stdv_" + String.valueOf(Core.mean(tmp_std).val[0]) + "_L2_" + String.valueOf(myL2norm) +"_L1_" + String.valueOf(myL1norm) +"_Min_" + String.valueOf(mymin)+"_Max_" + String.valueOf(mymax) +"_L2_" + String.valueOf(myL2norm) +"@" + String.valueOf(val_lens_x_global)+".png", dst_cropped);

        tmp_mean.release();
        tmp_std.release();

        return mystd;

    }


    void setGUIelements(SharedPreferences sharedPref){
        myIPAddress = sharedPref.getString("myIPAddress", myIPAddress);
        val_nperiods_calibration = Integer.parseInt(sharedPref.getString("val_nperiods_calibration", String.valueOf(val_nperiods_calibration)));
        val_period_measurement = Integer.parseInt(sharedPref.getString("val_period_measurement", String.valueOf(val_period_measurement)));
        val_duration_measurement = Integer.parseInt(sharedPref.getString("val_duration_measurement", String.valueOf(val_duration_measurement)));
        val_sofi_amplitude_x = Integer.parseInt(sharedPref.getString("val_sofi_amplitude_x", String.valueOf(val_sofi_amplitude_x)));
        val_sofi_amplitude_z = Integer.parseInt(sharedPref.getString("val_sofi_amplitude_z", String.valueOf(val_sofi_amplitude_z)));
        val_iso_index = Integer.parseInt(sharedPref.getString("val_iso_index", String.valueOf(val_iso_index)));
        val_texp_index = Integer.parseInt(sharedPref.getString("val_texp_index", String.valueOf(val_texp_index)));
        val_lens_x_global = Integer.parseInt(sharedPref.getString("val_lens_x_global", String.valueOf(val_lens_x_global)));
        val_lens_z_global = Integer.parseInt(sharedPref.getString("val_lens_z_global", String.valueOf(val_lens_z_global)));
        val_laser_red_global = Integer.parseInt(sharedPref.getString("val_laser_red_global", String.valueOf(val_laser_red_global)));
    }

}