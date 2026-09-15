package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.okhttp.OkHttpUtil;

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
 * 结构参考南瓜
 * 播放：找到 m3u8 直连；找不到 m3u8 走 v.dushe.online 代理
 */
public class DuShe extends Spider {

    private final String siteUrl = "https://www.dushehub.com";

    private final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.14; rv:102.0) Gecko/20100101 Firefox/102.0";

    private static final Pattern m3u8Pattern = Pattern.compile(
            "(https?://[^\\s<>\"']+\\.m3u8[^\\s<>\"']*)");
    private static final Pattern playerPattern = Pattern.compile(
            "var\\s+player_aaaa\\s*=\\s*(\\{[^;]+\\})");

    // ============================================================
    // header（照南瓜）
    // ============================================================
    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", userAgent);
        header.put("Referer", siteUrl + "/");
        return header;
    }

    private String headers() {
        try {
            JSONObject h = new JSONObject();
            h.put("User-Agent", userAgent);
            h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            h.put("Accept-Language", "zh-CN,zh;q=0.9");
            h.put("Referer", siteUrl + "/");
            return h.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String m3u8Headers() {
        try {
            JSONObject h = new JSONObject();
            h.put("User-Agent", userAgent);
            h.put("Referer", siteUrl + "/");
            h.put("Accept", "*/*");
            return h.toString();
        } catch (Exception e) {
            return "";
        }
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
    // 提取 m3u8（照南瓜）
    // ============================================================
    private String extractM3u8(String html) {
        if (TextUtils.isEmpty(html)) return null;

        Matcher pm = playerPattern.matcher(html);
        if (pm.find()) {
            try {
                String raw = pm.group(1);
                raw = raw.replaceAll("([{,])\\s*([a-zA-Z0-9_]+)\\s*:", "$1\"$2\":");
                raw = raw.replaceAll(":\\s*'([^']*)'", ":\"$1\"");
                raw = raw.replace("\\/", "/");
                raw = raw.replaceAll(",\\s*}", "}");
                JSONObject p = new JSONObject(raw);
                String url = p.optString("url", "");
                if (!TextUtils.isEmpty(url) && url.contains(".m3u8")) {
                    return cleanUrl(url);
                }
            } catch (Exception e) {
                SpiderDebug.log("player_aaaa parse error");
            }
        }

        Matcher mm = m3u8Pattern.matcher(html);
        if (mm.find()) return cleanUrl(mm.group(1));
        return null;
    }

    // ============================================================
    // 提取 player_aaaa 的全部字段（用于走代理）
    // ============================================================
    private JSONObject extractPlayerData(String html) {
        if (TextUtils.isEmpty(html)) return null;

        Matcher pm = playerPattern.matcher(html);
        if (pm.find()) {
            try {
                String raw = pm.group(1);
                raw = raw.replaceAll("([{,])\\s*([a-zA-Z0-9_]+)\\s*:", "$1\"$2\":");
                raw = raw.replaceAll(":\\s*'([^']*)'", ":\"$1\"");
                raw = raw.replace("\\/", "/");
                raw = raw.replaceAll(",\\s*}", "}");
                return new JSONObject(raw);
            } catch (Exception e) {
                SpiderDebug.log("extractPlayerData error");
            }
        }
        return null;
    }

    // ============================================================
    // 拼代理 URL
    // ============================================================
    private String buildProxyUrl(JSONObject p) {
        try {
            String url = p.optString("url", "");
            String from = p.optString("from", "");
            String next = p.optString("link_next", "");
            if (next.startsWith("/")) next = siteUrl + next;
            String title = "";
            JSONObject vd = p.optJSONObject("vod_data");
            if (vd != null) title = vd.optString("vod_name", "");

            return "https://v.dushe.online/?url=" + URLEncoder.encode(url, "UTF-8")
                    + "&next=" + URLEncoder.encode(next, "UTF-8")
                    + "&tittle=" + URLEncoder.encode(title, "UTF-8")
                    + "&t=" + URLEncoder.encode(from, "UTF-8")
                    + "&d=v2";
        } catch (Exception e) {
            SpiderDebug.log("buildProxyUrl error");
            return "";
        }
    }

    // ============================================================
    // TVBox 接口
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

        return result.toString();
    }

    private JSONObject buildFilters() throws Exception {
        JSONObject filters = new JSONObject();

        JSONArray areaValues = new JSONArray();
        areaValues.put(filterValue("全部地区", ""));
        String[][] areas = {{"中国大陆", "中国大陆"}, {"中国香港", "中国香港"}, {"中国台湾", "中国台湾"},
                {"美国", "美国"}, {"法国", "法国"}, {"英国", "英国"}, {"日本", "日本"},
                {"韩国", "韩国"}, {"德国", "德国"}, {"泰国", "泰国"}, {"印度", "印度"}};
        for (String[] a : areas) areaValues.put(filterValue(a[1], a[0]));

        JSONArray yearValues = new JSONArray();
        yearValues.put(filterValue("全部年份", ""));
        for (int y = 2026; y >= 2015; y--) {
            yearValues.put(filterValue(String.valueOf(y), String.valueOf(y)));
        }

        JSONArray sortValues = new JSONArray();
        sortValues.put(filterValue("默认排序", ""));
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

    @Override
    public String homeVideoContent() throws Exception {
        JSONObject result = new JSONObject();
        try {
            String html = OkHttpUtil.string(siteUrl + "/", getHeader());
            JSONArray list = extractList(html);
            JSONArray out = new JSONArray();
            for (int i = 0; i < Math.min(list.length(), 20); i++) out.put(list.get(i));
            result.put("list", out);
        } catch (Exception e) {
            SpiderDebug.log(e);
            result.put("list", new JSONArray());
        }
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter,
                                  HashMap<String, String> extend) throws Exception {
        try {
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

            String html = OkHttpUtil.string(url, getHeader());
            JSONArray list = extractList(html);
            int page = parseIntSafe(pg, 1);

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("pagecount", page + 1);
            result.put("limit", 36);
            result.put("total", Integer.MAX_VALUE);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            JSONObject result = new JSONObject();
            result.put("page", parseIntSafe(pg, 1));
            result.put("list", new JSONArray());
            result.put("pagecount", 1);
            result.put("limit", 36);
            result.put("total", 0);
            return result.toString();
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String vodId = ids.get(0);
            String detailUrl = vodId.startsWith("http") ? vodId : siteUrl + vodId;
            String html = OkHttpUtil.string(detailUrl, getHeader());
            Document doc = Jsoup.parse(html);

            String name = doc.selectFirst("h1") != null ? doc.selectFirst("h1").text().trim() : "";

            String pic = doc.select(".module-info-img img").attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = doc.select(".module-item-pic img").attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = doc.select(".module-item-pic img").attr("data-src");

            String content = doc.select(".module-info-introduction-content").text();
            if (TextUtils.isEmpty(content)) content = doc.select(".vod_content").text();
            if (TextUtils.isEmpty(content)) {
                content = doc.select("meta[name=description]").attr("content");
            }

            List<String> tabNames = new ArrayList<>();
            List<String> tabLineIds = new ArrayList<>();
            Elements tabItems = doc.select(".module-tab-item");
            for (Element tab : tabItems) {
                String nameT = tab.select("span").text().trim();
                if (TextUtils.isEmpty(nameT)) continue;
                tabNames.add(nameT);
                tabLineIds.add(extractLineId(tab.attr("href")));
            }

            List<String> contentLineIds = new ArrayList<>();
            List<String> contentUrls = new ArrayList<>();
            Elements contents = doc.select("div.module-list.sort-list.tab-list");
            if (contents.isEmpty()) contents = doc.select("div.module-play-list-content");
            for (Element contentEl : contents) {
                Elements links = contentEl.select("a.module-play-list-link");
                if (links.isEmpty()) {
                    contentLineIds.add("");
                    contentUrls.add("");
                    continue;
                }
                contentLineIds.add(extractLineId(links.first().attr("href")));

                StringBuilder urls = new StringBuilder();
                for (Element link : links) {
                    String epName = link.select("span").text().trim();
                    if (TextUtils.isEmpty(epName)) epName = link.attr("title");
                    String epUrl = link.attr("href");
                    if (epUrl.startsWith("/")) epUrl = siteUrl + epUrl;
                    if (urls.length() > 0) urls.append("#");
                    urls.append(epName).append("$").append(epUrl);
                }
                contentUrls.add(urls.toString());
            }

            List<String> playFrom = new ArrayList<>();
            List<String> playUrl = new ArrayList<>();
            int n = Math.min(tabNames.size(), contentUrls.size());
            for (int i = 0; i < n; i++) {
                String nameT = tabNames.get(i);
                if (TextUtils.isEmpty(nameT)) {
                    String lineId = tabLineIds.get(i);
                    if (TextUtils.isEmpty(lineId)) lineId = contentLineIds.get(i);
                    nameT = "线路" + lineId;
                }
                playFrom.add(nameT);
                playUrl.add(contentUrls.get(i));
            }
            for (int i = n; i < contentUrls.size(); i++) {
                playFrom.add("线路" + contentLineIds.get(i));
                playUrl.add(contentUrls.get(i));
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
        } catch (Exception e) {
            SpiderDebug.log(e);
            JSONObject result = new JSONObject();
            result.put("list", new JSONArray());
            return result.toString();
        }
    }

    private String extractLineId(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Matcher m = Pattern.compile("/play/(\\d+)-(\\d+)-\\d+\\.html").matcher(url);
        return m.find() ? m.group(2) : "";
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        try {
            String keyWord = URLEncoder.encode(key, "UTF-8");
            String searchUrl = siteUrl + "/search/" + keyWord + "-------------.html";
            String html = OkHttpUtil.string(searchUrl, getHeader());
            JSONArray list = extractList(html);
            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            JSONObject result = new JSONObject();
            result.put("list", new JSONArray());
            return result.toString();
        }
    }

    // ============================================================
    // ★ playerContent
    // 找到 m3u8 → 直连；找不到 m3u8 → 走 v.dushe.online 代理
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
                result.put("header", m3u8Headers());
                return result.toString();
            }

            // 2. 抓播放页（照南瓜）
            String html = OkHttpUtil.string(id, getHeader());
            if (TextUtils.isEmpty(html)) {
                JSONObject result = new JSONObject();
                result.put("parse", 1);
                result.put("url", id);
                result.put("header", headers());
                return result.toString();
            }

            // 3. ★ 优先提取 m3u8（照南瓜）
            String videoUrl = extractM3u8(html);
            if (!TextUtils.isEmpty(videoUrl)) {
                SpiderDebug.log("直连 m3u8=" + videoUrl);
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", videoUrl);
                result.put("header", m3u8Headers());
                return result.toString();
            }

            // 4. ★ 找不到 m3u8 → 走 v.dushe.online 代理
            JSONObject playerData = extractPlayerData(html);
            if (playerData != null) {
                String url = playerData.optString("url", "");
                if (!TextUtils.isEmpty(url)) {
                    String proxyUrl = buildProxyUrl(playerData);
                    SpiderDebug.log("找不到 m3u8，走代理=" + proxyUrl);
                    JSONObject result = new JSONObject();
                    result.put("parse", 1);
                    result.put("url", proxyUrl);
                    result.put("header", headers());
                    return result.toString();
                }
            }

            // 5. 最终兜底
            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("url", id);
            result.put("header", headers());
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

    private String cleanUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        url = url.replace("\\/", "/");
        url = url.replaceAll("^[\"']|[\"']$", "").trim();
        if (url.startsWith("//")) url = "https:" + url;
        return url;
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