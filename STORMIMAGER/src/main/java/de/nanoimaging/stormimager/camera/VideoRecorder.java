package de.nanoimaging.stormimager.camera;

import android.app.Activity;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.view.Surface;

import java.io.File;
import java.io.IOException;

import de.nanoimaging.stormimager.utils.CameraUtil;

public class VideoRecorder {

    private MediaRecorder mMediaRecorder;               // MediaRecorder
    private File mCurrentFile;
    private int frameRate;

    public VideoRecorder(){
        mMediaRecorder = new MediaRecorder();
    }

    public void setUpMediaRecorder(File myVideoFileName,int global_cameraquality, int global_framerate, int rotation) throws IOException {
        if (!myVideoFileName.getParentFile().exists()) {
            myVideoFileName.getParentFile().mkdirs();
            myVideoFileName.getParentFile().mkdir();
        }
        frameRate = global_framerate;
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

        mMediaRecorder.setOrientationHint(CameraUtil.ORIENTATIONS.get(rotation));

        mMediaRecorder.prepare();
    }

    public Surface getSurface()
    {
        return mMediaRecorder.getSurface();
    }

    public void start()
    {
        mMediaRecorder.start();
    }

    public void stop()
    {
        mMediaRecorder.stop();
        mMediaRecorder.reset();
    }

    public File getmCurrentFile()
    {
        return mCurrentFile;
    }

    public int getFrameRate()
    {
        return  frameRate;
    }
}
