package com.bililite.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bililite.app.R;
import com.bililite.app.api.BiliApi;
import com.bililite.app.api.VideoItem;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** UP 主空间页：投稿视频列表 */
public class UserSpaceActivity extends AppCompatActivity {

    private final List<VideoItem> items = new ArrayList<>();
    private VideoAdapter adapter;
    private long mid;
    private int page = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_space);

        mid = getIntent().getLongExtra("mid", 0);
        String uname = getIntent().getStringExtra("uname");
        ((TextView) findViewById(R.id.tv_uname)).setText(TextUtils.isEmpty(uname) ? getString(R.string.up_title) : uname);

        RecyclerView recycler = findViewById(R.id.recycler);
        SwipeRefreshLayout swipe = findViewById(R.id.swipe);
        adapter = new VideoAdapter(this, items, it -> {
            Intent in = new Intent(this, PlayActivity.class);
            in.putExtra("title", it.title);
            in.putExtra("bvid", it.bvid);
            in.putExtra("aid", it.aid);
            in.putExtra("cover", it.url());
            in.putExtra("uploader", it.uploader);
            in.putExtra("mid", it.mid);
            in.putExtra("plays", it.plays);
            in.putExtra("danmaku", it.danmaku);
            in.putExtra("date", it.dateText);
            in.putExtra("intro", it.intro);
            startActivity(in);
        });
        LinearLayoutManager lm = new LinearLayoutManager(this);
        recycler.setLayoutManager(lm);
        recycler.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        swipe.setOnRefreshListener(() -> load(1));
        load(1);
    }

    private void load(final int p) {
        BiliApi.get(this).userVideos(mid, p, (data, err) -> {
            SwipeRefreshLayout swipe = findViewById(R.id.swipe);
            swipe.setRefreshing(false);
            if (err != null) { Toast.makeText(this, err, Toast.LENGTH_SHORT).show(); return; }
            try {
                JSONArray arr = data.getJSONArray("list");
                if (p == 1) items.clear();
                page = p;
                items.addAll(VideoItem.listFrom(arr));
                adapter.notifyDataSetChanged();
            } catch (Exception e) {
                Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
