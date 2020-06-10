package de.nanoimaging.stormimager.camera;

import android.hardware.camera2.CameraAccessException;
import android.util.Size;
import android.view.Surface;

import de.nanoimaging.stormimager.camera.capture.ImageCaptureInterface;

public interface CameraInterface {

    interface CaptureSessionEvent
    {
        void onCaptureSessionConfigured();
    }

    /**
     *
     * @param id to open
     * @throws CameraAccessException
     */
    void openCamera(String id) throws CameraAccessException;

    /**
     * close current open camera
     */
    void closeCamera();

    /**
     * setSurface that should get used by the camera
     * mainly used to set Surface from TextureView or the mediaRecorder.
     * for image capture use addImageCaptureInterface
     * surfaces must get set bevor the preview get started
     * @param surface
     */
    void setSurface(Surface surface);

    /**
     * start camera preview, make sure you set the ImageCaptureInterfaces or surfaces
     * @throws CameraAccessException
     */
    void startPreview() throws CameraAccessException;

    /**
     * stop the active capturession
     */
    void stopPreview();

    /**
     * applies iso to the capturesession
     * @param iso to apply
     */
    void setIso(int iso);

    /**
     * applies exposuretime to the capturesession
     * @param exposureTime to set
     */
    void setExposureTime(int exposureTime);

    /**
     * startBackgroundThread, make sure to call it bevor camera get open
     */
    void startBackgroundThread();
    /**
     * startBackgroundThread, make sure to call it after camera is closed
     */
    void stopBackgroundThread();

    /**
     *
     * @return the current state of the camera
     */
    CameraStates getCameraState();
    Size getPreviewSize();
    int getSensorOrientation();
    boolean isFrontCamera();
    Size getMaxPreviewSize();
    Size[] getSizesForSurfaceTexture();
    void setCaptureEventListner(CaptureSessionEvent eventListner);
    void addImageCaptureInterface(ImageCaptureInterface imageCaptureInterface);
    void captureImage() throws Exception;
}
