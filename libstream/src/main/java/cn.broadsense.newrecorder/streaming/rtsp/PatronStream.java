package cn.broadsense.newrecorder.streaming.rtsp;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

import cn.broadsense.newrecorder.streaming.SessionBuilder;
import cn.broadsense.newrecorder.streaming.hw.EncoderDebugger;
import cn.broadsense.newrecorder.streaming.mp4.MP4Config;
import cn.broadsense.newrecorder.streaming.rtp.H264Packetizer;
import cn.broadsense.newrecorder.streaming.util.JNIUtil;
import cn.broadsense.newrecorder.streaming.video.VideoQuality;
import cn.broadsense.newrecorder.streaming.video.VideoStream;


/**
 * Created by Payne on 1/27/16.
 * patron推流
 */
public class PatronStream extends VideoStream {

    protected final static String TAG = "PatronStream";
    private static final int TIMEOUT_USEC = 10000;    // 10[msec][10毫秒]
    //    private Semaphore mLock = new Semaphore(0);
    private MP4Config mConfig;
    private JNIUtil mJniUtil;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public PatronStream() {
        mJniUtil = new JNIUtil();
        mMimeType = "video/avc";
        mVideoEncoder = MediaRecorder.VideoEncoder.H264;
        mPacketizer = new H264Packetizer();
    }

    /**
     * Returns a description of the stream using SDP. It can then be included in an SDP file.
     */
    public synchronized String getSessionDescription() throws IllegalStateException {
        if (mConfig == null)
            throw new IllegalStateException("You need to call configure() first !");
        return "m=video " + String.valueOf(getDestinationPorts()[0]) + " RTP/AVP 96\r\n" +
                "a=rtpmap:96 H264/90000\r\n" +
                "a=fmtp:96 packetization-mode=1;profile-level-id=" + mConfig.getProfileLevel() +
                ";sprop-parameter-sets=" + mConfig.getB64SPS() + "," + mConfig.getB64PPS() + ";\r\n";
    }

    /**
     * Starts the stream.
     * This will also open the camera and display the preview if {@link #startPreview()} has not already been called.
     */
    public synchronized void start() throws IllegalStateException, IOException {
        if (!mStreaming) {
            Log.e(TAG, "--------  PatronStream start");
            configure();
            byte[] pps = Base64.decode(mConfig.getB64PPS(), Base64.NO_WRAP);
            byte[] sps = Base64.decode(mConfig.getB64SPS(), Base64.NO_WRAP);
            ((H264Packetizer) mPacketizer).setStreamParameters(pps, sps);
            super.start();
        }
    }

    /**
     * Configures the stream. You need to call this before calling {@link #getSessionDescription()} to apply
     * your configuration of the stream.
     */
    public synchronized void configure() throws IllegalStateException, IOException {
        Log.e(TAG, "configure()");
        super.configure();
        mMode = mRequestedMode;
        //mQuality = mRequestedQuality.clone();

        //lurong modify
//        mQuality = new VideoQuality(1280, 720, 20, (1280 * 720) << 3);
        Log.e(TAG, "---------  PatronStream configure");
        mConfig = testH264();

        Log.e(TAG, "---------  PatronStream configure pps " + mConfig.getB64PPS());
    }

    public synchronized void configure(byte mode) throws IllegalStateException, IOException {
        Log.e(TAG, "configure() --------- mode:" + mode);
        super.configure();
        mMode = mode;
        //lurong modify
        Log.d(TAG, "configure() mode resX:" + mRequestedQuality.resX + " resY:" + mRequestedQuality.resY);
        mQuality = new VideoQuality(mRequestedQuality.resX, mRequestedQuality.resY, 20,
                (mRequestedQuality.resX * mRequestedQuality.resY) << 3);
        mConfig = testH264();
        Log.e(TAG, "---------  PatronStream configure pps " + mConfig.getB64PPS());
    }

    /**
     * Tests if com.deepwits.streaming with the given configuration (bit rate, frame rate, resolution) is possible
     * and determines the pps and sps. Should not be called by the UI thread.
     **/
    private MP4Config testH264() throws IllegalStateException, IOException {
        Log.e(TAG, "testH264   " + (mMode & 0xFF));
        mMode = MODE_PATRON_RECORD;
        return testPatronAPI();
    }

    private MP4Config testPatronAPI() throws RuntimeException, IOException {
        Log.e(TAG, "--------    testPatronAPI  mQuality.resX:" + mQuality.resX + "  mQuality.resY:" + mQuality.resY);
        try {
//            if (true) {
//            EncoderDebugger debugger = EncoderDebugger.debug(mSettings, mQuality.resX, mQuality.resY);
            //add
            //String SPS = debugger.getB64SPS();
            //String PPS = debugger.getB64PPS();
            //Log.e(TAG, "----------- SPS:" + SPS + "  PPS:" + PPS);//OMX.MTK.VIDEO.ENCODER.AVC,   Z2QAKawbGoCgPaAeEQin    aOpDyw==
            //SPS:Z0LAHrkQFAe0IAAAAwAgAAAFEeLF1A==  PPS:aM48gA==
//                return new MP4Config(debugger.getB64SPS(), debugger.getB64PPS());
//            } else {
//                return new MP4Config("Z2QAKawbGoCgPaAeEQin", "aOpDyw=="); //480p
//            return new MP4Config("Z0IAH42NQCgC3QDwiEU4", "aMpDyA==");//720p


            Context context = SessionBuilder.getInstance().getContext();
            int screenHeight = 480;
            if (context != null) {
                screenHeight = context.getResources().getDisplayMetrics().heightPixels;
            }

            if (screenHeight > 320) {
                //5寸寸
                if (mQuality.resY == 720) {
                    return new MP4Config("Z0IAH42NQCgC3QDwiEU4", "aMpDyA==");//720p
                } else if (mQuality.resY == 540) {
                    return new MP4Config("Z0IAKY2NQHgIv3APCIRTgA", "aMpDyA==");//540p
                } else {
                    return new MP4Config("Z0IAKY2NQFAe0A8IhFOA", "aMpDyA==");//480p
                }
            } else {
                //3.5寸
                return new MP4Config("Z0LAH7kQCgC3QDxgyoA=", "aM48gA==");//720p
            }
        } catch (Exception e) {
            // Fallback on the old com.deepwits.streaming method using the MediaRecorder API
            Log.e(TAG, "Resolution not supported with the MediaCodec API, we fallback on the old streamign method.");
            return testH264();
        }
    }

    @Override
    public synchronized void stop() {
        Log.d(TAG, "Patron Stream Stop");
        super.stop();
    }

    /**
     * @param data YV12数据
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public synchronized void writeVideoSampleData(byte[] data) {
//        Log.i(TAG, "writeVideoSampleData() data length:" + data.length + " resX:" + mQuality.resX + " resY:" + mQuality.resY);
        //uv反转
        mJniUtil.reverseUV(data, mQuality.resX, mQuality.resY);

        //lurong modify
        if (mMediaCodec == null || !mCodecStarted || !isStreaming()) return;
        long now = System.nanoTime() / 1000;
        int bufferIndex = mMediaCodec.dequeueInputBuffer(TIMEOUT_USEC);
        //Log.e(TAG, "PatronStream frame data:" + data.length + " bufferIndex = "+ bufferIndex);
        if (bufferIndex >= 0) {
            patronInputBuffers[bufferIndex].clear();
            ByteBuffer buffer = patronInputBuffers[bufferIndex];
            if (data == null) {
                Log.e(TAG, "Symptom of the Callback buffer was to small problem...");
            } else {
                int min = buffer.capacity() < data.length ? buffer.capacity() : data.length;
                buffer.put(data, 0, min);
            }
            mMediaCodec.queueInputBuffer(bufferIndex, 0, patronInputBuffers[bufferIndex].position(), now, 0);
        } else {
            Log.e(TAG, "No buffer available !");
        }
    }
}
