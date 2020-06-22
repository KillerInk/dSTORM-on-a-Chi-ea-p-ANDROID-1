package de.nanoimaging.stormimager.process;

import android.graphics.Bitmap;
import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import static org.opencv.core.Core.NORM_MINMAX;
import static org.opencv.core.Core.normalize;
import static org.opencv.imgcodecs.Imgcodecs.imwrite;
import static org.opencv.imgproc.Imgproc.cvtColor;

public class VideoProcessor {

    public VideoProcessor(String VideoFile, int cropsize, int framerate) {
        my_file_path = VideoFile;
        mycropsize = cropsize;
        video_framerate = framerate;
    }

    // initiialize video-reader
    MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
    MediaExtractor extractor = new MediaExtractor();

    String TAG = "VideoProcessor";

    // get video parameters
    long video_duration = -1;
    long video_width = -1;
    long video_heigth = -1;
    long video_framerate = 1;
    float video_frame_duration = -1;
    int num_frame_first = 1;

    Mat myglobalresult = new Mat();

    int mycropsize = 512;

    // File-IO
    String my_file_path = "";


    public void setupvideo() {
        // VIDEO-STUFF -------
        // read video and video-information
        mediaMetadataRetriever.setDataSource(my_file_path);

        // get video parameters
        video_duration = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        video_width = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        video_heigth = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));

        video_frame_duration = (float) ((1. / (float) video_framerate) * 10000000.);
    }


    public Mat process(int num_frames) {

        // read the first frame of the video and set it to the image viewer
        Mat current_mat = new Mat();
        Mat result_mat = new Mat();
        for(int iter = num_frame_first; iter< num_frames; iter++) {
            long video_time_position = (long) ((float) iter * video_frame_duration);
            Log.i(TAG, "Videoframe-position: " + String.valueOf(iter) + ", Time: (us)" + String.valueOf(video_time_position));

            // get first frame of the video
            Bitmap current_frame = mediaMetadataRetriever.getFrameAtTime(video_time_position, MediaMetadataRetriever.OPTION_CLOSEST_SYNC); //unit in microsecond

            Utils.bitmapToMat(current_frame, current_mat);
            cvtColor(current_mat, current_mat, Imgproc.COLOR_RGB2GRAY);

            // crop center part
            org.opencv.core.Rect roi = new Rect((int) current_mat.width() / 2 - mycropsize / 2, (int) current_mat.height() / 2 - mycropsize / 2, mycropsize, mycropsize);
            Mat dst_cropped = new Mat(current_mat, roi);

            // create pseudo gradient
            dst_cropped = computelaplacian(dst_cropped);
            dst_cropped.convertTo(dst_cropped, CvType.CV_32FC(1));
            // accumulate
            if(iter==num_frame_first){
                dst_cropped.copyTo(result_mat);
            }
            else{
                Core.add(result_mat, dst_cropped, result_mat);
            }

        }

        result_mat.copyTo(myglobalresult);
        return result_mat;
    }


    private Mat computelaplacian(Mat inputmat){
        Mat destination = new Mat();
        Imgproc.Laplacian(inputmat, destination, 3);
        if(false){
            MatOfDouble median = new MatOfDouble();
            MatOfDouble std= new MatOfDouble();
            Core.meanStdDev(destination, median , std);

            Math.pow(std.get(0,0)[0],2);
        }
        return destination;
    }

    public void saveresult(String path){
        if(myglobalresult!= null){
            imwriteNorm(path, myglobalresult);
        }
    }

    public static void imwriteNorm(String filename, Bitmap Bitmap_input){
        // Save image from light-source
        Mat Mat_norm = new Mat ();
        Utils.bitmapToMat(Bitmap_input, Mat_norm);
        normalize(Mat_norm, Mat_norm, 0, 255, NORM_MINMAX);
        Mat_norm.convertTo(Mat_norm, CvType.CV_8UC1);
        imwrite(filename, Mat_norm);

    }

    public static void imwriteNorm(String filename, Mat Mat_input){
        // save image
        Mat Mat_norm = new Mat();
        normalize(Mat_input, Mat_norm, 0, 255, NORM_MINMAX);
        Mat_norm.convertTo(Mat_norm, CvType.CV_8UC1);
        imwrite(filename, Mat_norm);

    }


}




