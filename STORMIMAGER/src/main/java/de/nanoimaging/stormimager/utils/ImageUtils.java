package de.nanoimaging.stormimager.utils;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static org.opencv.core.Core.NORM_MINMAX;
import static org.opencv.core.Core.mulSpectrums;
import static org.opencv.core.Core.normalize;
import static org.opencv.imgcodecs.Imgcodecs.imwrite;

public class ImageUtils {

    public static final double PI =  3.141592653589793 ;
    private static final String TAG = "ImageUtils";


    public static Mat toLog(Mat Input){

            Mat magI = Input;
            Mat magI2 = new Mat(magI.size(), magI.type());
            Mat magI3 = new Mat(magI.size(), magI.type());
            Mat magI4 = new Mat(magI.size(), magI.type());
            Mat magI5 = new Mat(magI.size(), magI.type());

            //Core.add(magI, Mat.ones(paddedReal.rows(), paddedReal.cols(), CvType.CV_64FC1),  magI2); // switch to logarithmic scale
            Core.add(magI, Mat.ones(Input.rows(), Input.cols(), CvType.CV_32FC1),  magI2); // switch to logarithmic scale
            Core.log(magI2, magI3);

            Mat crop = new Mat(magI3, new Rect(0, 0, magI3.cols() & -2, magI3.rows() & -2));

            magI4 = crop.clone();

        return magI4;

    }

    public static Mat toMat(Bitmap bmp) {
        Mat mat = new Mat();
        Utils.bitmapToMat(bmp, mat);
        return mat;
    }

    public static Bitmap toBitmap(Mat mat) {
        Bitmap bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bmp);
        return bmp;
    }






    public static Mat ExtractAndCropMat(Mat inputMat, int subSize, int cchannel){
        // extracts a submatrix with size "subSize" and extracts the colour channel "cchannel"

        List<Mat> temp_bgr = new ArrayList<Mat>();

        int dpcWidth = inputMat.width();
        int dpcHeight = inputMat.height();

        inputMat = inputMat.submat((dpcHeight-subSize)/2, (dpcHeight+subSize)/2,(dpcWidth-subSize)/2, (dpcWidth+subSize)/2);
        Mat inputMat_sub = inputMat.clone();  // make sure that mat is continuous
        Core.split(inputMat_sub,temp_bgr); //split source
        return inputMat_sub = temp_bgr.get(cchannel); //green channel; red=2; blue=0
    }


    public static Mat preprocess(Mat Mat_input){
        // tune the data to -1..1
        // read one cellSTORM frame
        if(true) {
            normalize(Mat_input, Mat_input, 0, 1, NORM_MINMAX);
        }
        else {
            // / Localizing the best match with minMaxLoc
            Core.MinMaxLocResult mymmr = Core.minMaxLoc(Mat_input);
            int mymax = (int) mymmr.maxVal;
            Core.divide(Mat_input, new Scalar(mymax), Mat_input);
            Core.multiply(Mat_input, new Scalar(2), Mat_input);
            Core.subtract(Mat_input, new Scalar(1), Mat_input);
        }

        return Mat_input;
    }

    public static Mat deprocess(Mat Mat_input){
        // tune the data to 0.1
        // read one cellSTORM frame
        if(false) {
            normalize(Mat_input, Mat_input, 0, 1, NORM_MINMAX);
        }
        else {
            // / Localizing the best match with minMaxLoc
            Core.add(Mat_input, new Scalar(1), Mat_input);
            Core.divide(Mat_input, new Scalar(2), Mat_input);
        }


        return Mat_input;
    }



    public static void imwriteNorm(Bitmap Bitmap_input, String filename){
        // Save image from light-source
        Mat Mat_norm = new Mat ();
        Utils.bitmapToMat(Bitmap_input, Mat_norm);
        normalize(Mat_norm, Mat_norm, 0, 255, NORM_MINMAX);
        Mat_norm.convertTo(Mat_norm, CvType.CV_8UC1);
        imwrite(filename, Mat_norm);

    }



    public static Mat imwriteNorm(Mat Mat_input, String filename){
        // save image
        Mat Mat_norm = new Mat();
        normalize(Mat_input, Mat_norm, 0, 255, NORM_MINMAX);
        Mat_norm.convertTo(Mat_norm, CvType.CV_8UC1);
        imwrite(filename, Mat_norm);
        return Mat_norm;
    }











}

