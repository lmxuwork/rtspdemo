
package com.xlm.rtspdemo.rtsp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Camera;
import android.preference.PreferenceManager;

import com.xlm.rtspdemo.Log;
import com.xlm.rtspdemo.MainActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.broadsense.newrecorder.streaming.Session;
import cn.broadsense.newrecorder.streaming.rtsp.PatronStream;
import cn.broadsense.newrecorder.streaming.rtsp.UriParser;


/**
 * Implementation of a subset of the RTSP protocol (RFC 2326).
 * <p/>
 * It allows remote control of an android device cameras & microphone.
 * For each connected client, a Session is instantiated.
 * The Session will start or stop streams according to what the client wants.
 */
public class PatronServer {

    public final static String TAG = "PatronServer";

    /**
     * The server name that will appear in responses.
     */
    public static String SERVER_NAME = "Patron RTSP Server";

    /**
     * Port used by default.
     */
    public static final int DEFAULT_RTSP_PORT = 8086;

    /**
     * Port already in use.
     */
    public final static int ERROR_BIND_FAILED = 0x00;

    /**
     * A stream could not be started.
     */
    public final static int ERROR_START_FAILED = 0x01;

    /**
     * Streaming started.
     */
    public final static int MESSAGE_STREAMING_STARTED = 0X00;

    /**
     * Streaming stopped.
     */
    public final static int MESSAGE_STREAMING_STOPPED = 0X01;

    /**
     * Key used in the SharedPreferences to store whether the RTSP server is enabled or not.
     */
    public final static String KEY_ENABLED = "rtsp_enabled";

    /**
     * Key used in the SharedPreferences for the port used by the RTSP server.
     */
    public final static String KEY_PORT = "rtsp_port";

    protected SharedPreferences mSharedPreferences;
    protected boolean mEnabled = true;
    protected int mPort = DEFAULT_RTSP_PORT;
    protected WeakHashMap<Session, Object> mSessions = new WeakHashMap<Session, Object>(2);

    private RequestListener mListenerThread;
    private boolean mRestart = false;
    private final LinkedList<CallbackListener> mListeners = new LinkedList<CallbackListener>();

    private Context mContext;

    public PatronServer(Context context) {
        mContext = context;
        if (mSharedPreferences == null) {
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        }
    }

    /**
     * Be careful: those callbacks won't necessarily be called from the ui thread !
     */
    public interface CallbackListener {

        /**
         * Called when an error occurs.
         */
        void onError(PatronServer server, Exception e, int error);

        /**
         * Called when com.deepwits.streaming starts/stops.
         */
        void onMessage(PatronServer server, int message);

    }

    /**
     * See {@link CallbackListener} to check out what events will be fired once you set up a listener.
     *
     * @param listener The listener
     */
    public void addCallbackListener(CallbackListener listener) {
        synchronized (mListeners) {
            if (mListeners.size() > 0) {
                for (CallbackListener cl : mListeners) {
                    if (cl == listener) return;
                }
            }
            mListeners.add(listener);
        }
    }

    /**
     * Removes the listener.
     *
     * @param listener The listener
     */
    public void removeCallbackListener(CallbackListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
            //rtsp://192.168.0.1:8086
        }
    }

    /**
     * Returns the port used by the RTSP server.
     */
    public int getPort() {
        return mPort;
    }

    /**
     * Sets the port for the RTSP server to use.
     *
     * @param port The port
     */
    public void setPort(int port) {
        if (mSharedPreferences == null) {
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        }
        Editor editor = mSharedPreferences.edit();
        editor.putString(KEY_PORT, String.valueOf(port));
        editor.apply();
    }

    /**
     * Starts (or restart if needed, if for example the configuration
     * of the server has been modified) the RTSP server.
     */
    public void start() {
        if (!mEnabled || mRestart) stop();
        if (mEnabled && mListenerThread == null) {
            try {
                mListenerThread = new RequestListener();
            } catch (Exception e) {
                mListenerThread = null;
            }
        }
        mRestart = false;
    }

    public void reStart() {
        mRestart = true;
        start();
    }

    /**
     * Stops the RTSP server but not the Android Service.
     * To stop the Android Service you need to call {@link Context#stopService(Intent)};
     */
    public void stop() {
        if (mListenerThread != null) {
            try {
                mListenerThread.kill();
                for (Session session : mSessions.keySet()) {
                    if (session != null) {
                        if (session.isStreaming()) session.stop();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            } finally {
                mListenerThread = null;
            }
        }
    }

    /**
     * Returns whether or not the RTSP server is com.deepwits.streaming to some client(s).
     */
    public boolean isStreaming() {
        for (Session session : mSessions.keySet()) {
            if (session != null) {
                if (session.isStreaming()) return true;
            }
        }
        return false;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Returns the bandwidth consumed by the RTSP server in bits per second.
     */
    public long getBitrate() {
        long bitrate = 0;
        for (Session session : mSessions.keySet()) {
            if (session != null) {
                if (session.isStreaming()) bitrate += session.getBitrate();
            }
        }
        return bitrate;
    }

    public void onCreate() {

        // Let's restore the state of the service
        Log.e(TAG, "PatronServer onCreate");
        mPort = Integer.parseInt(mSharedPreferences.getString(KEY_PORT, String.valueOf(mPort)));
        mEnabled = mSharedPreferences.getBoolean(KEY_ENABLED, mEnabled);

        // If the configuration is modified, the server will adjust
        mSharedPreferences.registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);

        start();
    }

    private OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

            if (key.equals(KEY_PORT)) {
                int port = Integer.parseInt(sharedPreferences.getString(KEY_PORT, String.valueOf(mPort)));
                if (port != mPort) {
                    mPort = port;
                    mRestart = true;
                    start();
                }
            } else if (key.equals(KEY_ENABLED)) {
                mEnabled = sharedPreferences.getBoolean(KEY_ENABLED, mEnabled);
                start();
            }
        }
    };
/*
    */

    /**
     * The Binder you obtain when a connection with the Service is established.
     *//*
    public class LocalBinder extends Binder {
		public PatronServer getService() {
			return PatronServer.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}*/
    protected void postMessage(int id) {
        synchronized (mListeners) {
            if (mListeners.size() > 0) {
                for (CallbackListener cl : mListeners) {
                    cl.onMessage(this, id);
                }
            }
        }
    }

    protected void postError(Exception exception, int id) {
        synchronized (mListeners) {
            if (mListeners.size() > 0) {
                for (CallbackListener cl : mListeners) {
                    cl.onError(this, exception, id);
                }
            }
        }
    }

    /**
     * By default the RTSP uses {@link UriParser} to parse the URI requested by the client
     * but you can change that behavior by override this method.
     *
     * @param uri    The uri that the client has requested
     * @param client The socket associated to the client
     * @return A proper session
     */
    protected Session handleRequest(String uri, Socket client) throws IllegalStateException, IOException {
        Log.e(TAG, "--------   handleRequest uri:" + uri + "origin:" + client.getLocalAddress().getHostAddress());
        Session session = UriParser.parse(uri);
        session.setOrigin(client.getLocalAddress().getHostAddress());
        if (session.getDestination() == null) {
            session.setDestination(client.getInetAddress().getHostAddress());
        }
        return session;
    }

    class RequestListener extends Thread implements Runnable {

        private final ServerSocket mServer;

        public RequestListener() throws IOException {
            try {
                mServer = new ServerSocket(mPort);
                start();
            } catch (BindException e) {
                Log.e(TAG, e.getMessage(), e);
                Log.e(TAG, "Port already in use !");
                postError(e, ERROR_BIND_FAILED);
                throw e;
            }
        }

        public void run() {
            Log.e(TAG, "RTSP server listening on port " + mServer.getLocalPort());
            while (!Thread.interrupted()) {
                try {
                    new WorkerThread(mServer.accept()).start();
                } catch (SocketException e) {
                    Log.e(TAG, e.getMessage(), e);
                    break;
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage());
                    continue;
                }
            }
            Log.e(TAG, "RTSP server stopped !");
        }

        public void kill() {
            try {
                mServer.close();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            try {
                this.join();
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

    }

    public void setTest() {
        this.test = true;
    }

    private boolean test = false;

    // One thread per client
    class WorkerThread extends Thread implements Runnable {

        private final Socket mClient;
        private final OutputStream mOutput;
        private final BufferedReader mInput;

        // Each client has an associated session
        private Session mSession;

        public WorkerThread(final Socket client) throws IOException {
            mInput = new BufferedReader(new InputStreamReader(client.getInputStream()));
            mOutput = client.getOutputStream();
            mClient = client;
            mSession = new Session();
        }

        public void run() {
            Request request;
            Response response;

            Log.e(TAG, "Connection from " + mClient.getInetAddress().getHostAddress());

            while (!Thread.interrupted()) {
                request = null;
                response = null;
                // Parse the request
                try {
                    request = Request.parseRequest(mInput);
                } catch (SocketException e) {
                    Log.e(TAG, e.getMessage() , e);
                    break;
                } catch (Exception e) {
                    // We don't understand the request :/
                    response = new Response();
                    response.status = Response.STATUS_BAD_REQUEST;
                }

                // Do something accordingly like starting the streams, sending a session description
                if (request != null) {
                    try {
                        if (test) {
                            request.method = "DESCRIBE";
                        }
                        Log.e(TAG, "processRequest ::::request.method = " + request.method);
                        response = processRequest(request);
                    } catch (Exception e) {
                        // This alerts the main thread that something has gone wrong in this thread
                        Log.e(TAG, "err:" + e.toString());
                        postError(e, ERROR_START_FAILED);
                        Log.e(TAG, e.getMessage() != null ? e.getMessage() : "An error occurred");
                        response = new Response(request);
                    }
                }

                // We always send a response
                // The client will receive an "INTERNAL SERVER ERROR" if an exception has been thrown at some point
                try {
                    if (response != null)
                        response.send(mOutput);
                } catch (IOException e) {
                    Log.e(TAG, "Response was not sent properly");
                    break;
                }
            }
            Log.e(TAG, "Lurong rtsp disconnected");
            MainActivity.stopStream();
//            if (mSession.getCameraPosition() == Camera.CameraInfo.CAMERA_FACING_BACK) {
//                FrameCallback.getInstance().setRtspEnable(false, null);
//            } else {
//                BackFrameCallback.getInstance().setRtspEnable(false, null);
//            }
            // Streaming stops when client disconnects
            boolean streaming = isStreaming();
            mSession.syncStop();
            if (streaming && !isStreaming()) {
                postMessage(MESSAGE_STREAMING_STOPPED);
            }
            mSession.release();
            try {
                mClient.close();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            Log.i(TAG, "Client close");
        }

        public Response processRequest(Request request) throws IllegalStateException, IOException {
            Log.e(TAG, "Command processRequest   : + " + request.method);
            Response response = new Response(request);

            //C表示RTSP客户端,S表示RTSP服务端
            //查询服务器端可用方法
            //C->S OPTION request 询问S有哪些方法可用
            //S->C OPTION response S回应信息的public头字段中包括提供的所有可用方法
            /* ********************************************************************************** */
            /* ********************************* Method OPTIONS ********************************* */
            /* ********************************************************************************** */
            if (request.method.equalsIgnoreCase("OPTIONS")) {
                response.status = Response.STATUS_OK;
                response.attributes = "Public: DESCRIBE,SETUP,TEARDOWN,PLAY,PAUSE\r\n";
                response.status = Response.STATUS_OK;
            }

            //得到媒体描述信息
            // C->S DESCRIBE request 要求得到S提供的媒体描述信息
            // S->C DESCRIBE response S回应媒体描述信息，一般是sdp信息
            /* ********************************************************************************** */
            /* ********************************* Method DESCRIBE ******************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("DESCRIBE")) {

                // Parse the requested URI and configure the session
                mSession = handleRequest(request.uri, mClient);
                mSessions.put(mSession, null);
                mSession.setTest();
                mSession.syncConfigure();

                if (test) {
                    test = false;
                    return null;
                }
                Log.i(TAG, "processRequest() Lurong setRtspEnable cameraposition:" + mSession.getCameraPosition());
                //开始RTSP SDP是一个比较独立的协议，仅仅起到描述码流的作用
                MainActivity.startStream((PatronStream) mSession.getVideoTrack());
//                if (mSession.getCameraPosition() == Camera.CameraInfo.CAMERA_FACING_BACK) {
////                    FrameCallback.getInstance().setRtspEnable(true, ((PatronStream) mSession.getVideoTrack()));
//                } else {
////                    BackFrameCallback.getInstance().setRtspEnable(true, ((PatronStream) mSession.getVideoTrack()));
//                }
                String requestContent = mSession.getSessionDescription();
                response.attributes = "Content-Base: " + mClient.getLocalAddress().getHostAddress() + ":" + mClient.getLocalPort() + "/\r\n" +
                        "Content-Type: application/sdp\r\n";
                response.content = requestContent;
                // If no exception has been thrown, we reply with OK
                response.status = Response.STATUS_OK;

            }

            //建立RTSP会话 ，协商传输用的协议。在RTSP协议中，SETUP命令的一种报文负载就是使用SDP协议的码流描述
            // C->S SETUP request //通过Transport头字段列出可接受的传输选项，请求S建立会话
            // S->C SETUP response //S建立会话，通过Transport头字段返回选择的具体转输选项，并返回建立的Session ID;
            /* ********************************************************************************** */
            /* ********************************** Method SETUP ********************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("SETUP")) {
                Pattern p;
                Matcher m;
                int p2, p1, ssrc, trackId, src[];
                String destination;

                p = Pattern.compile("trackID=(\\w+)", Pattern.CASE_INSENSITIVE);
                m = p.matcher(request.uri);

                if (!m.find()) {
                    response.status = Response.STATUS_BAD_REQUEST;
                    return response;
                }

                trackId = Integer.parseInt(m.group(1));

                if (!mSession.trackExists(trackId)) {
                    response.status = Response.STATUS_NOT_FOUND;
                    return response;
                }

                p = Pattern.compile("client_port=(\\d+)-(\\d+)", Pattern.CASE_INSENSITIVE);
                m = p.matcher(request.headers.get("transport"));

                if (!m.find()) {
                    int[] ports = mSession.getTrack(trackId).getDestinationPorts();
                    p1 = ports[0];
                    p2 = ports[1];
                } else {
                    p1 = Integer.parseInt(m.group(1));
                    p2 = Integer.parseInt(m.group(2));
                }

                ssrc = mSession.getTrack(trackId).getSSRC();
                src = mSession.getTrack(trackId).getLocalPorts();
                destination = mSession.getDestination();

                mSession.getTrack(trackId).setDestinationPorts(p1, p2);

                boolean streaming = isStreaming();
                mSession.syncStart(trackId);
                if (!streaming && isStreaming()) {
                    postMessage(MESSAGE_STREAMING_STARTED);
                }

                // MP2T/RTP/UDP表示是TS流用于RTP打包，基于UDP传输。MP2T/RTP/TCP表示是TS流用于RTP打包，基于TCP
                //unicast = 单播，multicast = 多播，该参数为必填参数
                response.attributes = "Transport: RTP/AVP/UDP;" + (InetAddress.getByName(destination).isMulticastAddress() ? "multicast" : "unicast") +
                        ";destination=" + mSession.getDestination() +
                        ";client_port=" + p1 + "-" + p2 +//UDP做为底层传输协议时的RTSP客户端的端口号
                        ";server_port=" + src[0] + "-" + src[1] +//UDP做为底层传输协议时的RTSP服务器的端口号
                        //ssrc同步源标识符 synchronization source (SSRC) identifier
                        //csrc 参与源（ 特约信源）标识符contributing source (CSRC) identifiers
                        ";ssrc=" + Integer.toHexString(ssrc) +
                        ";mode=play\r\n" +//即将进行的取流操作
                        "Session: " + "1185d20035702ca" + "\r\n" +
                        "Cache-Control: no-cache\r\n";
                response.status = Response.STATUS_OK;

                // If no exception has been thrown, we reply with OK
                response.status = Response.STATUS_OK;

            }

            //请求开始传送数据
            // C->S PLAY request //C请求S开始发送数据
            // S->C PLAY response //S回应该请求的信息
            //之后数据传送播放中
            /* ********************************************************************************** */
            /* ********************************** Method PLAY *********************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("PLAY")) {
                String requestAttributes = "RTP-Info: ";
                if (mSession.trackExists(0))
                    requestAttributes += "url=rtsp://" + mClient.getLocalAddress().getHostAddress() + ":" + mClient.getLocalPort() + "/trackID=" + 0 + ";seq=0,";
                if (mSession.trackExists(1))
                    requestAttributes += "url=rtsp://" + mClient.getLocalAddress().getHostAddress() + ":" + mClient.getLocalPort() + "/trackID=" + 1 + ";seq=0,";
                requestAttributes = requestAttributes.substring(0, requestAttributes.length() - 1) + "\r\nSession: 1185d20035702ca\r\n";

                response.attributes = requestAttributes;
                // If no exception has been thrown, we reply with OK
                response.status = Response.STATUS_OK;
            }

            //暂停发送数据
            /* ********************************************************************************** */
            /* ********************************** Method PAUSE ********************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("PAUSE")) {
                response.status = Response.STATUS_OK;
            }

            //关闭会话:退出
            // C->S EARDOWN request //C请求关闭会话
            // S->C TEARDOWN response //S回应该请求
            /* ********************************************************************************** */
            /* ********************************* Method TEARDOWN ******************************** */
            /* ********************************************************************************** */
            else if (request.method.equalsIgnoreCase("TEARDOWN")) {
                response.status = Response.STATUS_OK;
            }

			/* ********************************************************************************** */
            /* ********************************* Unknown method ? ******************************* */
            /* ********************************************************************************** */
            else {
                Log.e(TAG, "Command unknown: " + request);
                response.status = Response.STATUS_BAD_REQUEST;
            }
            return response;

        }

    }

    static class Request {

        // Parse method & uri
        public static final Pattern regexMethod = Pattern.compile("(\\w+) (\\S+) RTSP", Pattern.CASE_INSENSITIVE);
        // Parse a request header
        public static final Pattern rexegHeader = Pattern.compile("(\\S+):(.+)", Pattern.CASE_INSENSITIVE);

        public String method;
        public String uri;
        public HashMap<String, String> headers = new HashMap<String, String>();

        /**
         * Parse the method, uri & headers of a RTSP request
         */
        public static Request parseRequest(BufferedReader input) throws IOException, IllegalStateException {

            //Log.d(TAG, "parseRequest " + input.readLine());

            Request request = new Request();
            String line;
            Matcher matcher;

            // Parsing request method & uri 解析请求方法和URI
            if ((line = input.readLine()) == null)
                throw new SocketException("Client disconnected");

            Log.e(TAG, "============= Request ==============  " + line);

            matcher = regexMethod.matcher(line);
            matcher.find();
            request.method = matcher.group(1);
            request.uri = matcher.group(2);

            // It's not an error, it's just easier to follow what's happening in logcat with the request in red
            Log.e(TAG, request.method + " " + request.uri);

            // Parsing headers of the request
            while ((line = input.readLine()) != null && line.length() > 3) {
                matcher = rexegHeader.matcher(line);
                matcher.find();
                request.headers.put(matcher.group(1).toLowerCase(Locale.US), matcher.group(2));

                Log.e(TAG, "header: " + line);
            }
            if (line == null) {
                throw new SocketException("Client disconnected");
            }
            return request;
        }
    }

    static class Response {

        // Status code definitions
        public static final String STATUS_OK = "200 OK";
        public static final String STATUS_BAD_REQUEST = "400 Bad Request";
        public static final String STATUS_NOT_FOUND = "404 Not Found";
        public static final String STATUS_INTERNAL_SERVER_ERROR = "500 Internal Server Error";

        public String status = STATUS_INTERNAL_SERVER_ERROR;
        public String content = "";
        public String attributes = "";

        private final Request mRequest;

        public Response(Request request) {
            this.mRequest = request;
        }

        public Response() {
            // Be carefull if you modify the send() method because request might be null !
            mRequest = null;
        }

        public void send(OutputStream output) throws IOException {
            Log.e(TAG, "RequestHeade..... " + mRequest);
            if (mRequest == null || mRequest.headers == null || !mRequest.headers.containsKey("cseq")) {
                Log.e(TAG, "RequestHeader is Mull");

                String response = "RTSP/1.0 " + status + "\r\n" +
                        "Server: " + SERVER_NAME + "\r\n" +
                        "Content-Length: 0\r\n" +
                        "\r\n";

                output.write(response.getBytes());
                return;
            }
            int seqid = -1;

            try {
                seqid = Integer.parseInt(mRequest.headers.get("cseq").replace(" ", ""));
            } catch (Exception e) {
                Log.e(TAG, "Error parsing CSeq: " + (e.getMessage() != null ? e.getMessage() : ""));
            }

            String response = "RTSP/1.0 " + status + "\r\n" +
                    "Server: " + SERVER_NAME + "\r\n" +
                    (seqid >= 0 ? ("Cseq: " + seqid + "\r\n") : "") +
                    "Content-Length: " + content.length() + "\r\n" +
                    attributes +
                    "\r\n" +
                    content;
            Log.d(TAG, "====== response =====");
            Log.d(TAG, response.replace("\r", ""));

            output.write(response.getBytes());
        }
    }

}
