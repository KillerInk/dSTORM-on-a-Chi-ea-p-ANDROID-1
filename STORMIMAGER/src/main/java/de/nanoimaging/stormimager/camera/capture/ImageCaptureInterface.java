package de.nanoimaging.stormimager.camera.capture;

import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.view.Surface;

public interface ImageCaptureInterface extends ImageReader.OnImageAvailableListener {
    Surface getSurface();
    void setCaptureResult(CaptureResult captureResult);
    void onCaptureCompleted(Image image, CaptureResult result);
    void release();
}
