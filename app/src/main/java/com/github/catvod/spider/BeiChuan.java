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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 北川影视 - j.qulv8.com
 * 结构参考 Dm84
 */
public class BeiChuan extends Spider {

    private final String siteUrl = "https://j.qulv8.com";

    private final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // ============================================================
    // 工具
    // ============================================================
    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", userAgent);
        header.put("Referer", siteUrl + "/");
        return header;
    }

    private String req(String url) {
        return OkHttpUtil.string(url, getHeader());
    }

    private String find(String regexStr, String htmlStr) {
        Pattern pattern = Pattern.compile(regexStr, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(htmlStr);
        if (matcher.find()) return matcher.group(1).trim();
        return "";
    }

    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
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

    // ============================================================
    // 列表解析（复用）
    // ============================================================
    private JSONArray parseVodList(String url) throws Exception {
        String html = req(url);
        return parseVodListFromHtml(html);
    }

    private JSONArray parseVodListFromHtml(String html) throws Exception {
        JSONArray videos = new JSONArray();
        if (TextUtils.isEmpty(html)) return videos;

        Document doc = Jsoup.parse(html);
        Elements items = doc.select("li.stui-vodlist__item");
        for (Element item : items) {
            Element a = item.selectFirst("a[href]");
            if (a == null) continue;
            String href = a.attr("href");
            if (TextUtils.isEmpty(href)) continue;
            if (href.startsWith("/")) href = siteUrl + href;

            String name = item.select("h4 a").text().trim();
            if (TextUtils.isEmpty(name)) name = a.attr("title");

            String pic = a.attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = item.select("img").attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = item.select("img").attr("src");
            if (!TextUtils.isEmpty(pic) && pic.contains("/load")) pic = "";

            String remark = item.select("span.pic-text").text().trim();

            JSONObject vod = new JSONObject();
            vod.put("vod_id", href);
            vod.put("vod_name", name);
            vod.put("vod_pic", TextUtils.isEmpty(pic) ? "" : fixUrl(pic));
            vod.put("vod_remarks", remark);
            videos.put(vod);
        }
        return videos;
    }

    // ============================================================
    // 首页
    // ============================================================
    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONArray classes = new JSONArray();
        String[][] classesConfig = {
                {"5922692431", "电影"},
                {"5033975762", "连续剧"},
                {"5046095573", "综艺"},
                {"5057378904", "动漫"},
                {"5218091400", "短剧"}
        };
        for (String[] c : classesConfig) {
            JSONObject obj = new JSONObject();
            obj.put("type_id", c[0]);
            obj.put("type_name", c[1]);
            classes.put(obj);
        }

        JSONObject result = new JSONObject();
        result.put("class", classes);

        if (filter) {
            result.put("filters", buildFilters());
        }

        return result.toString();
    }

    private JSONObject buildFilters() throws Exception {
        JSONObject filters = new JSONObject();

        JSONArray areaValues = new JSONArray();
        areaValues.put(filterValue("全部", ""));
        String[] areas = {"大陆", "香港", "台湾", "日本", "韩国", "欧美", "泰国", "其他"};
        for (String a : areas) areaValues.put(filterValue(a, a));

        JSONArray yearValues = new JSONArray();
        yearValues.put(filterValue("全部", ""));
        for (int y = 2026; y >= 2010; y--) {
            yearValues.put(filterValue(String.valueOf(y), String.valueOf(y)));
        }

        JSONArray byValues = new JSONArray();
        byValues.put(filterValue("默认", ""));
        byValues.put(filterValue("时间", "time"));
        byValues.put(filterValue("人气", "hit"));
        byValues.put(filterValue("推荐", "commend"));

        JSONArray langValues = new JSONArray();
        langValues.put(filterValue("全部", ""));
        langValues.put(filterValue("国语", "国语"));
        langValues.put(filterValue("粤语", "粤语"));
        langValues.put(filterValue("英语", "英语"));
        langValues.put(filterValue("日语", "日语"));
        langValues.put(filterValue("韩语", "韩语"));
        langValues.put(filterValue("泰语", "泰语"));
        langValues.put(filterValue("法语", "法语"));

        // 电影
        JSONArray movieClass = buildClassValues(new String[][]{
                {"", "全部"}, {"5068662235", "动作片"}, {"5079945566", "爱情片"},
                {"5082228897", "科幻片"}, {"5093512228", "恐怖片"}, {"5005632039", "战争片"},
                {"5016915360", "喜剧片"}, {"5028198691", "纪录片"}, {"5139482022", "剧情片"},
                {"5269838275", "悬疑片"}, {"6437164322", "动画片"}, {"5295524748", "其他电影"}
        });

        // 连续剧
        JSONArray tvClass = buildClassValues(new String[][]{
                {"", "全部"}, {"5141765353", "国产剧"}, {"5153048684", "港台剧"},
                {"5165168495", "欧美剧"}, {"5176451826", "韩剧"}, {"5229374731", "日剧"},
                {"5272958086", "泰剧"}, {"5206808079", "番剧"}, {"5284241417", "其他"}
        });

        // 综艺
        JSONArray varietyClass = buildClassValues(new String[][]{
                {"", "全部"}, {"6332494542", "国产综艺"}, {"6343777873", "港台综艺"},
                {"6355061204", "日韩综艺"}, {"6366344535", "欧美综艺"}, {"6377627866", "其他综艺"}
        });

        // 动漫
        JSONArray animeClass = buildClassValues(new String[][]{
                {"", "全部"}, {"6389747677", "国产动漫"}, {"6392031008", "日韩动漫"},
                {"6303314339", "欧美动漫"}, {"6314597660", "港台动漫"}, {"6325880991", "其他动漫"}
        });

        // 短剧
        JSONArray shortClass = buildClassValues(new String[][]{
                {"", "全部"}
        });

        // 组装
        JSONArray movieFilters = new JSONArray();
        movieFilters.put(filterGroup("class", "分类", movieClass));
        movieFilters.put(filterGroup("area", "地区", areaValues));
        movieFilters.put(filterGroup("year", "年份", yearValues));
        movieFilters.put(filterGroup("by", "排序", byValues));
        movieFilters.put(filterGroup("lang", "语言", langValues));
        filters.put("5922692431", movieFilters);

        JSONArray tvFilters = new JSONArray();
        tvFilters.put(filterGroup("class", "分类", tvClass));
        tvFilters.put(filterGroup("area", "地区", areaValues));
        tvFilters.put(filterGroup("year", "年份", yearValues));
        tvFilters.put(filterGroup("by", "排序", byValues));
        tvFilters.put(filterGroup("lang", "语言", langValues));
        filters.put("5033975762", tvFilters);

        JSONArray varietyFilters = new JSONArray();
        varietyFilters.put(filterGroup("class", "分类", varietyClass));
        varietyFilters.put(filterGroup("area", "地区", areaValues));
        varietyFilters.put(filterGroup("year", "年份", yearValues));
        varietyFilters.put(filterGroup("by", "排序", byValues));
        filters.put("5046095573", varietyFilters);

        JSONArray animeFilters = new JSONArray();
        animeFilters.put(filterGroup("class", "分类", animeClass));
        animeFilters.put(filterGroup("area", "地区", areaValues));
        animeFilters.put(filterGroup("year", "年份", yearValues));
        animeFilters.put(filterGroup("by", "排序", byValues));
        filters.put("5057378904", animeFilters);

        JSONArray shortFilters = new JSONArray();
        shortFilters.put(filterGroup("class", "分类", shortClass));
        shortFilters.put(filterGroup("area", "地区", areaValues));
        shortFilters.put(filterGroup("year", "年份", yearValues));
        shortFilters.put(filterGroup("by", "排序", byValues));
        filters.put("5218091400", shortFilters);

        return filters;
    }

    private JSONArray buildClassValues(String[][] pairs) throws Exception {
        JSONArray arr = new JSONArray();
        for (String[] p : pairs) {
            arr.put(filterValue(p[1], p[0]));
        }
        return arr;
    }

    // ============================================================
    // 首页推荐
    // ============================================================
    @Override
    public String homeVideoContent() throws Exception {
        JSONObject result = new JSONObject();
        try {
            JSONArray list = parseVodList(siteUrl + "/");
            JSONArray out = new JSONArray();
            for (int i = 0; i < Math.min(list.length(), 20); i++) out.put(list.get(i));
            result.put("list", out);
        } catch (Exception e) {
            SpiderDebug.log(e);
            result.put("list", new JSONArray());
        }
        return result.toString();
    }

    // ============================================================
    // 分类
    // ============================================================
    @Override
    public String categoryContent(String tid, String pg, boolean filter,
                                  HashMap<String, String> extend) throws Exception {
        try {
            int page = parseIntSafe(pg, 1);
            String cls = extend.get("class") == null ? "" : extend.get("class");
            String area = extend.get("area") == null ? "" : extend.get("area");
            String year = extend.get("year") == null ? "" : extend.get("year");
            String by = extend.get("by") == null ? "" : extend.get("by");
            String lang = extend.get("lang") == null ? "" : extend.get("lang");

            if ("全部".equals(cls)) cls = "";
            if ("全部".equals(area)) area = "";
            if ("全部".equals(year)) year = "";
            if ("全部".equals(by)) by = "";
            if ("全部".equals(lang)) lang = "";

            // 按 QPython 验证的规则拼接
            String url;
            if (!TextUtils.isEmpty(cls)) {
                // 子分类：/filter_sort/vod_list_{cls}[-{page}].html
                url = siteUrl + "/filter_sort/vod_list_" + cls;
                if (page > 1) url += "-" + page;
                url += ".html";
            } else {
                StringBuilder sb = new StringBuilder(siteUrl);
                sb.append("/library");
                if (!TextUtils.isEmpty(lang)) {
                    sb.append("-yuyan-").append(URLEncoder.encode(lang, "UTF-8"));
                }
                if (!TextUtils.isEmpty(year)) {
                    sb.append("-year-").append(year);
                }
                sb.append("-tid-").append(tid);
                sb.append("-searchtype-5");
                if (page > 1) {
                    sb.append("-page-").append(page);
                }
                if (!TextUtils.isEmpty(by)) {
                    sb.append("-order-").append(by);
                }
                if (!TextUtils.isEmpty(area)) {
                    sb.append("-area-").append(URLEncoder.encode(area, "UTF-8"));
                }
                sb.append(".html");
                url = sb.toString();
            }

            SpiderDebug.log("category url: " + url);

            JSONArray videos = parseVodList(url);

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("pagecount", page + 1);
            result.put("limit", 24);
            result.put("total", Integer.MAX_VALUE);
            result.put("list", videos);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            JSONObject result = new JSONObject();
            result.put("page", parseIntSafe(pg, 1));
            result.put("list", new JSONArray());
            result.put("pagecount", 1);
            result.put("limit", 24);
            result.put("total", 0);
            return result.toString();
        }
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

        String name = doc.selectFirst("h3.title") != null
                ? doc.selectFirst("h3.title").text().trim() : "";

        String pic = doc.select("img.img-responsive").attr("data-original");
        if (TextUtils.isEmpty(pic)) pic = doc.select("img[data-original]").attr("data-original");

        String classType = "";
        String area = "";
        String year = "";

        Elements tags = doc.select(".text-muted.hidden-xs");
        for (Element el : tags) {
            String text = el.text();
            if (text.contains("类型：")) {
                classType = el.nextElementSibling() != null ? el.nextElementSibling().text().trim() : "";
            } else if (text.contains("地区：")) {
                Element next = el.nextElementSibling();
                if (next != null) {
                    area = next.text().trim();
                    if (area.startsWith(" ")) area = area.trim();
                }
            } else if (text.contains("年份：")) {
                Element next = el.nextElementSibling();
                if (next != null) year = next.text().trim();
            }
        }

        String actor = find("主演：</span>([\\s\\S]*?)</p>", html).replaceAll("<[^>]+>", "").trim();
        String director = find("导演：</span>([\\s\\S]*?)</p>", html).replaceAll("<[^>]+>", "").trim();

        String content = doc.select(".stui-content__desc").text();

        // 播放列表
        String[] playResult = extractPlaylist(html);

        JSONObject vod = new JSONObject();
        vod.put("vod_id", vodId);
        vod.put("vod_name", name);
        vod.put("vod_pic", fixUrl(pic));
        vod.put("vod_year", year);
        vod.put("vod_area", area);
        vod.put("type_name", classType);
        vod.put("vod_actor", actor);
        vod.put("vod_director", director);
        vod.put("vod_content", content);
        vod.put("vod_play_from", playResult[0]);
        vod.put("vod_play_url", playResult[1]);

        JSONArray list = new JSONArray();
        list.put(vod);
        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    // 播放列表（按位置对齐）
    private String[] extractPlaylist(String html) {
        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();

        List<String> tabNames = new ArrayList<>();
        Matcher tabM = Pattern.compile("<label class=\"module-tab-name\">\\s*<span[^>]*>([^<]+)</span>").matcher(html);
        while (tabM.find()) tabNames.add(tabM.group(1).trim());

        if (tabNames.isEmpty()) {
            Document doc = Jsoup.parse(html);
            Elements tabs = doc.select("label.module-tab-name span");
            if (tabs.isEmpty()) tabs = doc.select("div.module-tab-item span");
            for (Element tab : tabs) tabNames.add(tab.text().trim());
        }

        Document doc = Jsoup.parse(html);
        Elements contents = doc.select("div.module-list.sort-list.tab-list");
        if (contents.isEmpty()) contents = doc.select("div.module-play-list-content");

        for (int i = 0; i < contents.size(); i++) {
            Elements links = contents.get(i).select("a.module-play-list-link");
            if (links.isEmpty()) continue;

            String tabName = i < tabNames.size() ? tabNames.get(i) : "线路" + (i + 1);
            if (TextUtils.isEmpty(tabName)) tabName = "线路" + (i + 1);

            StringBuilder urls = new StringBuilder();
            for (Element link : links) {
                String epName = link.select("span").text().trim();
                if (TextUtils.isEmpty(epName)) epName = link.attr("title");
                String epUrl = link.attr("href");
                if (epUrl.startsWith("/")) epUrl = siteUrl + epUrl;
                if (urls.length() > 0) urls.append("#");
                urls.append(epName).append("$").append(epUrl);
            }

            playFrom.add(tabName);
            playUrl.add(urls.toString());
        }

        return new String[]{TextUtils.join("$$$", playFrom), TextUtils.join("$$$", playUrl)};
    }

    // ============================================================
    // 搜索
    // ============================================================
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String encoded = URLEncoder.encode(key, "UTF-8");
        String searchUrl = siteUrl + "/search.php?searchword=" + encoded;
        JSONArray videos = parseVodList(searchUrl);
        JSONObject result = new JSONObject();
        result.put("list", videos);
        result.put("page", 1);
        result.put("pagecount", 1);
        return result.toString();
    }

    // ============================================================
    // 播放：抓 m3u8 → parse:0
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (id != null && Pattern.compile("\\.(m3u8|mp4|flv|mkv|webm|ts)",
                Pattern.CASE_INSENSITIVE).matcher(id).find()) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", id);
            result.put("header", headerJson());
            return result.toString();
        }

        String html = req(id);
        if (TextUtils.isEmpty(html)) {
            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("url", id);
            result.put("header", headerJson());
            return result.toString();
        }

        // ① player_aaaa
        Matcher pm = Pattern.compile("var\\s+player_aaaa\\s*=\\s*(\\{[^;]+\\})").matcher(html);
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
                    SpiderDebug.log("player_aaaa url=" + url);
                    JSONObject result = new JSONObject();
                    result.put("parse", 0);
                    result.put("url", cleanUrl(url));
                    result.put("header", headerJson());
                    return result.toString();
                }
            } catch (Exception e) {
                SpiderDebug.log("player_aaaa parse error");
            }
        }

        // ② var now
        Matcher nowM = Pattern.compile("var\\s+now\\s*=\\s*\"([^\"]+)\"").matcher(html);
        if (nowM.find() && nowM.group(1).contains(".m3u8")) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", cleanUrl(nowM.group(1)));
            result.put("header", headerJson());
            return result.toString();
        }

        // ③ 正则
        Matcher mm = Pattern.compile("(https?://[^\\s<>\"']+\\.m3u8[^\\s<>\"']*)").matcher(html);
        if (mm.find()) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", cleanUrl(mm.group(1)));
            result.put("header", headerJson());
            return result.toString();
        }

        JSONObject result = new JSONObject();
        result.put("parse", 1);
        result.put("url", id);
        result.put("header", headerJson());
        return result.toString();
    }

    private String headerJson() {
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
    // filter 工具
    // ============================================================
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