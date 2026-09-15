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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DuShe extends Spider {

    private static final String siteUrl = "https://www.dushehub.com";
    private static final String playProxy = "https://v.dushe.online";

    private static final Pattern playerPattern = Pattern.compile(
            "var\\s+player_aaaa\\s*=\\s*(\\{[^;]+\\})");
    private static final Pattern directVideoPattern = Pattern.compile(
            "\\.(m3u8|mp4|flv|mkv|webm|ts)(\\?|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern lineIdPattern = Pattern.compile(
            "/play/(\\d+)-(\\d+)-\\d+\\.html");
    private static final Pattern tailPagePattern = Pattern.compile(
            "(\\d+)---\\.html");

    private static final String UA = "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    // ============================================================
    // 首页：分类 + 筛选
    // ============================================================
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

        if (filter) {
            JSONObject filters = new JSONObject();

            JSONArray areaValues = new JSONArray();
            areaValues.put(filterValue("全部地区", ""));
            areaValues.put(filterValue("中国大陆", "中国大陆"));
            areaValues.put(filterValue("中国香港", "中国香港"));
            areaValues.put(filterValue("中国台湾", "中国台湾"));
            areaValues.put(filterValue("美国", "美国"));
            areaValues.put(filterValue("法国", "法国"));
            areaValues.put(filterValue("英国", "英国"));
            areaValues.put(filterValue("日本", "日本"));
            areaValues.put(filterValue("韩国", "韩国"));
            areaValues.put(filterValue("德国", "德国"));
            areaValues.put(filterValue("泰国", "泰国"));
            areaValues.put(filterValue("印度", "印度"));

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

            JSONArray varietyClass = new JSONArray();
            varietyClass.put(filterValue("全部", ""));

            JSONArray animeClass = new JSONArray();
            animeClass.put(filterValue("全部", ""));

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
            varietyFilters.put(filterGroup("class", "分类", varietyClass));
            varietyFilters.put(filterGroup("area", "地区", areaValues));
            varietyFilters.put(filterGroup("year", "年份", yearValues));
            varietyFilters.put(filterGroup("sort", "排序", sortValues));
            filters.put("zongyi", varietyFilters);

            JSONArray animeFilters = new JSONArray();
            animeFilters.put(filterGroup("class", "分类", animeClass));
            animeFilters.put(filterGroup("area", "地区", areaValues));
            animeFilters.put(filterGroup("year", "年份", yearValues));
            animeFilters.put(filterGroup("sort", "排序", sortValues));
            filters.put("dongman", animeFilters);

            result.put("filters", filters);
        }

        return result.toString();
    }

    // ============================================================
    // 首页推荐
    // ============================================================
    @Override
    public String homeVideoContent() throws Exception {
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        try {
            String html = OkHttp.string(siteUrl + "/");
            Document doc = Jsoup.parse(html);
            Elements items = doc.select("a.module-poster-item");
            int count = 0;
            for (Element item : items) {
                if (count >= 20) break;
                JSONObject vod = parsePosterItem(item);
                if (vod != null) {
                    list.put(vod);
                    count++;
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        result.put("list", list);
        return result.toString();
    }

    // ============================================================
    // 分类
    // ============================================================
    @Override
    public String categoryContent(String tid, String pg, boolean filter,
                                  HashMap<String, String> extend) throws Exception {
        String url = buildCategoryUrl(tid, pg, extend);
        SpiderDebug.log("category url: " + url);

        String html = OkHttp.string(url);
        Document doc = Jsoup.parse(html);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        Elements items = doc.select("a.module-poster-item");
        if (items.isEmpty()) items = doc.select("a[href^=/album/]");

        for (Element item : items) {
            JSONObject vod = parsePosterItem(item);
            if (vod != null) list.put(vod);
        }

        int pageCount = parsePageCount(doc);
        int page = parseIntSafe(pg, 1);

        result.put("page", page);
        result.put("list", list);
        result.put("pagecount", pageCount);
        result.put("limit", Math.max(list.length(), 24));
        result.put("total", pageCount * Math.max(list.length(), 24));
        return result.toString();
    }

    // ============================================================
    // 详情：按位置对齐线路名和选集块
    // ============================================================
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String url = ids.get(0);
        if (!url.startsWith("http")) url = siteUrl + url;
        String html = OkHttp.string(url);
        Document doc = Jsoup.parse(html);

        JSONObject vod = new JSONObject();
        vod.put("vod_id", ids.get(0));

        Element h1 = doc.selectFirst("h1");
        vod.put("vod_name", h1 != null ? h1.text().trim() : "");

        String pic = doc.select(".module-info-img img").attr("data-original");
        if (TextUtils.isEmpty(pic)) pic = doc.select(".module-item-pic img").attr("data-original");
        if (TextUtils.isEmpty(pic)) pic = doc.select(".module-item-pic img").attr("data-src");
        vod.put("vod_pic", fixUrl(pic));

        String content = doc.select(".module-info-introduction-content").text();
        if (TextUtils.isEmpty(content)) content = doc.select(".vod_content").text();
        if (TextUtils.isEmpty(content))
            content = doc.select("meta[name=description]").attr("content");
        vod.put("vod_content", content);

        Elements tags = doc.select(".module-info-tag-link a");
        StringBuilder classSb = new StringBuilder();
        for (Element tag : tags) {
            String t = tag.text().trim();
            if (TextUtils.isEmpty(t)) continue;
            if (t.matches("^\\d{4}$")) {
                vod.put("vod_year", t);
            } else if (isArea(t)) {
                vod.put("vod_area", t);
            } else {
                if (classSb.length() > 0) classSb.append("/");
                classSb.append(t);
            }
        }
        vod.put("vod_class", classSb.toString());

        String director = extractInfoItem(doc, "导演");
        if (!TextUtils.isEmpty(director)) vod.put("vod_director", director);

        String actor = extractInfoItem(doc, "主演");
        if (!TextUtils.isEmpty(actor)) vod.put("vod_actor", actor);

        // ========== 1. 收集 tab：名字 + 可选线路ID ==========
        List<String> tabNames = new ArrayList<>();
        List<String> tabLineIds = new ArrayList<>();
        Elements tabItems = doc.select(".module-tab-item");
        for (Element tab : tabItems) {
            String name = tab.select("span").text().trim();
            if (TextUtils.isEmpty(name)) continue;
            tabNames.add(name);
            tabLineIds.add(extractLineId(tab.attr("href")));
        }
        SpiderDebug.log("tabNames=" + tabNames);
        SpiderDebug.log("tabLineIds=" + tabLineIds);

        // ========== 2. 收集选集块：线路ID + 选集 ==========
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
        SpiderDebug.log("contentLineIds=" + contentLineIds);

        // ========== 3. 按位置对齐 ==========
        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();
        int n = Math.min(tabNames.size(), contentUrls.size());
        for (int i = 0; i < n; i++) {
            String name = tabNames.get(i);
            if (TextUtils.isEmpty(name)) {
                String lineId = tabLineIds.get(i);
                if (TextUtils.isEmpty(lineId)) lineId = contentLineIds.get(i);
                name = "线路" + lineId;
            }
            if (playFrom.length() > 0) playFrom.append("$$$");
            playFrom.append(name);
            if (playUrl.length() > 0) playUrl.append("$$$");
            playUrl.append(contentUrls.get(i));
        }
        for (int i = n; i < contentUrls.size(); i++) {
            if (playFrom.length() > 0) playFrom.append("$$$");
            playFrom.append("线路" + contentLineIds.get(i));
            if (playUrl.length() > 0) playUrl.append("$$$");
            playUrl.append(contentUrls.get(i));
        }

        SpiderDebug.log("playFrom=" + playFrom);
        vod.put("vod_play_from", playFrom.toString());
        vod.put("vod_play_url", playUrl.toString());

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
        String url = siteUrl + "/search/" + encode(key) + "-------------.html";
        SpiderDebug.log("search url: " + url);

        String html = OkHttp.string(url);
        Document doc = Jsoup.parse(html);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        Elements items = doc.select("div.module-card-item.module-item");
        if (items.isEmpty()) items = doc.select("a.module-poster-item");

        for (Element item : items) {
            JSONObject vod = new JSONObject();

            Element a = item.selectFirst("a[href^=/album/]");
            if (a == null) a = item.selectFirst("a[href*=/album/]");
            if (a == null) continue;

            String href = a.attr("href");
            if (href.startsWith("/")) href = siteUrl + href;

            String name = item.select(".module-card-item-title").text().trim();
            if (TextUtils.isEmpty(name)) name = item.select(".module-poster-item-title").text().trim();
            if (TextUtils.isEmpty(name)) name = a.attr("title");

            String pic = item.select("img").attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = item.select("img").attr("data-src");

            vod.put("vod_id", href);
            vod.put("vod_name", name);
            vod.put("vod_pic", fixUrl(pic));
            vod.put("vod_remarks", item.select(".module-item-note").text());
            list.put(vod);
        }

        result.put("list", list);
        return result.toString();
    }

    // ============================================================
    // 播放：完全对齐 JS 版
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();

        // 1. id 本身是直链 → parse=0
        if (directVideoPattern.matcher(id).find()) {
            result.put("parse", 0);
            result.put("url", id);
            result.put("header", m3u8Headers());
            return result.toString();
        }

        // 2. 抓播放页
        String html = OkHttp.string(id);
        if (TextUtils.isEmpty(html)) {
            result.put("parse", 1);
            result.put("url", id);
            result.put("header", headers());
            return result.toString();
        }

        // 3. 提取播放地址
        String playUrl = extractPlayUrl(html);
        SpiderDebug.log("play extracted=" + playUrl);

        if (!TextUtils.isEmpty(playUrl)) {
            // 3a. 代理地址 → parse=1（带完整 HEADERS）
            if (playUrl.contains("v.dushe.online")) {
                result.put("parse", 1);
                result.put("url", playUrl);
                result.put("header", headers());
            }
            // 3b. 直链 → parse=0（带 M3U8_HEADERS）
            else if (directVideoPattern.matcher(playUrl).find()) {
                result.put("parse", 0);
                result.put("url", playUrl);
                result.put("header", m3u8Headers());
            }
            // 3c. 其它 → parse=1
            else {
                result.put("parse", 1);
                result.put("url", playUrl);
                result.put("header", headers());
            }
        } else {
            // 4. 兜底
            result.put("parse", 1);
            result.put("url", id);
            result.put("header", headers());
        }

        return result.toString();
    }

    // ============================================================
    // 提取播放地址：先自己拼，再抠 iframe
    // ============================================================
    private String extractPlayUrl(String html) {
        try {
            // ① 主力：自己拼 player_aaaa（和 JS 版一致）
            Matcher matcher = playerPattern.matcher(html);
            if (matcher.find()) {
                String raw = matcher.group(1);
                raw = raw.replaceAll("([{,])\\s*([a-zA-Z0-9_]+)\\s*:", "$1\"$2\":");
                raw = raw.replaceAll(":\\s*'([^']*)'", ":\"$1\"");
                raw = raw.replace("\\/", "/");
                raw = raw.replaceAll(",\\s*}", "}");

                JSONObject p = new JSONObject(raw);
                String url = p.optString("url", "");
                // ★ next 用 link_next
                String next = p.optString("link_next", "");
                if (!TextUtils.isEmpty(next) && next.startsWith("/")) {
                    next = siteUrl + next;
                }
                String from = p.optString("from", "");
                String title = "";
                JSONObject vd = p.optJSONObject("vod_data");
                if (vd != null) title = vd.optString("vod_name", "");

                SpiderDebug.log("player_aaaa url=" + url + " next=" + next
                        + " from=" + from + " title=" + title);

                if (!TextUtils.isEmpty(url)) {
                    String finalUrl = playProxy + "/?url=" + encode(url)
                            + "&next=" + encode(next)
                            + "&tittle=" + encode(title)
                            + "&t=" + encode(from)
                            + "&d=v2";
                    SpiderDebug.log("proxy final=" + finalUrl);
                    return finalUrl;
                }
            }

            // ② 兜底：抠 iframe
            Document doc = Jsoup.parse(html);
            for (Element iframe : doc.select("iframe")) {
                String src = iframe.attr("src");
                if (TextUtils.isEmpty(src)) src = iframe.attr("data-src");
                if (!TextUtils.isEmpty(src) && src.contains("v.dushe.online")) {
                    SpiderDebug.log("iframe fallback=" + src);
                    return src;
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
    }

    // ============================================================
    // header：完全对齐 JS 版 HEADERS（4 项）
    // ============================================================
    private String headers() {
        JSONObject h = new JSONObject();
        try {
            h.put("User-Agent", UA);
            h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            h.put("Accept-Language", "zh-CN,zh;q=0.9");
            h.put("Referer", siteUrl + "/");
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return h.toString();
    }

    // ============================================================
    // header：完全对齐 JS 版 M3U8_HEADERS（3 项）
    // ============================================================
    private String m3u8Headers() {
        JSONObject h = new JSONObject();
        try {
            h.put("User-Agent", UA);
            h.put("Referer", siteUrl + "/");
            h.put("Accept", "*/*");
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return h.toString();
    }

    // ============================================================
    // 工具方法
    // ============================================================

    private String extractLineId(String url) {
        if (TextUtils.isEmpty(url)) return "";
        Matcher m = lineIdPattern.matcher(url);
        return m.find() ? m.group(2) : "";
    }

    private JSONObject parsePosterItem(Element item) throws Exception {
        String href = item.attr("href");
        if (TextUtils.isEmpty(href)) return null;
        if (href.startsWith("/")) href = siteUrl + href;

        String name = item.attr("title");
        if (TextUtils.isEmpty(name)) name = item.select(".module-poster-item-title").text();
        if (TextUtils.isEmpty(name)) name = item.select("img").attr("alt");

        String pic = item.select("img").attr("data-original");
        if (TextUtils.isEmpty(pic)) pic = item.select("img").attr("data-src");
        if (TextUtils.isEmpty(pic)) pic = item.select("img").attr("src");

        JSONObject vod = new JSONObject();
        vod.put("vod_id", href);
        vod.put("vod_name", name);
        vod.put("vod_pic", fixUrl(pic));
        vod.put("vod_remarks", item.select(".module-item-note").text());
        return vod;
    }

    private int parsePageCount(Document doc) {
        int maxPage = 1;
        try {
            Elements pageLinks = doc.select("a.page-link.page-number");
            for (Element link : pageLinks) {
                int n = parseIntSafe(link.text().trim(), 0);
                if (n > maxPage) maxPage = n;
            }
            for (Element a : doc.select("a")) {
                if ("尾页".equals(a.text().trim())) {
                    Matcher m = tailPagePattern.matcher(a.attr("href"));
                    if (m.find()) {
                        int n = parseIntSafe(m.group(1), 0);
                        if (n > maxPage) maxPage = n;
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return maxPage;
    }

    private String extractInfoItem(Document doc, String label) {
        try {
            for (Element span : doc.select("span.module-info-item-title")) {
                if (span.text().contains(label)) {
                    Element content = span.nextElementSibling();
                    if (content != null) {
                        StringBuilder sb = new StringBuilder();
                        for (Element a : content.select("a")) {
                            if (sb.length() > 0) sb.append("/");
                            sb.append(a.text().trim());
                        }
                        if (sb.length() == 0) sb.append(content.text().trim());
                        return sb.toString();
                    }
                }
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    private boolean isArea(String tag) {
        String[] areas = {"中国大陆", "中国香港", "中国台湾", "美国", "韩国", "日本",
                "英国", "法国", "泰国", "德国", "印度", "意大利", "西班牙", "加拿大", "澳大利亚",
                "内地", "港台", "欧美", "其他"};
        for (String a : areas) if (a.equals(tag)) return true;
        return false;
    }

    private String buildCategoryUrl(String tid, String pg, HashMap<String, String> extend) {
        String area = extend != null ? extend.getOrDefault("area", "") : "";
        String year = extend != null ? extend.getOrDefault("year", "") : "";
        String genre = extend != null ? extend.getOrDefault("class", "") : "";
        String sort = extend != null ? extend.getOrDefault("sort", "time") : "time";

        String[] parts = new String[12];
        Arrays.fill(parts, "");

        parts[0] = tid;
        parts[1] = area;
        parts[2] = sort;
        parts[3] = genre;
        parts[11] = year;

        if (!"1".equals(pg) && !TextUtils.isEmpty(pg)) {
            parts[8] = pg;
        }

        return siteUrl + "/show/" + TextUtils.join("-", parts) + ".html";
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

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        url = url.trim();
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http")) return url;
        if (url.startsWith("/")) return siteUrl + url;
        return siteUrl + "/" + url;
    }

    // 和 JS encodeURIComponent 行为一致
    private String encode(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, "UTF-8")
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            return s;
        }
    }

    private int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }
}