package de.nanoimaging.stormimager.acquisition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
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
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
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
import android.widget.ImageView;
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
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfFloat;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
//import org.opencv.core.Size;

import java.io.File;
import java.io.IOException;
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
import java.util.concurrent.Semaphore;

import de.nanoimaging.stormimager.R;
import de.nanoimaging.stormimager.camera.CameraImpl;
import de.nanoimaging.stormimager.camera.CameraInterface;
import de.nanoimaging.stormimager.camera.CameraStates;
import de.nanoimaging.stormimager.camera.capture.YuvImageCapture;
import de.nanoimaging.stormimager.databinding.ActivityAcquireBinding;
import de.nanoimaging.stormimager.network.MqttClient;
import de.nanoimaging.stormimager.network.MqttClientInterface;
import de.nanoimaging.stormimager.process.VideoProcessor;
import de.nanoimaging.stormimager.tasks.FindFocusTask;
import de.nanoimaging.stormimager.tflite.TFLitePredict;
import de.nanoimaging.stormimager.utils.HideNavBarHelper;
import de.nanoimaging.stormimager.utils.ImageUtils;
import de.nanoimaging.stormimager.utils.OpenCVUtil;
import de.nanoimaging.stormimager.utils.PermissionUtil;

/**
 * Created by Bene on 26.09.2015.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class AcquireActivity extends Activity implements FragmentCompat.OnRequestPermissionsResultCallback, AcquireSettings.NoticeDialogListener ,ZFocusInterface {


    String STATE_CALIBRATION = "state_calib";       // STate signal sent to ESP for light signal
    String STATE_WAIT = "state_wait";               // STate signal sent to ESP for light signal
    String STATE_RECORD = "state_record";           // STate signal sent to ESP for light signal

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
    double val_stdv_max = 0;                            // for focus stdv
    int i_search_maxintensity = 0;                      // global counter for number of search steps
    int val_lens_x_maxintensity = 0;                    // lens-position for maximum intensity
    int val_lens_x_global_old = 0;                      // last lens position before optimization
    int ROI_SIZE = 512;                                 // region which gets cropped to measure the coupling efficiencey
    boolean is_findcoupling_coarse = true;              // State if coupling is in fine mode
    boolean is_findcoupling_fine = false;               // State if coupling is in coarse mode

    // File IO parameters
    File myVideoFileName = new File("");
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
    int val_stepsize_focus_z = 1;                      // Stepsize to move the objective lens
    int val_lens_x_global = 0;                          // global position for the x-lens
    int val_lens_z_global = 0;                          // global position for the z-lens
    int val_laser_red_global = 0;                       // global value for the laser

    int val_sofi_amplitude_z = 20; // amplitude of the lens in each periode
    int val_sofi_amplitude_x = 20; // amplitude of the lens in each periode

    boolean is_SOFI_x = false;
    boolean is_SOFI_z = false;

    // Tensorflow stuff
    int Nx_in = 128;
    int Ny_in = Nx_in;
    int N_time = 20;
    int N_upscale = 2; // Upscalingfactor

    int i_time = 0;     // global counter for timesteps to feed the neural network

    boolean is_display_result = false;
    Bitmap myresult_bmp = null;

    private TFLitePredict mypredictor;
    private TFLitePredict mypredictor_mean;
    private TFLitePredict mypredictor_stdv;
    String mymodelfile = "converted_model256_20.tflite";
    String mymodelfile_mean = "converted_model_mean.tflite";
    String mymodelfile_stdv = "converted_model_stdv.tflite";
    List<Mat> listMat = new ArrayList<>();
    // define ouput Data to store result
    float[] TF_input = new float[(int)(Nx_in*Ny_in*N_time)];

    // Need to convert TestMat to float array to feed into TF
    MatOfFloat TF_input_f = new MatOfFloat(CvType.CV_32F);

    boolean is_process_sofi = false;

    private CameraInterface cameraInterface;
    private MqttClientInterface mqttClientInterface;
    private YuvImageCapture yuvImageCapture;
    private FindFocusTask findFocusTask;


    /**
     * CAMERA-Related stuff
     */
    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    /**
     * Tolerance when comparing aspect ratios.
     */
    private static final double ASPECT_RATIO_TOLERANCE = 0.005;

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
     * A lock protecting camera state.
     */
    private final Object mCameraStateLock = new Object();

    /**
     * A {@link Handler} for showing {@link Toast}s on the UI thread.
     */
    private final Handler mMessageHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            Toast.makeText(AcquireActivity.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
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
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;


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
                // Do lens aligning here
                global_bitmap = binding.texture.getBitmap();
                global_bitmap = Bitmap.createBitmap(global_bitmap, 0, 0, global_bitmap.getWidth(), global_bitmap.getHeight(), binding.texture.getTransform(null), true);

                // START THREAD AND ALIGN THE LENS
                if (is_findcoupling_coarse) {
                    new run_calibration_thread_coarse("CoarseThread");
                } else if (is_findcoupling_fine) {
                    new run_calibration_thread_fine("FineThread");
                }
            }

            else if(is_process_sofi & !isCameraBusy){
                // Collect images for SOFI-prediction
                global_bitmap = binding.texture.getBitmap();
                global_bitmap = Bitmap.createBitmap(global_bitmap, 0, 0, global_bitmap.getWidth(), global_bitmap.getHeight(), binding.texture.getTransform(null), true);

                new run_sofiprocessing_thread("ProcessingThread");
            }
            else if(is_display_result){
                Log.i(TAG, "Displaying result of SOFI prediction");
                binding.imageViewPreview.setVisibility(View.VISIBLE);
                try{
                    binding.imageViewPreview.setImageBitmap(myresult_bmp);
                    is_display_result = false;
                }
                catch(Exception e){
                    Log.i(TAG, "Could not display result...");
                }


            }

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
    private static int sensorToDeviceRotation(CameraInterface c, int deviceOrientation) {
        int sensorOrientation = c.getSensorOrientation();

        // Get device orientation in degrees
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);

        // Reverse device orientation for front-facing cameras
        if (c.isFrontCamera()) {
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

    /**
     * view binding from activity_acquire.xml
     */
    private ActivityAcquireBinding binding;
    private PermissionUtil permissionUtil;
    private HideNavBarHelper hideNavBarHelper;

    //**********************************************************************************************
    //  Method onCreate
    //**********************************************************************************************
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAcquireBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        permissionUtil = new PermissionUtil();
        hideNavBarHelper = new HideNavBarHelper();

        // Initialize OpenCV using external library for now //TODO use internal!
        OpenCVLoader.initDebug();
        //OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);

        cameraInterface = new CameraImpl();
        binding.texture.setSurfaceTextureListener(mSurfaceTextureListener);
        findFocusTask = new FindFocusTask(cameraInterface,this);
        // load tensorflow stuff
        mypredictor = new TFLitePredict(AcquireActivity.this, mymodelfile, Nx_in, Ny_in, N_time, N_upscale);
        mypredictor_mean = new TFLitePredict(AcquireActivity.this, mymodelfile_mean, Nx_in, Ny_in, N_time);
        mypredictor_stdv = new TFLitePredict(AcquireActivity.this, mymodelfile_stdv, Nx_in, Ny_in, N_time);

        // Load previously saved settings and set GUIelements
        SharedPreferences sharedPref = this.getSharedPreferences(
                PREFERENCE_FILE_KEY, Context.MODE_PRIVATE);
        editor = sharedPref.edit();


        // build the pop-up settings activity
        settingsDialogFragment = new AcquireSettings();

        // start MQTT
        mqttClientInterface = new MqttClient(new MqttClientInterface.MessageEvent() {
            @Override
            public void onMessage(String msg) {
                showToast(msg);
            }
        });
        if (isNetworkAvailable()) {
            showToast("Connecting MQTT");
            mqttClientInterface.connect();
        } else
            showToast("We don't have network");

        setGUIelements(sharedPref);
        // *****************************************************************************************
        //  Camera STUFF
        //******************************************************************************************
        // Setup a new OrientationEventListener.  This is used to handle rotation events like a
        // 180 degree rotation that do not normally trigger a call to onCreate to do view re-layout
        // or otherwise cause the preview TextureView's size to change.

        mOrientationListener = new OrientationEventListener(this,
                SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (binding.texture != null && binding.texture.isAvailable()) {
                    configureTransform(binding.texture.getWidth(), binding.texture.getHeight());
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
        binding.acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up



        /*
        Seekbar for the ISO-Setting
         */
        binding.seekBarIso.setVisibility(View.GONE);
        binding.seekBarShutter.setVisibility(View.GONE);

        binding.seekBarIso.setMax(isovalues.length - 1);
        binding.seekBarIso.setProgress(val_iso_index); // 50x16=800
        binding.textViewIso.setText("Iso:" + isovalues[val_iso_index]);

        binding.seekBarIso.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    editor.putInt("val_iso_index", progress);
                    editor.commit();

                    binding.textViewIso.setText("Iso:" + isovalues[progress]);
                    global_isoval = isovalues[progress];
                    cameraInterface.setIso(Integer.parseInt(isovalues[progress]));
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
        Seekbar for the shutter-Setting
         */
        binding.seekBarShutter.setMax(texpvalues.length - 1);
        binding.seekBarShutter.setProgress(val_texp_index); // == 1/30
        binding.textViewShutter.setText("Shutter:" + texpvalues[val_texp_index]);

        binding.seekBarShutter.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    editor.putInt("val_texp_index", progress);
                    editor.commit();

                    global_expval = texpvalues[progress];
                    binding.textViewShutter.setText("Shutter:" + texpvalues[progress]);
                    int msexpo = (int) getMilliSecondStringFromShutterString(texpvalues[progress]);
                    cameraInterface.setExposureTime(msexpo);
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
        binding.seekBarLensX.setMax(PWM_RES);
        binding.seekBarLensX.setProgress(0);

        String text_lens_x_pre = "Lens (X): ";
        binding.textViewLensX.setText(text_lens_x_pre + binding.seekBarLensX.getProgress());

        binding.seekBarLensX.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        val_lens_x_global = progress;
                        setLensX(val_lens_x_global);
                        binding.textViewLensX.setText(text_lens_x_pre + String.format("%.2f", val_lens_x_global * 1.));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        binding.textViewLensX.setText(text_lens_x_pre + String.format("%.2f", val_lens_x_global * 1.));
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        binding.textViewLensX.setText(text_lens_x_pre + String.format("%.2f", val_lens_x_global * 1.));
                    }
                }
        );


        /*
        Seekbar for Lens in X-direction
         */
        binding.seekBarLensZ.setMax(PWM_RES);
        binding.seekBarLensZ.setProgress(val_lens_z_global);

        String text_lens_z_pre = "Lens (Z): ";
        binding.textViewLensZ.setText(text_lens_z_pre + binding.seekBarLensZ.getProgress());

        binding.seekBarLensZ.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        val_lens_z_global = progress;
                        setLensZ(val_lens_z_global);
                        binding.textViewLensZ.setText(text_lens_z_pre + val_lens_z_global * 1.);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        binding.textViewLensZ.setText(text_lens_z_pre + val_lens_z_global * 1.);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        binding.textViewLensZ.setText(text_lens_z_pre + val_lens_z_global * 1.);
                    }
                }

        );

        /*
        Seekbar for Lens in X-direction
         */

        binding.seekBarLaser.setMax(PWM_RES-10); // just make sure there is no overlow!
        binding.seekBarLaser.setProgress(0);

        String text_laser_pre = "Laser (I): ";
        binding.textViewLaser.setText(text_laser_pre + binding.seekBarLaser.getProgress());

        binding.seekBarLaser.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        val_laser_red_global = progress;
                        setLaser(val_laser_red_global);
                        binding.textViewLaser.setText(text_laser_pre + String.format("%.2f", val_laser_red_global * 1.));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        binding.textViewLaser.setText(text_laser_pre + String.format("%.2f", val_laser_red_global * 1.));
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        binding.textViewLaser.setText(text_laser_pre + String.format("%.2f", val_laser_red_global * 1.));
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
        binding.btnSofi.setText("SOFI (x): 0");
        binding.btnSofi.setTextOn("SOFI (x): 1");
        binding.btnSofi.setTextOff("SOFI (x): 0");

        binding.btnSetup.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openSettingsDialog();
            }
        });

        binding.btnLiveView.setText("LIVE: 0");
        binding.btnLiveView.setTextOn("LIVE: 1");
        binding.btnLiveView.setTextOff("LIVE: 0");



        //******************* SOFI-Mode  ********************************************//
        // This is to let the lens vibrate by a certain amount
        binding.btnSofi.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.i(TAG, "Checked");
                    // turn on fluctuation
                    mqttClientInterface.set_lens_sofi_z(String.valueOf(val_sofi_amplitude_z));
                } else {
                    mqttClientInterface.set_lens_sofi_z(String.valueOf(0));
                }
            }

        });

        //******************* Live PRocessing-Mode  ********************************************//
        // This is to let the lens vibrate by a certain amount
        binding.btnLiveView.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.i(TAG, "Live PRocessing Checked");
                    // turn on fluctuation
                    is_process_sofi = true;
                } else {
                    is_process_sofi = false;
                    binding.imageViewPreview.setVisibility(View.GONE);
                }
            }

        });

        //******************* Move X ++ ********************************************//
        binding.buttonXLensPlus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                val_lens_x_global = val_lens_x_global+10;
                setLensX(val_lens_x_global);
                binding.textViewLensX.setText(text_lens_x_pre + String.format("%.2f", val_lens_x_global * 1.));
                binding.seekBarLensX.setProgress(val_lens_x_global);
            }

        });

        //******************* Move X -- ********************************************//
        binding.buttonXLensMinus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                val_lens_x_global = val_lens_x_global-10;
                setLensX(val_lens_x_global);
                binding.textViewLensX.setText(text_lens_x_pre + String.format("%.2f", val_lens_x_global * 1.));
                binding.seekBarLensX.setProgress(val_lens_x_global);
            }

        });


        //******************* Move X ++ ********************************************//
        binding.buttonZFocusPlus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setZFocus(val_stepsize_focus_z);
            }

        });

        //******************* Move X -- ********************************************//
        binding.buttonZFocusMinus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setZFocus(-val_stepsize_focus_z);
            }

        });

        //******************* Optimize Coupling ********************************************//
        binding.btnCalib.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                showToast("Optimize  Coupling");
                // turn on the laser
                setLaser(val_laser_red_global);
                Log.i(TAG, "Lens Calibration in progress");
                String my_gui_text = "Lens Calibration in progress";
                is_findcoupling = true;
                is_measurement = false;

                binding.textViewGuiText.setText(my_gui_text);
                mqttClientInterface.setState(STATE_CALIBRATION);
            }
        });

        //******************* Autofocus ********************************************//
        binding.btnAutofocus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                showToast("Start Autofocussing ");
                // turn on the laser
                Log.i(TAG, "Autofocussing in progress");
                String my_gui_text = "Lens Calibration in progress";

                binding.textViewGuiText.setText(my_gui_text);
                if (!findFocusTask.isSearchForFocus()) {
                    yuvImageCapture.setYuvToBitmapEventListner(findFocusTask);
                    findFocusTask.process();
                }
                else
                {
                    yuvImageCapture.setYuvToBitmapEventListner(null);
                    findFocusTask.stop();
                }

            }
        });


        //******************* Start MEasurement ********************************************//
        binding.btnStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(!is_measurement&!is_findcoupling){
                    is_measurement = true;
                    new run_sofimeasurement().execute();
                }
            }
        });

        //******************* Stop Measurement ********************************************//
        binding.btnStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                is_measurement = false;
                is_findcoupling = false;
                is_findcoupling_coarse = false;
                is_findcoupling_coarse = true;
                findFocusTask.stop();
                yuvImageCapture.setYuvToBitmapEventListner(null);
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
        cameraInterface.closeCamera();
        super.onPause();
        cameraInterface.stopBackgroundThread();
    }

    @Override
    public void onResume() {
        super.onResume();
        cameraInterface.startBackgroundThread();
        if (!permissionUtil.hasAllPermissionsGranted()) {
            permissionUtil.requestCameraPermissions(this);
            return;
        }
        try {

            cameraInterface.openCamera("0");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        if (mOrientationListener != null && mOrientationListener.canDetectOrientation()) {
            mOrientationListener.enable();
        }
        mMediaRecorder = new MediaRecorder();


        // SET CAMERA PARAMETERS FROM GUI
        cameraInterface.setIso(Integer.parseInt(isovalues[val_iso_index]));
        int msexpo = (int) getMilliSecondStringFromShutterString(texpvalues[val_texp_index]);
        cameraInterface.setExposureTime(msexpo);
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
        if (!permissionUtil.onRequestPermissionsResult(requestCode,permissions,grantResults)) {
            showMissingPermissionError();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus)
            hideNavBarHelper.HIDENAVBAR(getWindow());
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


    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = AcquireActivity.this;
        synchronized (mCameraStateLock) {
            if (null == binding.texture || null == activity || !permissionUtil.hasAllPermissionsGranted()) {
                return;
            }

            // For still image captures, we always use the largest available size.
            Size largest = Collections.max(Arrays.asList(cameraInterface.getSizesForSurfaceTexture()),
                    new CompareSizesByArea());
            // Find the rotation of the device relative to the native device orientation.
            int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            Point displaySize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

            // Find the rotation of the device relative to the camera sensor's orientation.
            int totalRotation = sensorToDeviceRotation(cameraInterface, deviceRotation);

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

            Size maxpreviewsize = cameraInterface.getMaxPreviewSize();
            // Preview should not be larger than display size and 1080p.
            if (maxPreviewWidth > maxpreviewsize.getWidth()) {
                maxPreviewWidth =  maxpreviewsize.getWidth();
            }

            if (maxPreviewHeight >  maxpreviewsize.getHeight()) {
                maxPreviewHeight =  maxpreviewsize.getHeight();
            }

            // Find the best preview size for these view dimensions and configured JPEG size.
            Size previewSize = chooseOptimalSize(cameraInterface.getSizesForSurfaceTexture(),
                    rotatedViewWidth, rotatedViewHeight, maxPreviewWidth, maxPreviewHeight,
                    largest);

            if (swappedDimensions) {
                binding.texture.setAspectRatio(
                        previewSize.getHeight(), previewSize.getWidth());
            } else {
                binding.texture.setAspectRatio(
                        previewSize.getWidth(), previewSize.getHeight());
            }

            // Find rotation of device in degrees (reverse device orientation for front-facing
            // cameras).
            int rotation = (cameraInterface.isFrontCamera()) ?
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

            binding.texture.setTransform(matrix);

            // Start or restart the active capture session if the preview was initialized or
            // if its aspect ratio changed significantly.
            if (mPreviewSize == null || !checkAspectsEqual(previewSize, mPreviewSize)) {
                mPreviewSize = previewSize;
                if (cameraInterface.getCameraState() != CameraStates.Closed) {
                    startPreview();
                }
            }
        }
    }

    // Utility classes and methods:
    // *********************************************************************************************


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

    /**
     * Start the camera preview.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startPreview() {
        if (!binding.texture.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            cameraInterface.stopPreview();
            if (yuvImageCapture != null)
                yuvImageCapture.release();
            SurfaceTexture texture = binding.texture.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            cameraInterface.setSurface(previewSurface);
            yuvImageCapture = new YuvImageCapture(mPreviewSize);
            cameraInterface.addImageCaptureInterface(yuvImageCapture);
            cameraInterface.startPreview();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        binding.seekBarIso.post(new Runnable() {
            @Override
            public void run() {
                binding.seekBarIso.setVisibility(View.VISIBLE);
                binding.seekBarShutter.setVisibility(View.VISIBLE);
            }
        });
    }

    public void startRecordingVideo() {
        if (!binding.texture.isAvailable() || null == cameraInterface.getPreviewSize()) {
            return;
        }
        try {
            cameraInterface.stopPreview();
            yuvImageCapture.release();
            setUpMediaRecorder();
            SurfaceTexture texture = binding.texture.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            cameraInterface.setSurface(previewSurface);
            //MediaRecorder setup for surface
            Surface recorderSurface = mMediaRecorder.getSurface();
            cameraInterface.setSurface(recorderSurface);
            yuvImageCapture = new YuvImageCapture(mPreviewSize);
            cameraInterface.addImageCaptureInterface(yuvImageCapture);
            // Start a capture session
            cameraInterface.setCaptureEventListner(() -> {
                mIsRecordingVideo = true;
                cameraInterface.setCaptureEventListner(null);
                // Start recording
                try {
                    mMediaRecorder.start();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            });
            cameraInterface.startPreview();
        } catch (IOException | CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void stopRecordingVideo() throws Exception {
        // UI
        mIsRecordingVideo = false;
        cameraInterface.stopPreview();
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        startPreview();
    }


    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }


    // -------------------------------
    // ------ MQTT STUFFF -----------
    //- ------------------------------

    public void setIPAddress(String mIPaddress) {
        mqttClientInterface.setIPAddress(mIPaddress);
        editor.putString("myIPAddress", mIPaddress);
        editor.commit();
    }

    public String getIpAdress()
    {
        return mqttClientInterface.getIPAdress();
    }

    public void setSOFIX(boolean misSOFI_X, int mvalSOFIX) {
        val_sofi_amplitude_x = mvalSOFIX;
        is_SOFI_x = misSOFI_X;
        mqttClientInterface.set_lens_sofi_x(String.valueOf(val_sofi_amplitude_x));
        editor.putInt("val_sofi_amplitude_x", val_sofi_amplitude_x);
        editor.commit();
    }

    public void setSOFIZ(boolean misSOFI_Z, int mvalSOFIZ) {
        val_sofi_amplitude_z = mvalSOFIZ;
        is_SOFI_z = misSOFI_Z;
        mqttClientInterface.set_lens_sofi_z(String.valueOf(val_sofi_amplitude_z));
        editor.putInt("val_sofi_amplitude_z", val_sofi_amplitude_z);
        editor.commit();
    }

    public void setValSOFIX(int mval_sofi_amplitude_x) {
        val_sofi_amplitude_x = mval_sofi_amplitude_x;
        // Save the IP address for next start
        editor.putInt("mval_sofi_amplitude_x", mval_sofi_amplitude_x);
        editor.commit();
    }

    public void setValSOFIZ(int mval_sofi_amplitude_z) {
        val_sofi_amplitude_z = mval_sofi_amplitude_z;
        // Save the IP address for next start
        editor.putInt("mval_sofi_amplitude_z", mval_sofi_amplitude_z);
        editor.commit();
    }

    public void setValDurationMeas(int mval_duration_measurement) {
        val_duration_measurement = mval_duration_measurement;
        // Save the IP address for next start
        editor.putInt("val_duration_measurement", mval_duration_measurement);
        editor.commit();
    }

    public void setValPeriodMeas(int mval_period_measurement) {
        val_period_measurement = mval_period_measurement;
        // Save the IP address for next start
        editor.putInt("val_period_measurement", mval_period_measurement);
        editor.commit();
    }

    public void setNValPeriodCalibration(int mval_period_calibration) {
        val_nperiods_calibration = mval_period_calibration;
        // Save the IP address for next start
        editor.putInt("val_nperiods_calibration", mval_period_calibration);
        editor.commit();
    }

    void MQTT_Reconnect(String mIP) {
        mqttClientInterface.stopConnection();
        mqttClientInterface.setIPAddress(mIP);
        mqttClientInterface.connect();
        showToast("IP-Address set to: " + mIP);

        // Save the IP address for next start
        editor.putString("myIPAddress", mIP);
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
            mqttClientInterface.set_laser(String.valueOf(lin2qudratic(laserintensity, PWM_RES)));
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

    @Override
    public void setZFocus(int stepsize) {
        if(stepsize>0) mqttClientInterface.set_focus_z_fwd(String.valueOf(Math.abs(stepsize)));
        if(stepsize<0) mqttClientInterface.set_focus_z_bwd(String.valueOf(Math.abs(stepsize)));

        try {Thread.sleep(stepsize*80); }
        catch (Exception e) { Log.e(TAG, String.valueOf(e));}
    }



    void setLensX(int lensposition) {
        if ((lensposition < PWM_RES) && (lensposition >=0)) {
            if (lensposition ==  0)lensposition=1;
            mqttClientInterface.set_lens_x(String.valueOf(lin2qudratic(lensposition, PWM_RES)));
            // Wait until the command was actually sent
            if(is_findcoupling){
            try {
                Thread.sleep(MQTT_SLEEP);
            } catch (Exception e) {
                Log.e(TAG, String.valueOf(e));
            }
            }
            editor.putInt("val_lens_x_global", lensposition);
            editor.commit();


        }
    }

    void setLensZ(int lensposition) {
        if (lensposition < PWM_RES && lensposition >= 0) {
            if (lensposition ==  0)lensposition=1;
            mqttClientInterface.set_lens_z(String.valueOf(lin2qudratic(lensposition, PWM_RES)));
            // Wait until the command was actually sent
            if(is_findcoupling){
                try {
                    Thread.sleep(MQTT_SLEEP);
                } catch (Exception e) {
                    Log.e(TAG, String.valueOf(e));
                }
            }
            editor.putInt("val_lens_z_global", lensposition);
            editor.commit();

        }
    }

    @Override
    public void onGuiMessage(String msg) {
        binding.textViewGuiText.post(new Runnable() {
            public void run() {
                binding.textViewGuiText.setText(msg);
            }
        });
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
            binding.acquireProgressBar.setVisibility(View.VISIBLE); // Make invisible at first, then have it pop up
            binding.acquireProgressBar.setMax(val_nperiods_calibration);
            showToast("Start Measurements");

        }

        @Override
        protected void onProgressUpdate(Void... params) {
            binding.acquireProgressBar.setProgress(i_meas);

            // some GUI interaction
            binding.textViewGuiText.setText(my_gui_text);
            binding.btnStart.setEnabled(false);

            // Update GUI
            String text_lens_x_pre = "Lens (X): ";
            binding.textViewLensX.setText(text_lens_x_pre + String.format("%.2f", val_lens_x_global * 1.));
            binding.seekBarLensX.setProgress(val_lens_x_global);

            String text_laser_pre = "Laser: ";
            binding.textViewLaser.setText(text_laser_pre + String.format("%.2f", val_laser_red_global * 1.));
            binding.seekBarLaser.setProgress(val_laser_red_global);
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
                    mqttClientInterface.setState(STATE_CALIBRATION);
                }
                else if(!is_findcoupling&is_measurement) {// if no coupling has to be done -> measure!
                    mqttClientInterface.setState(STATE_RECORD);
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
                    mqttClientInterface.set_lens_sofi_z(String.valueOf(val_sofi_amplitude_z));
                    mSleep(val_duration_measurement * 1000); //Let AEC stabalize if it's on

                    // turn off fluctuation
                    mqttClientInterface.set_lens_sofi_z(String.valueOf(0));
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
                    //setLensX(1); // Heavily detune the lens to reduce phototoxicity

                    // Once in a while update the GUI
                    my_gui_text = "Waiting for next measurements. "+String.valueOf(val_nperiods_calibration-i_meas)+"/"+String.valueOf(val_nperiods_calibration)+"left until recalibration";
                    publishProgress();
                    mqttClientInterface.setState(STATE_WAIT);

                    // only perform the measurements if the camera is not looking for best coupling

                    my_gui_text = "Processing Video...";
                    publishProgress();
                    VideoProcessor vidproc = new VideoProcessor(String.valueOf(myVideoFileName), ROI_SIZE, global_framerate);
                    vidproc.setupvideo();
                    vidproc.process(10);
                    vidproc.saveresult(mypath + File.separator + "VID_" + String.valueOf(i_meas) + ".png");

                    for(int iwait = 0; iwait<val_period_measurement*10; iwait++){
                        if(!is_measurement)break;
                        my_gui_text = "Waiting: "+String.valueOf(iwait/10) + "/" +String.valueOf(val_period_measurement)+"s";
                        publishProgress();
                        mSleep(100);


                    }

                    i_meas++;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);

            // Set some GUI components
            binding.acquireProgressBar.setVisibility(View.GONE); // Make invisible at first, then have it pop up
            binding.textViewGuiText.setText("Done Measurements.");
            binding.btnStart.setEnabled(true);

            // Switch off laser
            setLaser(0);

            // free memory
            is_findcoupling = false;
            showToast("Stop Measurements");
            System.gc();
        }
    }

    class run_sofiprocessing_thread implements Runnable {

        Thread mythread;
        // to stop the thread
        private boolean exit;
        private String name;

        run_sofiprocessing_thread(String threadname) {
            name = threadname;
            mythread = new Thread(this, name);
            exit = false;
            mythread.start(); // Starting the thread
        }

        // execution of thread starts from run() method
        public void run() {
            try {

                isCameraBusy = true;
                if(i_time < N_time & is_process_sofi){
                    // convert the Bitmap coming from the camera frame to MAT and crop it
                    Mat src = new Mat();
                    Utils.bitmapToMat(global_bitmap, src);
                    Mat grayMat = new Mat();
                    Imgproc.cvtColor(src, grayMat, Imgproc.COLOR_BGRA2BGR);
                    // Extract one frame channel
                    List<Mat> rgb_list = new ArrayList(3);
                    Core.split(grayMat,rgb_list);
                    rgb_list.get(0).copyTo(grayMat);

                    Rect roi = new Rect((int)src.width()/2-Nx_in/2, (int)src.height()/2-Ny_in/2, Nx_in, Ny_in);
                    Mat dst = new Mat(grayMat, roi);


                    // accumulate the result of all frames
                    //dst.convertTo(dst, CvType.CV_32FC1);

                    // preprocess the frame
                    // dst = preprocess(dst);
                    //Log.e(TAG,dst.dump());

                    // convert MAT to MatOfFloat
                    dst.convertTo(TF_input_f,CvType.CV_32F);
                    //Log.e(TAG,dst.dump());

                    // Add the frame to the list
                    listMat.add(TF_input_f);
                    i_time ++;

                    // Release memory
                    src.release();
                    grayMat.release();
                    dst.release();
                }
                else{
                    // reset counters
                    i_time = 0;
                    //is_process_sofi = false;

                    // If a stack of batch_size images is loaded, feed it into the TF object and run iference
                    Mat tmp_dst = new Mat();
                    Core.merge(listMat, tmp_dst); // Log.i(TAG, String.valueOf(tmp_dst));
                    listMat = new ArrayList<>(); // reset the list

                    // define ouput Data to store result
                    // TF_output = new float[(int) (OUTPUT_SIZE[0]*OUTPUT_SIZE[1]*OUTPUT_SIZE[2]*OUTPUT_SIZE[3])];

                    // get the frame/image and allocate it in the MOF object
                    tmp_dst.get(0, 0, TF_input);

                    tmp_dst.release();

                    Log.i(TAG, "All frames have been accumulated");

                    String is_output_nn = "nn_sofi"; // nn_stdv, nn_mean, nn_sofi
                    Mat myresult = null;
                    if(is_output_nn=="nn_sofi"){
                        myresult = mypredictor.predict(TF_input);
                    }
                    else if(is_output_nn=="nn_stdv"){
                        myresult = mypredictor_mean.predict(TF_input);
                    }
                    else{
                        myresult = mypredictor_stdv.predict(TF_input);
                    }
                    is_display_result = true;

                    String mytimestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
                    String myresultpath = String.valueOf(Environment.getExternalStorageDirectory() + "/STORMimager/"+mytimestamp+"_test.png");
                    myresult = ImageUtils.imwriteNorm(myresult, myresultpath);

                    myresult_bmp = Bitmap.createBitmap(Nx_in*N_upscale, Ny_in*N_upscale, Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(myresult, myresult_bmp);

                }





                System.gc();
                global_bitmap.recycle();
                }


            catch(Exception v){
                System.out.println(v);
                isCameraBusy=false;
            }

            isCameraBusy=false;
            System.out.println(name + " Stopped.");
        }

        // for stopping the thread
        public void stop ()
        {
            exit = true;
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
                        int i_mean = (int)OpenCVUtil.measureCoupling(dst, OpenCVUtil.ROI_SIZE, 9);
                        String mycouplingtext = "Coupling (coarse) @ "+String.valueOf(i_search_maxintensity)+" is "+String.valueOf(i_mean)+"with max: "+String.valueOf(val_mean_max);
                        binding.textViewGuiText.post(new Runnable() {
                            public void run() {
                                binding.textViewGuiText.setText(mycouplingtext);
                            }
                        });
                        Log.i(TAG, mycouplingtext);
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


                    int i_mean = (int) OpenCVUtil.measureCoupling(dst, OpenCVUtil.ROI_SIZE, 9);
                    String mycouplingtext = "Coupling (fine) @ "+String.valueOf(i_search_maxintensity)+" is "+String.valueOf(i_mean)+"with max: "+String.valueOf(val_mean_max);
                    binding.textViewGuiText.post(new Runnable() {
                        public void run() {
                            binding.textViewGuiText.setText(mycouplingtext);
                        }
                    });
                    Log.i(TAG, mycouplingtext);
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

    void setGUIelements(SharedPreferences sharedPref){
        mqttClientInterface.setIPAddress(sharedPref.getString("myIPAddress", "192.168.43.88"));
        val_nperiods_calibration = sharedPref.getInt("val_nperiods_calibration", val_nperiods_calibration);
        val_period_measurement = sharedPref.getInt("val_period_measurement", val_period_measurement);
        val_duration_measurement = sharedPref.getInt("val_duration_measurement", val_duration_measurement);
        val_sofi_amplitude_x = sharedPref.getInt("val_sofi_amplitude_x", val_sofi_amplitude_x);
        val_sofi_amplitude_z = sharedPref.getInt("val_sofi_amplitude_z", val_sofi_amplitude_z);
        val_iso_index = sharedPref.getInt("val_iso_index", val_iso_index);
        val_texp_index = sharedPref.getInt("val_texp_index", val_texp_index);
        val_lens_x_global = sharedPref.getInt("val_lens_x_global",val_lens_x_global);
        val_lens_z_global = sharedPref.getInt("val_lens_z_global", val_lens_z_global);
        val_laser_red_global = sharedPref.getInt("val_laser_red_global", val_laser_red_global);
    }



}