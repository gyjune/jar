package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.okhttp.OkHttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

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
 * 支持分类、筛选、搜索、播放
 * 播放优先抓 player_aaaa 里的裸 m3u8，抓不到再走 iframe 代理
 */
public class DuShe extends Spider {

    private static final String API_HOST = "https://www.dushehub.com";
    private static final String PROXY_HOST = "https://v.dushe.online";

    private static final String UA = "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    // ============================================================
    // 通用工具
    // ============================================================

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", UA);
        header.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        header.put("Accept-Language", "zh-CN,zh;q=0.9");
        header.put("Referer", API_HOST + "/");
        return header;
    }

    private Map<String, String> getM3U8Header() {
        return getM3U8Header(API_HOST);
    }

    /**
     * m3u8 源的 header，Referer 用主站（QPython 实测通过）
     */
    private Map<String, String> getM3U8Header(String m3u8Url) {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", UA);
        header.put("Accept", "*/*");
        header.put("Referer", API_HOST + "/");
        return header;
    }

    private String req(String url) {
        try {
            return OkHttpUtil.string(url, getHeader());
        } catch (Exception e) {
            return "";
        }
    }

    private String find(String regexStr, String htmlStr) {
        if (htmlStr == null) return "";
        Pattern pattern = Pattern.compile(regexStr, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(htmlStr);
        if (matcher.find()) return matcher.group(1).trim();
        return "";
    }

    private String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        url = url.trim();
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http")) return url;
        if (url.startsWith("/")) return API_HOST + url;
        return API_HOST + "/" + url;
    }

    private String cleanUrl(String url) {
        if (url == null) return "";
        url = url.replace("\\/", "/");
        url = url.replaceAll("^[\"']|[\"']$", "").trim();
        if (url.startsWith("//")) url = "https:" + url;
        return url;
    }

    private String encode(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    // ============================================================
    // 构建筛选URL
    // ============================================================

    private String buildVodShowUrl(String tid, int page, Map<String, String> filter) {
        String url = "/show/" + tid;

        boolean hasFilter = filter != null && (
                !TextUtils.isEmpty(filter.get("area")) ||
                !TextUtils.isEmpty(filter.get("sort")) ||
                !TextUtils.isEmpty(filter.get("year"))
        );

        if (hasFilter) {
            String area = filter.get("area") == null ? "" : filter.get("area");
            String sort = filter.get("sort") == null ? "" : filter.get("sort");
            String year = filter.get("year") == null ? "" : filter.get("year");

            StringBuilder dashPart = new StringBuilder();
            if (!area.isEmpty()) {
                dashPart.append("-").append(encode(area));
            } else {
                dashPart.append("-");
            }
            dashPart.append("-");

            if (!sort.isEmpty()) {
                dashPart.append(sort);
            }
            for (int i = 0; i < 8; i++) dashPart.append("-");
            if (!year.isEmpty()) dashPart.append(year);

            String pagePart = "";
            if (page > 1) pagePart = "---" + page;

            url += dashPart + pagePart + ".html";
        } else {
            if (page == 1) {
                url += "-----------.html";
            } else {
                url += "--------" + page + "---.html";
            }
        }
        return url;
    }

    // ============================================================
    // 提取视频列表 (列表页/首页 - module-poster-item)
    // ============================================================

    private JSONArray extractList(String html) throws Exception {
        JSONArray videos = new JSONArray();
        if (html == null || html.isEmpty()) return videos;

        Pattern p = Pattern.compile(
                "<a[^>]*class=\"[^\"]*module-poster-item[^\"]*\"[^>]*>.*?</a>\\s*</div>",
                Pattern.DOTALL);
        Matcher m = p.matcher(html);

        while (m.find()) {
            String item = m.group();
            Matcher urlMatch = Pattern.compile("<a[^>]*href=\"([^\"]+)\"[^>]*>").matcher(item);
            if (!urlMatch.find()) continue;
            String vodId = urlMatch.group(1);
            if (!vodId.startsWith("/album/")) continue;

            Matcher titleMatch = Pattern.compile(
                    "<div[^>]*class=\"[^\"]*module-poster-item-title[^\"]*\"[^>]*>([^<]+)</div>").matcher(item);
            if (!titleMatch.find()) continue;
            String name = titleMatch.group(1).trim();

            Matcher picMatch = Pattern.compile("data-original=\"([^\"]+)\"").matcher(item);
            String pic = picMatch.find() ? fixUrl(picMatch.group(1)) : "";

            JSONObject vod = new JSONObject();
            vod.put("vod_id", vodId);
            vod.put("vod_name", name);
            vod.put("vod_pic", pic);
            videos.put(vod);
        }
        return videos;
    }

    // ============================================================
    // 提取搜索列表 (搜索页 - module-card-item)
    // ============================================================

    private JSONArray extractSearchList(String html) throws Exception {
        JSONArray videos = new JSONArray();
        if (html == null || html.isEmpty()) return videos;

        Pattern p = Pattern.compile(
                "<div class=\"module-card-item module-item\">.*?</div>\\s*</div>\\s*</div>",
                Pattern.DOTALL);
        Matcher m = p.matcher(html);

        while (m.find()) {
            String item = m.group();

            Matcher urlMatch = Pattern.compile("<a[^>]*href=\"(/album/[^\"]+)\"[^>]*>").matcher(item);
            if (!urlMatch.find()) continue;
            String vodId = urlMatch.group(1);

            String title = "";
            Matcher tm1 = Pattern.compile(
                    "<div[^>]*class=\"[^\"]*module-card-item-title[^\"]*\"[^>]*>.*?<strong>([^<]+)</strong>.*?</a>",
                    Pattern.DOTALL).matcher(item);
            if (tm1.find()) {
                title = tm1.group(1).trim();
            } else {
                Matcher tm2 = Pattern.compile(
                        "<div[^>]*class=\"[^\"]*module-card-item-title[^\"]*\"[^>]*>.*?<a[^>]*>([^<]+)</a>",
                        Pattern.DOTALL).matcher(item);
                if (tm2.find()) title = tm2.group(1).trim();
            }
            if (title.isEmpty()) continue;

            Matcher classMatch = Pattern.compile(
                    "<div class=\"module-card-item-class\">([^<]+)</div>").matcher(item);
            String category = classMatch.find() ? classMatch.group(1).trim() : "";

            Matcher picMatch = Pattern.compile("data-original=\"([^\"]+)\"").matcher(item);
            String pic = picMatch.find() ? fixUrl(picMatch.group(1)) : "";

            JSONObject vod = new JSONObject();
            vod.put("vod_id", vodId);
            vod.put("vod_name", title);
            vod.put("vod_pic", pic);
            vod.put("vod_class", category);
            videos.put(vod);
        }
        return videos;
    }

    // ============================================================
    // 提取分页信息
    // ============================================================

    private int extractPageCount(String html) {
        int maxPage = 1;
        if (html == null) return maxPage;

        Pattern p = Pattern.compile(
                "<a[^>]*class=\"[^\"]*page-link[^\"]*page-number[^\"]*\"[^>]*>(\\d+)</a>");
        Matcher m = p.matcher(html);
        while (m.find()) {
            try {
                int num = Integer.parseInt(m.group(1));
                if (num > maxPage) maxPage = num;
            } catch (Exception ignored) {}
        }

        Matcher lastMatch = Pattern.compile(
                "<a[^>]*href=\"([^\"]+)\"[^>]*>尾页</a>").matcher(html);
        if (lastMatch.find()) {
            Matcher pnMatch = Pattern.compile("(\\d+)---\\.html").matcher(lastMatch.group(1));
            if (pnMatch.find()) {
                try {
                    int num = Integer.parseInt(pnMatch.group(1));
                    if (num > maxPage) maxPage = num;
                } catch (Exception ignored) {}
            }
        }
        return maxPage > 1 ? maxPage : 1;
    }

    // ============================================================
    // 提取详情
    // ============================================================

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

        String title = find("<h1>([^<]+)</h1>", html);
        if (!title.isEmpty()) info.put("vod_name", title);

        String pic = find("<div class=\"module-item-pic\">\\s*<img[^>]*data-original=\"([^\"]+)\"", html);
        if (!pic.isEmpty()) info.put("vod_pic", fixUrl(pic));

        List<String> tags = new ArrayList<>();
        Matcher tm = Pattern.compile(
                "<div class=\"module-info-tag-link\">\\s*<a[^>]*>([^<]+)</a>\\s*</div>").matcher(html);
        while (tm.find()) tags.add(tm.group(1).trim());

        List<String> areas = Arrays.asList(
                "中国大陆", "中国香港", "中国台湾", "美国", "韩国",
                "日本", "英国", "法国", "泰国");
        StringBuilder classSb = new StringBuilder();
        for (String tag : tags) {
            if (tag.matches("^\\d{4}$")) {
                info.put("vod_year", tag);
            } else if (areas.contains(tag)) {
                info.put("vod_area", tag);
            } else {
                if (classSb.length() > 0) classSb.append("/");
                classSb.append(tag);
            }
        }
        info.put("vod_class", classSb.toString());

        String desc = find(
                "<div class=\"module-info-introduction-content\">\\s*<p>([^<]+)</p>", html);
        if (!desc.isEmpty()) info.put("vod_content", desc);

        String directorBlock = find(
                "<span class=\"module-info-item-title\">导演：</span>\\s*<div class=\"module-info-item-content\">\\s*(.*?)</div>",
                html);
        if (!directorBlock.isEmpty()) {
            List<String> dirs = new ArrayList<>();
            Matcher dm = Pattern.compile("<a[^>]*>([^<]+)</a>").matcher(directorBlock);
            while (dm.find()) dirs.add(dm.group(1).trim());
            info.put("vod_director", TextUtils.join("/", dirs));
        }

        String actorBlock = find(
                "<span class=\"module-info-item-title\">主演：</span>\\s*<div class=\"module-info-item-content\">\\s*(.*?)</div>",
                html);
        if (!actorBlock.isEmpty()) {
            List<String> actors = new ArrayList<>();
            Matcher am = Pattern.compile("<a[^>]*>([^<]+)</a>").matcher(actorBlock);
            while (am.find()) actors.add(am.group(1).trim());
            info.put("vod_actor", TextUtils.join("/", actors));
        }

        // 线路名 + 集数
        List<String> cleanFromNames = new ArrayList<>();
        Matcher fnm = Pattern.compile(
                "<div[^>]*class=\"[^\"]*module-tab-item[^\"]*\"[^>]*>.*?</div>",
                Pattern.DOTALL).matcher(html);

        while (fnm.find()) {
            String block = fnm.group();

            String name = "";
            Matcher nm = Pattern.compile("data-dropdown-value=\"([^\"]+)\"").matcher(block);
            if (nm.find()) {
                name = nm.group(1).trim();
            } else {
                Matcher sm = Pattern.compile("<span>([^<]+)</span>").matcher(block);
                if (sm.find()) name = sm.group(1).trim();
            }
            if (name.isEmpty()) continue;

            String count = "";
            Matcher cm = Pattern.compile("<small>([^<]*)</small>").matcher(block);
            if (cm.find()) count = cm.group(1).trim();

            cleanFromNames.add(count.isEmpty() ? name : name + "[" + count + "]");
        }

        List<String> playBlocks = new ArrayList<>();
        Matcher pbm = Pattern.compile(
                "<div[^>]*id=\"panel\\d+\"[^>]*>\\s*<div class=\"module-play-list\">\\s*<div class=\"module-play-list-content[^\"]*\">(.*?)</div>\\s*</div>\\s*</div>",
                Pattern.DOTALL).matcher(html);
        while (pbm.find()) playBlocks.add(pbm.group(1));

        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();

        for (int i = 0; i < playBlocks.size(); i++) {
            List<String> eps = new ArrayList<>();
            Matcher epm = Pattern.compile(
                    "<a[^>]*class=\"[^\"]*module-play-list-link[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>\\s*<span>([^<]+)</span>")
                    .matcher(playBlocks.get(i));
            while (epm.find()) {
                String epUrl = fixUrl(epm.group(1));
                String epName = epm.group(2).trim();
                eps.add(epName + "$" + epUrl);
            }
            if (!eps.isEmpty()) {
                String fromName = (i < cleanFromNames.size() && !cleanFromNames.get(i).isEmpty())
                        ? cleanFromNames.get(i)
                        : ("线路" + (i + 1));
                playFrom.add(fromName);
                playUrl.add(TextUtils.join("#", eps));
            }
        }

        info.put("vod_play_from", TextUtils.join("$$$", playFrom));
        info.put("vod_play_url", TextUtils.join("$$$", playUrl));

        return info;
    }

    // ============================================================
    // 提取播放地址
    // ============================================================

    private String extractPlayUrl(String html) {
        if (html == null || html.isEmpty()) return null;

        // 1. player_aaaa 里的 "url" 字段
        String m3u8Url = extractM3u8FromPlayerAaaa(html);
        if (m3u8Url != null && !m3u8Url.isEmpty()) {
            return m3u8Url;
        }

        // 2. iframe 里的 v.dushe.online 代理页
        Matcher ifm = Pattern.compile(
                "<iframe[^>]*src=\"(https://v\\.dushe\\.online/\\?[^\"]+)\"").matcher(html);
        if (ifm.find()) {
            return ifm.group(1).replace("&amp;", "&");
        }

        // 3. 全局找 m3u8
        Matcher mm = Pattern.compile(
                "(https?://[^\\s\"'<>\\\\]+\\.m3u8[^\\s\"'<>\\\\]*)").matcher(html);
        if (mm.find()) {
            return mm.group(1).replace("\\/", "/");
        }

        // 4. 任意 iframe
        Matcher ifm2 = Pattern.compile("<iframe[^>]*src=\"([^\"]+)\"[^>]*>").matcher(html);
        if (ifm2.find()) {
            return ifm2.group(1).replace("&amp;", "&");
        }

        return null;
    }

    /**
     * 从 player_aaaa 里抓 url 字段
     */
    private String extractM3u8FromPlayerAaaa(String html) {
        Matcher block = Pattern.compile(
                "var\\s+player_aaaa\\s*=\\s*(\\{.*?})(?=\\s*</script>|\\s*<script)",
                Pattern.DOTALL).matcher(html);

        String scope = null;
        if (block.find()) {
            scope = block.group(1);
        } else {
            Matcher block2 = Pattern.compile(
                    "var\\s+player_aaaa\\s*=\\s*(\\{.*?\\})",
                    Pattern.DOTALL).matcher(html);
            if (block2.find()) scope = block2.group(1);
        }

        if (scope == null || scope.isEmpty()) return null;

        Matcher um = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+?)\"").matcher(scope);
        if (um.find()) {
            String url = um.group(1).replace("\\/", "/");
            if (url.contains(".m3u8")) return url;
        }

        return null;
    }

    // ============================================================
    // 构建筛选 JSON
    // ============================================================

    private JSONArray buildFilter(JSONArray classValues, JSONArray areaValues,
                                  JSONArray yearValues, JSONArray sortValues) throws Exception {
        JSONArray arr = new JSONArray();

        JSONObject f1 = new JSONObject();
        f1.put("key", "class");
        f1.put("name", "分类");
        f1.put("value", classValues);
        arr.put(f1);

        JSONObject f2 = new JSONObject();
        f2.put("key", "area");
        f2.put("name", "地区");
        f2.put("value", areaValues);
        arr.put(f2);

        JSONObject f3 = new JSONObject();
        f3.put("key", "year");
        f3.put("name", "年份");
        f3.put("value", yearValues);
        arr.put(f3);

        JSONObject f4 = new JSONObject();
        f4.put("key", "sort");
        f4.put("name", "排序");
        f4.put("value", sortValues);
        arr.put(f4);

        return arr;
    }

    // ============================================================
    // TVBox 标准接口
    // ============================================================

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONArray classes = new JSONArray();
        String[][] cats = {
                {"dianying", "电影"},
                {"dianshiju", "电视剧"},
                {"zongyi", "综艺"},
                {"dongman", "动漫"}
        };
        for (String[] c : cats) {
            JSONObject o = new JSONObject();
            o.put("type_id", c[0]);
            o.put("type_name", c[1]);
            classes.put(o);
        }

        JSONArray areaValues = new JSONArray();
        String[][] areas = {
                {"", "全部地区"}, {"中国大陆", "中国大陆"}, {"中国香港", "中国香港"},
                {"中国台湾", "中国台湾"}, {"美国", "美国"}, {"法国", "法国"}, {"英国", "英国"},
                {"日本", "日本"}, {"韩国", "韩国"}, {"德国", "德国"}, {"泰国", "泰国"}, {"印度", "印度"}
        };
        for (String[] a : areas) {
            JSONObject o = new JSONObject();
            o.put("v", a[0]);
            o.put("n", a[1]);
            areaValues.put(o);
        }

        JSONArray yearValues = new JSONArray();
        JSONObject allYear = new JSONObject();
        allYear.put("v", "");
        allYear.put("n", "全部年份");
        yearValues.put(allYear);
        for (int y = 2026; y >= 2015; y--) {
            JSONObject o = new JSONObject();
            o.put("v", String.valueOf(y));
            o.put("n", String.valueOf(y));
            yearValues.put(o);
        }

        JSONArray sortValues = new JSONArray();
        String[][] sorts = {{"", "默认排序"}, {"time", "按时间"}, {"hits", "按人气"}, {"score", "按评分"}};
        for (String[] s : sorts) {
            JSONObject o = new JSONObject();
            o.put("v", s[0]);
            o.put("n", s[1]);
            sortValues.put(o);
        }

        JSONArray classValues = new JSONArray();
        String[][] movieClasses = {
                {"dianying", "电影"}, {"dongzuo", "动作片"}, {"xiju", "喜剧片"}, {"aiqing", "爱情片"},
                {"kehuan", "科幻片"}, {"kongbu", "恐怖片"}, {"juqing", "剧情片"},
                {"zhanzheng", "战争片"}, {"jilupian", "纪录片"}
        };
        for (String[] c : movieClasses) {
            JSONObject o = new JSONObject();
            o.put("v", c[0]);
            o.put("n", c[1]);
            classValues.put(o);
        }

        JSONArray tvClassValues = new JSONArray();
        String[][] tvClasses = {
                {"dianshiju", "电视剧"}, {"meiju", "美剧"}, {"hanju", "韩剧"}, {"riju", "日剧"},
                {"taiju", "泰剧"}, {"gangju", "港剧"}, {"guochan", "国产剧"}
        };
        for (String[] c : tvClasses) {
            JSONObject o = new JSONObject();
            o.put("v", c[0]);
            o.put("n", c[1]);
            tvClassValues.put(o);
        }

        JSONArray zongyiClassValues = new JSONArray();
        JSONObject zy = new JSONObject();
        zy.put("v", "zongyi");
        zy.put("n", "综艺");
        zongyiClassValues.put(zy);

        JSONArray dongmanClassValues = new JSONArray();
        JSONObject dm = new JSONObject();
        dm.put("v", "dongman");
        dm.put("n", "动漫");
        dongmanClassValues.put(dm);

        JSONObject filters = new JSONObject();
        filters.put("dianying", buildFilter(classValues, areaValues, yearValues, sortValues));
        filters.put("dianshiju", buildFilter(tvClassValues, areaValues, yearValues, sortValues));
        filters.put("zongyi", buildFilter(zongyiClassValues, areaValues, yearValues, sortValues));
        filters.put("dongman", buildFilter(dongmanClassValues, areaValues, yearValues, sortValues));

        JSONObject result = new JSONObject();
        result.put("class", classes);
        result.put("filters", filters);
        return result.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = req(API_HOST + "/");
        JSONArray list = extractList(html);
        JSONArray limited = new JSONArray();
        for (int i = 0; i < Math.min(12, list.length()); i++) {
            limited.put(list.get(i));
        }
        JSONObject result = new JSONObject();
        result.put("list", limited);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

        Map<String, String> params = new HashMap<>();
        params.put("area", extend.get("area") == null ? "" : extend.get("area"));
        params.put("year", extend.get("year") == null ? "" : extend.get("year"));
        params.put("sort", extend.get("sort") == null ? "" : extend.get("sort"));

        String url = API_HOST + buildVodShowUrl(tid, page, params);
        String html = req(url);
        JSONArray list = extractList(html);
        int pagecount = extractPageCount(html);

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("list", list);
        result.put("pagecount", Math.max(pagecount, 1));
        result.put("limit", 12);
        result.put("total", Math.max(pagecount, 1) * 12);
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = id.startsWith("http") ? id : API_HOST + id;
        String html = req(url);

        JSONObject info = extractDetail(html);
        info.put("vod_id", id);

        JSONArray list = new JSONArray();
        list.put(info);

        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

        String encoded = URLEncoder.encode(key, "UTF-8");
        String url = API_HOST + "/search/" + encoded + "-------------.html";
        String html = req(url);
        JSONArray list = extractSearchList(html);

        JSONObject result = new JSONObject();
        result.put("list", list);
        result.put("page", page);
        result.put("pagecount", 1);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (id != null && id.matches("(?i).*\\.(m3u8|mp4|flv|mkv|webm|ts).*")) {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", id);
            result.put("header", new JSONObject(getM3U8Header(id)).toString());
            return result.toString();
        }

        String html = req(id);
        if (html.isEmpty()) {
            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("url", id);
            result.put("header", new JSONObject(getHeader()).toString());
            return result.toString();
        }

        String playUrl = extractPlayUrl(html);

        JSONObject result = new JSONObject();
        if (playUrl == null) {
            result.put("parse", 1);
            result.put("url", id);
            result.put("header", new JSONObject(getHeader()).toString());
        } else if (playUrl.matches("(?i).*\\.(m3u8|mp4|flv|mkv|webm|ts).*")) {
            result.put("parse", 0);
            result.put("url", playUrl);
            result.put("header", new JSONObject(getM3U8Header(playUrl)).toString());
        } else if (playUrl.contains("v.dushe.online")) {
            result.put("parse", 1);
            result.put("url", playUrl);
            Map<String, String> h = getHeader();
            h.put("Referer", API_HOST + "/");
            result.put("header", new JSONObject(h).toString());
        } else {
            result.put("parse", 1);
            result.put("url", playUrl);
            result.put("header", new JSONObject(getHeader()).toString());
        }
        return result.toString();
    }
}