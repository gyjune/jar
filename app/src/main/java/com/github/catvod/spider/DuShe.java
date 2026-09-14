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
 *
 * 站点结构分析（基于2026-09 HTML逆向）:
 * - 分类列表: /show/{type}-{area}-{sort}-{genre}-{lang}-{letter}-{p1}-{p2}-{page}-{p3}-{p4}-{year}.html
 * - 详情页: /album/{id}.html
 * - 播放页: /play/{id}-{sid}-{nid}.html
 * - 列表项: a.module-poster-item.module-item > .module-poster-item-title / .module-item-note / img[data-original]
 * - 选集: div.module-list.sort-list.tab-list > div.module-play-list > div.module-play-list-content > a.module-play-list-link
 * - 播放源标签: label.module-tab-name span 或 div.module-tab-item span
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
        url = url.replace("\\/", "/");
        // 去除首尾引号
        while (url.length() > 0 && (url.charAt(0) == '"' || url.charAt(0) == '\'')) {
            url = url.substring(1);
        }
        while (url.length() > 0 && (url.charAt(url.length() - 1) == '"' || url.charAt(url.length() - 1) == '\'')) {
            url = url.substring(0, url.length() - 1);
        }
        url = url.trim();
        if (url.startsWith("//")) url = "https:" + url;
        return url;
    }

    // ================== URL构造 ==================

    /**
     * 构建分类列表页URL
     * 实际站点URL模板（12段）:
     * /show/{type}-{area}-{sort}-{genre}-{lang}-{letter}-{p1}-{p2}-{page}-{p3}-{p4}-{year}.html
     *
     * 段含义:
     * 1. type: 类型标识 (dianying/dianshiju/guochan/gangju/hanju等)
     * 2. area: 地区 (URL编码或空)
     * 3. sort: 排序 (time/hits/score)
     * 4. genre: 剧情 (URL编码或空)
     * 5. lang: 语言 (URL编码或空)
     * 6. letter: 字母 (A-Z或空)
     * 7-10. p1-p4: 空段(预留)
     * 11. year: 年份(数字或空)
     */
    private String buildCategoryUrl(String tid, String pg, Map<String, String> params) {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
        if (page < 1) page = 1;

        // 类型标识：优先使用extend中的class筛选值，否则用tid
        String type = tid;
        if (params != null) {
            String cls = params.get("class");
            if (cls != null && !cls.isEmpty()) {
                type = cls;
            }
        }

        String area = params != null ? params.getOrDefault("area", "") : "";
        String sort = params != null ? params.getOrDefault("sort", "time") : "time";
        String genre = params != null ? params.getOrDefault("genre", "") : "";
        String language = params != null ? params.getOrDefault("language", "") : "";
        String letter = params != null ? params.getOrDefault("letter", "") : "";
        String year = params != null ? params.getOrDefault("year", "") : "";

        // 如果没有任何筛选参数，使用基础类型URL格式: {type}-----------.html
        boolean allFiltersEmpty = area.isEmpty() && genre.isEmpty()
                && language.isEmpty() && letter.isEmpty() && year.isEmpty();
        if (allFiltersEmpty) {
            return API_HOST + "/show/" + type + "-----------.html";
        }

        // 实际站点URL模板（12段，11个短横线）:
        // /show/{type}-{area}-{sort}-{genre}-{lang}-{letter}-{p1}-{p2}-{page}-{p3}-{p4}-{year}.html
        // 段含义:
        // 1. type: 类型标识 (guochan/dianying/dianshiju等)
        // 2. area: 地区 (URL编码)
        // 3. sort: 排序 (time/hits/score)
        // 4. genre: 剧情 (URL编码)
        // 5. lang: 语言 (URL编码)
        // 6. letter: 字母 (A-Z或空)
        // 7-8. p1-p2: 空段(预留)
        // 9. page: 页码(第1页时空字符串)
        // 10-11. p3-p4: 空段(预留)
        // 12. year: 年份
        List<String> parts = new ArrayList<>();
        parts.add(type);                          // 1. type
        parts.add(area);                          // 2. area
        parts.add(sort);                          // 3. sort (time/hits/score)
        parts.add(genre);                         // 4. genre
        parts.add(language);                      // 5. language
        parts.add(letter);                        // 6. letter
        parts.add("");                            // 7. p1 (预留)
        parts.add("");                            // 8. p2 (预留)
        parts.add(page > 1 ? String.valueOf(page) : ""); // 9. page
        parts.add("");                            // 10. p3 (预留)
        parts.add("");                            // 11. p4 (预留)
        parts.add(year);                          // 12. year

        // 分页：页码放在第9段（index 8）
        // (已由上面parts.add(page > 1 ? String.valueOf(page) : "")处理)

        // URL编码area/genre/language（如果非空）
        if (!area.isEmpty()) {
            try { parts.set(1, URLEncoder.encode(area, "UTF-8")); } catch (Exception ignored) {}
        }
        if (!genre.isEmpty()) {
            try { parts.set(3, URLEncoder.encode(genre, "UTF-8")); } catch (Exception ignored) {}
        }
        if (!language.isEmpty()) {
            try { parts.set(4, URLEncoder.encode(language, "UTF-8")); } catch (Exception ignored) {}
        }

        String urlPath = TextUtils.join("-", parts);
        return API_HOST + "/show/" + urlPath + ".html";
    }
// ================== 列表解析 ==================

    /**
     * 解析分类列表页
     * 实际HTML结构:
     * div.module-items.module-poster-items-base > a.module-poster-item.module-item
     *   > div.module-item-cover > div.module-item-note (更新状态)
     *   > div.module-item-pic > img (封面, data-original属性)
     *   > div.module-poster-item-info > div.module-poster-item-title (标题)
     * href = /album/{id}.html
     */
    private JSONArray extractList(String html) throws Exception {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;

        Document doc = Jsoup.parse(html);

        // 主选择器：a.module-poster-item.module-item（实际HTML中是两个class）
        Elements items = doc.select("a.module-poster-item.module-item");
        if (items.isEmpty()) {
            // 兜底：选择所有带module-poster-item类的a标签
            items = doc.select("a.module-poster-item");
        }
        if (items.isEmpty()) {
            // 兜底2：选择/album/链接
            items = doc.select("a[href^=/album/]");
        }

        for (Element a : items) {
            String href = a.attr("href");
            if (href.isEmpty()) continue;

            // 只处理/album/开头的详情页链接
            if (!href.startsWith("/album/")) continue;

            // 标题：优先取title属性，否则取.module-poster-item-title
            String name = a.attr("title").trim();
            if (name.isEmpty()) {
                Element t = a.selectFirst("div.module-poster-item-title");
                if (t != null) name = t.text().trim();
            }
            if (name.isEmpty()) continue;

            // 封面图：img的data-original属性
            String pic = "";
            Element img = a.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (pic.isEmpty()) pic = img.attr("src");
                if (pic.endsWith("/load.gif")) pic = "";
            }

            // 更新状态
            String remark = "";
            Element note = a.selectFirst("div.module-item-note");
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

    /**
     * 解析搜索结果列表
     * 搜索结果使用div.module-card-item结构
     */
    private JSONArray extractSearchList(String html) throws Exception {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;

        Document doc = Jsoup.parse(html);
        Elements items = doc.select("div.module-card-item");

        if (items.isEmpty()) {
            // 兜底：使用列表页选择器
            items = doc.select("a.module-poster-item.module-item");
            if (items.isEmpty()) items = doc.select("a[href^=/album/]");

            for (Element a : items) {
                String href = a.attr("href");
                if (href.isEmpty() || !href.startsWith("/album/")) continue;

                String name = a.attr("title").trim();
                if (name.isEmpty()) {
                    Element t = a.selectFirst("div.module-poster-item-title");
                    if (t != null) name = t.text().trim();
                }
                if (name.isEmpty()) continue;

                String pic = "";
                Element img = a.selectFirst("img");
                if (img != null) {
                    pic = img.attr("data-original");
                    if (pic.isEmpty()) pic = img.attr("src");
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
            if (href.isEmpty()) continue;

            String name = "";
            Element s = item.selectFirst("div.module-card-item-title strong");
            if (s != null) name = s.text().trim();
            if (name.isEmpty()) {
                Element t = item.selectFirst("div.module-card-item-title");
                if (t != null) name = t.text().trim();
            }
            if (name.isEmpty()) name = a.attr("title").trim();
            if (name.isEmpty()) continue;

            String category = "";
            Element c = item.selectFirst("div.module-card-item-class");
            if (c != null) category = c.text().trim();

            String pic = "";
            Element img = item.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (pic.isEmpty()) pic = img.attr("src");
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

    /**
     * 解析页码
     * 查找尾页链接中的页码数字
     */
    private int extractPageCount(String html) {
        if (TextUtils.isEmpty(html)) return 1;
        int maxPage = 1;

        // 尝试匹配尾页链接
        Pattern lastP = Pattern.compile("尾页[^>]*href=\"([^\"]+)\"");
        Matcher lastM = lastP.matcher(html);
        if (lastM.find()) {
            String href = lastM.group(1);
            // 从href中提取页码数字
            Matcher numM = Pattern.compile("(\\d+)\\.html").matcher(href);
            if (numM.find()) {
                try { maxPage = Integer.parseInt(numM.group(1)); } catch (Exception ignored) {}
            }
        }

        // 兜底：尝试匹配分页链接中的数字
        if (maxPage <= 1) {
            Pattern pageP = Pattern.compile("page-link[^>]*>(\\d+)<");
            Matcher pageM = pageP.matcher(html);
            while (pageM.find()) {
                try {
                    int n = Integer.parseInt(pageM.group(1));
                    if (n > maxPage) maxPage = n;
                } catch (Exception ignored) {}
            }
        }

        // 兜底2：匹配/show/页面中的分页数字
        if (maxPage <= 1) {
            Pattern showP = Pattern.compile("/show/[^\"'\\s]+(\\d+)\\.html");
            Matcher showM = showP.matcher(html);
            while (showM.find()) {
                try {
                    int n = Integer.parseInt(showM.group(1));
                    if (n > maxPage) maxPage = n;
                } catch (Exception ignored) {}
            }
        }

        return maxPage > 1 ? maxPage : 1;
    }

    // ================== 详情解析 ==================

    /**
     * 解析详情页
     * 实际HTML结构:
     * h1 > a > 剧名
     * div.module-item-pic > img (封面, data-original)
     * div.module-info-tag-link > a (年份/地区/分类标签)
     * div.module-info-item > span.module-info-item-title + div.module-info-item-content (导演/主演)
     * div.module-info-introduction-content > p (简介)
     * label.module-tab-name span (播放源名称)
     * div.module-list.sort-list.tab-list > div.module-play-list > div.module-play-list-content > a.module-play-list-link (选集)
     */
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

        // 剧名：h1 > a
        Element h1 = doc.selectFirst("h1 a");
        if (h1 == null) h1 = doc.selectFirst("h1");
        if (h1 != null) info.put("vod_name", h1.text().trim());

        // 封面图
        Element pic = doc.selectFirst("div.module-item-pic img");
        if (pic != null) {
            String p = pic.attr("data-original");
            if (p.isEmpty()) p = pic.attr("src");
            info.put("vod_pic", fixUrl(p));
        }

        // 标签：年份/地区/分类
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

        // 简介
        Element desc = doc.selectFirst("div.module-info-introduction-content p");
        if (desc != null) info.put("vod_content", desc.text().trim());

        // 导演/主演
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

        // 播放源名称：label.module-tab-name span
        List<String> fromNames = new ArrayList<>();
        for (Element l : doc.select("label.module-tab-name span")) fromNames.add(l.text().trim());
        if (fromNames.isEmpty()) {
            for (Element l : doc.select("div.module-tab-item span")) fromNames.add(l.text().trim());
        }

        // 选集列表：div.module-list.sort-list.tab-list > div.module-play-list > div.module-play-list-content > a.module-play-list-link
        Elements panels = doc.select("div.module-list.sort-list.tab-list");

        int idx = 0;
        for (Element panel : panels) {
            List<String> eps = new ArrayList<>();
            // 选择器：a.module-play-list-link
            for (Element a : panel.select("a.module-play-list-link")) {
                String href = a.attr("href");
                String epName = a.text().trim();
                if (href.isEmpty() || epName.isEmpty()) continue;
                eps.add(epName + "$" + fixUrl(href));
            }
            if (!eps.isEmpty()) {
                String fromName = (idx < fromNames.size() && !fromNames.get(idx).isEmpty())
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
                "英国", "法国", "泰国", "德国", "印度", "加拿大", "西班牙", "俄罗斯", "新加坡", "马来西亚"};
        for (String a : areas) if (a.equals(tag)) return true;
        return false;
    }

    // ================== 播放解析 ==================

    /**
     * 解析播放页获取视频URL
     * 播放页包含:
     * 1. var player_aaaa = {...} JavaScript变量（含url/link等）
     * 2. iframe[src] 嵌入播放器（src指向v.dushe.online代理）
     * 3. m3u8直链
     */
    private String extractPlayUrl(String html) {
        if (html.isEmpty()) return null;

        // 1. 解析player_aaaa变量
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
                if (!videoUrl.isEmpty()) {
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

        // 2. 解析iframe
        Matcher ifM = Pattern.compile("<iframe[^>]*src=\"([^\"]+)\"").matcher(html);
        if (ifM.find()) {
            String src = ifM.group(1);
            if (src.contains("v.dushe.online")) return fixUrl(src);
            Matcher urlM = Pattern.compile("[?&]url=([^&]+)").matcher(src);
            if (urlM.find()) {
                try {
                    String decoded = java.net.URLDecoder.decode(urlM.group(1), "UTF-8");
                    return PROXY_HOST + "/?url=" + URLEncoder.encode(decoded, "UTF-8") + "&d=v2";
                } catch (Exception ignored) {}
            }
            return fixUrl(src);
        }

        // 3. 解析m3u8直链
        Matcher m3u8M = Pattern.compile("(https?://[^\\s<>\"']+\\.m3u8[^\\s<>\"']*)").matcher(html);
        if (m3u8M.find()) return cleanUrl(m3u8M.group(1));

        return null;
    }

    // ================== TVBox接口 ==================

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

        // 分类筛选
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
            classValues.put(item("台剧", "zilei10"));
            classValues.put(item("海外", "haiwaiju"));
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

        // 剧情筛选
        JSONArray genreValues = new JSONArray();
        genreValues.put(item("全部剧情", ""));
        String[] genres = {"古装", "战争", "青春偶像", "喜剧", "家庭", "犯罪", "动作", "奇幻", "剧情", "历史", "经典", "乡村", "情景", "商战", "网剧", "其他"};
        for (String g : genres) genreValues.put(item(g, g));
        JSONObject genreObj = new JSONObject();
        genreObj.put("key", "genre");
        genreObj.put("name", "剧情");
        genreObj.put("value", genreValues);
        arr.put(genreObj);

        // 地区筛选
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

        // 年份筛选
        JSONArray yearValues = new JSONArray();
        yearValues.put(item("全部年份", ""));
        for (int y = 2026; y >= 2004; y--) yearValues.put(item(String.valueOf(y), String.valueOf(y)));
        JSONObject yearObj = new JSONObject();
        yearObj.put("key", "year");
        yearObj.put("name", "年份");
        yearObj.put("value", yearValues);
        arr.put(yearObj);

        // 排序筛选
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

            // 构建参数Map：提取所有筛选条件
            Map<String, String> params = new HashMap<>();
            if (extend != null) {
                for (String key : extend.keySet()) {
                    params.put(key, extend.get(key));
                }
            }

            String url = buildCategoryUrl(tid, String.valueOf(page), params);
            String html = fetchHtml(url);
            JSONArray list = extractList(html);
            int pagecount = extractPageCount(html);

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("list", list);
            result.put("pagecount", pagecount > 0 ? pagecount : 1);
            result.put("limit", 24);
            result.put("total", (pagecount > 0 ? pagecount : 1) * 24);
            return result.toString();
        } catch (Exception e) {
            JSONObject result = new JSONObject();
            result.put("page", 1);
            result.put("list", new JSONArray());
            result.put("pagecount", 1);
            result.put("limit", 24);
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
            if (html.isEmpty()) {
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
        if (html.isEmpty()) {
            result.put("parse", 1);
            result.put("url", id);
            result.put("header", headerToJson(getHeader()));
            return result.toString();
        }

        String playUrl = extractPlayUrl(html);
        if (playUrl != null && !playUrl.isEmpty()) {
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
