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

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DuShe extends Spider {

    private static final String siteUrl = "https://www.dushehub.com";
    private static final String playProxy = "https://v.dushe.online";

    private static final Pattern playerPattern = Pattern.compile(
            "var\\s+player_aaaa\\s*=\\s*(\\{[^;]+\\})");
    private static final Pattern directVideoPattern = Pattern.compile(
            "\\.(m3u8|mp4|flv|mkv|webm|ts)(\\?|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern m3u8Pattern = Pattern.compile(
            "(https?://[^\\s\"'<>]+\\.m3u8[^\\s\"'<>]*)");
    private static final Pattern lineIdPattern = Pattern.compile(
            "/play/(\\d+)-(\\d+)-\\d+\\.html");
    private static final Pattern tailPagePattern = Pattern.compile(
            "(\\d+)---\\.html");

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
    // 详情（方案 B：线路 ID 映射）
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

        // ========== 方案 B：线路 ID 映射 ==========
        // 1. 抓全部 tab，建 线路ID -> 线路名 映射
        Map<String, String> lineIdToName = new LinkedHashMap<>();
        Elements tabItems = doc.select(
                "div.module-tab-items-box div.module-tab-item, " +
                "div.module-tab-items-box a.module-tab-item");
        if (tabItems.isEmpty()) {
            tabItems = doc.select("div.module-tab-item, a.module-tab-item");
        }
        for (Element tab : tabItems) {
            String name = tab.select("span").text().trim();
            String lineId = extractLineId(tab.attr("href"));
            if (TextUtils.isEmpty(lineId)) lineId = extractLineId(url);
            if (!TextUtils.isEmpty(lineId) && !TextUtils.isEmpty(name)) {
                if (!lineIdToName.containsKey(lineId)) {
                    lineIdToName.put(lineId, name);
                }
            }
        }
        SpiderDebug.log("lineIdToName=" + lineIdToName);

        // 2. 抓选集块，按块内链接的线路 ID 匹配名字
        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();

        Elements contents = doc.select("div.module-list.sort-list.tab-list");
        if (contents.isEmpty()) contents = doc.select("div.module-play-list-content");

        for (Element contentEl : contents) {
            Elements links = contentEl.select("a.module-play-list-link");
            if (links.isEmpty()) continue;

            String lineId = extractLineId(links.first().attr("href"));
            String lineName = lineIdToName.containsKey(lineId)
                    ? lineIdToName.get(lineId) : "线路" + lineId;

            StringBuilder urls = new StringBuilder();
            for (Element link : links) {
                String epName = link.select("span").text().trim();
                if (TextUtils.isEmpty(epName)) epName = link.attr("title");
                String epUrl = link.attr("href");
                if (epUrl.startsWith("/")) epUrl = siteUrl + epUrl;
                if (urls.length() > 0) urls.append("#");
                urls.append(epName).append("$").append(epUrl);
            }

            if (playFrom.length() > 0) playFrom.append("$$$");
            playFrom.append(lineName);
            if (playUrl.length() > 0) playUrl.append("$$$");
            playUrl.append(urls);
        }

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
    // 播放
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();

        if (directVideoPattern.matcher(id).find()) {
            result.put("parse", 0);
            result.put("url", id);
            return result.toString();
        }

        String html = OkHttp.string(id);
        if (TextUtils.isEmpty(html)) {
            result.put("parse", 1);
            result.put("url", id);
            return result.toString();
        }

        String playUrl = extractPlayUrl(html);

        if (!TextUtils.isEmpty(playUrl) && playUrl.contains("v.dushe.online")) {
            result.put("parse", 1);
            result.put("url", playUrl);
            JSONObject headers = new JSONObject();
            headers.put("Referer", siteUrl + "/");
            headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36");
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

    // ============================================================
    // ★★★ 核心：播放地址提取（next 用 link_next）
    // ============================================================
    private String extractPlayUrl(String html) {
        try {
            Matcher matcher = playerPattern.matcher(html);
            if (matcher.find()) {
                String raw = matcher.group(1);
                // 手动修成合法 JSON
                raw = raw.replaceAll("([{,])\\s*([a-zA-Z0-9_]+)\\s*:", "$1\"$2\":");
                raw = raw.replaceAll(":\\s*'([^']*)'", ":\"$1\"");
                raw = raw.replace("\\/", "/");
                raw = raw.replaceAll(",\\s*}", "}");

                JSONObject p = new JSONObject(raw);
                String url = p.optString("url", "");

                // ★★★ 关键：next 用 link_next，并补全为绝对地址
                String next = p.optString("link_next", "");
                if (!TextUtils.isEmpty(next) && next.startsWith("/")) {
                    next = siteUrl + next;
                }

                String from = p.optString("from", "");
                String title = "";
                JSONObject vd = p.optJSONObject("vod_data");
                if (vd != null) title = vd.optString("vod_name", "");

                SpiderDebug.log("player url=" + url + " next=" + next
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

            // 兜底 1：iframe
            Document doc = Jsoup.parse(html);
            String iframeSrc = doc.select(".MacPlayer iframe").attr("src");
            if (TextUtils.isEmpty(iframeSrc)) iframeSrc = doc.select("iframe").attr("src");
            if (!TextUtils.isEmpty(iframeSrc)) {
                if (iframeSrc.contains("v.dushe.online")) return fixUrl(iframeSrc);
                Matcher um = Pattern.compile("[?&]url=([^&]+)").matcher(iframeSrc);
                if (um.find()) {
                    String decoded = URLDecoder.decode(um.group(1), "UTF-8");
                    return playProxy + "/?url=" + encode(decoded) + "&d=v2";
                }
                return fixUrl(iframeSrc);
            }

            // 兜底 2：页面直接嵌 m3u8
            Matcher m3u8Matcher = m3u8Pattern.matcher(html);
            if (m3u8Matcher.find()) return m3u8Matcher.group(1);
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return null;
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

    // 和 JS encodeURIComponent 行为一致的编码
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