package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.okhttp.OkHttpUtil;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 毒舌电影
 */
public class DuShe extends Spider {

    private final String siteUrl = "https://www.dushehub.com";
    private final String playProxy = "https://v.dushe.online";

    private final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36";

    private String req(String targetUrl) throws Exception {
        Request request = new Request.Builder()
                .addHeader("User-Agent", userAgent)
                .addHeader("Referer", siteUrl + "/")
                .get()
                .url(targetUrl)
                .build();
        OkHttpClient okHttpClient = OkHttpUtil.defaultClient();
        Response response = okHttpClient.newCall(request).execute();
        if (response.body() == null) return "";
        byte[] bytes = response.body().bytes();
        response.close();
        return new String(bytes, "utf-8");
    }

    private String find(Pattern pattern, String html) {
        Matcher matcher = pattern.matcher(html);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static final Pattern playerPattern = Pattern.compile("var player_aaaa\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL);
    private static final Pattern directVideoPattern = Pattern.compile("\\.(m3u8|mp4|flv|mkv|webm|ts)(\\?|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern tabNamePattern = Pattern.compile("<label class=\"module-tab-name\">\\s*<span[^>]*>([^<]+)</span>");
    private static final Pattern playListPattern = Pattern.compile("<div class=\"module-list sort-list tab-list[^\"]*\"[^>]*>(.*?)</div>\\s*<div", Pattern.DOTALL);

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONArray classes = new JSONArray();
        List<String> typeIds = Arrays.asList("dianying", "dianshiju", "zongyi", "dongman");
        List<String> typeNames = Arrays.asList("电影", "电视剧", "综艺", "动漫");
        for (int i = 0; i < typeIds.size(); i++) {
            JSONObject obj = new JSONObject();
            obj.put("type_id", typeIds.get(i));
            obj.put("type_name", typeNames.get(i));
            classes.put(obj);
        }

        // 筛选条件
        JSONObject filters = new JSONObject();
        JSONArray filterArr = new JSONArray();

        // 地区筛选
        JSONArray areaItems = new JSONArray();
        String[] areas = {"", "中国大陆", "中国香港", "中国台湾", "美国", "韩国", "日本", "泰国", "新加坡", "德国", "马来西亚", "印度", "英国", "法国", "加拿大", "西班牙", "俄罗斯", "其它"};
        for (String area : areas) {
            JSONObject item = new JSONObject();
            item.put("n", TextUtils.isEmpty(area) ? "全部" : area);
            item.put("v", area);
            areaItems.put(item);
        }
        JSONObject areaFilter = new JSONObject();
        areaFilter.put("key", "area");
        areaFilter.put("name", "地区");
        areaFilter.put("value", areaItems);
        filterArr.put(areaFilter);

        // 年份筛选
        JSONArray yearItems = new JSONArray();
        JSONObject allYear = new JSONObject();
        allYear.put("n", "全部");
        allYear.put("v", "");
        yearItems.put(allYear);
        for (int y = 2027; y >= 2004; y--) {
            JSONObject item = new JSONObject();
            item.put("n", String.valueOf(y));
            item.put("v", String.valueOf(y));
            yearItems.put(item);
        }
        JSONObject yearFilter = new JSONObject();
        yearFilter.put("key", "year");
        yearFilter.put("name", "年份");
        yearFilter.put("value", yearItems);
        filterArr.put(yearFilter);

        // 类型筛选
        JSONArray genreItems = new JSONArray();
        String[] genres = {"", "古装", "战争", "青春偶像", "喜剧", "家庭", "犯罪", "动作", "奇幻", "剧情", "历史", "经典", "乡村", "情景", "商战", "网剧", "其他"};
        for (String genre : genres) {
            JSONObject item = new JSONObject();
            item.put("n", TextUtils.isEmpty(genre) ? "全部" : genre);
            item.put("v", genre);
            genreItems.put(item);
        }
        JSONObject genreFilter = new JSONObject();
        genreFilter.put("key", "class");
        genreFilter.put("name", "剧情");
        genreFilter.put("value", genreItems);
        filterArr.put(genreFilter);

        // 排序筛选
        JSONArray sortItems = new JSONArray();
        String[] sorts = {"time", "hits", "score"};
        String[] sortNames = {"时间排序", "人气排序", "评分排序"};
        for (int s = 0; s < sorts.length; s++) {
            JSONObject item = new JSONObject();
            item.put("n", sortNames[s]);
            item.put("v", sorts[s]);
            sortItems.put(item);
        }
        JSONObject sortFilter = new JSONObject();
        sortFilter.put("key", "sort");
        sortFilter.put("name", "排序");
        sortFilter.put("value", sortItems);
        filterArr.put(sortFilter);

        for (String typeId : typeIds) {
            filters.put(typeId, filterArr);
        }

        JSONObject result = new JSONObject();
        result.put("class", classes);
        result.put("filters", filters);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String url = buildCategoryUrl(tid, pg, extend);
        String html = req(url);
        Document doc = Jsoup.parse(html);
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

        JSONObject result = new JSONObject();
        result.put("list", list);
        result.put("page", pg);
        result.put("pagecount", 999);
        result.put("limit", 36);
        result.put("total", 999);
        return result.toString();
    }

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

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0);
        String html = req(url);
        Document doc = Jsoup.parse(html);

        JSONObject vod = new JSONObject();
        vod.put("vod_id", url);
        vod.put("vod_name", doc.select("h1").text());
        vod.put("vod_pic", doc.select(".module-info-img img").attr("data-original"));
        vod.put("vod_content", doc.select(".vod_content").text());

        // 提取线路和选集
        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();

        // 1. 提取线路名称
        List<String> tabNames = new ArrayList<>();
        Matcher tabMatcher = tabNamePattern.matcher(html);
        while (tabMatcher.find()) {
            tabNames.add(tabMatcher.group(1).trim());
        }

        // 兜底：用 Jsoup 选择器
        if (tabNames.isEmpty()) {
            Elements tabElements = doc.select("label.module-tab-name span");
            if (tabElements.isEmpty()) tabElements = doc.select("div.module-tab-item span");
            for (Element tab : tabElements) {
                tabNames.add(tab.text().trim());
            }
        }

        // 2. 提取选集块
        List<String> playListBlocks = new ArrayList<>();
        Matcher listMatcher = playListPattern.matcher(html);
        while (listMatcher.find()) {
            playListBlocks.add(listMatcher.group(1));
        }

        // 兜底：用 Jsoup 选择器
        if (playListBlocks.isEmpty()) {
            Elements listElements = doc.select("div.module-list.sort-list.tab-list");
            for (Element listEl : listElements) {
                playListBlocks.add(listEl.html());
            }
        }

        // 3. 组装线路和选集
        int maxCount = Math.max(tabNames.size(), playListBlocks.size());
        for (int i = 0; i < maxCount; i++) {
            String tabName = i < tabNames.size() ? tabNames.get(i) : "线路" + (i + 1);
            if (TextUtils.isEmpty(tabName)) tabName = "线路" + (i + 1);
            playFrom.append(tabName).append("$$$");

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
        JSONArray jsonArray = new JSONArray().put(vod);
        result.put("list", jsonArray);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = siteUrl + "/search-------------.html?wd=" + URLEncoder.encode(key, "UTF-8");
        String html = req(url);
        Document doc = Jsoup.parse(html);
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
        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

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
        String html = req(id);
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
            // 静默处理异常
        }
        return null;
    }
}
