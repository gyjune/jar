package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DuShe extends Spider {

    private static final String siteUrl = "https://www.dushehub.com";
    private static final String playProxy = "https://v.dushe.online";
    private static final Pattern playerPattern = Pattern.compile("var player_aaaa\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL);
    private static final Pattern directVideoPattern = Pattern.compile("\\.(m3u8|mp4|flv|mkv|webm|ts)(\\?|$)", Pattern.CASE_INSENSITIVE);

    // 提取线路名称的正则
    private static final Pattern tabNamePattern = Pattern.compile("<label class=\"module-tab-name\">\\s*<span[^>]*>([^<]+)</span>");
    // 提取选集块的正则
    private static final Pattern playListPattern = Pattern.compile("<div class=\"module-list sort-list tab-list[^\"]*\"[^>]*>(.*?)</div>\\s*<div", Pattern.DOTALL);

    @Override
    public void init(Context context, String extend) {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();
        String[] typeNames = {"电影", "电视剧", "综艺", "动漫"};
        String[] typeIds = {"dianying", "dianshiju", "zongyi", "dongman"};
        for (int i = 0; i < typeIds.length; i++) {
            JSONObject obj = new JSONObject();
            obj.put("type_id", typeIds[i]);
            obj.put("type_name", typeNames[i]);
            classes.put(obj);
        }
        result.put("class", classes);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = buildCategoryUrl(tid, pg, extend);
        String html = OkHttp.string(url);
        Document doc = Jsoup.parse(html);
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        
        Elements items = doc.select("a.module-poster-item");
        if (items.isEmpty()) items = doc.select("a[href^=/album/]");
        
        for (Element item : items) {
            JSONObject vod = new JSONObject();
            String href = item.attr("href");
            if (href.startsWith("/")) href = siteUrl + href;
            vod.put("vod_id", href);
            vod.put("vod_name", item.attr("title"));
            vod.put("vod_pic", item.select("img").attr("data-original"));
            vod.put("vod_remarks", item.select(".module-item-note").text());
            list.put(vod);
        }
        
        result.put("list", list);
        result.put("page", pg);
        result.put("pagecount", 999);
        result.put("limit", 36);
        result.put("total", 999);
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0);
        String html = OkHttp.string(url);
        Document doc = Jsoup.parse(html);
        
        JSONObject vod = new JSONObject();
        vod.put("vod_id", url);
        vod.put("vod_name", doc.select("h1").text());
        vod.put("vod_pic", doc.select(".module-info-img img").attr("data-original"));
        vod.put("vod_content", doc.select(".vod_content").text());
        
        // 提取线路和选集（完全对齐 JS 逻辑）
        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();
        
        // 1. 提取所有线路名称
        List<String> tabNames = new ArrayList<>();
        Matcher tabMatcher = tabNamePattern.matcher(html);
        while (tabMatcher.find()) {
            tabNames.add(tabMatcher.group(1).trim());
        }
        
        // 如果正则没匹配到，用 Jsoup 兜底
        if (tabNames.isEmpty()) {
            Elements tabElements = doc.select("label.module-tab-name span");
            if (tabElements.isEmpty()) tabElements = doc.select("div.module-tab-item span");
            for (Element tab : tabElements) {
                tabNames.add(tab.text().trim());
            }
        }
        
        // 2. 提取所有选集块
        List<String> playListBlocks = new ArrayList<>();
        Matcher listMatcher = playListPattern.matcher(html);
        while (listMatcher.find()) {
            playListBlocks.add(listMatcher.group(1));
        }
        
        // 如果正则没匹配到，用 Jsoup 兜底
        if (playListBlocks.isEmpty()) {
            Elements listElements = doc.select("div.module-list.sort-list.tab-list");
            for (Element listEl : listElements) {
                playListBlocks.add(listEl.html());
            }
        }
        
        // 3. 组装线路和选集
        int maxCount = Math.max(tabNames.size(), playListBlocks.size());
        for (int i = 0; i < maxCount; i++) {
            // 线路名
            String tabName = i < tabNames.size() ? tabNames.get(i) : "线路" + (i + 1);
            if (TextUtils.isEmpty(tabName)) tabName = "线路" + (i + 1);
            playFrom.append(tabName).append("$$$");
            
            // 选集
            StringBuilder urls = new StringBuilder();
            if (i < playListBlocks.size()) {
                Document blockDoc = Jsoup.parse(playListBlocks.get(i));
                Elements links = blockDoc.select("a.module-play-list-link");
                for (Element link : links) {
                    String epName = link.select("span").text();
                    if (TextUtils.isEmpty(epName)) epName = link.attr("title");
                    String epUrl = link.attr("href");
                    if (epUrl.startsWith("/")) epUrl = siteUrl + epUrl;
                    urls.append(epName).append("$").append(epUrl).append("#");
                }
            }
            playUrl.append(urls).append("$$$");
        }
        
        vod.put("vod_play_from", playFrom.toString());
        vod.put("vod_play_url", playUrl.toString());
        
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        list.put(vod);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = siteUrl + "/search-------------.html?wd=" + URLEncoder.encode(key);
        String html = OkHttp.string(url);
        Document doc = Jsoup.parse(html);
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        
        Elements items = doc.select("a.module-poster-item");
        for (Element item : items) {
            JSONObject vod = new JSONObject();
            String href = item.attr("href");
            if (href.startsWith("/")) href = siteUrl + href;
            vod.put("vod_id", href);
            vod.put("vod_name", item.attr("title"));
            vod.put("vod_pic", item.select("img").attr("data-original"));
            vod.put("vod_remarks", item.select(".module-item-note").text());
            list.put(vod);
        }
        result.put("list", list);
        return result.toString();
    }

    /**
     * 播放逻辑：完全对齐 JS 的 play 函数
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        
        // 1. 如果本身就是视频直链，直接播放
        if (directVideoPattern.matcher(id).find()) {
            result.put("parse", 0);
            result.put("url", id);
            return result.toString();
        }
        
        // 2. 抓取播放页
        String html = OkHttp.string(id);
        if (TextUtils.isEmpty(html)) {
            result.put("parse", 1);
            result.put("url", id);
            return result.toString();
        }
        
        // 3. 提取真实播放地址
        String playUrl = extractPlayUrl(html);
        
        // 4. 判断是否为代理地址，设置 parse 和 header
        if (!TextUtils.isEmpty(playUrl) && playUrl.contains("v.dushe.online")) {
            result.put("parse", 1);
            result.put("url", playUrl);
            JSONObject headers = new JSONObject();
            headers.put("Referer", siteUrl + "/");
            result.put("header", headers.toString());
        } else if (!TextUtils.isEmpty(playUrl) && directVideoPattern.matcher(playUrl).find()) {
            result.put("parse", 0);
            result.put("url", playUrl);
        } else {
            result.put("parse", 1);
            result.put("url", playUrl != null ? playUrl : id);
        }
        
        return result.toString();
    }
    
    /**
     * 提取播放地址：完全对齐 JS 的 extractPlayUrl
     */
    private String extractPlayUrl(String html) {
        try {
            // 优先从 var player_aaaa 中提取
            Matcher matcher = playerPattern.matcher(html);
            if (matcher.find()) {
                JSONObject playerJson = new JSONObject(matcher.group(1));
                String url = playerJson.optString("url", "");
                String next = playerJson.optString("url_next", "");
                String title = playerJson.optString("vod_data", "");
                String from = playerJson.optString("from", "");
                
                if (!TextUtils.isEmpty(url)) {
                    return playProxy + "/?url=" + url + "&next=" + next + "&tittle=" + title + "&t=" + from + "&d=v2";
                }
            }
            
            // 兜底：从 iframe 提取
            Document doc = Jsoup.parse(html);
            String iframeSrc = doc.select(".MacPlayer iframe").attr("src");
            if (!TextUtils.isEmpty(iframeSrc)) {
                return iframeSrc;
            }
            
            // 兜底：正则匹配 m3u8
            Matcher m3u8Matcher = Pattern.compile("(https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*)").matcher(html);
            if (m3u8Matcher.find()) {
                return m3u8Matcher.group(1);
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }
    
    /**
     * 构造分类 URL（保持你之前确认正确的12段式短横线拼接）
     */
    private String buildCategoryUrl(String tid, String pg, HashMap<String, String> extend) {
        String area = extend.getOrDefault("area", "");
        String year = extend.getOrDefault("year", "");
        String genre = extend.getOrDefault("class", "");
        String sort = extend.getOrDefault("sort", "time");
        
        String[] parts = new String[12];
        Arrays.fill(parts, "");
        
        parts[0] = tid;
        parts[1] = area;
        parts[2] = sort;
        parts[3] = genre;
        parts[11] = year;
        
        if (!"1".equals(pg)) {
            parts[8] = pg;
        }
        
        return siteUrl + "/" + TextUtils.join("-", parts) + ".html";
    }
}