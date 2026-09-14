package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.okhttp.OkHttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 毒舌电影 - www.dushehub.com
 * 播放走 v.dushe.online 代理
 */
public class DuShe extends Spider {

    private static final String API_HOST = "https://www.dushehub.com";
    private static final String PROXY_HOST = "https://v.dushe.online";

    private static final String UA = "Mozilla/5.0 (Linux; Android 14; SM-G998B) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    // ================== 基础工具 ==================

    private Map<String, String> getHeader() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        h.put("Accept-Language", "zh-CN,zh;q=0.9");
        h.put("Referer", API_HOST + "/");
        return h;
    }

    private Map<String, String> getM3u8Header() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Referer", API_HOST + "/");
        h.put("Accept", "*/*");
        return h;
    }

    private String headerToJson(Map<String, String> map) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<String, String> e : map.entrySet()) o.put(e.getKey(), e.getValue());
        } catch (Exception ignored) {}
        return o.toString();
    }

    private String fetchHtml(String url) {
        try {
            return OkHttpUtil.string(url, getHeader());
        } catch (Exception e) {
            return "";
        }
    }

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
        url = url.replace("\\/", "/").replaceAll("^[\"']|[\"']$", "").trim();
        if (url.startsWith("//")) url = "https:" + url;
        return url;
    }

    private String find(Pattern p, String html) {
        if (html == null) return "";
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : "";
    }

    // ================== 分类 URL ==================

    private String buildVodShowUrl(String tid, String pg, Map<String, String> filter) {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
        if (page < 1) page = 1;

        String url = "/show/" + tid;

        if (filter != null) {
            String area = filter.get("area") == null ? "" : filter.get("area");
            String sort = filter.get("sort") == null ? "" : filter.get("sort");
            String year = filter.get("year") == null ? "" : filter.get("year");

            StringBuilder dash = new StringBuilder();
            if (!TextUtils.isEmpty(area)) {
                try {
                    dash.append("-").append(URLEncoder.encode(area, "UTF-8"));
                } catch (Exception e) {
                    dash.append("-");
                }
            } else {
                dash.append("-");
            }
            dash.append("-");
            if (!TextUtils.isEmpty(sort)) dash.append(sort);
            for (int i = 0; i < 8; i++) dash.append("-");
            if (!TextUtils.isEmpty(year)) dash.append(year);

            String pagePart = page > 1 ? "---" + page : "";
            url += dash + pagePart + ".html";
        } else {
            if (page == 1) url += "-----------.html";
            else url += "--------" + page + "---.html";
        }
        return url;
    }

    // ================== 列表解析 ==================

    private JSONArray extractList(String html) throws Exception {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;

        Document doc = Jsoup.parse(html);
        Elements items = doc.select("a.module-poster-item");
        if (items.isEmpty()) items = doc.select("a[href^=/album/]");

        for (Element a : items) {
            String href = a.attr("href");
            if (TextUtils.isEmpty(href) || !href.startsWith("/album/")) continue;

            String name = a.attr("title").trim();
            if (TextUtils.isEmpty(name)) {
                Element t = a.selectFirst(".module-poster-item-title");
                if (t != null) name = t.text().trim();
            }
            if (TextUtils.isEmpty(name)) continue;

            String pic = "";
            Element img = a.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                if (pic.endsWith("/load.gif")) pic = "";
            }

            String remark = "";
            Element note = a.selectFirst(".module-item-note");
            if (note != null) remark = note.text().trim();

            JSONObject vod = new JSONObject();
            vod.put("vod_id", href);
            vod.put("vod_name", name);
            vod.put("vod_pic", fixUrl(pic));
            vod.put("vod_remarks", remark);
            list.put(vod);
        }
        return list;
    }

    private JSONArray extractSearchList(String html) throws Exception {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;

        Document doc = Jsoup.parse(html);
        Elements items = doc.select("div.module-card-item");

        if (items.isEmpty()) {
            // 兜底
            Elements fallback = doc.select("a[href^=/album/]");
            for (Element a : fallback) {
                String href = a.attr("href");
                if (TextUtils.isEmpty(href) || !href.startsWith("/album/")) continue;

                String name = a.attr("title").trim();
                if (TextUtils.isEmpty(name)) {
                    Element t = a.selectFirst(".module-card-item-title");
                    if (t != null) name = t.text().trim();
                }
                if (TextUtils.isEmpty(name)) continue;

                String pic = "";
                Element img = a.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                    if (pic.endsWith("/load.gif")) pic = "";
                }

                JSONObject vod = new JSONObject();
                vod.put("vod_id", href);
                vod.put("vod_name", name);
                vod.put("vod_pic", fixUrl(pic));
                list.put(vod);
            }
            return list;
        }

        for (Element item : items) {
            Element a = item.selectFirst("a[href^=/album/]");
            if (a == null) continue;
            String href = a.attr("href");
            if (TextUtils.isEmpty(href)) continue;

            String name = "";
            Element s = item.selectFirst(".module-card-item-title strong");
            if (s != null) name = s.text().trim();
            if (TextUtils.isEmpty(name)) {
                Element t = item.selectFirst(".module-card-item-title");
                if (t != null) name = t.text().trim();
            }
            if (TextUtils.isEmpty(name)) name = a.attr("title").trim();
            if (TextUtils.isEmpty(name)) continue;

            String category = "";
            Element c = item.selectFirst(".module-card-item-class");
            if (c != null) category = c.text().trim();

            String pic = "";
            Element img = item.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
                if (pic.endsWith("/load.gif")) pic = "";
            }

            JSONObject vod = new JSONObject();
            vod.put("vod_id", href);
            vod.put("vod_name", name);
            vod.put("vod_pic", fixUrl(pic));
            vod.put("vod_class", category);
            list.put(vod);
        }
        return list;
    }

    private int extractPageCount(String html) {
        if (TextUtils.isEmpty(html)) return 1;
        int maxPage = 1;

        Pattern p = Pattern.compile(
                "<a[^>]*class=\"[^\"]*page-link[^\"]*page-number[^\"]*\"[^>]*>(\\d+)</a>");
        Matcher m = p.matcher(html);
        while (m.find()) {
            try {
                int n = Integer.parseInt(m.group(1));
                if (n > maxPage) maxPage = n;
            } catch (Exception ignored) {}
        }

        Pattern lastP = Pattern.compile("<a[^>]*href=\"([^\"]+)\"[^>]*>尾页</a>");
        Matcher lastM = lastP.matcher(html);
        if (lastM.find()) {
            Matcher numM = Pattern.compile("(\\d+)---\\.html").matcher(lastM.group(1));
            if (numM.find()) {
                try {
                    int n = Integer.parseInt(numM.group(1));
                    if (n > maxPage) maxPage = n;
                } catch (Exception ignored) {}
            }
        }
        return maxPage > 1 ? maxPage : 1;
    }

    // ================== 详情解析 ==================

    private JSONObject extractDetail(String html) throws Exception {
        JSONObject info = new JSONObject();
        info.put("vod_id", "");
        info.put("vod_name", "");
        info.put("vod_pic", "");
        info.put("vod_class", "");
        info.put("vod_year", "");
        info.put("vod_area", "");
        info.put("vod_lang", "");
        info.put("vod_actor", "");
        info.put("vod_director", "");
        info.put("vod_content", "");
        info.put("vod_play_from", "");
        info.put("vod_play_url", "");

        Document doc = Jsoup.parse(html);

        Element h1 = doc.selectFirst("h1");
        if (h1 != null) info.put("vod_name", h1.text().trim());

        Element pic = doc.selectFirst("div.module-item-pic img");
        if (pic != null) {
            String p = pic.attr("data-original");
            if (TextUtils.isEmpty(p)) p = pic.attr("src");
            info.put("vod_pic", fixUrl(p));
        }

        // 标签：年份 / 地区 / 分类
        String year = "";
        String area = "";
        StringBuilder cls = new StringBuilder();
        Elements tags = doc.select("div.module-info-tag-link a");
        for (Element t : tags) {
            String tag = t.text().trim();
            if (tag.matches("^\\d{4}$")) year = tag;
            else if (isArea(tag)) area = tag;
            else {
                if (cls.length() > 0) cls.append("/");
                cls.append(tag);
            }
        }
        info.put("vod_year", year);
        info.put("vod_area", area);
        info.put("vod_class", cls.toString());

        Element desc = doc.selectFirst("div.module-info-introduction-content p");
        if (desc != null) info.put("vod_content", desc.text().trim());

        // 导演 / 主演
        Elements blocks = doc.select("div.module-info-item");
        for (Element b : blocks) {
            Element title = b.selectFirst("span.module-info-item-title");
            if (title == null) continue;
            String tname = title.text().trim();
            Element content = b.selectFirst("div.module-info-item-content");
            if (content == null) continue;

            List<String> names = new ArrayList<>();
            for (Element link : content.select("a")) names.add(link.text().trim());
            if (names.isEmpty()) names.add(content.text().trim());

            if (tname.contains("导演")) info.put("vod_director", TextUtils.join("/", names));
            else if (tname.contains("主演")) info.put("vod_actor", TextUtils.join("/", names));
        }

        // 播放线路
        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();

        List<String> fromNames = new ArrayList<>();
        for (Element l : doc.select("label.module-tab-name span")) fromNames.add(l.text().trim());
        if (fromNames.isEmpty()) {
            for (Element l : doc.select("div.module-tab-item span")) fromNames.add(l.text().trim());
        }

        Elements panels = doc.select("div.module-list.sort-list.tab-list");
        if (panels.isEmpty()) panels = doc.select("div.module-play-list");

        int idx = 0;
        for (Element panel : panels) {
            List<String> eps = new ArrayList<>();
            for (Element a : panel.select("a.module-play-list-link")) {
                String href = a.attr("href");
                String epName = a.text().trim();
                if (TextUtils.isEmpty(href) || TextUtils.isEmpty(epName)) continue;
                eps.add(epName + "$" + fixUrl(href));
            }
            if (!eps.isEmpty()) {
                String fromName = (idx < fromNames.size() && !TextUtils.isEmpty(fromNames.get(idx)))
                        ? fromNames.get(idx) : ("线路" + (idx + 1));
                playFrom.add(fromName);
                playUrl.add(TextUtils.join("#", eps));
                idx++;
            }
        }

        info.put("vod_play_from", TextUtils.join("$$$", playFrom));
        info.put("vod_play_url", TextUtils.join("$$$", playUrl));
        return info;
    }

    private boolean isArea(String tag) {
        String[] areas = {"中国大陆", "中国香港", "中国台湾", "美国", "韩国", "日本",
                "英国", "法国", "泰国", "德国", "印度"};
        for (String a : areas) if (a.equals(tag)) return true;
        return false;
    }

    // ================== 播放解析 ==================

    private String extractPlayUrl(String html) {
        if (TextUtils.isEmpty(html)) return null;

        Matcher pm = Pattern.compile("var\\s+player_aaaa\\s*=\\s*(\\{[^;]+})").matcher(html);
        if (pm.find()) {
            try {
                String objStr = pm.group(1);
                objStr = objStr.replaceAll("([{,])\\s*([a-zA-Z0-9_]+)\\s*:", "$1\"$2\":");
                objStr = objStr.replaceAll(":\\s*'([^']*)'", ":\"$1\"");
                objStr = objStr.replace("\\/", "/");
                objStr = objStr.replaceAll(",\\s*}", "}");

                JSONObject data = new JSONObject(objStr);
                String videoUrl = data.optString("url", "");
                if (!TextUtils.isEmpty(videoUrl)) {
                    String nextUrl = data.optString("link_next", "");
                    String from = data.optString("from", "");
                    String title = "";
                    JSONObject vodData = data.optJSONObject("vod_data");
                    if (vodData != null) title = vodData.optString("vod_name", "");

                    return PROXY_HOST + "/?url=" + URLEncoder.encode(videoUrl, "UTF-8")
                            + "&next=" + URLEncoder.encode(fixUrl(nextUrl), "UTF-8")
                            + "&tittle=" + URLEncoder.encode(title, "UTF-8")
                            + "&t=" + URLEncoder.encode(from, "UTF-8")
                            + "&d=v2";
                }
            } catch (Exception ignored) {}
        }

        Matcher ifM = Pattern.compile("<iframe[^>]*src=\"([^\"]+)\"[^>]*>").matcher(html);
        if (ifM.find()) {
            String src = ifM.group(1);
            if (src.contains("v.dushe.online")) return fixUrl(src);
            Matcher urlM = Pattern.compile("[?&]url=([^&]+)").matcher(src);
            if (urlM.find()) {
                try {
                    String decoded = URLDecoder.decode(urlM.group(1), "UTF-8");
                    return PROXY_HOST + "/?url=" + URLEncoder.encode(decoded, "UTF-8") + "&d=v2";
                } catch (Exception ignored) {}
            }
            return fixUrl(src);
        }

        Matcher m3u8M = Pattern.compile("(https?://[^\\s<>\"']+\\.m3u8[^\\s<>\"']*)").matcher(html);
        if (m3u8M.find()) return cleanUrl(m3u8M.group(1));

        return null;
    }

    // ================== TVBox 接口 ==================

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONArray classes = new JSONArray();
        String[][] cls = {
                {"dianying", "电影"},
                {"dianshiju", "电视剧"},
                {"zongyi", "综艺"},
                {"dongman", "动漫"}
        };
        for (String[] c : cls) {
            JSONObject o = new JSONObject();
            o.put("type_id", c[0]);
            o.put("type_name", c[1]);
            classes.put(o);
        }

        JSONObject filters = new JSONObject();
        filters.put("dianying", buildFilter("movie"));
        filters.put("dianshiju", buildFilter("tv"));
        filters.put("zongyi", buildFilter("zongyi"));
        filters.put("dongman", buildFilter("dongman"));

        JSONObject result = new JSONObject();
        result.put("class", classes);
        result.put("filters", filters);
        return result.toString();
    }

    private JSONArray buildFilter(String type) throws Exception {
        JSONArray arr = new JSONArray();

        JSONArray classValues = new JSONArray();
        if ("movie".equals(type)) {
            classValues.put(item("电影", "dianying"));
            classValues.put(item("动作片", "dongzuo"));
            classValues.put(item("喜剧片", "xiju"));
            classValues.put(item("爱情片", "aiqing"));
            classValues.put(item("科幻片", "kehuan"));
            classValues.put(item("恐怖片", "kongbu"));
            classValues.put(item("剧情片", "juqing"));
            classValues.put(item("战争片", "zhanzheng"));
            classValues.put(item("纪录片", "jilupian"));
        } else if ("tv".equals(type)) {
            classValues.put(item("电视剧", "dianshiju"));
            classValues.put(item("美剧", "meiju"));
            classValues.put(item("韩剧", "hanju"));
            classValues.put(item("日剧", "riju"));
            classValues.put(item("泰剧", "taiju"));
            classValues.put(item("港剧", "gangju"));
            classValues.put(item("国产剧", "guochan"));
        } else if ("zongyi".equals(type)) {
            classValues.put(item("综艺", "zongyi"));
        } else {
            classValues.put(item("动漫", "dongman"));
        }
        JSONObject classObj = new JSONObject();
        classObj.put("key", "class");
        classObj.put("name", "分类");
        classObj.put("value", classValues);
        arr.put(classObj);

        JSONArray areaValues = new JSONArray();
        areaValues.put(item("全部地区", ""));
        String[] areas = {"中国大陆", "中国香港", "中国台湾", "美国", "法国", "英国",
                "日本", "韩国", "德国", "泰国", "印度"};
        for (String a : areas) areaValues.put(item(a, a));
        JSONObject areaObj = new JSONObject();
        areaObj.put("key", "area");
        areaObj.put("name", "地区");
        areaObj.put("value", areaValues);
        arr.put(areaObj);

        JSONArray yearValues = new JSONArray();
        yearValues.put(item("全部年份", ""));
        for (int y = 2026; y >= 2015; y--) yearValues.put(item(String.valueOf(y), String.valueOf(y)));
        JSONObject yearObj = new JSONObject();
        yearObj.put("key", "year");
        yearObj.put("name", "年份");
        yearObj.put("value", yearValues);
        arr.put(yearObj);

        JSONArray sortValues = new JSONArray();
        sortValues.put(item("默认排序", ""));
        sortValues.put(item("按时间", "time"));
        sortValues.put(item("按人气", "hits"));
        sortValues.put(item("按评分", "score"));
        JSONObject sortObj = new JSONObject();
        sortObj.put("key", "sort");
        sortObj.put("name", "排序");
        sortObj.put("value", sortValues);
        arr.put(sortObj);

        return arr;
    }

    private JSONObject item(String n, String v) throws Exception {
        JSONObject o = new JSONObject();
        o.put("n", n);
        o.put("v", v);
        return o;
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = fetchHtml(API_HOST + "/");
        JSONArray list = extractList(html);
        JSONArray limited = new JSONArray();
        for (int i = 0; i < list.length() && i < 12; i++) limited.put(list.get(i));
        JSONObject result = new JSONObject();
        result.put("list", limited);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend)
            throws Exception {
        try {
            int page = 1;
            try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
            if (page < 1) page = 1;

            Map<String, String> params = new HashMap<>();
            params.put("area", extend != null && extend.get("area") != null ? extend.get("area") : "");
            params.put("year", extend != null && extend.get("year") != null ? extend.get("year") : "");
            params.put("sort", extend != null && extend.get("sort") != null ? extend.get("sort") : "");

            String url = API_HOST + buildVodShowUrl(tid, String.valueOf(page), params);
            String html = fetchHtml(url);
            JSONArray list = extractList(html);
            int pagecount = extractPageCount(html);

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("list", list);
            result.put("pagecount", pagecount > 0 ? pagecount : 1);
            result.put("limit", 12);
            result.put("total", (pagecount > 0 ? pagecount : 1) * 12);
            return result.toString();
        } catch (Exception e) {
            JSONObject result = new JSONObject();
            result.put("page", 1);
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
            String html = fetchHtml(url);
            if (TextUtils.isEmpty(html)) {
                JSONObject r = new JSONObject();
                r.put("list", new JSONArray());
                return r.toString();
            }
            JSONObject info = extractDetail(html);
            info.put("vod_id", id);
            JSONArray arr = new JSONArray();
            arr.put(info);
            JSONObject result = new JSONObject();
            result.put("list", arr);
            return result.toString();
        } catch (Exception e) {
            JSONObject r = new JSONObject();
            r.put("list", new JSONArray());
            return r.toString();
        }
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        try {
            String encoded = URLEncoder.encode(key, "UTF-8");
            String url = API_HOST + "/search/" + encoded + "-------------.html";
            String html = fetchHtml(url);
            JSONArray list = extractSearchList(html);
            JSONObject result = new JSONObject();
            result.put("list", list);
            result.put("page", 1);
            result.put("pagecount", 1);
            return result.toString();
        } catch (Exception e) {
            JSONObject r = new JSONObject();
            r.put("list", new JSONArray());
            return r.toString();
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();

        if (id != null && id.matches("(?i).*\\.(m3u8|mp4|flv|mkv|webm|ts).*")) {
            result.put("parse", 0);
            result.put("url", id);
            result.put("header", headerToJson(getM3u8Header()));
            return result.toString();
        }

        String html = fetchHtml(id);
        if (TextUtils.isEmpty(html)) {
            result.put("parse", 1);
            result.put("url", id);
            result.put("header", headerToJson(getHeader()));
            return result.toString();
        }

        String playUrl = extractPlayUrl(html);
        if (!TextUtils.isEmpty(playUrl)) {
            if (playUrl.contains("v.dushe.online")) {
                Map<String, String> h = getHeader();
                h.put("Referer", API_HOST + "/");
                result.put("parse", 1);
                result.put("url", playUrl);
                result.put("header", headerToJson(h));
                return result.toString();
            }
            if (playUrl.matches("(?i).*\\.(m3u8|mp4|flv|mkv|webm|ts).*")) {
                result.put("parse", 0);
                result.put("url", playUrl);
                result.put("header", headerToJson(getM3u8Header()));
                return result.toString();
            }
            result.put("parse", 1);
            result.put("url", playUrl);
            result.put("header", headerToJson(getHeader()));
            return result.toString();
        }

        result.put("parse", 1);
        result.put("url", id);
        result.put("header", headerToJson(getHeader()));
        return result.toString();
    }
}