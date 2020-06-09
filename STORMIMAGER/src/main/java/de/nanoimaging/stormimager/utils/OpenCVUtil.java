package de.nanoimaging.stormimager.utils;

import android.graphics.ImageFormat;
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

    public static Mat imageToMat(Image image) throws IllegalStateException {
        ByteBuffer buffer;
        int rowStride;
        int pixelStride;
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = 0;

        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)/8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        for (int i = 0; i < planes.length; i++) {
            buffer = planes[i].getBuffer();
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            int w = (i == 0) ? width : width/2;
            int h = (i == 0) ? height : height/2;
            for (int row = 0; row < h; row++) {
                int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)/8;
                if (pixelStride == bytesPerPixel) {
                    int length = w * bytesPerPixel;
                    buffer.get(data, offset, length);

                    if (h - row != 1) {
                        buffer.position(buffer.position() + rowStride - length);
                    }
                    offset += length;
                } else {


                    if (h - row == 1) {
                        buffer.get(rowData, 0, width - pixelStride + 1);
                    } else {
                        buffer.get(rowData, 0, rowStride);
                    }

                    for (int col = 0; col < w; col++) {
                        data[offset++] = rowData[col * pixelStride];
                    }
                }
            }
        }

        Mat mat = new Mat(height + height/2, width, CvType.CV_8UC1);
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
