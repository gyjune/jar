package com.github.catvod.spider;

import android.content.Context;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 骚火影视 SaoHuo
 * 对应 cat_骚火.js
 */
public class SaoHuo extends Spider {

    private String host = "https://shdy2.com";
    private String cookie = "";

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 9; ALN-AL00 Build/PQ3B.190801.05281406; wv) AppleWebKit/537.36";

    // ==================== 基础请求 ====================

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("accept-language", "zh-CN,zh;q=0.9");
        if (!TextUtils.isEmpty(cookie)) h.put("Cookie", cookie);
        return h;
    }

    private Map<String, String> headers(String extraReferer) {
        Map<String, String> h = headers();
        if (!TextUtils.isEmpty(extraReferer)) h.put("Referer", extraReferer);
        return h;
    }

    /**
     * GET 请求，自动维护 cookie
     */
    private String request(String url) {
        return request(url, null);
    }

    private String request(String url, String referer) {
        try {
            Map<String, String> h = headers(referer);
            // OkHttpUtil 无法直接拿 set-cookie 的场景：这里只用简化版
            String content = OkHttpUtil.string(url, h);
            return content == null ? "" : content;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * POST JSON，参考 JS 的 postJson
     */
    private String postJson(String url, JSONObject body, String referer) {
        try {
            Map<String, String> h = headers();
            h.put("Content-Type", "application/json");
            if (!TextUtils.isEmpty(referer)) h.put("Referer", referer);
            return OkHttpUtil.post(url, h, body.toString());
        } catch (Exception e) {
            return "";
        }
    }

    // ==================== init / 首页 ====================

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (!TextUtils.isEmpty(extend)) {
            host = extend.trim();
        }
        request(host);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONArray classes = new JSONArray();
        classes.put(clazz("1", "电影"));
        classes.put(clazz("2", "电视剧"));
        classes.put(clazz("4", "动漫"));

        JSONObject filters = new JSONObject();
        filters.put("1", arr(
                filterItem("cateId", "类型", new String[][]{
                        {"全部", "1"}, {"喜剧", "6"}, {"爱情", "7"}, {"恐怖", "8"},
                        {"动作", "9"}, {"科幻", "10"}, {"战争", "11"}, {"犯罪", "12"},
                        {"动画", "13"}, {"奇幻", "14"}, {"剧情", "15"}, {"冒险", "16"},
                        {"悬疑", "17"}, {"惊悚", "18"}, {"其他", "20"}
                })));
        filters.put("2", arr(
                filterItem("cateId", "类型", new String[][]{
                        {"全部", "2"}, {"国产剧", "20"}, {"TVB", "21"}, {"韩剧", "22"},
                        {"美剧", "23"}, {"日剧", "24"}, {"英剧", "25"}, {"台剧", "26"},
                        {"其他", "27"}
                })));
        filters.put("4", arr(
                filterItem("cateId", "类型", new String[][]{
                        {"全部", "4"}, {"搞笑", "38"}, {"恋爱", "39"}, {"热血", "40"},
                        {"格斗", "41"}, {"美少女", "42"}, {"魔法", "43"}, {"机战", "44"},
                        {"校园", "45"}, {"亲子", "46"}, {"童话", "47"}, {"冒险", "48"},
                        {"真人", "49"}, {"LOLI", "50"}, {"其他", "51"}
                })));

        JSONObject result = new JSONObject();
        result.put("class", classes);
        result.put("filters", filters);
        return result.toString();
    }

    private JSONObject clazz(String id, String name) throws Exception {
        JSONObject o = new JSONObject();
        o.put("type_id", id);
        o.put("type_name", name);
        return o;
    }

    private JSONArray arr(JSONObject... items) {
        JSONArray a = new JSONArray();
        for (JSONObject o : items) a.put(o);
        return a;
    }

    private JSONObject filterItem(String key, String name, String[][] values) throws Exception {
        JSONObject o = new JSONObject();
        o.put("key", key);
        o.put("name", name);
        JSONArray arr = new JSONArray();
        for (String[] v : values) {
            JSONObject item = new JSONObject();
            item.put("n", v[0]);
            item.put("v", v[1]);
            arr.put(item);
        }
        o.put("value", arr);
        return o;
    }

    @Override
    public String homeVideoContent() throws Exception {
        if (TextUtils.isEmpty(host)) host = "https://shdy2.com";
        String html = request(host);
        if (TextUtils.isEmpty(html)) {
            JSONObject r = new JSONObject();
            r.put("list", new JSONArray());
            return r.toString();
        }
        JSONArray list = parseList(html, 6);
        JSONObject result = new JSONObject();
        result.put("list", list);
        return result.toString();
    }

    // ==================== 列表解析 ====================

    private JSONArray parseList(String html, int limit) throws Exception {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;

        Document doc = Jsoup.parse(html);
        Elements items = doc.select(".v_list li, .module-item, .myui-vodlist__box, " +
                ".stui-vodlist__box, li.vodlist_box");

        int count = 0;
        for (Element el : items) {
            if (limit > 0 && count >= limit) break;

            Element a = el.selectFirst("a");
            if (a == null) continue;

            String href = a.attr("href");
            String title = a.attr("title");
            if (TextUtils.isEmpty(title)) {
                Element img = a.selectFirst("img");
                if (img != null) title = img.attr("alt");
            }
            if (TextUtils.isEmpty(href) || TextUtils.isEmpty(title)) continue;

            String pic = "";
            Element img = a.selectFirst("img");
            if (img != null) {
                pic = img.attr("data-original");
                if (TextUtils.isEmpty(pic)) pic = img.attr("src");
            }

            String remarks = "";
            Element note = el.selectFirst(".v_note, .continu, .pic-text, .pic-tag, .module-item-note");
            if (note != null) remarks = note.text().trim();

            JSONObject vod = new JSONObject();
            vod.put("vod_id", href.startsWith("http") ? href : (host + href));
            vod.put("vod_name", title);
            vod.put("vod_pic", pic.startsWith("http") ? pic : (host + pic));
            vod.put("vod_remarks", remarks);
            list.put(vod);

            count++;
        }
        return list;
    }

    // ==================== 分类 ====================

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend)
            throws Exception {
        if (TextUtils.isEmpty(host)) host = "https://shdy2.com";
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
        if (page < 1) page = 1;

        String cateId = (extend != null && !TextUtils.isEmpty(extend.get("cateId")))
                ? extend.get("cateId") : tid;
        String url = host + "/list/" + cateId;
        if (page > 1) url += "-" + page;
        url += ".html";

        String html = request(url);
        if (TextUtils.isEmpty(html)) {
            JSONObject r = new JSONObject();
            r.put("list", new JSONArray());
            r.put("page", page);
            r.put("pagecount", 1);
            return r.toString();
        }

        JSONArray list = parseList(html, 0);

        int pagecount = page;
        Document doc = Jsoup.parse(html);
        Elements pageLinks = doc.select(".page a, .pagination a, #page a, .pages a");
        if (!pageLinks.isEmpty()) {
            int maxPage = 0;
            Pattern p = Pattern.compile("[-_](\\d+)\\.html");
            for (Element a : pageLinks) {
                Matcher m = p.matcher(a.attr("href"));
                if (m.find()) {
                    try {
                        int n = Integer.parseInt(m.group(1));
                        if (n > maxPage) maxPage = n;
                    } catch (Exception ignored) {}
                }
            }
            if (maxPage > 0) pagecount = maxPage;
        } else if (list.length() >= 10) {
            pagecount = page + 1;
        }

        JSONObject result = new JSONObject();
        result.put("list", list);
        result.put("page", page);
        result.put("pagecount", pagecount);
        result.put("limit", list.length());
        result.put("total", pagecount * list.length());
        return result.toString();
    }

    // ==================== 详情 ====================

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (TextUtils.isEmpty(host)) host = "https://shdy2.com";
        String id = ids.get(0);
        String url = id.startsWith("http") ? id : (host + id);
        String html = request(url);
        if (TextUtils.isEmpty(html)) {
            JSONObject r = new JSONObject();
            r.put("list", new JSONArray());
            return r.toString();
        }

        Document doc = Jsoup.parse(html);

        String vodName = "";
        Element h1 = doc.selectFirst("h1.v_title, h1.title");
        if (h1 != null) vodName = h1.text().trim().replaceAll("\\s*-.*$", "");

        String vodPic = "";
        Element img = doc.selectFirst("img.lazyload");
        if (img != null) {
            vodPic = img.attr("data-original");
            if (TextUtils.isEmpty(vodPic)) vodPic = img.attr("src");
        }
        if (!TextUtils.isEmpty(vodPic) && !vodPic.startsWith("http")) vodPic = host + vodPic;

        String vodRemarks = doc.select(".score, .text-red").text().trim();

        String typeName = "", vodArea = "", vodYear = "";
        String vodDirector = "", vodActor = "";

        Element infoP = doc.selectFirst(".v_info_box p");
        if (infoP != null) {
            String infoStr = infoP.text().trim();
            if (!TextUtils.isEmpty(infoStr)) {
                String[] segs = infoStr.split("/");
                for (int i = 0; i < segs.length; i++) segs[i] = segs[i].trim();
                if (segs.length >= 3) {
                    vodArea = segs[0];
                    vodYear = segs[1];
                    typeName = segs[2];
                    for (int i = 3; i < segs.length; i++) {
                        String seg = segs[i];
                        if (seg.startsWith("导演:")) vodDirector = seg.replace("导演:", "").trim();
                        else if (seg.startsWith("主演:")) {
                            vodActor = seg.replace("主演:", "").trim();
                            vodActor = vodActor.replaceAll("剧情介绍.*$", "").trim();
                        }
                    }
                }
            }
        }

        String vodContent = doc.select(".intro, .des, p.p_txt").text().trim();
        if (TextUtils.isEmpty(vodContent)) vodContent = doc.select("#info_more").text().trim();

        List<String> sourceNames = new ArrayList<>();
        List<String> sourceUrls = new ArrayList<>();

        Elements fromList = doc.select(".play_from ul.from_list li");
        Elements linkBlocks = doc.select("#play_link > li");

        if (!fromList.isEmpty() && !linkBlocks.isEmpty() && fromList.size() == linkBlocks.size()) {
            for (int i = 0; i < fromList.size(); i++) {
                sourceNames.add(fromList.get(i).text().trim());
                sourceUrls.add(parseEpisodes(linkBlocks.get(i)));
            }
        } else if (!linkBlocks.isEmpty()) {
            for (int i = 0; i < linkBlocks.size(); i++) {
                sourceNames.add("线路" + (i + 1));
                sourceUrls.add(parseEpisodes(linkBlocks.get(i)));
            }
        }

        JSONObject vod = new JSONObject();
        vod.put("vod_id", id);
        vod.put("vod_name", vodName);
        vod.put("vod_pic", vodPic);
        vod.put("vod_content", vodContent);
        vod.put("vod_play_from", TextUtils.join("$$$", sourceNames));
        vod.put("vod_play_url", TextUtils.join("$$$", sourceUrls));
        vod.put("vod_director", vodDirector);
        vod.put("vod_actor", vodActor);
        vod.put("type_name", typeName);
        vod.put("vod_area", vodArea);
        vod.put("vod_year", vodYear);
        vod.put("vod_remarks", vodRemarks);

        JSONArray arr = new JSONArray();
        arr.put(vod);
        JSONObject result = new JSONObject();
        result.put("list", arr);
        return result.toString();
    }

    private String parseEpisodes(Element block) {
        List<Ep> eps = new ArrayList<>();
        Elements links = block.select("a");
        for (int i = 0; i < links.size(); i++) {
            Element a = links.get(i);
            String href = a.attr("href");
            String text = a.text().trim();
            int num = i + 1;
            String digits = text.replaceAll("[^0-9]", "");
            if (!TextUtils.isEmpty(digits)) {
                try { num = Integer.parseInt(digits); } catch (Exception ignored) {}
            }
            eps.add(new Ep(text, href, num));
        }
        eps.sort((a, b) -> a.num - b.num);

        List<String> out = new ArrayList<>();
        for (Ep ep : eps) {
            out.add(ep.text + "$" + (ep.href.startsWith("http") ? ep.href : host + ep.href));
        }
        return TextUtils.join("#", out);
    }

    private static class Ep {
        String text;
        String href;
        int num;

        Ep(String t, String h, int n) {
            this.text = t;
            this.href = h;
            this.num = n;
        }
    }

    // ==================== 搜索 ====================

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        if (TextUtils.isEmpty(host)) host = "https://shdy2.com";
        String url = host + "/s----------.html?wd=" + URLEncoder.encode(key, "UTF-8");
        String html = request(url);
        JSONObject result = new JSONObject();
        if (TextUtils.isEmpty(html)) {
            result.put("list", new JSONArray());
            return result.toString();
        }
        result.put("list", parseList(html, 0));
        return result.toString();
    }

    // ==================== 播放 ====================

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(host)) host = "https://shdy2.com";
        String playPageUrl = id.startsWith("http") ? id : (host + id);

        // 1. 播放页直链
        String html = request(playPageUrl);
        if (!TextUtils.isEmpty(html)) {
            String direct = extractM3u8(html);
            if (!TextUtils.isEmpty(direct)) {
                return buildResult(0, direct, null);
            }
        }

        String hhUrl = extractHhUrl(html);
        if (TextUtils.isEmpty(hhUrl)) {
            return buildResult(0, playPageUrl, null);
        }

        // 2. HHPlayer 页
        String hhHtml = request(hhUrl, playPageUrl);
        if (!TextUtils.isEmpty(hhHtml)) {
            String direct = extractM3u8(hhHtml);
            if (!TextUtils.isEmpty(direct)) {
                return buildResult(0, direct, null);
            }
        }

        JSONObject boot = extractBootstrap(hhHtml);
        if (boot == null
                || TextUtils.isEmpty(boot.optString("url"))
                || TextUtils.isEmpty(boot.optString("key"))) {
            return buildResult(0, hhUrl, null);
        }

        // 3. POST /api/parse
        String hhDomain = "";
        Matcher dm = Pattern.compile("^https?://([^/]+)").matcher(hhUrl);
        if (dm.find()) hhDomain = dm.group(1);
        String apiUrl = "https://" + hhDomain + "/api/parse";

        JSONObject postBody = new JSONObject();
        postBody.put("url", boot.optString("url"));
        postBody.put("t", boot.optString("t"));
        postBody.put("key", boot.optString("key"));
        postBody.put("client_fallback", false);

        String respText = postJson(apiUrl, postBody, hhUrl);
        if (TextUtils.isEmpty(respText)) {
            return buildResult(0, hhUrl, null);
        }

        JSONObject resp = null;
        try {
            resp = new JSONObject(respText);
        } catch (Exception e) {
            String fb = extractM3u8(respText);
            if (!TextUtils.isEmpty(fb)) return buildResult(0, fb, null);
            return buildResult(0, hhUrl, null);
        }

        if (resp.optInt("code", 0) != 200 || TextUtils.isEmpty(resp.optString("url"))) {
            return buildResult(0, hhUrl, null);
        }

        String m3u8 = resp.optString("url")
                .replace("\\u0026", "&")
                .replace("\\/", "/");

        JSONObject headers = new JSONObject();
        headers.put("Referer", hhUrl);
        headers.put("User-Agent", UA);

        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("url", m3u8);
        result.put("jx", 0);
        result.put("headers", headers);
        return result.toString();
    }

    private String buildResult(int parse, String url, JSONObject headers) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", parse);
        result.put("url", url);
        result.put("jx", 0);
        if (headers != null) result.put("headers", headers);
        return result.toString();
    }

    // ==================== 解析工具 ====================

    private String extractHhUrl(String html) {
        if (TextUtils.isEmpty(html)) return "";
        Matcher m = Pattern.compile("<iframe[^>]+src=[\"'](https?://[^\"']+[?&]url=[A-Za-z0-9]+)[\"']",
                Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return m.group(1).replace("&amp;", "&");

        m = Pattern.compile("(https?://[^\"'\\s<>]+[?&]url=[A-Za-z0-9]+)").matcher(html);
        if (m.find()) return m.group(1).replace("&amp;", "&");
        return "";
    }

    private JSONObject extractBootstrap(String html) {
        if (TextUtils.isEmpty(html)) return null;
        Matcher m = Pattern.compile("__HHJX_BOOTSTRAP__\\s*=\\s*(\\{[^}]+})").matcher(html);
        if (!m.find()) return null;
        try {
            return new JSONObject(m.group(1));
        } catch (Exception e) {
            return null;
        }
    }

    private String extractM3u8(String text) {
        if (TextUtils.isEmpty(text)) return "";
        String[] patterns = {
                "\"url\"\\s*:\\s*\"(https?:[^\"]+?\\.m3u8[^\"]*)\"",
                "\"m3u8_url\"\\s*:\\s*\"(https?:[^\"]+?\\.m3u8[^\"]*)\"",
                "(https?:[^\"'\\s\\\\<>]+?\\.m3u8[^\"'\\s\\\\<>]*)"
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(text);
            if (m.find() && m.group(1) != null) {
                return m.group(1)
                        .replace("\\u0026", "&")
                        .replace("\\/", "/")
                        .replace("&amp;", "&");
            }
        }
        return "";
    }
}