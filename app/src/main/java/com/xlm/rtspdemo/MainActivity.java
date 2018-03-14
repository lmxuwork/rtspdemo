package com.xlm.rtspdemo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;

import com.xlm.rtspdemo.rtp.RtpSenderWrapper;


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

	private RtpSenderWrapper mRtpSenderWrapper;
	private byte[] h264 ;

	private boolean isStreaming = false;
	private AvcEncoder encoder;
	private InetAddress address;
	private int port;

	
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
        
        this.findViewById(R.id.btnCamSize).setOnClickListener(
        	new View.OnClickListener() 
        	{
				@Override
				public void onClick(View v) 
				{
					showSettingsDlg();
				}
			});
        
        this.findViewById(R.id.btnStream).setOnClickListener(
            	new View.OnClickListener() 
            	{
    				@Override
    				public void onClick(View v) 
    				{
    					if (isStreaming)
    					{
    						((Button)v).setText("Stream");
    						stopStream();
    					}
    					else
    					{
    						showStreamDlg();
    					}
    				}
    			});        


		mCameraPreview = new CameraPreview(mPreviewCallback);
		SharedPreferences sp = this.getPreferences(Context.MODE_PRIVATE);
		int width = sp.getInt(SP_CAM_WIDTH, 0);
		int height = sp.getInt(SP_CAM_HEIGHT, 0);
		mCameraPreview.setParameters(width,height);
		h264 = new byte[ width * height * 3];
		mSurfaceView = (SurfaceView) findViewById(R.id.svCameraPreview);
		mHolder = mSurfaceView.getHolder();
		mHolder.addCallback(mCameraPreview);
    }
    
    @Override
    protected void onPause() 
    {
    	this.stopStream();
    	

        if (encoder != null)
        	encoder.close();
        
    	super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) 
    {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) 
    {
        int id = item.getItemId();
        if (id == R.id.action_settings)
            return true;
        return super.onOptionsItemSelected(item);
    }

	@SuppressWarnings("deprecation")
	private Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			if (isStreaming)
			{
				int ret = encoder.offerEncoder(data, h264);
				if(ret > 0){
					//实时发送数据流
					mRtpSenderWrapper.sendAvcPacket(h264, 0, ret, 0);
				}
//				if (encDataLengthList.size() > 100)
//				{
//					Log.e(TAG, "OUT OF BUFFER");
//					return;
//				}
//
//				byte[] encData = encoder.offerEncoder(data);
//				if (encData.length > 0)
//				{
//					synchronized(encDataList)
//					{
//						encDataList.add(encData);
//					}
//				}
			}





			camera.addCallbackBuffer(data);
		}
	};


	private synchronized void startStream(String ip, int port)
	{
		SharedPreferences sp = this.getPreferences(Context.MODE_PRIVATE);
        int width = sp.getInt(SP_CAM_WIDTH, 0);
        int height = sp.getInt(SP_CAM_HEIGHT, 0);        
        
        this.encoder = new AvcEncoder();
        this.encoder.init(width, height, DEFAULT_FRAME_RATE, DEFAULT_BIT_RATE);

        try {
            this.address = InetAddress.getByName(ip); 
            this.port = port;
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
        	e.printStackTrace();
        	return;
        }

		sp.edit().putString(SP_DEST_IP, ip).commit();
		sp.edit().putInt(SP_DEST_PORT, port).commit();

		this.mRtpSenderWrapper = new RtpSenderWrapper(ip, port, false);

        this.isStreaming = true;
        
		((Button)this.findViewById(R.id.btnStream)).setText("Stop");		
		this.findViewById(R.id.btnCamSize).setEnabled(false);
	}
	
	private void stopStream()
	{
		this.isStreaming = false;
		
		if (this.encoder != null)
			this.encoder.close();
		this.encoder = null;
		
		this.findViewById(R.id.btnCamSize).setEnabled(true);
	}

	
	private void showStreamDlg() {

		LayoutInflater inflater = this.getLayoutInflater();
		View content = inflater.inflate(R.layout.stream_dlg_view, null);
		
		SharedPreferences sp = this.getPreferences(Context.MODE_PRIVATE);
        String ip = sp.getString(SP_DEST_IP, "");
        int port = sp.getInt(SP_DEST_PORT, -1);

		if (ip.length() > 0) {
        	EditText etIP = (EditText)content.findViewById(R.id.etIP);
        	etIP.setText(ip);
        	EditText etPort = (EditText)content.findViewById(R.id.etPort);
        	etPort.setText(String.valueOf(port));
        }
		
		AlertDialog.Builder dlgBld = new AlertDialog.Builder(this);
		dlgBld.setTitle(R.string.app_name);
		dlgBld.setView(content);
		dlgBld.setPositiveButton(android.R.string.ok, 
			new DialogInterface.OnClickListener() 
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					EditText etIP = (EditText) ((AlertDialog)dialog).findViewById(R.id.etIP);
					EditText etPort = (EditText) ((AlertDialog)dialog).findViewById(R.id.etPort);
					String ip = etIP.getText().toString();
					int port = Integer.valueOf(etPort.getText().toString());
					if (ip.length() > 0 && (port >=0 && port <= 65535)) {
						startStream(ip, port);
					} else {
						//TODO:
					}					
				}
			});
		dlgBld.setNegativeButton(android.R.string.cancel, null);
		dlgBld.show();
	}
	
	private void showSettingsDlg()
	{
		Camera.Parameters params = mCameraPreview.getParameters();
		final List<Size> prevSizes = params.getSupportedPreviewSizes();
		String[] choiceStrItems = new String[prevSizes.size()];
		ArrayList<String> choiceItems = new ArrayList<String>();
		for (Size s : prevSizes)
		{
			choiceItems.add(s.width + "x" + s.height);
		}
		choiceItems.toArray(choiceStrItems);
		
		AlertDialog.Builder dlgBld = new AlertDialog.Builder(this);
		dlgBld.setTitle(R.string.app_name);
		dlgBld.setSingleChoiceItems(choiceStrItems, 0, null);			
		dlgBld.setPositiveButton(android.R.string.ok, 
			new DialogInterface.OnClickListener() 
			{
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					int pos = ((AlertDialog)dialog).getListView().getCheckedItemPosition();
					Size s = prevSizes.get(pos);
					SharedPreferences sp = MainActivity.this.getPreferences(Context.MODE_PRIVATE);					
					sp.edit().putInt(SP_CAM_WIDTH, s.width).commit();
					sp.edit().putInt(SP_CAM_HEIGHT, s.height).commit();
					
					mCameraPreview.changeResolution(s.width,s.height);
					h264 = new byte[ s.width * s.height * 3];
				}
			});
		dlgBld.setNegativeButton(android.R.string.cancel, null);
		dlgBld.show();
	}
}
