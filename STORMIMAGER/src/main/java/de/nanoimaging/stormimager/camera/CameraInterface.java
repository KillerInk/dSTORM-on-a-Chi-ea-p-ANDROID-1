package de.nanoimaging.stormimager.camera;

import android.hardware.camera2.CameraAccessException;
import android.util.Size;
import android.view.Surface;

public interface CameraInterface {

    interface CaptureSessionEvent
    {
        void onCaptureSessionConfigured();
    }

    void openCamera(String id) throws CameraAccessException;
    void closeCamera();
    void setSurface(Surface surface);
    void startPreview() throws CameraAccessException;
    void stopPreview();
    void setIso(int iso);
    void setExposureTime(int exposureTime);
    void startBackgroundThread();
    void stopBackgroundThread();
    CameraStates getCameraState();
    Size getPreviewSize();
    int getSensorOrientation();
    boolean isFrontCamera();
    Size getMaxPreviewSize();
    Size[] getSizesForSurfaceTexture();
    void setCaptureEventListner(CaptureSessionEvent eventListner);
}
