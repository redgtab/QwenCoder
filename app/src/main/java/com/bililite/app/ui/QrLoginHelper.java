package com.bililite.app.ui;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bililite.app.R;
import com.bililite.app.api.BiliApi;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 扫码登录逻辑（供 LoginActivity 复用）：
 * 生成 TV 登录二维码 -> zxing core 离线渲染成 Bitmap -> 轮询状态 -> 成功写入 Cookie
 */
public class QrLoginHelper {

    public interface Callback {
        void onStatus(String text);
        void onSuccess();
    }

    private final AppCompatActivity act;
    private final ImageView qrView;
    private final TextView statusView;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String authCode;
    private boolean running;

    public QrLoginHelper(AppCompatActivity act, ImageView qrView, TextView statusView, Callback cb) {
        this.act = act;
        this.qrView = qrView;
        this.statusView = statusView;
        this.callback = cb;
    }

    public void start() {
        running = true;
        refresh();
    }

    public void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    public void refresh() {
        setStatus("正在获取二维码…");
        BiliApi.get(act).qrGenerate((data, err) -> {
            if (!running) return;
            if (err != null || data == null) {
                setStatus("获取失败：" + err + "，点击重试");
                return;
            }
            try {
                Bitmap bmp = renderQr(data.getString("url"), 512);
                qrView.setImageBitmap(bmp);
                authCode = data.getString("auth_code");
                setStatus("请使用哔哩哔哩客户端扫码登录");
                pollDelay(0);
            } catch (Exception e) {
                setStatus("二维码渲染失败：" + e.getMessage());
            }
        });
    }

    private void pollDelay(int times) {
        if (!running) return;
        if (times > 120) { // 约 4 分钟超时
            setStatus("二维码已失效，点击刷新");
            return;
        }
        handler.postDelayed(() -> doPoll(times), 2000);
    }

    private void doPoll(int times) {
        if (!running || TextUtils.isEmpty(authCode)) return;
        BiliApi.get(act).qrPoll(authCode, (data, err) -> {
            if (!running) return;
            if (err != null) { pollDelay(times + 1); return; }
            try {
                int status = data.getInt("status");
                switch (status) {
                    case 0:
                        running = false;
                        setStatus(getString(R.string.login_success));
                        Toast.makeText(act, R.string.login_success, Toast.LENGTH_SHORT).show();
                        // 拉取用户信息后回调
                        BiliApi.get(act).myInfo((d2, e2) -> {
                            if (callback != null) callback.onSuccess();
                        });
                        break;
                    case 86038:
                        setStatus("等待扫码…");
                        pollDelay(times + 1);
                        break;
                    case 86039:
                        setStatus("已扫描，请在手机上确认");
                        pollDelay(times + 1);
                        break;
                    case 86040:
                        setStatus("二维码已过期，点击刷新");
                        break;
                    default:
                        setStatus(data.optString("message", "状态未知") + "，点击刷新");
                        break;
                }
            } catch (Exception e) {
                pollDelay(times + 1);
            }
        });
    }

    /** zxing core 纯 Java 渲染 QR Code 为 Bitmap（不依赖 javase 模块） */
    public static Bitmap renderQr(String content, int size) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            int off = y * size;
            for (int x = 0; x < size; x++) {
                pixels[off + x] = m.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }

    private void setStatus(String s) {
        if (statusView != null) statusView.setText(s);
        if (callback != null) callback.onStatus(s);
    }

    private String getString(int res) { return act.getString(res); }
}
