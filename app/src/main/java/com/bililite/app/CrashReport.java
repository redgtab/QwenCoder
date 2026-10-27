package com.bililite.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.bililite.app.api.BiliApi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 崩溃收集：捕获未处理异常写入 filesDir/crash/last_crash.txt，
 * 下次启动提示用户，可将日志作为 Issue 提交到 GitHub，或复制全文。
 */
public class CrashReport {

    private static final String REPO = "redgtab/QwenCoder";
    private static File dir(Context c) { return new File(c.getFilesDir(), "crash"); }
    public static File lastFile(Context c) { return new File(dir(c), "last_crash.txt"); }
    public static boolean hasNewCrash(Context c) { return lastFile(c).exists(); }

    /** 在 Application.onCreate 中安装 */
    public static void install(final Context app) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                if (!dir(app).exists()) dir(app).mkdirs();
                File f = lastFile(app);
                FileOutputStream fos = new FileOutputStream(f, false);
                PrintWriter pw = new PrintWriter(new java.io.OutputStreamWriter(fos, "UTF-8"));
                pw.println("=== BiliLite Crash Report ===");
                pw.println("time: " + new java.text.SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
                pw.println("version: " + BuildConfig.VERSION_NAME
                        + " (" + BuildConfig.VERSION_CODE + ")");
                pw.println("device: " + Build.MANUFACTURER + " " + Build.MODEL);
                pw.println("android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
                pw.println("thread: " + t.getName());
                pw.println();
                e.printStackTrace(pw);
                pw.flush();
                pw.close();
            } catch (Throwable ignore) {
            } finally {
                if (prev != null) prev.uncaughtException(t, e);
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    /** 读取崩溃日志文本 */
    public static String readLast(Context c) {
        try {
            FileInputStream in = new FileInputStream(lastFile(c));
            byte[] buf = new byte[(int) Math.min(lastFile(c).length(), 60 * 1024)];
            int n = in.read(buf);
            in.close();
            return new String(buf, 0, Math.max(n, 0), "UTF-8");
        } catch (Exception e) { return ""; }
    }

    /** 清除本地崩溃记录 */
    public static void clear(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) f.delete();
    }

    /** 打开 GitHub 新建 Issue 页面（预填标题，正文需手动粘贴——已自动复制到剪贴板） */
    public static void openGithubIssue(Context c) {
        try {
            copyToClipboard(c, "BiliLite crash log", readLast(c));
            String url = "https://github.com/" + REPO + "/issues/new?title="
                    + Uri.encode("[Crash] BiliLite " + BuildConfig.VERSION_NAME + " 崩溃反馈");
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            android.widget.Toast.makeText(c, "无法打开浏览器：" + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("deprecation")
    public static void copyToClipboard(Context c, String label, String text) {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    c.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setText(text);
        } catch (Exception ignore) {}
    }

    /** 是否有可写的外部存储权限（Android 6+ 保存文件到下载目录需要） */
    public static boolean canWriteExternal(Context c) {
        return Build.VERSION.SDK_INT < 23 ||
                "allowed".equals(Settings.System.getString(
                        c.getContentResolver(), "dummy")) || // never true, placeholder
                androidx.core.content.ContextCompat.checkSelfPermission(c,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
}
