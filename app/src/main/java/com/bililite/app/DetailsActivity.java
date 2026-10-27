package com.bililite.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * TV 详情/播放中转页：接收 PlayActivity 传来的 web_url，
 * 尝试唤起系统播放器或浏览器完成播放（不内置解码器以保证 API19 兼容性）。
 */
public class DetailsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        String url = getIntent().getStringExtra("web_url");
        String title = getIntent().getStringExtra("title");
        tv.setText((title == null ? "" : title) + "\n\n正在打开播放…");
        tv.setPadding(48, 48, 48, 48);
        setContentView(tv);

        if (url != null) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (ActivityNotFoundException e) {
                tv.setText(getString(R.string.no_player));
            }
        }
        finish();
    }
}
