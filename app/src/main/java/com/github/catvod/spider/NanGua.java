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
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NanGua extends Spider {

    private static final String API_HOST = "https://www.bhloushi.com";

    private static final String UA = "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private static final Pattern pageDisplayPattern = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static final Pattern tailPagePattern = Pattern.compile(
            "<a[^>]*href=\"[^\"]*[?&]page=(\\d+)\"[^>]*>尾页</");
    private static final Pattern m3u8Pattern = Pattern.compile(
            "(https?://[^\\s<>\"']+\\.m3u8[^\\s<>\"']*)");
    private static final Pattern playerPattern = Pattern.compile(
            "var\\s+player_aaaa\\s*=\\s*(\\{[^;]+\\})");
    private static final Pattern bgImagePattern = Pattern.compile(
            "background-image:\\s*url\\(([^)]+)\\)");

    // ============================================================
    // header
    // ============================================================
    private String headers() {
        try {
            JSONObject h = new JSONObject();
            h.put("User-Agent", UA);
            h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            h.put("Accept-Language", "zh-CN,zh;q=0.9");
            h.put("Referer", API_HOST + "/");
            return h.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String m3u8Headers() {
        try {
            JSONObject h = new JSONObject();
            h.put("User-Agent", UA);
            h.put("Referer", API_HOST + "/");
            h.put("Accept", "*/*");
            return h.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ============================================================
    // buildVodShowUrl（短横线总数固定 11）
    // ============================================================
    private String buildVodShowUrl(String tid, String area, String sort, String pg, String year) {
        int page = parseIntSafe(pg, 1);
        StringBuilder url = new StringBuilder("/vodshow/" + tid);

        if (!TextUtils.isEmpty(area)) url.append("-").append(area);
        if (!TextUtils.isEmpty(sort)) {
            if (!TextUtils.isEmpty(area)) url.append("-").append(sort);
            else url.append("--").append(sort);
        }

        int usedDash = 0;
        if (!TextUtils.isEmpty(area)) usedDash += 1;
        if (!TextUtils.isEmpty(sort)) {
            if (!TextUtils.isEmpty(area)) usedDash += 1;
            else usedDash += 2;
        }

        int remainingDash = 11 - usedDash;
        if (page == 1) {
            for (int i = 0; i < remainingDash; i++) url.append("-");
        } else {
            int afterDash = 3;
            int beforeDash = remainingDash - afterDash;
            if (beforeDash < 1) beforeDash = 1;
            for (int i = 0; i < beforeDash; i++) url.append("-");
            url.append(page);
            for (int i = 0; i < afterDash; i++) url.append("-");
        }

        if (!TextUtils.isEmpty(year)) url.append(year);
        url.append(".html");
        return url.toString();
    }

    // ============================================================
    // 提取视频列表（★ 修复图片）
    // ============================================================
    private JSONArray extractList(String html) throws Exception {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;

        Document doc = Jsoup.parse(html);
        Elements items = doc.select("div.movie-list-item");
        for (Element item : items) {
            Element a = item.selectFirst("a[href]");
            if (a == null) continue;

            String vodId = a.attr("href");

            // 标题：优先 .movie-title 文本，去掉「影片信息」
            String title = item.select("div.movie-title").text().trim();
            if (TextUtils.isEmpty(title)) {
                Element titleEl = item.selectFirst(".movie-title");
                if (titleEl != null) title = titleEl.attr("title");
            }
            title = title.replace("影片信息", "").trim();
            if (TextUtils.isEmpty(title)) continue;

            // ★★★ 图片：四级兜底 ★★★
            String pic = "";

            // 1. div.movie-post-lazyload 的 data-original（最优先）
            Element picDiv = item.selectFirst("div.movie-post-lazyload");
            if (picDiv != null) {
                pic = picDiv.attr("data-original").trim();
            }

            // 2. 任何带 data-original 的元素
            if (TextUtils.isEmpty(pic)) {
                Element any = item.selectFirst("[data-original]");
                if (any != null) pic = any.attr("data-original").trim();
            }

            // 3. 任何带 data-src 的元素
            if (TextUtils.isEmpty(pic)) {
                Element any = item.selectFirst("[data-src]");
                if (any != null) pic = any.attr("data-src").trim();
            }

            // 4. background-image（处理 &quot; 和占位图）
            if (TextUtils.isEmpty(pic)) {
                Matcher m = bgImagePattern.matcher(item.outerHtml());
                if (m.find()) {
                    pic = m.group(1).trim();
                    pic = pic.replace("&quot;", "").replace("\"", "").replace("'", "").trim();
                }
            }

            // 5. img src
            if (TextUtils.isEmpty(pic)) {
                Element img = item.selectFirst("img[src]");
                if (img != null) pic = img.attr("src").trim();
            }

            // 过滤占位图
            if (pic.contains("img-bj-k.png")) pic = "";

            // 清理 &quot; 等实体
            pic = pic.replace("&quot;", "").replace("&amp;", "&").trim();

            JSONObject vod = new JSONObject();
            vod.put("vod_id", vodId);
            vod.put("vod_name", title);
            vod.put("vod_pic", fixUrl(pic));
            list.put(vod);
        }
        return list;
    }

    // ============================================================
    // 分页
    // ============================================================
    private int extractPageCount(String html) {
        if (TextUtils.isEmpty(html)) return 1;

        Matcher m1 = pageDisplayPattern.matcher(html);
        if (m1.find()) {
            return parseIntSafe(m1.group(2), 1);
        }

        Matcher m2 = tailPagePattern.matcher(html);
        if (m2.find()) {
            return parseIntSafe(m2.group(1), 1);
        }
        return 1;
    }

    // ============================================================
    // 详情
    // ============================================================
    private JSONObject extractDetail(String html, String vodId) throws Exception {
        JSONObject info = new JSONObject();
        info.put("vod_id", vodId);

        Matcher titleM = Pattern.compile("<title>《([^》]+)》").matcher(html);
        info.put("vod_name", titleM.find() ? titleM.group(1).trim() : "");

        info.put("vod_class", metaContent(html, "og:video:class"));
        info.put("vod_area", metaContent(html, "og:video:area"));
        info.put("vod_lang", metaContent(html, "og:video:language"));
        info.put("vod_director", metaContent(html, "og:video:director"));
        info.put("vod_actor", metaContent(html, "og:video:actor"));
        info.put("vod_pic", fixUrl(metaContent(html, "og:image")));
        info.put("vod_content", metaContent(html, "og:description"));

        Matcher yearM = Pattern.compile("上映时间[：:]\\s*(\\d{4})").matcher(html);
        info.put("vod_year", yearM.find() ? yearM.group(1) : "");

        // 播放列表
        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();

        List<String> fromNames = new ArrayList<>();
        Matcher nameM = Pattern.compile("<a[^>]*class=\"[^\"]*titleName[^\"]*\"[^>]*>([^<]+)</a>").matcher(html);
        while (nameM.find()) fromNames.add(nameM.group(1).trim());

        List<String> playBlocks = extractDivBlocks(html, "playsx");
        for (int i = 0; i < playBlocks.size(); i++) {
            Document blockDoc = Jsoup.parse(playBlocks.get(i));
            Elements eps = blockDoc.select("a[href]");
            if (eps.isEmpty()) continue;

            StringBuilder urls = new StringBuilder();
            for (Element ep : eps) {
                String epUrl = ep.attr("href");
                String epName = ep.text().trim();
                if (TextUtils.isEmpty(epUrl)) continue;
                if (urls.length() > 0) urls.append("#");
                urls.append(epName).append("$").append(fixUrl(epUrl));
            }
            if (urls.length() == 0) continue;

            String fromName = (i < fromNames.size() && !TextUtils.isEmpty(fromNames.get(i)))
                    ? fromNames.get(i) : "线路" + (i + 1);
            playFrom.add(fromName);
            playUrl.add(urls.toString());
        }

        info.put("vod_play_from", TextUtils.join("$$$", playFrom));
        info.put("vod_play_url", TextUtils.join("$$$", playUrl));
        return info;
    }

    private String metaContent(String html, String property) {
        Matcher m = Pattern.compile(
                "<meta[^>]*property=\"" + property + "\"[^>]*content=\"([^\"]+)\"").matcher(html);
        return m.find() ? m.group(1).trim() : "";
    }

    // 按大括号配对提取 div 块
    private List<String> extractDivBlocks(String html, String id) {
        List<String> result = new ArrayList<>();
        Matcher m = Pattern.compile("<div id=\"" + id + "\"[^>]*>").matcher(html);
        while (m.find()) {
            int start = m.start();
            int i = m.end();
            int depth = 1;
            while (i < html.length() && depth > 0) {
                if (html.startsWith("<div", i)) {
                    depth++;
                    i += 4;
                } else if (html.startsWith("</div>", i)) {
                    depth--;
                    i += 6;
                } else {
                    i++;
                }
            }
            if (depth == 0) result.add(html.substring(start, i));
        }
        return result;
    }

    // ============================================================
    // m3u8 提取
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
    // TVBox 接口
    // ============================================================
    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();

        JSONArray classes = new JSONArray();
        String[] typeIds = {"1", "2", "3", "4", "26"};
        String[] typeNames = {"电影", "电视剧", "综艺", "动漫", "短剧"};
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
            String[][] areas = {{"大陆", "大陆"}, {"香港", "香港"}, {"台湾", "台湾"},
                    {"美国", "美国"}, {"法国", "法国"}, {"英国", "英国"}, {"日本", "日本"},
                    {"韩国", "韩国"}, {"德国", "德国"}, {"泰国", "泰国"}, {"印度", "印度"},
                    {"意大利", "意大利"}, {"西班牙", "西班牙"}, {"加拿大", "加拿大"}, {"其他", "其他"}};
            for (String[] a : areas) areaValues.put(filterValue(a[1], a[0]));

            JSONArray yearValues = new JSONArray();
            yearValues.put(filterValue("全部年份", ""));
            for (int y = 2026; y >= 2010; y--) {
                yearValues.put(filterValue(String.valueOf(y), String.valueOf(y)));
            }

            JSONArray sortValues = new JSONArray();
            sortValues.put(filterValue("默认排序", ""));
            sortValues.put(filterValue("按时间", "time"));
            sortValues.put(filterValue("按人气", "hits"));
            sortValues.put(filterValue("按评分", "score"));

            JSONArray movieClass = new JSONArray();
            movieClass.put(filterValue("电影", "1"));
            movieClass.put(filterValue("动作片", "6"));
            movieClass.put(filterValue("喜剧片", "7"));
            movieClass.put(filterValue("爱情片", "8"));
            movieClass.put(filterValue("科幻片", "9"));
            movieClass.put(filterValue("恐怖片", "10"));
            movieClass.put(filterValue("剧情片", "11"));
            movieClass.put(filterValue("战争片", "12"));
            movieClass.put(filterValue("纪录片", "24"));

            JSONArray tvClass = new JSONArray();
            tvClass.put(filterValue("电视剧", "2"));
            tvClass.put(filterValue("美剧", "20"));
            tvClass.put(filterValue("韩剧", "13"));
            tvClass.put(filterValue("日剧", "14"));
            tvClass.put(filterValue("泰剧", "15"));
            tvClass.put(filterValue("港剧", "16"));
            tvClass.put(filterValue("国产剧", "25"));

            JSONArray varietyClass = new JSONArray();
            varietyClass.put(filterValue("综艺", "3"));

            JSONArray animeClass = new JSONArray();
            animeClass.put(filterValue("动漫", "4"));

            JSONArray shortClass = new JSONArray();
            shortClass.put(filterValue("短剧", "26"));

            JSONArray movieFilters = new JSONArray();
            movieFilters.put(filterGroup("class", "分类", movieClass));
            movieFilters.put(filterGroup("area", "地区", areaValues));
            movieFilters.put(filterGroup("year", "年份", yearValues));
            movieFilters.put(filterGroup("sort", "排序", sortValues));
            filters.put("1", movieFilters);

            JSONArray tvFilters = new JSONArray();
            tvFilters.put(filterGroup("class", "分类", tvClass));
            tvFilters.put(filterGroup("area", "地区", areaValues));
            tvFilters.put(filterGroup("year", "年份", yearValues));
            tvFilters.put(filterGroup("sort", "排序", sortValues));
            filters.put("2", tvFilters);

            JSONArray varietyFilters = new JSONArray();
            varietyFilters.put(filterGroup("class", "分类", varietyClass));
            varietyFilters.put(filterGroup("area", "地区", areaValues));
            varietyFilters.put(filterGroup("year", "年份", yearValues));
            varietyFilters.put(filterGroup("sort", "排序", sortValues));
            filters.put("3", varietyFilters);

            JSONArray animeFilters = new JSONArray();
            animeFilters.put(filterGroup("class", "分类", animeClass));
            animeFilters.put(filterGroup("area", "地区", areaValues));
            animeFilters.put(filterGroup("year", "年份", yearValues));
            animeFilters.put(filterGroup("sort", "排序", sortValues));
            filters.put("4", animeFilters);

            JSONArray shortFilters = new JSONArray();
            shortFilters.put(filterGroup("class", "分类", shortClass));
            shortFilters.put(filterGroup("area", "地区", areaValues));
            shortFilters.put(filterGroup("year", "年份", yearValues));
            shortFilters.put(filterGroup("sort", "排序", sortValues));
            filters.put("26", shortFilters);

            result.put("filters", filters);
        }

        return result.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        JSONObject result = new JSONObject();
        try {
            String html = OkHttp.string(API_HOST + "/");
            JSONArray list = extractList(html);
            JSONArray out = new JSONArray();
            for (int i = 0; i < Math.min(list.length(), 12); i++) out.put(list.get(i));
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
            String cls = extend != null && extend.get("class") != null ? extend.get("class") : "";
            String area = extend != null && extend.get("area") != null ? extend.get("area") : "";
            String year = extend != null && extend.get("year") != null ? extend.get("year") : "";
            String sort = extend != null && extend.get("sort") != null ? extend.get("sort") : "";

            String typeId = TextUtils.isEmpty(cls) ? tid : cls;
            String url = API_HOST + buildVodShowUrl(typeId, area, sort, pg, year);
            SpiderDebug.log("category url: " + url);

            String html = OkHttp.string(url);
            JSONArray list = extractList(html);
            int pagecount = extractPageCount(html);

            JSONObject result = new JSONObject();
            result.put("page", parseIntSafe(pg, 1));
            result.put("list", list);
            result.put("pagecount", Math.max(pagecount, 1));
            result.put("limit", 12);
            result.put("total", Math.max(pagecount, 1) * 12);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            JSONObject result = new JSONObject();
            result.put("page", parseIntSafe(pg, 1));
            result.put("list", new JSONArray());
            result.put("pagecount", 1);
            result.put("limit", 12);
            result.put("total", 0);
            return result.toString();
        }
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String id = ids.get(0);
            String url = id.startsWith("http") ? id : API_HOST + id;
            String html = OkHttp.string(url);
            if (TextUtils.isEmpty(html)) {
                JSONObject result = new JSONObject();
                result.put("list", new JSONArray());
                return result.toString();
            }

            JSONObject info = extractDetail(html, id);

            JSONObject result = new JSONObject();
            JSONArray list = new JSONArray();
            list.put(info);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            JSONObject result = new JSONObject();
            result.put("list", new JSONArray());
            return result.toString();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        try {
            String encoded = URLEncoder.encode(key, "UTF-8");
            String url = API_HOST + "/vodsearch/" + encoded + "-------------.html";
            String html = OkHttp.string(url);
            JSONArray list = extractList(html);

            JSONObject result = new JSONObject();
            result.put("list", list);
            result.put("page", 1);
            result.put("pagecount", 1);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            JSONObject result = new JSONObject();
            result.put("list", new JSONArray());
            return result.toString();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            if (id != null && Pattern.compile("\\.(m3u8|mp4|flv|mkv|webm|ts)",
                    Pattern.CASE_INSENSITIVE).matcher(id).find()) {
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", id);
                result.put("header", m3u8Headers());
                return result.toString();
            }

            String html = OkHttp.string(id);
            if (TextUtils.isEmpty(html)) {
                JSONObject result = new JSONObject();
                result.put("parse", 1);
                result.put("url", id);
                result.put("header", headers());
                return result.toString();
            }

            String videoUrl = extractM3u8(html);
            JSONObject result = new JSONObject();
            if (!TextUtils.isEmpty(videoUrl)) {
                result.put("parse", 0);
                result.put("url", videoUrl);
                result.put("header", m3u8Headers());
            } else {
                result.put("parse", 1);
                result.put("url", id);
                result.put("header", headers());
            }
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
        if (url.startsWith("/")) return API_HOST + url;
        return API_HOST + "/" + url;
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