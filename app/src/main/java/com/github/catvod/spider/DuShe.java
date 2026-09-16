package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 毒舌电影 - www.dushehub.com
 * playerContent 返回 {"parse":0/1,"url":"..."}
 */
public class DuShe extends Spider {

    private final String siteUrl = "https://www.dushehub.com";

    private final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.14; rv:102.0) Gecko/20100101 Firefox/102.0";

    private static final Pattern m3u8Pattern = Pattern.compile(
            "(https?://[^\\s<>\"']+\\.m3u8[^\\s<>\"']*)");
    private static final Pattern playerPattern = Pattern.compile(
            "var\\s+player_aaaa\\s*=\\s*(\\{[^;]+\\})");

    // ============================================================
    // header
    // ============================================================
    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", userAgent);
        header.put("Referer", siteUrl + "/");
        return header;
    }

    private String req(String url) {
        return OkHttp.string(url, getHeader());
    }

    // ============================================================
    // 列表解析
    // ============================================================
    private JSONArray extractList(String html) throws Exception {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;

        Document doc = Jsoup.parse(html);
        Elements items = doc.select("a.module-poster-item");
        if (items.isEmpty()) items = doc.select("a[href^=/album/]");

        for (Element item : items) {
            String href = item.attr("href");
            if (TextUtils.isEmpty(href)) continue;
            if (href.startsWith("/")) href = siteUrl + href;

            String name = item.attr("title");
            if (TextUtils.isEmpty(name)) name = item.select(".module-poster-item-title").text();
            if (TextUtils.isEmpty(name)) name = item.select("img").attr("alt");

            String pic = item.select("img").attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = item.select("img").attr("data-src");
            if (TextUtils.isEmpty(pic)) pic = item.select("img").attr("src");

            String remark = item.select(".module-item-note").text();

            JSONObject vod = new JSONObject();
            vod.put("vod_id", href);
            vod.put("vod_name", name);
            vod.put("vod_pic", fixUrl(pic));
            vod.put("vod_remarks", remark);
            list.put(vod);
        }
        return list;
    }

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

        if (filter) {
            result.put("filters", buildFilters());
        }

        try {
            String html = req(siteUrl + "/");
            result.put("list", extractList(html));
        } catch (Exception e) {
            SpiderDebug.log(e);
            result.put("list", new JSONArray());
        }

        return result.toString();
    }

    private JSONObject buildFilters() throws Exception {
        JSONObject filters = new JSONObject();

        JSONArray areaValues = new JSONArray();
        areaValues.put(filterValue("全部", ""));
        String[] areas = {"中国大陆", "中国香港", "中国台湾", "美国", "法国", "英国", "日本", "韩国", "德国", "泰国", "印度"};
        for (String a : areas) areaValues.put(filterValue(a, a));

        JSONArray yearValues = new JSONArray();
        yearValues.put(filterValue("全部", ""));
        for (int y = 2026; y >= 2015; y--) {
            yearValues.put(filterValue(String.valueOf(y), String.valueOf(y)));
        }

        JSONArray sortValues = new JSONArray();
        sortValues.put(filterValue("默认", ""));
        sortValues.put(filterValue("按时间", "time"));
        sortValues.put(filterValue("按人气", "hits"));
        sortValues.put(filterValue("按评分", "score"));

        JSONArray movieClass = new JSONArray();
        movieClass.put(filterValue("全部", ""));
        movieClass.put(filterValue("动作片", "dongzuo"));
        movieClass.put(filterValue("喜剧片", "xiju"));
        movieClass.put(filterValue("爱情片", "aiqing"));
        movieClass.put(filterValue("科幻片", "kehuan"));
        movieClass.put(filterValue("恐怖片", "kongbu"));
        movieClass.put(filterValue("剧情片", "juqing"));
        movieClass.put(filterValue("战争片", "zhanzheng"));
        movieClass.put(filterValue("纪录片", "jilupian"));

        JSONArray tvClass = new JSONArray();
        tvClass.put(filterValue("全部", ""));
        tvClass.put(filterValue("国产剧", "guochan"));
        tvClass.put(filterValue("美剧", "meiju"));
        tvClass.put(filterValue("韩剧", "hanju"));
        tvClass.put(filterValue("日剧", "riju"));
        tvClass.put(filterValue("泰剧", "taiju"));
        tvClass.put(filterValue("港剧", "gangju"));

        JSONArray emptyClass = new JSONArray();
        emptyClass.put(filterValue("全部", ""));

        JSONArray movieFilters = new JSONArray();
        movieFilters.put(filterGroup("class", "分类", movieClass));
        movieFilters.put(filterGroup("area", "地区", areaValues));
        movieFilters.put(filterGroup("year", "年份", yearValues));
        movieFilters.put(filterGroup("sort", "排序", sortValues));
        filters.put("dianying", movieFilters);

        JSONArray tvFilters = new JSONArray();
        tvFilters.put(filterGroup("class", "分类", tvClass));
        tvFilters.put(filterGroup("area", "地区", areaValues));
        tvFilters.put(filterGroup("year", "年份", yearValues));
        tvFilters.put(filterGroup("sort", "排序", sortValues));
        filters.put("dianshiju", tvFilters);

        JSONArray varietyFilters = new JSONArray();
        varietyFilters.put(filterGroup("class", "分类", emptyClass));
        varietyFilters.put(filterGroup("area", "地区", areaValues));
        varietyFilters.put(filterGroup("year", "年份", yearValues));
        varietyFilters.put(filterGroup("sort", "排序", sortValues));
        filters.put("zongyi", varietyFilters);

        JSONArray animeFilters = new JSONArray();
        animeFilters.put(filterGroup("class", "分类", emptyClass));
        animeFilters.put(filterGroup("area", "地区", areaValues));
        animeFilters.put(filterGroup("year", "年份", yearValues));
        animeFilters.put(filterGroup("sort", "排序", sortValues));
        filters.put("dongman", animeFilters);

        return filters;
    }

    // ============================================================
    // 分类
    // ============================================================
    @Override
    public String categoryContent(String tid, String pg, boolean filter,
                                  HashMap<String, String> extend) throws Exception {
        String area = extend != null && extend.get("area") != null ? extend.get("area") : "";
        String year = extend != null && extend.get("year") != null ? extend.get("year") : "";
        String classType = extend != null && extend.get("class") != null ? extend.get("class") : "";
        String sort = extend != null && extend.get("sort") != null ? extend.get("sort") : "";

        if ("全部".equals(area)) area = "";
        if ("全部".equals(year)) year = "";
        if ("全部".equals(classType)) classType = "";
        if ("全部".equals(sort)) sort = "";

        String[] parts = new String[12];
        Arrays.fill(parts, "");
        parts[0] = tid;
        parts[1] = area;
        parts[2] = sort;
        parts[3] = classType;
        parts[11] = year;
        if (!"1".equals(pg) && !TextUtils.isEmpty(pg)) {
            parts[8] = pg;
        }

        String url = siteUrl + "/show/" + TextUtils.join("-", parts) + ".html";
        SpiderDebug.log("category url: " + url);

        JSONArray videos = extractList(req(url));
        int page = parseIntSafe(pg, 1);

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", page + 1);
        result.put("limit", 36);
        result.put("total", Integer.MAX_VALUE);
        result.put("list", videos);
        return result.toString();
    }

    // ============================================================
    // 详情
    // ============================================================
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String detailUrl = vodId.startsWith("http") ? vodId : siteUrl + vodId;
        String html = req(detailUrl);
        Document doc = Jsoup.parse(html);

        String name = doc.selectFirst("h1") != null ? doc.selectFirst("h1").text().trim() : "";

        String pic = doc.select(".module-info-img img").attr("data-original");
        if (TextUtils.isEmpty(pic)) pic = doc.select(".module-item-pic img").attr("data-original");
        if (TextUtils.isEmpty(pic)) pic = doc.select(".module-item-pic img").attr("data-src");

        String content = doc.select(".module-info-introduction-content").text();
        if (TextUtils.isEmpty(content)) content = doc.select(".vod_content").text();
        if (TextUtils.isEmpty(content)) content = doc.select("meta[name=description]").attr("content");

        List<String> tabNames = new ArrayList<>();
        Elements tabItems = doc.select(".module-tab-item");
        for (Element tab : tabItems) {
            String nameT = tab.select("span").text().trim();
            if (!TextUtils.isEmpty(nameT)) tabNames.add(nameT);
        }

        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();
        Elements contents = doc.select("div.module-list.sort-list.tab-list");
        if (contents.isEmpty()) contents = doc.select("div.module-play-list-content");

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
        vod.put("vod_pic", fixUrl(pic));
        vod.put("vod_content", content);
        vod.put("vod_play_from", TextUtils.join("$$$", playFrom));
        vod.put("vod_play_url", TextUtils.join("$$$", playUrl));

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        list.put(vod);
        result.put("list", list);
        return result.toString();
    }

    // ============================================================
    // 搜索
    // ============================================================
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String keyWord = URLEncoder.encode(key, "UTF-8");
        String searchUrl = siteUrl + "/search/" + keyWord + "-------------.html";
        JSONArray videos = extractList(req(searchUrl));
        JSONObject result = new JSONObject();
        result.put("list", videos);
        return result.toString();
    }

    // ============================================================
    // ★ playerContent（带 parse）
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            // 1. id 本身是直链
            if (id != null && Pattern.compile("\\.(m3u8|mp4|flv|mkv|webm|ts)",
                    Pattern.CASE_INSENSITIVE).matcher(id).find()) {
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", id);
                return result.toString();
            }

            // 2. 抓播放页
            String html = req(id);
            if (TextUtils.isEmpty(html)) {
                JSONObject result = new JSONObject();
                result.put("parse", 1);
                result.put("url", id);
                return result.toString();
            }

            // 3. 抠 player_aaaa
            String realUrl = "";
            String from = "";
            String next = "";
            String title = "";

            Matcher pm = playerPattern.matcher(html);
            if (pm.find()) {
                try {
                    String raw = pm.group(1);
                    raw = raw.replaceAll("([{,])\\s*([a-zA-Z0-9_]+)\\s*:", "$1\"$2\":");
                    raw = raw.replaceAll(":\\s*'([^']*)'", ":\"$1\"");
                    raw = raw.replace("\\/", "/");
                    raw = raw.replaceAll(",\\s*}", "}");
                    JSONObject p = new JSONObject(raw);
                    realUrl = p.optString("url", "");
                    from = p.optString("from", "");
                    next = p.optString("link_next", "");
                    if (next.startsWith("/")) next = siteUrl + next;
                    JSONObject vd = p.optJSONObject("vod_data");
                    if (vd != null) title = vd.optString("vod_name", "");
                } catch (Exception e) {
                    SpiderDebug.log("player_aaaa parse error");
                }
            }

            if (TextUtils.isEmpty(realUrl)) {
                JSONObject result = new JSONObject();
                result.put("parse", 1);
                result.put("url", id);
                return result.toString();
            }

            // 4. m3u8 直连
            if (realUrl.contains(".m3u8") || realUrl.contains(".mp4")) {
                SpiderDebug.log("direct m3u8=" + realUrl);
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", realUrl);
                return result.toString();
            }

            // 5. 不是 m3u8 → 代理地址当 url
            String proxyUrl = "https://v.dushe.online/?url=" + URLEncoder.encode(realUrl, "UTF-8")
                    + "&next=" + URLEncoder.encode(next, "UTF-8")
                    + "&tittle=" + URLEncoder.encode(title, "UTF-8")
                    + "&t=" + URLEncoder.encode(from, "UTF-8")
                    + "&d=v2";
            SpiderDebug.log("proxy=" + proxyUrl);
            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("url", proxyUrl);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("url", id);
            return result.toString();
        }
    }

    // ============================================================
    // 工具
    // ============================================================
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        url = url.trim();
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http")) return url;
        if (url.startsWith("/")) return siteUrl + url;
        return siteUrl + "/" + url;
    }

    private JSONObject filterValue(String name, String value) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("n", name);
        obj.put("v", value);
        return obj;
    }

    private JSONObject filterGroup(String key, String name, JSONArray values) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("key", key);
        obj.put("name", name);
        obj.put("value", values);
        return obj;
    }

    private int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }
}