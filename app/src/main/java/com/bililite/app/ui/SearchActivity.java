package com.bililite.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
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

/** 搜索页：app 端 all/inline 接口，支持分页加载 */
public class SearchActivity extends AppCompatActivity {

    private final List<VideoItem> items = new ArrayList<>();
    private VideoAdapter adapter;
    private RecyclerView recycler;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private String keyword;
    private int page = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        recycler = findViewById(R.id.recycler);
        swipe = findViewById(R.id.swipe);
        progress = findViewById(R.id.progress);

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
        findViewById(R.id.btn_do_search).setOnClickListener(v -> doSearch());
        swipe.setOnRefreshListener(() -> { if (!TextUtils.isEmpty(keyword)) search(1); });
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || TextUtils.isEmpty(keyword)) return;
                LinearLayoutManager llm = (LinearLayoutManager) rv.getLayoutManager();
                if (llm != null && llm.findLastVisibleItemPosition() >= items.size() - 3) search(page + 1);
            }
        });
    }

    private void doSearch() {
        TextView et = findViewById(R.id.et_keyword);
        String kw = ((android.widget.EditText) et).getText().toString().trim();
        if (kw.isEmpty()) {
            Toast.makeText(this, R.string.search_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        keyword = kw;
        search(1);
    }

    private void search(final int p) {
        progress.setVisibility(View.VISIBLE);
        BiliApi.get(this).search(keyword, p, (data, err) -> {
            progress.setVisibility(View.GONE);
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
