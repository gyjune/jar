package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * 毒舌电影 - 测试版
 * playerContent 写死 m3u8，测影视仓能不能播
 */
public class DuShe extends Spider {

    private final String siteUrl = "https://www.dushehub.com";

    private final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.14; rv:102.0) Gecko/20100101 Firefox/102.0";

    // ============================================================
    // 首页
    // ============================================================
    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();

        JSONArray classes = new JSONArray();
        String[] typeIds = {"dianying", "dianshiju", "zongyi", "dongman"};
        String[] typeNames = {"电影", "电视剧", "综艺", "动漫"};
        for (int i = 0; i < typeIds.length; i++) {
            JSONObject obj = new JSONObject();
            obj.put("type_id", typeIds[i]);
            obj.put("type_name", typeNames[i]);
            classes.put(obj);
        }
        result.put("class", classes);

        JSONArray list = new JSONArray();
        try {
            String html = com.github.catvod.net.OkHttp.string(siteUrl + "/");
            Document doc = Jsoup.parse(html);
            Elements items = doc.select("a.module-poster-item");
            for (int i = 0; i < Math.min(items.size(), 20); i++) {
                Element item = items.get(i);
                String href = item.attr("href");
                if (TextUtils.isEmpty(href)) continue;
                if (href.startsWith("/")) href = siteUrl + href;

                String name = item.attr("title");
                if (TextUtils.isEmpty(name)) name = item.select(".module-poster-item-title").text();
                String pic = item.select("img").attr("data-original");
                String remark = item.select(".module-item-note").text();

                JSONObject vod = new JSONObject();
                vod.put("vod_id", href);
                vod.put("vod_name", name);
                vod.put("vod_pic", pic);
                vod.put("vod_remarks", remark);
                list.put(vod);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        result.put("list", list);

        return result.toString();
    }

    // ============================================================
    // 详情
    // ============================================================
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String detailUrl = vodId.startsWith("http") ? vodId : siteUrl + vodId;
        String html = com.github.catvod.net.OkHttp.string(detailUrl);
        Document doc = Jsoup.parse(html);

        String name = doc.selectFirst("h1") != null ? doc.selectFirst("h1").text().trim() : "";
        String pic = doc.select(".module-info-img img").attr("data-original");
        if (TextUtils.isEmpty(pic)) pic = doc.select(".module-item-pic img").attr("data-original");

        List<String> tabNames = new ArrayList<>();
        Elements tabItems = doc.select(".module-tab-item");
        for (Element tab : tabItems) {
            String nameT = tab.select("span").text().trim();
            if (!TextUtils.isEmpty(nameT)) tabNames.add(nameT);
        }

        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();
        Elements contents = doc.select("div.module-list.sort-list.tab-list");

        for (int i = 0; i < contents.size(); i++) {
            Elements links = contents.get(i).select("a.module-play-list-link");
            if (links.isEmpty()) continue;

            StringBuilder urls = new StringBuilder();
            for (Element link : links) {
                String epName = link.select("span").text().trim();
                if (TextUtils.isEmpty(epName)) epName = link.attr("title");
                String epUrl = link.attr("href");
                if (epUrl.startsWith("/")) epUrl = siteUrl + epUrl;
                if (urls.length() > 0) urls.append("#");
                urls.append(epName).append("$").append(epUrl);
            }

            String tabName = (i < tabNames.size() && !TextUtils.isEmpty(tabNames.get(i)))
                    ? tabNames.get(i) : ("线路" + (i + 1));
            playFrom.add(tabName);
            playUrl.add(urls.toString());
        }

        JSONObject vod = new JSONObject();
        vod.put("vod_id", vodId);
        vod.put("vod_name", name);
        vod.put("vod_pic", pic);
        vod.put("vod_play_from", TextUtils.join("$$$", playFrom));
        vod.put("vod_play_url", TextUtils.join("$$$", playUrl));

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        list.put(vod);
        result.put("list", list);
        return result.toString();
    }

    // ============================================================
    // ★ 测试版 playerContent（写死 m3u8）
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        return "{\"parse\":0,\"url\":\"https://v.lzcdn27.com/20260911/16824_7311a09b/index.m3u8\"}";
    }

    // ============================================================
    // 搜索（返回空）
    // ============================================================
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        JSONObject result = new JSONObject();
        result.put("list", new JSONArray());
        return result.toString();
    }
}