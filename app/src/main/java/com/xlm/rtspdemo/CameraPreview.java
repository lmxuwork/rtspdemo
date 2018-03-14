package com.xlm.rtspdemo;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.view.SurfaceHolder;

import java.util.List;

/**
 * Created by xlm on 17-7-4.
 */

@SuppressWarnings("deprecation")
public class CameraPreview implements SurfaceHolder.Callback {

    private static final String TAG = "CameraPreview";
    private Camera mCamera;
    private SurfaceHolder mHolder;
    private Camera.Parameters mCamParameters;
    private boolean isOpen = false;
    private int mWidth = 480;
    private int mHeight = 320;
    private Camera.PreviewCallback mPreviewCallback;
    private byte[] mBuffer;

    public int getWidth() {
        return mWidth;
    }
    public int getHeight(){
        return mHeight;
    }

    public CameraPreview(Camera.PreviewCallback callback) {
        this.mPreviewCallback = callback;
    }

    public void setParameters(int width,int height){
        if (width > 0 && height > 0) {
            mWidth = width;
            mHeight = height;
        }
    }


    private void openCamera(SurfaceHolder holder, int tagInfo){

        this.mHolder.setFixedSize(mWidth, mHeight);
        try{
            mCamera = Camera.open();
            mCamParameters = mCamera.getParameters(); //配置参数
            mCamParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            if (tagInfo == Camera.CameraInfo.CAMERA_FACING_BACK) {
                // YuanDao x1 not support
//                mCamParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);//1连续对焦
            }
            mCamParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            mCamParameters.setJpegQuality(90);
            mCamParameters.setPreviewFormat(ImageFormat.YV12);
            mCamParameters.setPreviewSize(mWidth, mHeight);
            mCamera.setParameters(mCamParameters);
            isOpen = true;
            List<Camera.Size> si = mCamParameters.getSupportedPreviewSizes();
            for (int i = 0; i < si.size(); i++) {
                Log.d(TAG, "摄像头" + tagInfo + "支持：" + si.get(i).width + "*" + si.get(i).height);
            }

            int bufferSize = (mWidth * mHeight * ImageFormat.getBitsPerPixel(ImageFormat.NV21)) / 8;
            mBuffer = new byte[bufferSize];
            mCamera.addCallbackBuffer(mBuffer);

            mCamera.setPreviewDisplay(holder);
            if (mPreviewCallback != null) {
                mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
            }

            mCamera.startPreview();

        }catch (Throwable e){
            Log.e(TAG,e.getMessage(),e);
        }
    }

    public void changeResolution(int width, int height){
        Log.d(TAG,"changeResolution " + width + " X " + height);
        mWidth = width;
        mHeight = height;
        releaseCamera();
        openCamera(mHolder, Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    public void releaseCamera(){
        if (mCamera != null){
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mHolder = surfaceHolder;
        openCamera(surfaceHolder, Camera.CameraInfo.CAMERA_FACING_BACK);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        releaseCamera();
    }

    public Camera.Parameters getParameters() {
        return mCamera.getParameters();
    }
}
