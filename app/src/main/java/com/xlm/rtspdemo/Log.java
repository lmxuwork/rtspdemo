package com.xlm.rtspdemo;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Locale;


public final class Log {

    public static boolean showLog = true;
    public static boolean showBytes = false;
    public static boolean showDownLog = false;

    private static final String TAG = "RtspDemo";

    public static FileWriter fw = null;

    public static void i(String msg) {
        i("Call AnalyzeImage", msg);
    }

    public static void v(String tag, String msg) {
        if (showLog) {
            msg = formatMsg(tag, msg);
            android.util.Log.v(TAG, msg);
            if (fw != null) {
                FileLog.i(TAG, msg, fw);
            }
        }
    }

    public static void i(String tag, String msg) {
        if (showLog) {
            msg = formatMsg(tag, msg);
            android.util.Log.i(TAG, msg);
            if (fw != null) {
                FileLog.i(TAG, msg, fw);
            }
        }
    }

    public static void d(String tag, String msg) {
        if (showLog) {
            msg = formatMsg(tag, msg);
            android.util.Log.d(TAG, msg);
            if (fw != null) {
                FileLog.d(TAG, msg, fw);
            }
        }
    }

    public static void w(String tag, String msg) {
        if (showLog) {
            msg = formatMsg(tag, msg);
            android.util.Log.w(TAG, msg);
            if (fw != null) {
                FileLog.w(TAG, msg, fw);
            }
        }
    }

    public static void e(String tag, String msg) {
        msg = formatMsg(tag, msg);
        android.util.Log.e(TAG, msg);
        if (fw != null) {
            FileLog.e(TAG, msg, fw);
        }
    }

    public static void e(String tag, Throwable t) {
        e(tag, t.getMessage(), t);
    }

    public static void e(String tag, String message, Throwable t) {
        String msg = formatMsg(tag, message != null ? message : t.toString());
        android.util.Log.e(TAG, msg, t);
        if (fw != null) {
            FileLog.e(TAG, msg, t, fw);
        }
    }

    private static String formatMsg(String tag, String msg) {
        StringBuffer sb = new StringBuffer();
        sb.append("        [").append(getPid()).append("]");
        sb.append("[").append(tag).append("] [thread=").append(String.valueOf(Thread.currentThread().getId())).append("] ====== ").append(msg);
        return sb.toString();
    }

    private static String getPid() {
        return "pid:" + android.os.Process.myPid() + " tid:" + android.os.Process.myTid();
    }

    /*========================================*/
    static class FileLog {
        private FileLog() {
        }

        public static void i(String tag, String msg, FileWriter fw) {
            log("INFO", tag, msg, fw);
        }

        public static void d(String tag, String msg, FileWriter fw) {
            log("DEBUG", tag, msg, fw);
        }

        public static void w(String tag, String msg, FileWriter fw) {
            log("WARN", tag, msg, fw);
        }

        public static void e(String tag, String msg, FileWriter fw) {
            log("ERROR", tag, msg, fw);
        }

        public static void e(String tag, String message, Throwable t, FileWriter fw) {
            log("ERROR", tag, message, fw);
            log("ERROR", tag, traceToString(t), fw);
        }

        private static void log(String level, String tag, String msg, FileWriter fw) {
            try {
                if (fw == null) {
                    return;
                }
                Date date = new Date();
                String d = formatDate(date);
                StringBuffer sb = new StringBuffer();
                sb.append(d).append(" ").append(level).append("  ").append(tag).append("  ").append(msg);
                fw.write(sb.toString() + "\r\n");
                fw.flush();
            } catch (IOException e) {
                android.util.Log.e("FileLog", "Store: " + e.getMessage(), e);
            }
        }

        private static String formatDate(Date d) {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA);
            return format.format(d);
        }

        private static String traceToString(Throwable t) {
            StringWriter writer = new StringWriter();
            PrintWriter pw = new PrintWriter(writer);
            t.printStackTrace(pw);
            return writer.toString();
        }
    }
}
