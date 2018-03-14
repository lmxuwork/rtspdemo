package com.xlm.rtspdemo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;

public class AvcEncoder
{
	private final static String TAG = AvcEncoder.class.getSimpleName();
	private final static String MIME_TYPE = "video/avc";
	private final static int I_FRAME_INTERVAL = 1;
	
    MediaCodec mMediaCodec;
    int width;  
    int height;
    byte[] m_info = null;
    private byte[] yuv420 = null;

    long frameIndex = 0;
    byte[] spsPpsInfo = null;
    private final static int TIME_UNIT = 50 * 1000;

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    public AvcEncoder() 
    {         
    }  
    
    public boolean init(int width, int height, int framerate, int bitrate)
    {
        try {
            this.width  = width;
            this.height = height;
            yuv420 = new byte[width*height*3/2];
            mMediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1024 * 1024 ) ;
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        }
        catch (IOException e) {
			return false;
		}

        mMediaCodec.start();
        return true;
    }


    public void close() 
    {  
        try {
            mMediaCodec.stop();
            mMediaCodec.release();
        }
        catch (Exception e) {
            Log.e(TAG,e.getMessage(),e);
        }  
    }

//    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
//    public byte[] offerEncoder(byte[] input)
//    {
//        try {
//            int inputBufferIndex = mMediaCodec.dequeueInputBuffer(TIME_UNIT);//等缓冲区
//            if (inputBufferIndex >= 0) {
//                ByteBuffer inputBuffer = mMediaCodec.getInputBuffer(inputBufferIndex);
//                inputBuffer.clear();
//                inputBuffer.put(input);
//                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
//                frameIndex++;
//            }
//
//            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
//            int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIME_UNIT);
//
//            while (outputBufferIndex >= 0) {
//                ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(outputBufferIndex);
//                byte[] outData = new byte[bufferInfo.size];
//                outputBuffer.get(outData);
//
//                if (spsPpsInfo == null) {
//                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
//                    if (spsPpsBuffer.getInt() == 0x00000001) {
//                        spsPpsInfo = new byte[outData.length];
//                        System.arraycopy(outData, 0, spsPpsInfo, 0, outData.length);
//                    } else {
//                    	return null;
//                    }
//                } else {
//                	outputStream.write(outData);
//                }
//
//                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
//                outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIME_UNIT);
//            }
//            byte[] ret = outputStream.toByteArray();
//            if (ret.length > 5 && ret[4] == 0x65) { //key frame need to add sps pps
//                outputStream.reset();
//                outputStream.write(spsPpsInfo);
//                outputStream.write(ret);
//            }
//        } catch (Throwable t) {
//            Log.e(TAG,t.getMessage(),t);
//        }
//        byte[] ret = outputStream.toByteArray();
//        outputStream.reset();
//        return ret;
//    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public int offerEncoder(byte[] input, byte[] output) {
        Log.v("xmc", "offerEncoder:"+input.length+"+"+output.length);
        int pos = 0;
        swapYV12toI420(input, yuv420, width, height);
        try {
            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mMediaCodec.getOutputBuffers();
            int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);

            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(input);
                mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, 0);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo,0);

            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if(m_info != null){
                    System.arraycopy(outData, 0,  output, pos, outData.length);
                    pos += outData.length;

                }else{//保存pps sps 只有开始时 第一个帧里有， 保存起来后面用
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
//                    Log.v(TAG, "swapYV12toI420:outData:"+outData);
//                    Log.v(TAG, "swapYV12toI420:spsPpsBuffer:"+spsPpsBuffer);
//
//                    for(int i=0;i<outData.length;i++){
//                        Log.e(TAG, "run: get data rtpData[i]="+i+":"+outData[i]);//输出SPS和PPS循环
//                    }

                    if (spsPpsBuffer.getInt() == 0x00000001) {
                        m_info = new byte[outData.length];
                        System.arraycopy(outData, 0, m_info, 0, outData.length);
                    }else {
                        return -1;
                    }
                }
                if(output[4] == 0x65) {//key frame 编码器生成关键帧时只有 00 00 00 01 65 没有pps sps， 要加上
                    System.arraycopy(m_info, 0,  output, 0, m_info.length);
                    System.arraycopy(outData, 0,  output, m_info.length, outData.length);
                }
                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }
        Log.v(TAG, "offerEncoder+pos:"+pos);
        return pos;
    }

    private void swapYV12toI420(byte[] yv12bytes, byte[] i420bytes, int width, int height) {
        int size = width * height;
        System.arraycopy(yv12bytes, 0, i420bytes, 0, size);
        System.arraycopy(yv12bytes, size + size/4, i420bytes, size + size/4, size/4);
        System.arraycopy(yv12bytes, size, i420bytes, size ,size/4);
    }

}
