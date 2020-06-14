package de.nanoimaging.stormimager.camera.capture;

import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

public abstract class AbstractImageCapture implements ImageCaptureInterface {

    private final String TAG = AbstractImageCapture.class.getSimpleName();
    private final int MAX_IMAGES = 5;
    private ImageReader imageReader;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private Image image;
    private CaptureResult captureResult;

    public AbstractImageCapture(Size size, int format)
    {
        startBackgroundThread();
        imageReader = ImageReader.newInstance(size.getWidth(),size.getHeight(),format,MAX_IMAGES);
        imageReader.setOnImageAvailableListener(this::onImageAvailable,mBackgroundHandler);
    }

    @Override
    public Surface getSurface()
    {
        return imageReader.getSurface();
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Log.d(TAG, "onImageAvailable");

        image = reader.acquireLatestImage();
        checkIfCaptureCompleted();
    }

    @Override
    public void setCaptureResult(CaptureResult  captureResult)
    {
        Log.d(TAG,"setCaptureResult");
        this.captureResult = captureResult;
        checkIfCaptureCompleted();
    }

    private synchronized void checkIfCaptureCompleted()
    {
        Log.d(TAG, "checkIfCaptureCompleted :" + (image != null && captureResult != null));
        if (image != null && captureResult != null)
            onCaptureCompleted(image,captureResult);
    }

    @Override
    public void onCaptureCompleted(Image image, CaptureResult result)
    {
        Log.d(TAG, "onCaptureCompleted");
        this.image.close();
        image = null;
        result = null;
    }

    @Override
    public void release()
    {
        Log.d(TAG,"release");
        if (imageReader != null)
            imageReader.close();
        if (image != null)
            image.close();
        captureResult = null;
        stopBackgroundThread();
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread == null)
            return;
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
