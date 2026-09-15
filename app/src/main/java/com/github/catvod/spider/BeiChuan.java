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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BeiChuan extends Spider {

    private static final String API_HOST = "https://j.qulv8.com";

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern m3u8Pattern = Pattern.compile(
            "(https?://[^\\s<>\"']+\\.m3u8[^\\s<>\"']*)");
    private static final Pattern nowPattern = Pattern.compile(
            "var\\s+now\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern playerPattern = Pattern.compile(
            "var\\s+player_aaaa\\s*=\\s*(\\{[^;]+\\})");
    private static final Pattern tailPagePattern = Pattern.compile(
            "<a[^>]*href=\"[^\"]*-(\\d+)\\.html\"[^>]*>尾页</");

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
    // 工具
    // ============================================================
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
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

    private String cleanHtmlTags(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    // ============================================================
    // 提取列表
    // ============================================================
    private JSONArray extractList(String html) throws Exception {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;

        Document doc = Jsoup.parse(html);
        Elements items = doc.select("li.stui-vodlist__item");
        for (Element item : items) {
            Element a = item.selectFirst("a[href]");
            if (a == null) continue;
            String href = a.attr("href");
            if (TextUtils.isEmpty(href)) continue;

            String name = item.select("h4 a").text().trim();
            if (TextUtils.isEmpty(name)) name = item.select("h4").text().trim();

            String pic = item.select("img").attr("data-original");
            if (TextUtils.isEmpty(pic)) pic = item.select("img").attr("src");
            if (!TextUtils.isEmpty(pic) && pic.contains("/load")) pic = "";

            String remark = item.select("span.pic-text").text().trim();

            JSONObject vod = new JSONObject();
            vod.put("vod_id", href);
            vod.put("vod_name", name);
            vod.put("vod_pic", TextUtils.isEmpty(pic) ? "" : fixUrl(pic));
            vod.put("vod_remarks", remark);
            list.put(vod);
        }
        return list;
    }

    // ============================================================
    // 详情
    // ============================================================
    private JSONObject extractDetail(String html, String vodId) throws Exception {
        JSONObject info = new JSONObject();
        info.put("vod_id", vodId);

        // 名称
        Matcher titleM = Pattern.compile("<h3 class=\"title\">([^<]+)</h3>").matcher(html);
        info.put("vod_name", titleM.find() ? titleM.group(1).trim() : "");

        // 图片
        Matcher picM = Pattern.compile("<img[^>]*class=\"img-responsive[^\"]*\"[^>]*data-original=\"([^\"]+)\"").matcher(html);
        info.put("vod_pic", picM.find() ? fixUrl(picM.group(1)) : "");

        // 分类
        Matcher classM = Pattern.compile("<span class=\"text-muted hidden-xs\">类型：</span><a[^>]*>([^<]+)</a>").matcher(html);
        info.put("vod_class", classM.find() ? classM.group(1).trim() : "");

        // 地区
        Matcher areaM = Pattern.compile("<span class=\"text-muted hidden-xs\">地区：</span>([^<]+)<span").matcher(html);
        info.put("vod_area", areaM.find() ? areaM.group(1).trim() : "");

        // 年份
        Matcher yearM = Pattern.compile("<span class=\"text-muted hidden-xs\">年份：</span>(\\d{4})").matcher(html);
        info.put("vod_year", yearM.find() ? yearM.group(1) : "");

        // 主演
        Matcher actorM = Pattern.compile("<span class=\"text-muted\">主演：</span>([\\s\\S]*?)</p>").matcher(html);
        if (actorM.find()) {
            String block = actorM.group(1);
            Matcher am = Pattern.compile("<a[^>]*>([^<]+)</a>").matcher(block);
            StringBuilder sb = new StringBuilder();
            while (am.find()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(am.group(1).trim());
            }
            info.put("vod_actor", sb.toString());
        } else {
            info.put("vod_actor", "");
        }

        // 导演
        Matcher directorM = Pattern.compile("<span class=\"text-muted\">导演：</span>([\\s\\S]*?)</p>").matcher(html);
        if (directorM.find()) {
            String block = directorM.group(1);
            Matcher dm = Pattern.compile("<a[^>]*>([^<]+)</a>").matcher(block);
            StringBuilder sb = new StringBuilder();
            while (dm.find()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(dm.group(1).trim());
            }
            info.put("vod_director", sb.toString());
        } else {
            info.put("vod_director", "");
        }

        // 简介
        Matcher descM = Pattern.compile("<div class=\"stui-content__desc\">([\\s\\S]*?)</div>").matcher(html);
        if (descM.find()) {
            info.put("vod_content", cleanHtmlTags(descM.group(1)));
        } else {
            info.put("vod_content", "");
        }

        // 播放列表
        String[] playResult = extractPlaylist(html);
        info.put("vod_play_from", playResult[0]);
        info.put("vod_play_url", playResult[1]);

        return info;
    }

    // ============================================================
    // 播放列表提取（对应 JS extractPlaylist）
    // ============================================================
    private String[] extractPlaylist(String html) {
        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();

        // 1. 动态提取线路名
        List<String> foundNames = new ArrayList<>();

        // 方法1: card-item
        Matcher cardM = Pattern.compile("<div[^>]*class=\"[^\"]*card-item[^\"]*\"[^>]*>([^<]+)</div>").matcher(html);
        while (cardM.find()) {
            String name = cleanHtmlTags(cardM.group(1));
            if (!TextUtils.isEmpty(name) && !foundNames.contains(name)) {
                foundNames.add(name);
            }
        }

        // 方法2: card-nav
        if (foundNames.isEmpty()) {
            Matcher navM = Pattern.compile("<div[^>]*class=\"[^\"]*card-nav[^\"]*\"[^>]*>([\\s\\S]*?)</div>").matcher(html);
            if (navM.find()) {
                Matcher divM = Pattern.compile("<div[^>]*>([^<]+)</div>").matcher(navM.group(1));
                while (divM.find()) {
                    String name = cleanHtmlTags(divM.group(1));
                    if (!TextUtils.isEmpty(name) && !foundNames.contains(name)) {
                        foundNames.add(name);
                    }
                }
            }
        }

        // 方法3: 隐藏 panel 里的 h3/strong
        if (foundNames.isEmpty()) {
            Matcher panelM = Pattern.compile(
                    "<div class=\"stui-pannel stui-pannel-bg clearfix\" style=\"display: none;\">([\\s\\S]*?)</div>").matcher(html);
            while (panelM.find()) {
                String panel = panelM.group(1);
                String name = "";
                Matcher h3M = Pattern.compile("<h3[^>]*class=\"[^\"]*episode-tab[^\"]*\"[^>]*>([^<]+)</h3>").matcher(panel);
                if (h3M.find()) {
                    name = cleanHtmlTags(h3M.group(1));
                } else {
                    Matcher strongM = Pattern.compile("<strong[^>]*>([^<]+)</strong>").matcher(panel);
                    if (strongM.find()) name = cleanHtmlTags(strongM.group(1));
                }
                if (!TextUtils.isEmpty(name) && !foundNames.contains(name)) {
                    foundNames.add(name);
                }
            }
        }

        // 方法4: "如果[ XXX ]播放源失败"
        if (foundNames.isEmpty()) {
            Matcher textM = Pattern.compile("如果\\[\\s*([^\\]]+)\\s*\\]播放源失败").matcher(html);
            while (textM.find()) {
                String name = cleanHtmlTags(textM.group(1));
                if (!TextUtils.isEmpty(name) && !foundNames.contains(name)) {
                    foundNames.add(name);
                }
            }
        }

        // 去重
        List<String> uniqueNames = new ArrayList<>();
        Set<String> set = new HashSet<>();
        for (String name : foundNames) {
            if (set.add(name)) uniqueNames.add(name);
        }

        SpiderDebug.log("提取到的线路名称: " + uniqueNames);

        // 2. 提取所有播放列表
        List<List<String[]>> allPlaylists = new ArrayList<>();
        Matcher ulM = Pattern.compile(
                "<ul[^>]*class=\"[^\"]*stui-content__playlist[^\"]*\"[^>]*>([\\s\\S]*?)</ul>").matcher(html);
        while (ulM.find()) {
            String ulHtml = ulM.group(1);
            List<String[]> eps = new ArrayList<>();
            Matcher epM = Pattern.compile("<a[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>").matcher(ulHtml);
            while (epM.find()) {
                String url = epM.group(1);
                String name = epM.group(2).trim();
                if (!TextUtils.isEmpty(url) && !TextUtils.isEmpty(name)) {
                    eps.add(new String[]{name, url});
                }
            }
            if (!eps.isEmpty()) allPlaylists.add(eps);
        }

        SpiderDebug.log("找到播放列表数量: " + allPlaylists.size());

        // 3. 匹配线路名和播放列表
        int nameCount = uniqueNames.size();
        int playlistCount = allPlaylists.size();

        for (int i = 0; i < playlistCount; i++) {
            List<String[]> eps = allPlaylists.get(i);
            String fromName;

            if (i < nameCount) {
                fromName = uniqueNames.get(i);
            } else {
                // 从对应隐藏 panel 抓
                String foundName = "";
                Matcher panelM = Pattern.compile(
                        "<div class=\"stui-pannel stui-pannel-bg clearfix\" style=\"display: none;\">([\\s\\S]*?)</div>").matcher(html);
                while (panelM.find()) {
                    String panel = panelM.group(1);
                    boolean allFound = true;
                    for (String[] ep : eps) {
                        if (!panel.contains(ep[1])) {
                            allFound = false;
                            break;
                        }
                    }
                    if (allFound) {
                        Matcher h3M = Pattern.compile("<h3[^>]*class=\"[^\"]*episode-tab[^\"]*\"[^>]*>([^<]+)</h3>").matcher(panel);
                        if (h3M.find()) {
                            foundName = cleanHtmlTags(h3M.group(1));
                            break;
                        }
                        Matcher strongM = Pattern.compile("<strong[^>]*>([^<]+)</strong>").matcher(panel);
                        if (strongM.find()) {
                            foundName = cleanHtmlTags(strongM.group(1));
                            break;
                        }
                    }
                }
                fromName = TextUtils.isEmpty(foundName) ? ("线路" + (i + 1)) : foundName;
            }

            StringBuilder epSb = new StringBuilder();
            for (String[] ep : eps) {
                if (epSb.length() > 0) epSb.append("#");
                epSb.append(ep[0]).append("$").append(fixUrl(ep[1]));
            }

            playFrom.add(fromName);
            playUrl.add(epSb.toString());
            SpiderDebug.log("线路 " + (i + 1) + ": " + fromName + " -> " + eps.size() + "集");
        }

        // 备用：完全没找到时，从隐藏 panel 里抓
        if (playFrom.isEmpty()) {
            Matcher panelM = Pattern.compile(
                    "<div class=\"stui-pannel stui-pannel-bg clearfix\" style=\"display: none;\">([\\s\\S]*?)</div>").matcher(html);
            int panelIndex = 0;
            while (panelM.find()) {
                String panel = panelM.group(1);
                List<String[]> eps = new ArrayList<>();
                Matcher epM = Pattern.compile("<a[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>").matcher(panel);
                while (epM.find()) {
                    eps.add(new String[]{epM.group(2).trim(), epM.group(1)});
                }
                if (!eps.isEmpty()) {
                    String name = "线路" + (panelIndex + 1);
                    Matcher h3M = Pattern.compile("<h3[^>]*>([^<]+)</h3>").matcher(panel);
                    if (h3M.find()) name = cleanHtmlTags(h3M.group(1));

                    StringBuilder epSb = new StringBuilder();
                    for (String[] ep : eps) {
                        if (epSb.length() > 0) epSb.append("#");
                        epSb.append(ep[0]).append("$").append(fixUrl(ep[1]));
                    }
                    playFrom.add(name);
                    playUrl.add(epSb.toString());
                    panelIndex++;
                }
            }
        }

        return new String[]{TextUtils.join("$$$", playFrom), TextUtils.join("$$$", playUrl)};
    }

    // ============================================================
    // 提取 m3u8
    // ============================================================
    private String extractM3u8(String html) {
        if (TextUtils.isEmpty(html)) return null;

        // var now = "..."
        Matcher nowM = nowPattern.matcher(html);
        if (nowM.find() && nowM.group(1).contains(".m3u8")) {
            return cleanUrl(nowM.group(1));
        }

        // player_aaaa
        Matcher pm = playerPattern.matcher(html);
        if (pm.find()) {
            try {
                String raw = pm.group(1);
                raw = raw.replaceAll("([{,])\\s*([a-zA-Z0-9_]+)\\s*:", "$1\"$2\":");
                raw = raw.replaceAll(":\\s*'([^']*)'", ":\"$1\"");
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

        // 正则直接抓
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
        result.put("class", classes);

        if (filter) {
            result.put("filters", buildFilters());
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

    // ============================================================
    // buildFilters（按 JS 写死）
    // ============================================================
    private JSONObject buildFilters() throws Exception {
        JSONObject filters = new JSONObject();

        // 电影
        JSONArray movieFilters = new JSONArray();
        movieFilters.put(filterGroup("class", "按分类", new String[][]{
                {"全部", ""}, {"动作片", "5068662235"}, {"爱情片", "5079945566"},
                {"科幻片", "5082228897"}, {"恐怖片", "5093512228"}, {"战争片", "5005632039"},
                {"喜剧片", "5016915360"}, {"纪录片", "5028198691"}, {"剧情片", "5139482022"},
                {"悬疑片", "5269838275"}, {"动画片", "6437164322"}, {"其他电影", "5295524748"}
        }));
        movieFilters.put(filterGroup("area", "按地区", new String[][]{
                {"全部", ""}, {"大陆", "大陆"}, {"香港", "香港"}, {"台湾", "台湾"},
                {"日本", "日本"}, {"韩国", "韩国"}, {"欧美", "欧美"}, {"泰国", "泰国"}, {"其他", "其他"}
        }));
        movieFilters.put(filterGroup("year", "按年份", buildYearValues(2026, 2010, false)));
        filters.put("5922692431", movieFilters);

        // 连续剧
        JSONArray tvFilters = new JSONArray();
        tvFilters.put(filterGroup("class", "按分类", new String[][]{
                {"全部", ""}, {"国产剧", "5141765353"}, {"港台剧", "5153048684"},
                {"欧美剧", "5165168495"}, {"韩剧", "5176451826"}, {"日剧", "5229374731"},
                {"泰剧", "5272958086"}, {"番剧", "5206808079"}, {"其他", "5284241417"}
        }));
        tvFilters.put(filterGroup("area", "按地区", new String[][]{
                {"全部", ""}, {"大陆", "大陆"}, {"香港", "香港"}, {"台湾", "台湾"},
                {"日本", "日本"}, {"韩国", "韩国"}, {"欧美", "欧美"}, {"泰国", "泰国"}
        }));
        tvFilters.put(filterGroup("year", "按年份", buildYearValues(2026, 2020, false)));
        filters.put("5033975762", tvFilters);

        // 综艺
        JSONArray varietyFilters = new JSONArray();
        varietyFilters.put(filterGroup("class", "按分类", new String[][]{
                {"全部", ""}, {"国产综艺", "6332494542"}, {"港台综艺", "6343777873"},
                {"日韩综艺", "6355061204"}, {"欧美综艺", "6366344535"}, {"其他综艺", "6377627866"}
        }));
        varietyFilters.put(filterGroup("area", "按地区", new String[][]{
                {"全部", ""}, {"大陆", "大陆"}, {"香港", "香港"}, {"台湾", "台湾"},
                {"韩国", "韩国"}, {"日本", "日本"}, {"欧美", "欧美"}
        }));
        varietyFilters.put(filterGroup("year", "按年份", buildYearValues(2026, 2023, false)));
        filters.put("5046095573", varietyFilters);

        // 动漫
        JSONArray animeFilters = new JSONArray();
        animeFilters.put(filterGroup("class", "按分类", new String[][]{
                {"全部", ""}, {"国产动漫", "6389747677"}, {"日韩动漫", "6392031008"},
                {"欧美动漫", "6303314339"}, {"港台动漫", "6314597660"}, {"其他动漫", "6325880991"}
        }));
        animeFilters.put(filterGroup("area", "按地区", new String[][]{
                {"全部", ""}, {"大陆", "大陆"}, {"日本", "日本"},
                {"欧美", "欧美"}, {"港台", "港台"}, {"其他", "其他"}
        }));
        animeFilters.put(filterGroup("year", "按年份", buildYearValues(2026, 2023, false)));
        filters.put("5057378904", animeFilters);

        // 短剧
        JSONArray shortFilters = new JSONArray();
        shortFilters.put(filterGroup("area", "按地区", new String[][]{
                {"全部", ""}, {"大陆", "大陆"}, {"香港", "香港"}, {"台湾", "台湾"}
        }));
        shortFilters.put(filterGroup("year", "按年份", buildYearValues(2026, 2024, false)));
        filters.put("5218091400", shortFilters);

        return filters;
    }

    private String[][] buildYearValues(int from, int to, boolean includeEmpty) {
        List<String[]> list = new ArrayList<>();
        if (includeEmpty) list.add(new String[]{"全部", ""});
        for (int y = from; y >= to; y--) {
            list.add(new String[]{String.valueOf(y), String.valueOf(y)});
        }
        return list.toArray(new String[0][]);
    }

    // ============================================================
    // categoryContent
    // ============================================================
    @Override
    public String categoryContent(String tid, String pg, boolean filter,
                                  HashMap<String, String> extend) throws Exception {
        try {
            int page = parseIntSafe(pg, 1);
            String area = extend != null && extend.get("area") != null ? extend.get("area") : "";
            String year = extend != null && extend.get("year") != null ? extend.get("year") : "";
            String cls = extend != null && extend.get("class") != null ? extend.get("class") : "";

            String url = API_HOST + "/filter_sort/vod_list_" + tid;
            if (!TextUtils.isEmpty(cls)) {
                url = API_HOST + "/filter_sort/vod_list_" + cls + ".html";
            } else if (!TextUtils.isEmpty(area) && !TextUtils.isEmpty(year)) {
                url = API_HOST + "/library-year-" + year + "-area-" + URLEncoder.encode(area, "UTF-8")
                        + "-tid-" + tid + "-searchtype-5.html";
            } else if (!TextUtils.isEmpty(area)) {
                url = API_HOST + "/library-tid-" + tid + "-searchtype-5-area-"
                        + URLEncoder.encode(area, "UTF-8") + ".html";
            } else if (!TextUtils.isEmpty(year)) {
                url = API_HOST + "/library-year-" + year + "-tid-" + tid + "-searchtype-5.html";
            } else if (page > 1) {
                url += "-" + page + ".html";
            } else {
                url += ".html";
            }

            SpiderDebug.log("category url: " + url);

            String html = OkHttp.string(url);
            JSONArray list = extractList(html);
            int pagecount = extractPageCount(html);

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("list", list);
            result.put("pagecount", Math.max(pagecount, 1));
            result.put("limit", 24);
            result.put("total", Math.max(pagecount, 1) * 24);
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

    private int extractPageCount(String html) {
        if (TextUtils.isEmpty(html)) return 1;
        int maxPage = 1;

        Matcher m1 = tailPagePattern.matcher(html);
        if (m1.find()) {
            maxPage = parseIntSafe(m1.group(1), 1);
        }

        Matcher m2 = Pattern.compile("<a[^>]*href=\"[^\"]*-(\\d+)\\.html\"[^>]*>").matcher(html);
        while (m2.find()) {
            int n = parseIntSafe(m2.group(1), 0);
            if (n > maxPage) maxPage = n;
        }
        return maxPage;
    }

    // ============================================================
    // detailContent
    // ============================================================
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

    // ============================================================
    // searchContent
    // ============================================================
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        try {
            String encoded = URLEncoder.encode(key, "UTF-8");
            String url = API_HOST + "/search.php?searchword=" + encoded;
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

    // ============================================================
    // playerContent
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            // 1. id 是直链
            if (id != null && Pattern.compile("\\.(m3u8|mp4|flv|mkv|webm|ts)",
                    Pattern.CASE_INSENSITIVE).matcher(id).find()) {
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", id);
                result.put("header", m3u8Headers());
                return result.toString();
            }

            // 2. 抓播放页
            String html = OkHttp.string(id);
            if (TextUtils.isEmpty(html)) {
                JSONObject result = new JSONObject();
                result.put("parse", 1);
                result.put("url", id);
                result.put("header", headers());
                return result.toString();
            }

            // 3. 提取 m3u8
            String videoUrl = extractM3u8(html);
            if (!TextUtils.isEmpty(videoUrl)) {
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", videoUrl);
                result.put("header", m3u8Headers());
                return result.toString();
            }

            // 4. 抠 iframe
            Document doc = Jsoup.parse(html);
            String iframeSrc = doc.select("iframe").attr("src");
            if (!TextUtils.isEmpty(iframeSrc)) {
                if (iframeSrc.startsWith("//")) iframeSrc = "https:" + iframeSrc;
                else if (iframeSrc.startsWith("/")) iframeSrc = API_HOST + iframeSrc;
                JSONObject result = new JSONObject();
                result.put("parse", 1);
                result.put("url", iframeSrc);
                result.put("header", headers());
                return result.toString();
            }

            // 5. 兜底
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
    private JSONObject filterValue(String name, String value) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("n", name);
        obj.put("v", value);
        return obj;
    }

    private JSONObject filterGroup(String key, String name, String[][] values) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("key", key);
        obj.put("name", name);
        JSONArray arr = new JSONArray();
        for (String[] v : values) {
            JSONObject item = new JSONObject(