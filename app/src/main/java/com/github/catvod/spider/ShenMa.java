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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 神马影院 - www.smyyok.com
 * playerContent 返回 {"parse":0/1,"url":"..."}
 */
public class ShenMa extends Spider {

    private final String siteUrl = "https://www.smyyok.com";

    private final String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final Pattern m3u8Pattern = Pattern.compile(
            "(https?://[^\\s<>\"']+\\.m3u8[^\\s<>\"']*)");
    private static final Pattern playerPattern = Pattern.compile(
            "var\\s+player_aaaa\\s*=\\s*(\\{[^;]+\\})");

    // ============================================================
    // header
    // ============================================================
    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", userAgent);
        header.put("Referer", siteUrl + "/");
        return header;
    }

    private String req(String url) {
        return OkHttp.string(url, getHeader());
    }

    // ============================================================
    // buildVodShowUrl（11 个 '-'）
    // ============================================================
    private String buildVodShowUrl(String tid, String area, String sort, String pg, String year, String cls) {
        String[] parts = new String[12];
        Arrays.fill(parts, "");
        parts[1] = area == null ? "" : area;
        parts[2] = sort == null ? "" : sort;
        parts[3] = cls == null ? "" : cls;
        int page = parseIntSafe(pg, 1);
        parts[8] = page > 1 ? pg : "";

        StringBuilder url = new StringBuilder("/vodshow/").append(tid);
        url.append(TextUtils.join("-", parts));
        if (!TextUtils.isEmpty(year)) url.append(year);
        url.append(".html");
        return url.toString();
    }

    // ============================================================
    // 列表解析
    // ============================================================
    private JSONArray extractList(String html) throws Exception {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;

        Document doc = Jsoup.parse(html);
        Elements items = doc.select("div.public-list-box.public-pic-b");
        for (Element item : items) {
            Element a = item.selectFirst("a.public-list-exp");
            if (a == null) continue;
            String href = a.attr("href");

            Element titleA = item.selectFirst("a.time-title");
            String name = titleA != null ? titleA.attr("title") : "";
            if (TextUtils.isEmpty(name) && titleA != null) name = titleA.text().trim();

            String pic = item.select("img").attr("data-src");
            String remark = item.select("span.public-list-prb").text().trim();

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
    // 提取 m3u8
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
                    return url;
                }
            } catch (Exception e) {
                SpiderDebug.log("player_aaaa parse error");
            }
        }

        Matcher mm = m3u8Pattern.matcher(html);
        if (mm.find()) return mm.group(1);
        return null;
    }

    // ============================================================
    // 首页
    // ============================================================
    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();

        JSONArray classes = new JSONArray();
        String[] typeIds = {"1", "2", "3", "4", "5"};
        String[] typeNames = {"电影", "电视剧", "综艺", "动漫", "短剧"};
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

        try {
            String html = req(siteUrl + "/");
            result.put("list", extractList(html));
        } catch (Exception e) {
            SpiderDebug.log(e);
            result.put("list", new JSONArray());
        }

        return result.toString();
    }

    private JSONObject buildFilters() throws Exception {
        JSONObject filters = new JSONObject();

        JSONArray areaValues = new JSONArray();
        areaValues.put(filterValue("全部", ""));
        String[] areas = {"大陆", "香港", "台湾", "美国", "日本", "韩国", "英国", "法国",
                "德国", "意大利", "西班牙", "俄罗斯", "加拿大", "印度", "泰国", "其它",
                "新加坡", "菲律宾", "澳大利亚", "土耳其", "瑞典", "巴西", "荷兰",
                "印度尼西亚", "挪威", "智利", "爱尔兰", "伊朗", "蒙古"};
        for (String a : areas) areaValues.put(filterValue(a, a));

        JSONArray yearValues = new JSONArray();
        yearValues.put(filterValue("全部", ""));
        for (int y = 2026; y >= 2000; y--) {
            yearValues.put(filterValue(String.valueOf(y), String.valueOf(y)));
        }

        JSONArray sortValues = new JSONArray();
        sortValues.put(filterValue("按最新", "time"));
        sortValues.put(filterValue("按最热", "hits"));
        sortValues.put(filterValue("按评分", "score"));

        JSONArray classValues1 = buildClassValues(new String[][]{
                {"", "全部"},
                {"动作片", "动作片"}, {"喜剧片", "喜剧片"}, {"科幻片", "科幻片"},
                {"恐怖片", "恐怖片"}, {"爱情片", "爱情片"}, {"剧情片", "剧情片"},
                {"战争片", "战争片"}, {"记录片", "记录片"}, {"动画片", "动画片"},
                {"惊悚", "惊悚"}, {"犯罪", "犯罪"}, {"悬疑", "悬疑"}, {"冒险", "冒险"},
                {"奇幻", "奇幻"}, {"家庭", "家庭"}, {"历史", "历史"}, {"传记", "传记"},
                {"古装", "古装"}, {"音乐", "音乐"}, {"同性", "同性"}, {"运动", "运动"},
                {"武侠", "武侠"}, {"短片", "短片"}, {"歌舞", "歌舞"}, {"西部", "西部"},
                {"儿童", "儿童"}, {"灾难", "灾难"}, {"戏曲", "戏曲"}, {"真人秀", "真人秀"},
                {"青春", "青春"}
        });

        JSONArray classValues2 = buildClassValues(new String[][]{
                {"", "全部"},
                {"国产剧", "国产剧"}, {"欧美剧", "欧美剧"}, {"香港剧", "香港剧"},
                {"韩国剧", "韩国剧"}, {"台湾剧", "台湾剧"}, {"日本剧", "日本剧"},
                {"海外剧", "海外剧"}, {"泰国剧", "泰国剧"}, {"剧情", "剧情"},
                {"爱情", "爱情"}, {"喜剧", "喜剧"}, {"悬疑", "悬疑"}, {"犯罪", "犯罪"},
                {"古装", "古装"}, {"动作", "动作"}, {"奇幻", "奇幻"}, {"惊悚", "惊悚"},
                {"家庭", "家庭"}, {"历史", "历史"}, {"科幻", "科幻"}, {"战争", "战争"},
                {"同性", "同性"}, {"武侠", "武侠"}, {"冒险", "冒险"}, {"恐怖", "恐怖"},
                {"纪录", "纪录"}, {"传记", "传记"}, {"短片", "短片"}, {"运动", "运动"},
                {"音乐", "音乐"}, {"儿童", "儿童"}, {"歌舞", "歌舞"}, {"西部", "西部"},
                {"灾难", "灾难"}
        });

        JSONArray classValues3 = buildClassValues(new String[][]{
                {"", "全部"},
                {"大陆综艺", "大陆综艺"}, {"港台综艺", "港台综艺"}, {"日韩综艺", "日韩综艺"},
                {"欧美综艺", "欧美综艺"}, {"真人秀", "真人秀"}, {"纪录片", "纪录片"},
                {"脱口秀", "脱口秀"}, {"音乐", "音乐"}, {"歌舞", "歌舞"}, {"相声", "相声"},
                {"喜剧", "喜剧"}, {"爱情", "爱情"}, {"历史", "历史"}, {"运动", "运动"},
                {"冒险", "冒险"}, {"剧情", "剧情"}, {"访谈", "访谈"}, {"旅游", "旅游"},
                {"悬疑", "悬疑"}, {"家庭", "家庭"}, {"短片", "短片"}, {"同性", "同性"},
                {"动作", "动作"}, {"儿童", "儿童"}, {"惊悚", "惊悚"}, {"美食", "美食"}
        });

        JSONArray classValues4 = buildClassValues(new String[][]{
                {"", "全部"},
                {"国产动漫", "国产动漫"}, {"日韩动漫", "日韩动漫"}, {"欧美动漫", "欧美动漫"},
                {"港台动漫", "港台动漫"}, {"海外动漫", "海外动漫"}, {"动画", "动画"},
                {"喜剧", "喜剧"}, {"剧情", "剧情"}, {"奇幻", "奇幻"}, {"冒险", "冒险"},
                {"动作", "动作"}, {"科幻", "科幻"}, {"爱情", "爱情"}, {"儿童", "儿童"},
                {"家庭", "家庭"}, {"短片", "短片"}, {"悬疑", "悬疑"}, {"运动", "运动"},
                {"古装", "古装"}, {"武侠", "武侠"}, {"音乐", "音乐"}, {"犯罪", "犯罪"},
                {"惊悚", "惊悚"}, {"战争", "战争"}, {"恐怖", "恐怖"}, {"历史", "历史"},
                {"搞笑", "搞笑"}, {"歌舞", "歌舞"}, {"热血", "热血"}
        });

        JSONArray classValues5 = buildClassValues(new String[][]{
                {"", "全部"},
                {"女频恋爱", "女频恋爱"}, {"反转爽剧", "反转爽剧"}, {"古装仙侠", "古装仙侠"},
                {"年代穿越", "年代穿越"}, {"脑洞悬疑", "脑洞悬疑"}, {"现代都市", "现代都市"}
        });

        JSONArray f1 = new JSONArray();
        f1.put(filterGroup("class", "类型", classValues1));
        f1.put(filterGroup("area", "地区", areaValues));
        f1.put(filterGroup("year", "年份", yearValues));
        f1.put(filterGroup("sort", "排序", sortValues));
        filters.put("1", f1);

        JSONArray f2 = new JSONArray();
        f2.put(filterGroup("class", "类型", classValues2));
        f2.put(filterGroup("area", "地区", areaValues));
        f2.put(filterGroup("year", "年份", yearValues));
        f2.put(filterGroup("sort", "排序", sortValues));
        filters.put("2", f2);

        JSONArray f3 = new JSONArray();
        f3.put(filterGroup("class", "类型", classValues3));
        f3.put(filterGroup("area", "地区", areaValues));
        f3.put(filterGroup("year", "年份", yearValues));
        f3.put(filterGroup("sort", "排序", sortValues));
        filters.put("3", f3);

        JSONArray f4 = new JSONArray();
        f4.put(filterGroup("class", "类型", classValues4));
        f4.put(filterGroup("area", "地区", areaValues));
        f4.put(filterGroup("year", "年份", yearValues));
        f4.put(filterGroup("sort", "排序", sortValues));
        filters.put("4", f4);

        JSONArray f5 = new JSONArray();
        f5.put(filterGroup("class", "类型", classValues5));
        f5.put(filterGroup("area", "地区", areaValues));
        f5.put(filterGroup("year", "年份", yearValues));
        f5.put(filterGroup("sort", "排序", sortValues));
        filters.put("5", f5);

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
    // 分类
    // ============================================================
    @Override
    public String categoryContent(String tid, String pg, boolean filter,
                                  HashMap<String, String> extend) throws Exception {
        String cls = extend != null && extend.get("class") != null ? extend.get("class") : "";
        String area = extend != null && extend.get("area") != null ? extend.get("area") : "";
        String year = extend != null && extend.get("year") != null ? extend.get("year") : "";
        String sort = extend != null && extend.get("sort") != null ? extend.get("sort") : "";

        if ("全部".equals(cls)) cls = "";
        if ("全部".equals(area)) area = "";
        if ("全部".equals(year)) year = "";
        if ("全部".equals(sort)) sort = "";

        String url = siteUrl + buildVodShowUrl(tid, area, sort, pg, year, cls);
        SpiderDebug.log("category url: " + url);

        JSONArray videos = extractList(req(url));
        int page = parseIntSafe(pg, 1);

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", page + 1);
        result.put("limit", 24);
        result.put("total", Integer.MAX_VALUE);
        result.put("list", videos);
        return result.toString();
    }

    // ============================================================
    // 详情
    // ============================================================
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vodId = ids.get(0);
        String detailUrl = vodId.startsWith("http") ? vodId : siteUrl + vodId;
        String html = req(detailUrl);

        JSONObject info = new JSONObject();
        info.put("vod_id", vodId);

        Matcher paramM = Pattern.compile(
                "<div[^>]*class=\"info-parameter none\"[^>]*>([\\s\\S]*?)</div>\\s*</div>").matcher(html);
        if (paramM.find()) {
            Matcher liM = Pattern.compile("<li>(.*?)</li>").matcher(paramM.group(1));
            while (liM.find()) {
                String li = liM.group(1);
                Matcher emM = Pattern.compile("<em[^>]*class=\"cor4\"[^>]*>([^<]+)</em>").matcher(li);
                if (!emM.find()) continue;
                String key = emM.group(1).trim().replaceAll("[：:]", "").trim();
                String content = li.replaceAll("<em[^>]*class=\"cor4\"[^>]*>.*?</em>", "");

                List<String> aTexts = new ArrayList<>();
                Matcher aM = Pattern.compile("<a[^>]*>([^<]+)</a>").matcher(content);
                while (aM.find()) aTexts.add(aM.group(1).trim());
                if (!aTexts.isEmpty()) {
                    content = TextUtils.join(" ", aTexts);
                } else {
                    content = content.replaceAll("<[^>]+>", "").trim();
                }
                content = content.replaceAll("\\s+", " ").trim();

                switch (key) {
                    case "片名": info.put("vod_name", content); break;
                    case "主演": info.put("vod_actor", content); break;
                    case "导演": info.put("vod_director", content); break;
                    case "年份": info.put("vod_year", content); break;
                    case "地区": info.put("vod_area", content); break;
                    case "类型": info.put("type_name", content); break;
                    default: break;
                }
            }
        }

        Matcher descM = Pattern.compile(
                "<div[^>]*id=\"height_limit\"[^>]*class=\"text[^\"]*\"[^>]*>([\\s\\S]*?)</div>").matcher(html);
        if (descM.find()) {
            info.put("vod_content", "神马影院提醒你注意广告防止被骗！"
                    + descM.group(1).replaceAll("<[^>]+>", "").trim());
        } else {
            info.put("vod_content", "");
        }

        Matcher picM = Pattern.compile(
                "<img[^>]*class=\"lazy lazy1 mask-1\"[^>]*data-src=\"([^\"]+)\"[^>]*>").matcher(html);
        info.put("vod_pic", picM.find() ? fixUrl(picM.group(1)) : "");

        List<String> names = new ArrayList<>();
        Matcher tabM = Pattern.compile(
                "<a[^>]*class=\"swiper-slide[^\"]*\"[^>]*>\\s*<i[^>]*></i>&nbsp;([^<]+?)(?:<span[^>]*>\\d+</span>)?</a>").matcher(html);
        while (tabM.find()) {
            String name = tabM.group(1).trim();
            if (!TextUtils.isEmpty(name) && !names.contains(name)) names.add(name);
        }

        List<List<String[]>> allPlaylists = new ArrayList<>();
        Matcher ulM = Pattern.compile(
                "<ul[^>]*class=\"anthology-list-play size\"[^>]*>([\\s\\S]*?)</ul>").matcher(html);
        while (ulM.find()) {
            List<String[]> eps = new ArrayList<>();
            Matcher epM = Pattern.compile(
                    "<a[^>]*class=\"hide[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>([^<]+)</a>").matcher(ulM.group(1));
            while (epM.find()) {
                String url = epM.group(1);
                String name = epM.group(2).trim();
                if (!TextUtils.isEmpty(url) && !TextUtils.isEmpty(name)) {
                    eps.add(new String[]{name, url});
                }
            }
            if (!eps.isEmpty()) allPlaylists.add(eps);
        }

        List<String> playFrom = new ArrayList<>();
        List<String> playUrl = new ArrayList<>();
        for (int i = 0; i < allPlaylists.size(); i++) {
            List<String[]> eps = allPlaylists.get(i);
            String fromName = i < names.size() ? names.get(i) : "线路" + (i + 1);

            StringBuilder epSb = new StringBuilder();
            for (String[] ep : eps) {
                if (epSb.length() > 0) epSb.append("#");
                epSb.append(ep[0]).append("$").append(fixUrl(ep[1]));
            }
            playFrom.add("【神马影院】" + fromName);
            playUrl.add(epSb.toString());
        }

        info.put("vod_play_from", TextUtils.join("$$$", playFrom));
        info.put("vod_play_url", TextUtils.join("$$$", playUrl));

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        list.put(info);
        result.put("list", list);
        return result.toString();
    }

    // ============================================================
    // 搜索
    // ============================================================
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String encoded = URLEncoder.encode(key, "UTF-8");
        String url = siteUrl + "/vodsearch/" + encoded + "-------------.html";
        JSONArray videos = extractList(req(url));
        JSONObject result = new JSONObject();
        result.put("list", videos);
        return result.toString();
    }

    // ============================================================
    // ★ playerContent（带 parse）
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            if (id != null && Pattern.compile("\\.(m3u8|mp4|flv|mkv|webm|ts)",
                    Pattern.CASE_INSENSITIVE).matcher(id).find()) {
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", id);
                return result.toString();
            }

            String html = req(id);
            if (TextUtils.isEmpty(html)) {
                JSONObject result = new JSONObject();
                result.put("parse", 1);
                result.put("url", id);
                return result.toString();
            }

            String videoUrl = extractM3u8(html);
            if (!TextUtils.isEmpty(videoUrl)) {
                SpiderDebug.log("direct m3u8=" + videoUrl);
                JSONObject result = new JSONObject();
                result.put("parse", 0);
                result.put("url", videoUrl);
                return result.toString();
            }

            JSONObject result = new JSONObject();
            result.put("parse", 1);
            result.put("url", id);
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