package com.bililite.app.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.bililite.app.R;

/** 扫码登录页（TV 与手机通用） */
public class LoginActivity extends AppCompatActivity {

    private QrLoginHelper helper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        helper = new QrLoginHelper(this,
                findViewById(R.id.iv_qrcode),
                findViewById(R.id.tv_status),
                new QrLoginHelper.Callback() {
                    @Override public void onStatus(String text) { }
                    @Override public void onSuccess() { finish(); }
                });
        findViewById(R.id.iv_qrcode).setOnClickListener(v -> helper.refresh());
        findViewById(R.id.btn_refresh).setOnClickListener(v -> helper.refresh());
        helper.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        helper.stop();
    }
}
