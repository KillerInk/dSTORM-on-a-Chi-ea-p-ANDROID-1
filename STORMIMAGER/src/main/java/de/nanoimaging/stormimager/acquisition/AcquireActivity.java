package de.nanoimaging.stormimager.acquisition;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.media.CamcorderProfile;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.support.annotation.RequiresApi;
import android.support.v13.app.FragmentCompat;
import android.util.Log;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.nanoimaging.stormimager.R;
import de.nanoimaging.stormimager.camera.CameraImpl;
import de.nanoimaging.stormimager.camera.CameraInterface;
import de.nanoimaging.stormimager.camera.CameraStates;
import de.nanoimaging.stormimager.camera.VideoRecorder;
import de.nanoimaging.stormimager.camera.capture.YuvImageCapture;
import de.nanoimaging.stormimager.databinding.ActivityAcquireBinding;
import de.nanoimaging.stormimager.events.SofiMeasurementUpdateUiEvent;
import de.nanoimaging.stormimager.events.StartRecordingEvent;
import de.nanoimaging.stormimager.events.StopRecordingEvent;
import de.nanoimaging.stormimager.microscope.MicroScopeController;
import de.nanoimaging.stormimager.microscope.MicroScopeInterface;
import de.nanoimaging.stormimager.tasks.FindCouplingTask;
import de.nanoimaging.stormimager.tasks.FindFocusTask;
import de.nanoimaging.stormimager.tasks.SofiMeasurementTask;
import de.nanoimaging.stormimager.tasks.SofiProcessTask;
import de.nanoimaging.stormimager.utils.CameraUtil;
import de.nanoimaging.stormimager.utils.HideNavBarHelper;
import de.nanoimaging.stormimager.utils.PermissionUtil;
import de.nanoimaging.stormimager.utils.SharedValues;
import io.github.controlwear.virtual.joystick.android.JoystickView;

//import org.opencv.core.Size;

/**
 * Created by Bene on 26.09.2015.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class AcquireActivity extends Activity implements
        FragmentCompat.OnRequestPermissionsResultCallback,
        AcquireSettings.NoticeDialogListener,
        GuiMessageEvent{
    /**
     * GUI related stuff
     */
    public DialogFragment settingsDialogFragment;       // For the pop-up window for external settings
    final String TAG = "STORMimager_AcquireActivity";         // TAG for the APP

    // Save settings for later
    private final String PREFERENCE_FILE_KEY = "myAppPreference";

    /**
     * Whether the app is recording video now
     */

    // Camera parameters
    String global_isoval = "0";                         // global iso-value
    String global_expval = "0";                         // global exposure time in ms
    private String[] isovalues;                         // array to store available iso values
    private String[] texpvalues;                        // array to store available exposure times
    int val_iso_index = 3;                              // Slider value for
    int val_texp_index = 10;

    // File IO parameters
    File myVideoFileName = new File("");
    // (default) global file paths
    final String mypath_measurements = Environment.getExternalStorageDirectory() + "/STORMimager/";
    String myfullpath_measurements = mypath_measurements;




    /**
     * HARDWARE Settings for MQTT related values
     */
    int PWM_RES = (int) (Math.pow(2, 15)) - 1;          // bitrate of the PWM signal 15 bit
    int val_stepsize_focus_z = 1;                      // Stepsize to move the objective lens
    int val_lens_z_global = 0;                          // global position for the z-lens


    /**
     * Camera-related Stuff
     */
    int global_framerate = 20;
    private CameraInterface cameraInterface;
    private YuvImageCapture yuvImageCapture;
    private FindFocusTask findFocusTask;
    SharedValues sharedValues;
    VideoRecorder recorder;

    static {
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }
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
        }
    };

    public AcquireActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    //**********************************************************************************************

    /**
     * view binding from activity_acquire.xml
     */
    private ActivityAcquireBinding binding;
    private PermissionUtil permissionUtil;
    private HideNavBarHelper hideNavBarHelper;
    MicroScopeInterface microScopeInterface;
    private FindCouplingTask couplingTask;
    private SofiMeasurementTask sofiMeasurementTask;
    private SofiProcessTask sofiProcessTask;

    //**********************************************************************************************
    //  Method onCreate
    //**********************************************************************************************
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //TODO: Dirty Workaround
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);

        EventBus.getDefault().register(this);
        binding = ActivityAcquireBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Initialize OpenCV using external library for now //TODO use internal!
        OpenCVLoader.initDebug();
        //OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);

        permissionUtil = new PermissionUtil();
        hideNavBarHelper = new HideNavBarHelper();

        sharedValues = new SharedValues();
        cameraInterface = new CameraImpl();
        recorder = new VideoRecorder();
        microScopeInterface = new MicroScopeController(sharedValues,this);
        findFocusTask = new FindFocusTask(cameraInterface,this,sharedValues,microScopeInterface);
        couplingTask = new FindCouplingTask(cameraInterface,this,sharedValues,microScopeInterface);
        sofiMeasurementTask = new SofiMeasurementTask(cameraInterface,this,sharedValues,microScopeInterface,recorder,mypath_measurements);
        sofiProcessTask = new SofiProcessTask(cameraInterface,this,sharedValues,microScopeInterface);

        // load settings from previous session
        loadSettings();
        microScopeInterface.Reconnect(); // Retrieve IP Adress from last run

        // build the pop-up settings activity
        settingsDialogFragment = new AcquireSettings();

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
        initUiItems();

    }

    private void initUiItems() {

        binding.texture.setSurfaceTextureListener(mSurfaceTextureListener);

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
                    global_expval = texpvalues[progress];
                    binding.textViewShutter.setText("Shutter:" + texpvalues[progress]);
                    int msexpo = (int) CameraUtil.getMilliSecondStringFromShutterString(texpvalues[progress]);
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
                        sharedValues.setVal_lens_x_global(progress);
                        microScopeInterface.setLensX(sharedValues.getVal_lens_x_global(),false);
                        binding.textViewLensX.setText(text_lens_x_pre + String.format("%.2f", sharedValues.getVal_lens_x_global() * 1.));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        binding.textViewLensX.setText(text_lens_x_pre + String.format("%.2f", sharedValues.getVal_lens_x_global() * 1.));
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        binding.textViewLensX.setText(text_lens_x_pre + String.format("%.2f", sharedValues.getVal_lens_x_global() * 1.));
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
                        microScopeInterface.setLensZ(val_lens_z_global,false);
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
                        sharedValues.val_laser_red_global = progress;
                        microScopeInterface.setLaser(sharedValues.val_laser_red_global,false);
                        binding.textViewLaser.setText(text_laser_pre + String.format("%.2f", sharedValues.val_laser_red_global * 1.));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        binding.textViewLaser.setText(text_laser_pre + String.format("%.2f", sharedValues.val_laser_red_global * 1.));
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        binding.textViewLaser.setText(text_laser_pre + String.format("%.2f", sharedValues.val_laser_red_global * 1.));
                    }
                }
        );


        /**
         * Joystick for Z-Motion
         */

        JoystickView joystick = (JoystickView) findViewById(R.id.joystickView);
        joystick.setFixedCenter(true); // set up auto-define center
        joystick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // workaround to send values if no change is detected
                microScopeInterface.setZFocus(sharedValues.val_z_step, sharedValues.val_z_speed);
            }
        });

        joystick.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                int direction = 0;
                if(0<angle & angle >180)
                    direction = 1;
                else
                    direction = -1;
                sharedValues.val_z_step = direction * (int) Math.ceil(strength/10)*100;
                sharedValues.val_z_speed = strength*20;
                microScopeInterface.setZFocus(sharedValues.val_z_step, sharedValues.val_z_speed);
            }
        });

        //Create second surface with another holder (holderTransparent) for drawing the rectangle
        //looks not like it get used somewhere
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
                    microScopeInterface.setSOFIZ(true,sharedValues.val_sofi_amplitude_z);
                } else {
                    microScopeInterface.setSOFIZ(false,0);
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
                    sofiProcessTask.setYuvImageCapture(yuvImageCapture);
                    sofiProcessTask.process();
                    binding.imageViewPreview.setVisibility(View.VISIBLE);
                } else {
                    sofiProcessTask.stop();
                    sofiProcessTask.setYuvImageCapture(null);
                    binding.imageViewPreview.setVisibility(View.GONE);
                }
            }

        });

        //******************* Move X ++ ********************************************//
        binding.buttonXLensPlus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sharedValues.setVal_lens_x_global(sharedValues.getVal_lens_x_global()+10);
                microScopeInterface.setLensX(sharedValues.getVal_lens_x_global(),false);
                binding.textViewLensX.setText(text_lens_x_pre + String.format("%.2f", sharedValues.getVal_lens_x_global() * 1.));
                binding.seekBarLensX.setProgress(sharedValues.getVal_lens_x_global());
            }

        });

        //******************* Move X -- ********************************************//
        binding.buttonXLensMinus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sharedValues.setVal_lens_x_global(sharedValues.getVal_lens_x_global()-10);
                microScopeInterface.setLensX(sharedValues.getVal_lens_x_global(),false);
                binding.textViewLensX.setText(text_lens_x_pre + String.format("%.2f", sharedValues.getVal_lens_x_global() * 1.));
                binding.seekBarLensX.setProgress(sharedValues.getVal_lens_x_global());
            }

        });


        //******************* Move X ++ ********************************************//
        binding.buttonZFocusPlus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                microScopeInterface.setZFocus(val_stepsize_focus_z, 1000);
            }

        });

        //******************* Move X -- ********************************************//
        binding.buttonZFocusMinus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                microScopeInterface.setZFocus(-val_stepsize_focus_z, 1000);
            }

        });

        //******************* Optimize Coupling ********************************************//
        binding.btnCalib.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                showToast("Optimize  Coupling");
                // turn on the laser
                microScopeInterface.setLaser(sharedValues.val_laser_red_global,false);
                Log.i(TAG, "Lens Calibration in progress");
                String my_gui_text = "Lens Calibration in progress";
                couplingTask.setYuvImageCapture(yuvImageCapture);
                couplingTask.process();

                binding.textViewGuiText.setText(my_gui_text);
                microScopeInterface.setState(MicroScopeController.STATE_CALIBRATION);
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
                if (!findFocusTask.isWorking()) {
                    findFocusTask.setYuvImageCapture(yuvImageCapture);
                    findFocusTask.process();
                }
                else
                {
                    findFocusTask.setYuvImageCapture(null);
                    findFocusTask.stop();
                }

            }
        });


        //******************* Start MEasurement ********************************************//
        binding.btnStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!sofiMeasurementTask.isWorking()) {
                    //new run_sofimeasurement().execute();
                    sofiMeasurementTask.setYuvImageCapture(yuvImageCapture);
                    sofiMeasurementTask.process();
                }
            }
        });

        //******************* Stop Measurement ********************************************//
        binding.btnStop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                findFocusTask.stop();
                findFocusTask.setYuvImageCapture(null);
                couplingTask.stop();
                couplingTask.setYuvImageCapture(null);
                sofiMeasurementTask.stop();
                sofiMeasurementTask.setYuvImageCapture(null);
                sofiProcessTask.stop();
                sofiProcessTask.setYuvImageCapture(null);
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
        saveSettings();
        super.onPause();
        cameraInterface.stopBackgroundThread();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSettings();
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

        // SET CAMERA PARAMETERS FROM GUI
        cameraInterface.setIso(Integer.parseInt(isovalues[val_iso_index]));
        int msexpo = (int) CameraUtil.getMilliSecondStringFromShutterString(texpvalues[val_texp_index]);
        cameraInterface.setExposureTime(msexpo);
    }

    public void onDestroy() {
        super.onDestroy();
        saveSettings();
        EventBus.getDefault().unregister(this);
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
                    new CameraUtil.CompareSizesByArea());
            // Find the rotation of the device relative to the native device orientation.
            int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            Point displaySize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

            // Find the rotation of the device relative to the camera sensor's orientation.
            int totalRotation = CameraUtil.sensorToDeviceRotation(cameraInterface, deviceRotation);

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
            Size previewSize = CameraUtil.chooseOptimalSize(cameraInterface.getSizesForSurfaceTexture(),
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
                    (360 + CameraUtil.ORIENTATIONS.get(deviceRotation)) % 360 :
                    (360 - CameraUtil.ORIENTATIONS.get(deviceRotation)) % 360;

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
            if (mPreviewSize == null || !CameraUtil.checkAspectsEqual(previewSize, mPreviewSize)) {
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
        settingsDialogFragment.show(getFragmentManager(), "AcquireSettings");
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

    private File getNewVideoFile(int id)
    {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
        String mypath = mypath_measurements + timestamp + "/" + File.separator + "VID_" + id + ".mp4";
        return new File(mypath);
    }

    @Subscribe
    public void startRecordingVideo(StartRecordingEvent startRecordingEvent) {
        int id = startRecordingEvent.video_id;
        if (!binding.texture.isAvailable() || null == cameraInterface.getPreviewSize()) {
            return;
        }
        try {
            cameraInterface.stopPreview();
            yuvImageCapture.release();
            myVideoFileName = getNewVideoFile(id);
            recorder.setUpMediaRecorder(myVideoFileName, CamcorderProfile.QUALITY_1080P,global_framerate,CameraUtil.ORIENTATIONS.get(getWindowManager().getDefaultDisplay().getRotation()));
            SurfaceTexture texture = binding.texture.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(texture);
            cameraInterface.setSurface(previewSurface);
            //MediaRecorder setup for surface
            Surface recorderSurface = recorder.getSurface();
            cameraInterface.setSurface(recorderSurface);
            yuvImageCapture = new YuvImageCapture(mPreviewSize);
            cameraInterface.addImageCaptureInterface(yuvImageCapture);
            // Start a capture session
            cameraInterface.setCaptureEventListener(() -> {
                cameraInterface.setCaptureEventListener(null);
                // Start recording
                try {
                    recorder.start();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            });
            cameraInterface.startPreview();
        } catch (IOException | CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Subscribe
    public void stopRecordingVideo(StopRecordingEvent stopRecordingEvent) throws Exception {
        // UI
        cameraInterface.stopPreview();
        // Stop recording
        recorder.stop();

        startPreview();
    }

    @Override
    public void onGuiMessage(String msg) {
        binding.textViewGuiText.post(new Runnable() {
            public void run() {
                binding.textViewGuiText.setText(msg);
            }
        });
    }

    @Override
    public void onShowToast(String msg) {
        showToast(msg);
    }

    @Override
    public void onUpdatePreviewImg(Bitmap bitmap) {
        binding.imageViewPreview.post(new Runnable() {
            @Override
            public void run() {
                if (binding.imageViewPreview.getVisibility() != View.VISIBLE)
                    binding.imageViewPreview.setVisibility(View.VISIBLE);
                Log.d(TAG,"onUpdatePreviewImg show image");
                binding.imageViewPreview.setImageBitmap(bitmap);
            }
        });
    }

    @Subscribe (threadMode = ThreadMode.MAIN)
    public void onSofiMeasurementUpdateEvent(SofiMeasurementUpdateUiEvent sofiMeasurementUpdateUiEvent)
    {
        if (sofiMeasurementUpdateUiEvent.preExecute)
        {
            // Make sure laser is not set to zero
            if (sharedValues.val_laser_red_global == 0) {
                sharedValues.val_laser_red_global = 2000;
            }

            // Set some GUI components
            binding.acquireProgressBar.setVisibility(View.VISIBLE); // Make invisible at first, then have it pop up
            binding.acquireProgressBar.setMax(sharedValues.val_nperiods_calibration);
            showToast("Start Measurements");
        }
        if (sofiMeasurementUpdateUiEvent.update)
        {
            binding.acquireProgressBar.setProgress(sofiMeasurementUpdateUiEvent.i_meas);

            binding.btnStart.setEnabled(false);

            // Update GUI
            String text_lens_x_pre = "Lens (X): ";
            binding.textViewLensX.setText(text_lens_x_pre + String.format("%.2f", sharedValues.getVal_lens_x_global() * 1.));
            binding.seekBarLensX.setProgress(sharedValues.getVal_lens_x_global());

            String text_laser_pre = "Laser: ";
            binding.textViewLaser.setText(text_laser_pre + String.format("%.2f", sharedValues.val_laser_red_global * 1.));
            binding.seekBarLaser.setProgress(sharedValues.val_laser_red_global);
        }
        if (sofiMeasurementUpdateUiEvent.postExecute)
        {
            // Set some GUI components
            binding.acquireProgressBar.setVisibility(View.GONE); // Make invisible at first, then have it pop up
            binding.textViewGuiText.setText("Done Measurements.");
            binding.btnStart.setEnabled(true);

            // Switch off laser
            microScopeInterface.setLaser(0,true);

            // free memory
            //is_findcoupling = false;
            showToast("Stop Measurements");
            System.gc();
        }
    }


    void loadSettings(){
        SharedPreferences sharedPref = this.getSharedPreferences(
                PREFERENCE_FILE_KEY, Context.MODE_PRIVATE);
        microScopeInterface.setIpAdress(sharedPref.getString("myIPAddress", "0.0.0.0"));
        sharedValues.val_nperiods_calibration = sharedPref.getInt("val_nperiods_calibration", sharedValues.val_nperiods_calibration);
        sharedValues.val_period_measurement = sharedPref.getInt("val_period_measurement", sharedValues.val_period_measurement);
        sharedValues.val_duration_measurement = sharedPref.getInt("val_duration_measurement", sharedValues.val_duration_measurement);
        sharedValues.val_sofi_amplitude_x = sharedPref.getInt("val_sofi_amplitude_x", sharedValues.val_sofi_amplitude_x);
        sharedValues.val_sofi_amplitude_z = sharedPref.getInt("val_sofi_amplitude_z", sharedValues.val_sofi_amplitude_z);
        val_iso_index = sharedPref.getInt("val_iso_index", val_iso_index);
        val_texp_index = sharedPref.getInt("val_texp_index", val_texp_index);
        sharedValues.setVal_lens_x_global(sharedPref.getInt("val_lens_x_global",sharedValues.getVal_lens_x_global()));
        val_lens_z_global = sharedPref.getInt("val_lens_z_global", val_lens_z_global);
        sharedValues.val_laser_red_global = sharedPref.getInt("val_laser_red_global", sharedValues.val_laser_red_global);
        Log.d(TAG, "Settings loaded sucessfully");
    }

    void saveSettings()
    {
        // Load previously saved settings and set GUIelements
        SharedPreferences sharedPref = this.getSharedPreferences(
                PREFERENCE_FILE_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("val_nperiods_calibration",sharedValues.val_nperiods_calibration).commit();
        editor.putInt("val_period_measurement",sharedValues.val_period_measurement).commit();
        editor.putInt("val_duration_measurement",sharedValues.val_duration_measurement).commit();
        editor.putInt("val_sofi_amplitude_x",sharedValues.val_sofi_amplitude_x).commit();
        editor.putInt("val_sofi_amplitude_z",sharedValues.val_sofi_amplitude_z).commit();
        editor.putInt("val_iso_index",val_iso_index).commit();
        editor.putInt("val_texp_index",val_texp_index).commit();
        editor.putInt("val_lens_x_global",sharedValues.getVal_lens_x_global()).commit();
        editor.putInt("val_lens_z_global",val_lens_z_global).commit();
        editor.putInt("val_laser_red_global",sharedValues.val_laser_red_global).commit();
        editor.putString("myIPAddress",microScopeInterface.getIpAdress()).commit();
    }


    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {
    }
}