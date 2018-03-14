package com.xlm.rtspdemo;

import java.net.InetAddress;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;

import com.xlm.rtspdemo.rtsp.PatronServer;

import cn.broadsense.newrecorder.streaming.SessionBuilder;
import cn.broadsense.newrecorder.streaming.rtsp.PatronStream;
import cn.broadsense.newrecorder.streaming.video.VideoQuality;


public class MainActivity extends Activity {

	private final static String TAG = MainActivity.class.getSimpleName();
	
	private final static String SP_CAM_WIDTH = "cam_width";
	private final static String SP_CAM_HEIGHT = "cam_height";
	private final static String SP_DEST_IP = "dest_ip";
	private final static String SP_DEST_PORT = "dest_port";
	
	private final static int DEFAULT_FRAME_RATE = 20;
	private final static int DEFAULT_BIT_RATE = 1024 * 1024;

	private SurfaceView mSurfaceView;
	private SurfaceHolder mHolder;
	private CameraPreview mCameraPreview;
	private Context mContext;

	private SessionBuilder sessionBuilder;
	private PatronServer patronServer;
	private static PatronStream patronStream;
	private static boolean isStream = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
		StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
				.detectDiskReads()
				.detectDiskWrites()
				.detectAll()   // or .detectAll() for all detectable problems
				.penaltyLog()
				.build());

		StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
				.detectLeakedSqlLiteObjects()
				.detectLeakedClosableObjects()
				.penaltyLog()
				.penaltyDeath()
				.build());

        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.setContentView(R.layout.activity_main);

		mContext = getApplicationContext();

		mCameraPreview = new CameraPreview(mPreviewCallback);
		SharedPreferences sp = this.getPreferences(Context.MODE_PRIVATE);
		int width = sp.getInt(SP_CAM_WIDTH, 0);
		int height = sp.getInt(SP_CAM_HEIGHT, 0);
		mCameraPreview.setParameters(width,height);
		mSurfaceView = (SurfaceView) findViewById(R.id.svCameraPreview);
		mHolder = mSurfaceView.getHolder();
		mHolder.addCallback(mCameraPreview);

		initRtsp();
    }

    private void initRtsp(){
		sessionBuilder = SessionBuilder.getInstance();
		sessionBuilder.setContext(this);
//		sessionBuilder.setAudioEncoder(SessionBuilder.AUDIO_AAC);
		sessionBuilder.setVideoEncoder(SessionBuilder.VIDEO_H264);
		int width = mCameraPreview.getWidth();
		int height = mCameraPreview.getHeight();
		VideoQuality videoQuality = new VideoQuality(width, height,20, (width * height) << 3);
		sessionBuilder.setVideoQuality(videoQuality);

		// sessionBuilder.build();
		patronServer = new PatronServer(mContext);
		patronServer.setPort(PatronServer.DEFAULT_RTSP_PORT);
		patronServer.onCreate();
		patronServer.addCallbackListener(new PatronServer.CallbackListener() {
			@Override
			public void onError(PatronServer server, Exception e, int error) {
				Log.e(TAG, "onError() code:" + error);
				stopStream();
			}

			@Override
			public void onMessage(PatronServer server, int message) {
				Log.e(TAG, "onMessage() Lurong message:" + message);
				if (message == PatronServer.MESSAGE_STREAMING_STOPPED) {
					stopStream();
				}
			}
		});
	}


	@SuppressWarnings("deprecation")
	private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			if (patronStream != null && isStream){
				patronStream.writeVideoSampleData(data);
			}
			camera.addCallbackBuffer(data);
		}
	};


	public static synchronized void startStream(PatronStream stream) {
        Log.d(TAG,"startStream");
		patronStream = stream;
		if (patronStream != null){
			isStream = true;
		}

	}
	
	public static synchronized void stopStream() {
		Log.d(TAG,"stopStream");
		isStream = false;
		patronStream = null;
	}


}
