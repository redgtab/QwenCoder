package com.bililite.app.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bililite.app.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** 动态列表适配器：兼容 polymer v1 items（mine 与 space 同格式） */
public class DynamicAdapter extends RecyclerView.Adapter<DynamicAdapter.VH> {

    public interface OnUserClick { void onUser(long mid, String name); }

    private final Context ctx;
    private final List<JSONObject> items;
    private final OnUserClick userClick;

    public DynamicAdapter(Context ctx, List<JSONObject> items, OnUserClick uc) {
        this.ctx = ctx; this.items = items; this.userClick = uc;
    }

    @Override public VH onCreateViewHolder(ViewGroup parent, int type) {
        return new VH(LayoutInflater.from(ctx).inflate(R.layout.item_dynamic, parent, false));
    }

    @Override public void onBindViewHolder(VH h, int pos) {
        JSONObject it = items.get(pos);
        try {
            JSONObject user = it.optJSONObject("modules") != null
                    ? it.getJSONObject("modules").optJSONObject("module_author") : null;
            if (user == null) user = it.optJSONObject("author");
            String name = user == null ? "未知用户" : user.optString("name", "?");
            long mid = user == null ? 0 : user.optLong("mid", 0);
            String face = user == null ? "" : user.optString("face", "");
            String desc = user != null && user.optJSONObject("pendant") == null ? "" : "";
            h.uname.setText(name);

            // 时间：pub_time 秒级
            long pub = user != null ? user.optLong("pub_timestamp",
                    user.optLong("pub_time", 0)) : 0;
            if (pub == 0) pub = it.optLong("create_time", 0);
            if (pub > 1_000_000_000_000L) pub /= 1000;
            h.time.setText(pub > 0 ? timeAgo(pub) : "");

            // 正文 + 视频模块
            StringBuilder text = new StringBuilder();
            String cover = null;
            JSONObject content = it.optJSONObject("modules") != null
                    ? it.getJSONObject("modules").optJSONObject("module_dynamic") : null;
            if (content == null) content = it.optJSONObject("card_content");
            if (content != null) {
                JSONObject major = content.optJSONObject("major");
                if (major != null) {
                    JSONObject video = major.optJSONObject("archive");
                    JSONObject opus = major.optJSONObject("opus");
                    if (video != null) {
                        text.append(video.optString("title", ""));
                        cover = video.optString("cover", "");
                        JSONObject stat = video.optJSONObject("stat");
                        if (stat != null) {
                            text.append("\n▶ ").append(stat.optString("play", ""))
                                    .append(" · 💬 ").append(stat.optString("danmaku", ""));
                        }
                    } else if (opus != null) {
                        JSONObject summary = opus.optJSONObject("summary");
                        if (summary != null) text.append(summary.optString("text", ""));
                        else text.append(opus.optString("title", ""));
                        JSONArray pics = opus.optJSONArray("pictures");
                        if (pics != null && pics.length() > 0)
                            cover = pics.getJSONObject(0).optString("url", "");
                    } else {
                        JSONObject draw = major.optJSONObject("draw");
                        if (draw != null) {
                            JSONArray imgs = draw.optJSONArray("items");
                            if (imgs != null && imgs.length() > 0)
                                cover = imgs.getJSONObject(0).optString("src", "");
                        }
                    }
                }
                JSONObject item = content.optJSONObject("item_text");
                if (item != null && text.length() == 0)
                    text.append(item.optString("desc", ""));
                if (text.length() == 0) text.append(content.optString("desc", ""));
            }
            if (TextUtils.isEmpty(text)) text.append("(转发/其他动态)");
            h.text.setText(text.toString());

            if (!TextUtils.isEmpty(cover)) {
                h.cover.setVisibility(View.VISIBLE);
                ImageLoader.load(h.cover, cover.startsWith("http") ? cover : "https:" + cover, 720);
            } else h.cover.setVisibility(View.GONE);

            // 底部统计（评论/转发/点赞）
            JSONObject cnt = it.optJSONObject("modules") != null
                    ? it.getJSONObject("modules").optJSONObject("module_bottom") : null;
            h.stat.setText(cnt == null ? "" : "互动数据见详情");

            h.itemView.setOnClickListener(v -> {
                if (userClick != null && mid > 0) userClick.onUser(mid, name);
            });
            h.uname.setOnClickListener(v -> {
                if (userClick != null && mid > 0) userClick.onUser(mid, name);
            });
            if (!TextUtils.isEmpty(face)) ImageLoader.load(h.face, face.startsWith("http") ? face : "https:" + face, 96);
            else h.face.setImageResource(R.drawable.ic_placeholder);
        } catch (Exception ignored) { }
    }

    static String timeAgo(long tsSec) {
        long d = System.currentTimeMillis() / 1000 - tsSec;
        if (d < 60) return "刚刚";
        if (d < 3600) return d / 60 + " 分钟前";
        if (d < 86400) return d / 3600 + " 小时前";
        if (d < 86400 * 7) return d / 86400 + " 天前";
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date(tsSec * 1000L));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView face, cover;
        TextView uname, time, text, stat;
        VH(View v) {
            super(v);
            face = v.findViewById(R.id.iv_face);
            cover = v.findViewById(R.id.iv_cover);
            uname = v.findViewById(R.id.tv_uname);
            time = v.findViewById(R.id.tv_time);
            text = v.findViewById(R.id.tv_text);
            stat = v.findViewById(R.id.tv_stat);
        }
    }
}
