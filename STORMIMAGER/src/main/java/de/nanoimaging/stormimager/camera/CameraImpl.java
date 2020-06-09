package de.nanoimaging.stormimager.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.List;

import de.nanoimaging.stormimager.StormApplication;
import de.nanoimaging.stormimager.camera.capture.AbstractImageCapture;
import de.nanoimaging.stormimager.camera.capture.ImageCaptureInterface;
import de.nanoimaging.stormimager.camera.vendor.CaptureRequestEx;

import static de.nanoimaging.stormimager.camera.vendor.CaptureRequestEx.HUAWEI_DUAL_SENSOR_MODE;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class CameraImpl implements CameraInterface {

    private final String TAG = CameraImpl.class.getSimpleName();
    /**
     * An additional thread for running tasks that shouldn't block the UI.  This is used for all
     * callbacks from the {@link CameraDevice} and {@link CameraCaptureSession}s.
     */
    private HandlerThread mBackgroundThread;
    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;
    /**
     * A reference to the open {@link CameraDevice}.
     */
    private CameraDevice cameraDevice;
    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession captureSession;
    /**
     * The {@link CameraCharacteristics} for the currently configured camera device.
     */
    private CameraCharacteristics characteristics;
    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;
    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    /**
     * Surface from TextureView
     */
    private List<Surface> surfaces = new ArrayList<>();

    private Size previewSize;
    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest.Builder mCaptureRequestBuilder;

    private CameraStates cameraState = CameraStates.Closed;

    private int global_expval;
    private int global_isoval;

    /**
     * listen to capturession configuration
     */
    private CaptureSessionEvent captureSessionEventListner;

    private List<ImageCaptureInterface> imageCapturesList = new ArrayList<>();

    @SuppressLint("MissingPermission")
    @Override
    public void openCamera(String id) throws CameraAccessException {
        Log.d(TAG, "openCamera " +id);
        CameraManager manager = (CameraManager) StormApplication.getContext().getSystemService(Context.CAMERA_SERVICE);
        characteristics = manager.getCameraCharacteristics(id);
        manager.openCamera(id, cameraStateCallback, null);
        previewSize = findPreviewSize();
    }

    @Override
    public void closeCamera() {
        Log.d(TAG, "closeCamera");
        if (cameraDevice != null)
            cameraDevice.close();
    }

    @Override
    public void setSurface(Surface surface) {
        surfaces.add(surface);
    }


    @Override
    public void startPreview() throws CameraAccessException {
        Log.d(TAG,"startPreview");
        if (previewSize != null && surfaces.size() >0) {
            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Log.d(TAG,"Surfaces to add:" + surfaces.size());
            for (Surface surface : surfaces)
                mPreviewRequestBuilder.addTarget(surface);

            Log.d(TAG,"ImageCaptures To add:" + imageCapturesList.size());
            if (imageCapturesList.size() > 0)
            {
                mCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                for (ImageCaptureInterface ici : imageCapturesList) {
                    mCaptureRequestBuilder.addTarget(ici.getSurface());
                    surfaces.add(ici.getSurface());
                }
            }

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == cameraDevice) {
                                return;
                            }

                            try {
                                setup3AControlsLocked(mPreviewRequestBuilder);
                                if (mCaptureRequestBuilder != null)
                                    setup3AControlsLocked(mCaptureRequestBuilder);
                                // Finally, we start displaying the camera preview.
                                cameraCaptureSession.setRepeatingRequest(
                                        mPreviewRequestBuilder.build(),
                                        previewCaptureCallback, mBackgroundHandler);
                                cameraState = CameraStates.Preview;
                            } catch (CameraAccessException | IllegalStateException e) {
                                e.printStackTrace();
                                return;
                            }
                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession;
                            Log.i(TAG, "mCaptureSession was created");
                            if (captureSessionEventListner != null)
                                captureSessionEventListner.onCaptureSessionConfigured();
                        }


                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG,"Failed to configure camera.");
                        }
                    }, mBackgroundHandler
            );
        }
    }

    @Override
    public void stopPreview() {
        Log.d(TAG, "stopPreview");
        if (null != captureSession) {
            captureSession.close();
            captureSession = null;
        }
        surfaces.clear();
        for (ImageCaptureInterface ici : imageCapturesList)
           ici.release();
        imageCapturesList.clear();
    }

    @Override
    public void setIso(int iso) {
        this.global_isoval = iso;
        if (mPreviewRequestBuilder != null)
            mPreviewRequestBuilder.set(CaptureRequestEx.HUAWEI_SENSOR_ISO_VALUE, iso);
        if (mCaptureRequestBuilder != null)
            mCaptureRequestBuilder.set(CaptureRequestEx.HUAWEI_SENSOR_ISO_VALUE, iso);
        if (captureSession != null) {
            try {
                captureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), previewCaptureCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setExposureTime(int exposureTime) {
        this.global_expval = exposureTime;
        if (mPreviewRequestBuilder != null)
            mPreviewRequestBuilder.set(CaptureRequestEx.HUAWEI_SENSOR_EXPOSURE_TIME, exposureTime);
        if (mCaptureRequestBuilder != null)
            mCaptureRequestBuilder.set(CaptureRequestEx.HUAWEI_SENSOR_EXPOSURE_TIME, exposureTime);
        if (captureSession != null)
            try {
                captureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), previewCaptureCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
    }

    @Override
    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    @Override
    public void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public CameraStates getCameraState() {
        return cameraState;
    }

    CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            CameraImpl.this.cameraDevice = cameraDevice;
            cameraState = CameraStates.Open;
            //TODO send event cameraopen
            Log.d(TAG, "Camera open");

        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            Log.d(TAG, "Camera Disconnected");
    //            mCameraOpenCloseLock.release();
            if (cameraDevice != null) {
                CameraImpl.this.cameraDevice.close();

                CameraImpl.this.cameraDevice = null;
            }
            cameraState = CameraStates.Closed;
            //TODO fire event CameraClosed
        }


        @Override
        public void onError(CameraDevice cameraDevice, final int error)
        {
            Log.d(TAG, "Camera Error" + error);
            if (CameraImpl.this.cameraDevice != null) {
                CameraImpl.this.cameraDevice.close();
                CameraImpl.this.cameraDevice = null;

            }
            cameraState = CameraStates.Closed;
            //TODO fire event camera got error

        }
    };

    private void setup3AControlsLocked(CaptureRequest.Builder builder) {
        // Enable auto-magical 3A run by camera device
        builder.set(CaptureRequest.CONTROL_MODE,
                CaptureRequest.CONTROL_MODE_AUTO);

        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);

        // If there is an auto-magical flash control mode available, use it, otherwise default to
        // the "on" mode, which is guaranteed to always be available.
        if (contains(characteristics.get(
                CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES),
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        }

        // If there is an auto-magical white balance control mode available, use it.
        if (contains(characteristics.get(
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

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events for the preview and
     * pre-capture sequence.
     */
    private CameraCaptureSession.CaptureCallback previewCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        private void process(CaptureResult result) {

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

    public Size getPreviewSize()
    {
        return previewSize;
    }

    @Override
    public int getSensorOrientation() {
        return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
    }

    @Override
    public boolean isFrontCamera() {
        return (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT);
    }

    @Override
    public Size getMaxPreviewSize() {
        return new Size(MAX_PREVIEW_WIDTH,MAX_PREVIEW_HEIGHT);
    }

    @Override
    public Size[] getSizesForSurfaceTexture() {
        return characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(SurfaceTexture.class);
    }

    @Override
    public void setCaptureEventListner(CaptureSessionEvent eventListner) {
        this.captureSessionEventListner = eventListner;
    }

    @Override
    public void addImageCaptureInterface(ImageCaptureInterface imageCaptureInterface) {
        imageCapturesList.add(imageCaptureInterface);
    }

    @Override
    public void captureImage() throws Exception {
        Log.d(TAG, "captureImage");
        if (imageCapturesList.size() == 0)
            throw  new Exception("No image capture Listners attached");
        try {
            captureSession.capture(mCaptureRequestBuilder.build(),imageCaptureCallback,mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size findPreviewSize()
    {
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        Size largestSize = new Size(MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT);
        Size[] sizeList = map.getOutputSizes(ImageFormat.YUV_420_888);
        for (Size size : sizeList) {
            if (size.getWidth() * size.getHeight() > 1000000)
                continue;
            else {
                largestSize = size;
                break;
            }
        }
        return largestSize;
    }

    CameraCaptureSession.CaptureCallback imageCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            Log.d(TAG,"onCaptureCompleted");
            for (ImageCaptureInterface ici : imageCapturesList)
                ici.setCaptureResult(result);
        }
    };
}
