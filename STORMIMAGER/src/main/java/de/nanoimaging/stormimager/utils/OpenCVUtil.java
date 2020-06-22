package de.nanoimaging.stormimager.utils;

import android.media.Image;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;

public class OpenCVUtil {

    public static final int ROI_SIZE = 512;                                 // region which gets cropped to measure the coupling efficiencey

    public static double measureCoupling(Mat inputmat, int mysize, int ksize){
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

    public static Mat yuvToMat(Image image) throws IllegalStateException {

        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        final byte[] data = new byte[buffer.limit()];
        buffer.get(data);
        Mat mat = new Mat(image.getHeight() + image.getHeight()/2, image.getWidth(), CvType.CV_8UC1);
        mat.put(0, 0, data);

        return mat;
    }

    public static Mat getBGRMatFromYuvMat(Mat yuv)
    {
        Mat bgrMat = new Mat(yuv.cols(), yuv.rows(),CvType.CV_8UC4);
        Imgproc.cvtColor(yuv, bgrMat, Imgproc.COLOR_YUV2BGR_I420);
        return bgrMat;
    }
}
