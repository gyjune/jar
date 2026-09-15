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
 * 毒舌电影 www.dushehub.com
 * Java TVBox spider
 * 重要：播放地址JS动态生成，静态html无法提取，播放使用 parse=1 交给WebView嗅探
 */
public class DuShe extends Spider {
    private static final String API_HOST = "https://www.dushehub.com";
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", UA);
        header.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        header.put("Accept-Language", "zh-CN,zh;q=0.9");
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

    private String encode(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private String buildVodShowUrl(String tid, int page, Map<String, String> filter) {
        String url = "/show/" + tid;
        boolean hasFilter = filter != null && (!TextUtils.isEmpty(filter.get("area")) || !TextUtils.isEmpty(filter.get("sort")) || !TextUtils.isEmpty(filter.get("year")));
        if (hasFilter) {
            String area = filter.get("area") == null ? "" : filter.get("area");
            String sort = filter.get("sort") == null ? "" : filter.get("sort");
            String year = filter.get("year") == null ? "" : filter.get("year");
            StringBuilder dashPart = new StringBuilder();
            if (!area.isEmpty()) dashPart.append("-").append(encode(area));
            else dashPart.append("-");
            dashPart.append("-");
            if (!sort.isEmpty()) dashPart.append(sort);
            for (int i = 0; i < 8; i++) dashPart.append("-");
            if (!year.isEmpty()) dashPart.append(year);
            String pagePart = "";
            if (page > 1) pagePart = "---" + page;
            url += dashPart + pagePart + ".html";
        } else {
            if (page == 1) url += "-----------.html";
            else url += "--------" + page + "---.html";
        }
        return url;
    }

    private JSONArray extractList(String html) throws Exception {
        JSONArray videos = new JSONArray();
        if (html == null || html.isEmpty()) return videos;
        Pattern p = Pattern.compile("<a[^>]*class=\"[^\"]*module-poster-item[^\"]*\"[^>]*>.*?</a>\\s*</div>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        while (m.find()) {
            String item = m.group();
            Matcher urlMatch = Pattern.compile("<a[^>]*href=\"([^\"]+)\"[^>]*>").matcher(item);
            if (!urlMatch.find()) continue;
            String vodId = urlMatch.group(1);
            if (!vodId.startsWith("/album/")) continue;
            Matcher titleMatch = Pattern.compile("<div[^>]*class=\"[^\"]*module-poster-item-title[^\"]*\"[^>]*>([^<]+)</div>").matcher(item);
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

    private JSONArray extractSearchList(String html) throws Exception {
        JSONArray videos = new JSONArray();
        if (html == null || html.isEmpty()) return videos;
        Pattern p = Pattern.compile("<div class=\"module-card-item module-item\">.*?</div>\\s*</div>\\s*</div>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        while (m.find()) {
            String item = m.group();
            Matcher urlMatch = Pattern.compile("<a[^>]*href=\"(/album/[^\"]+)\"[^>]*>").matcher(item);
            if (!urlMatch.find()) continue;
            String vodId = urlMatch.group(1);
            String title = "";
            Matcher tm1 = Pattern.compile("<div[^>]*class=\"[^\"]*module-card-item-title[^\"]*\"[^>]*>.*?<strong>([^<]+)</strong>.*?</a>", Pattern.DOTALL).matcher(item);
            if (tm1.find()) {
                title = tm1.group(1).trim();
            } else {
                Matcher tm2 = Pattern.compile("<div[^>]*class=\"[^\"]*module-card-item-title[^\"]*\"[^>]*>.*?<a[^>]*>([^<]+)</a>", Pattern.DOTALL).matcher(item);
                if (tm2.find()) title = tm2.group(1).trim();
            }
            if (title.isEmpty()) continue;
            Matcher classMatch = Pattern.compile("<div class=\"module-card-item-class\">([^<]+)</div>").matcher(item);
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

    private int extractPageCount(String html) {
        int maxPage = 1;
        if (html == null) return maxPage;
        Pattern p = Pattern.compile("<a[^>]*class=\"[^\"]*page-link[^\"]*page-number[^\"]*\"[^>]*>(\\d+)</a>");
        Matcher m = p.matcher(html);
        while (m.find()) {
            try {
                int num = Integer.parseInt(m.group(1));
                if (num > maxPage) maxPage = num;
            } catch (Exception ignored) {}
        }
        Matcher lastMatch = Pattern.compile("<a[^>]*href=\"([^\"]+)\"[^>]*>尾页</a>").matcher(html);
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
        Matcher tm = Pattern.compile("<div class=\"module-info-tag-link\">\\s*<a[^>]*>([^<]+)</a>\\s*</div>").matcher(html);
        while (tm.find()) tags.add(tm.group(1).trim());

        List<String> areas = Arrays.asList("中国大陆", "中国香港", "中国台湾", "美国", "韩国", "日本", "英国", "法国", "泰国");
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

        String desc = find("<div class=\"module-info-introduction-content\">\\s*<p>([^<]+)</p>", html);
        if (!desc.isEmpty()) info.put("vod_content", desc);

        String directorBlock = find("<span class=\"module-info-item-title\">导演：</span>\\s*<div class=\"module-info-item-content\">\\s*(.*?)</div>", html);
        if (!directorBlock.isEmpty()) {
            List<String> dirs = new ArrayList<>();
            Matcher dm = Pattern.compile("<a[^>]*>([^<]+)</a>").matcher(directorBlock);
            while (dm.find()) dirs.add(dm.group(1).trim());
            info.put("vod_director", TextUtils.join("/", dirs));
        }

        String actorBlock = find("<span class=\"module-info-item-title\">主演：</span>\\s*<div class=\"module-info-item-content\">\\s*(.*?)</div>", html);
        if (!actorBlock.isEmpty()) {
            List<String> actors = new ArrayList<>();
            Matcher am = Pattern.compile("<a[^>]*>([^<]+)</a>").matcher(actorBlock);
            while (am.find()) actors.add(am.group(1).trim());
            info.put("vod_actor", TextUtils.join("/", actors));
        }

        List<String> playFromList = new ArrayList<>();
        List<String> playUrlList = new ArrayList<>();

        Pattern playBlockPat = Pattern.compile("<div class=\"module-list sort-list tab-list[^\"]*\" id=\"panel[^\"]*\">\\s*<div class=\"module-play-list\">\\s*<div class=\"module-play-list-content[^\"]*\">(.*?)</div>\\s*</div>\\s*</div>", Pattern.DOTALL);
        Matcher playBlockMatcher = playBlockPat.matcher(html);
        List<String> blockList = new ArrayList<>();
        while (playBlockMatcher.find()) blockList.add(playBlockMatcher.group(1));

        Pattern fromNamePat = Pattern.compile("<label class=\"module-tab-name\">\\s*<span[^>]*>([^<]+)</span>");
        Matcher fromNameMatcher = fromNamePat.matcher(html);
        List<String> fromNameList = new ArrayList<>();
        while (fromNameMatcher.find()) fromNameList.add(fromNameMatcher.group(1).trim());

        for (int i = 0; i < blockList.size(); i++) {
            String blk = blockList.get(i);
            List<String> eps = new ArrayList<>();
            Matcher epMatcher = Pattern.compile("<a[^>]*class=\"[^\"]*module-play-list-link[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>").matcher(blk);
            while (epMatcher.find()) {
                String epName = epMatcher.group(2).trim();
                String epHref = fixUrl(epMatcher.group(1));
                eps.add(epName + "$" + epHref);
            }
            if (eps.size() > 0) {
                String fromName;
                if (i < fromNameList.size() && !TextUtils.isEmpty(fromNameList.get(i))) {
                    fromName = fromNameList.get(i);
                } else {
                    fromName = "线路" + (i + 1);
                }
                playFromList.add(fromName);
                playUrlList.add(TextUtils.join("#", eps));
            }
        }
        info.put("vod_play_from", TextUtils.join("$$$", playFromList));
        info.put("vod_play_url", TextUtils.join("$$$", playUrlList));
        return info;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONArray classes = new JSONArray();
        classes.put(new JSONObject().put("type_id", "dianying").put("type_name", "电影"));
        classes.put(new JSONObject().put("type_id", "dianshiju").put("type_name", "电视剧"));
        classes.put(new JSONObject().put("type_id", "zongyi").put("type_name", "综艺"));
        classes.put(new JSONObject().put("type_id", "dongman").put("type_name", "动漫"));

        JSONObject result = new JSONObject();
        result.put("class", classes);
        result.put("filters", new JSONObject());
        return result.toString();
    }

    @Override
    public String homeVideoContent() throws Exception {
        String html = req(API_HOST + "/");
        JSONArray all = extractList(html);
        JSONArray list = new JSONArray();
        int take = Math.min(all.length(), 12);
        for(int i=0;i<take;i++){
            list.put(all.getJSONObject(i));
        }
        JSONObject res = new JSONObject();
        res.put("list", list);
        return res.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = Integer.parseInt(pg);
        String url = API_HOST + buildVodShowUrl(tid, page, extend);
        String html = req(url);
        JSONArray list = extractList(html);
        int pc = extractPageCount(html);
        JSONObject res = new JSONObject();
        res.put("page", page);
        res.put("pagecount", pc);
        res.put("limit", 12);
        res.put("total", pc * 12);
        res.put("list", list);
        return res.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String url = id.startsWith("http") ? id : API_HOST + id;
        String html = req(url);
        JSONObject info = extractDetail(html);
        info.put("vod_id", id);
        JSONArray arr = new JSONArray();
        arr.put(info);
        JSONObject res = new JSONObject();
        res.put("list", arr);
        return res.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = API_HOST + "/search/" + encode(key) + "-------------.html";
        String html = req(url);
        JSONArray list = extractSearchList(html);
        JSONObject res = new JSONObject();
        res.put("list", list);
        res.put("pagecount",1);
        return res.toString();
    }

    /**
     * 核心播放：不解析m3u8，交给WebView嗅探 parse=1
     * id：播放页面完整url
     */
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 1);
        result.put("url", id);
        result.put("header", new JSONObject(getHeader()).toString());
        return result.toString();
    }
}
