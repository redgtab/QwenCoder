package com.bililite.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bililite.app.R;
import com.bililite.app.api.BiliApi;
import com.bililite.app.api.VideoItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 收藏：先显示收藏夹列表，点击后显示该收藏夹内视频 */
public class FavFolderActivity extends AppCompatActivity {

    private final List<VideoItem> items = new ArrayList<>();
    private VideoAdapter adapter;
    private TextView title;
    private long currentMediaId = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_space);
        title = findViewById(R.id.tv_uname);
        title.setText("我的收藏夹");
        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VideoAdapter(this, items, item -> {
            if (currentMediaId == 0) {
                // item.id 存的是文件夹 media_id
                try { currentMediaId = Long.parseLong(item.bvid); } catch (Exception ignored) {}
                if (currentMediaId != 0) loadFolderVideos(currentMediaId);
            } else {
                Intent in = new Intent(this, PlayActivity.class);
                in.putExtra("title", item.title);
                in.putExtra("bvid", item.bvid);
                in.putExtra("aid", item.aid);
                in.putExtra("cover", item.url());
                in.putExtra("uploader", item.uploader);
                in.putExtra("mid", item.mid);
                startActivity(in);
            }
        });
        rv.setAdapter(adapter);
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (currentMediaId != 0) { currentMediaId = 0; loadFolders(); }
            else finish();
        });
        loadFolders();
    }

    /** 把文件夹 JSON 伪装成 VideoItem：bvid 字段存 media_id */
    private static VideoItem folderItem(JSONObject o) {
        VideoItem vi = new VideoItem();
        vi.bvid = String.valueOf(o.optLong("id"));
        vi.title = o.optString("title", o.optString("name", "-"));
        vi.cover = o.optString("cover", "");
        vi.uploader = "共 " + o.optInt("media_count", 0) + " 个视频";
        return vi;
    }

    private void loadFolders() {
        title.setText("我的收藏夹");
        BiliApi.get(this).favFolderList((resp, err) -> {
            if (isFinishing()) return;
            if (err != null) { Toast.makeText(this, err, Toast.LENGTH_SHORT).show(); return; }
            List<VideoItem> folders = new ArrayList<>();
            JSONArray list = resp.optJSONArray("list");
            try {
                for (int i = 0; list != null && i < list.length(); i++)
                    folders.add(folderItem(list.getJSONObject(i)));
            } catch (org.json.JSONException ignored) { }
            items.clear(); items.addAll(folders);
            adapter.notifyDataSetChanged();
        });
    }

    private void loadFolderVideos(final long mediaId) {
        BiliApi.get(this).favContents(mediaId, 1, (resp, err) -> {
            if (isFinishing()) return;
            if (err != null) { Toast.makeText(this, err, Toast.LENGTH_SHORT).show(); return; }
            List<VideoItem> vids = new ArrayList<>();
            JSONArray medias = resp.optJSONArray("list");
            for (int i = 0; medias != null && i < medias.length(); i++) {
                JSONObject o = medias.optJSONObject(i);
                if (o == null) continue;
                VideoItem vi = new VideoItem();
                String bv = o.optString("bvid", "");
                vi.bvid = bv;
                vi.aid = o.optLong("id", 0);
                vi.title = o.optString("title", "-");
                vi.cover = o.optString("cover", "");
                vi.uploader = o.optString("upper_name", "");
                vids.add(vi);
            }
            items.clear(); items.addAll(vids);
            adapter.notifyDataSetChanged();
            title.setText("收藏视频 (" + vids.size() + ")");
        });
    }

    @Override
    public void onBackPressed() {
        if (currentMediaId != 0) { currentMediaId = 0; loadFolders(); }
        else super.onBackPressed();
    }
}
