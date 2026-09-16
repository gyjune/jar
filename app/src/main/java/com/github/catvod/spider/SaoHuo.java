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
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 骚火影视 SaoHuo
 * 参考反编译版逻辑：
 *  - 分类 URL: /list/{cateId}-{page}.html
 *  - cookie: 从首页 Set-Cookie 获取
 *  - 播放: 取 iframe.src, parse=1 交给嗅探器
 */
public class SaoHuo extends Spider {

    private String host = "https://shdy2.com";
    private String cookie = "";

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 9; ALN-AL00 Build/PQ3B.190801.05281406; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/91.0.4472.114 Safari/537.36";

    // ==================== 请求 ====================

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("accept-language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
        if (!TextUtils.isEmpty(cookie)) h.put("Cookie", cookie);
        return h;
    }

    private Map<String, String> headers(String referer) {
        Map<String, String> h = headers();
        if (!TextUtils.isEmpty(referer)) h.put("Referer", referer);
        return h;
    }

    private String request(String url) {
        return request(url, null);
    }

    private String request(String url, String referer) {
        try {
            Request.Builder builder = new Request.Builder().url(url).get();
            for (Map.Entry<String, String> e : headers(referer).entrySet()) {
                builder.addHeader(e.getKey(), e.getValue());
            }
            return execute(builder.build());
        } catch (Exception e) {
            return "";
        }
    }

    private String execute(Request request) {
        Response response = null;
        try {
            OkHttpClient client = OkHttpUtil.defaultClient();
            response = client.newCall(request).execute();

            List<String> setCookies = response.headers("Set-Cookie");
            if (setCookies != null && !setCookies.isEmpty()) {
                Map<String, String> map = parseCookie(cookie);
                for (String c : setCookies) {
                    int end = c.indexOf(';');
                    if (end > 0) c = c.substring(0, end);
                    int eq = c.indexOf('=');
                    if (eq > 0) map.put(c.substring(0, eq).trim(), c.substring(eq + 1).trim());
                }
                cookie = joinCookie(map);
            }

            if (response.body() == null) return "";
            byte[] bytes = response.body().bytes();
            return new String(bytes, "UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            if (response != null) response.close();
        }
    }

    private Map<String, String> parseCookie(String c) {
        Map<String, String> map = new HashMap<>();
        if (TextUtils.isEmpty(c)) return map;
        for (String part : c.split(";")) {
            part = part.trim();
            int eq = part.indexOf('=');
            if (eq > 0) map.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
        }
        return map;
    }

    private String joinCookie(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    // ==================== init ====================

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (!TextUtils.isEmpty(extend)) host = extend.trim();
        request(host);
    }

    // ==================== 首页 ====================

    @Override
    public String homeContent(boolean filter) throws Exception {
        if (TextUtils.isEmpty(cookie)) request(host);

        JSONArray classes = new JSONArray();
        classes.put(clazz("1", "电影"));
        classes.put(clazz("2", "电视剧"));
        classes.put(clazz("20", "国产剧"));
        classes.put(clazz("4", "动漫"));

        JSONObject filters = new JSONObject();
        filters.put("1", arr(filterItem("cateId", "类型", new String[][]{
                {"全部", "1"}, {"喜剧", "6"}, {"爱情", "7"}, {"恐怖", "8"},
                {"动作", "9"}, {"科幻", "10"}, {"战争", "11"}, {"犯罪", "12"},
                {"动画", "13"}, {"奇幻", "14"}, {"剧情", "15"}, {"冒险", "16"},
                {"悬疑", "17"}, {"惊悚", "18"}, {"其他", "20"}
        })));
        filters.put("2", arr(filterItem("cateId", "类型", new String[][]{
                {"全部", "2"}, {"国产剧", "20"}, {"TVB", "21"}, {"韩剧", "22"},
                {"美剧", "23"}, {"日剧", "24"}, {"英剧", "25"}, {"台剧", "26"},
                {"其他", "27"}
        })));
        filters.put("4", arr(filterItem("cateId", "类型", new String[][]{
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
        if (TextUtils.isEmpty(cookie)) request(host);
        String html = request(host);
        JSONObject result = new JSONObject();
        if (TextUtils.isEmpty(html)) {
            result.put("list", new JSONArray());
            return result.toString();
        }
        result.put("list", parseList(html, 6));
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
                Element img0 = a.selectFirst("img");
                if (img0 != null) title = img0.attr("alt");
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
        if (TextUtils.isEmpty(cookie)) request(host);

        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
        if (page < 1) page = 1;

        String cateId = (extend != null && !TextUtils.isEmpty(extend.get("cateId")))
                ? extend.get("cateId") : tid;

        String url = host + String.format("/list/%s-%s.html", cateId, page);

        String html = request(url);
        JSONArray list = parseList(html, 0);

        int pagecount = page;
        Document doc = Jsoup.parse(html == null ? "" : html);
        Elements pageLinks = doc.select(".page a, .pagination a, #page a, .pages a");
        if (!pageLinks.isEmpty()) {
            int maxPage = 0;
            Pattern p = Pattern.compile("-?(\\d+)\\.html");
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
        if (TextUtils.isEmpty(cookie)) request(host);
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

        JSONArray jsonArray = new JSONArray();
        jsonArray.put(vod);
        JSONObject result = new JSONObject();
        result.put("list", jsonArray);
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
        String text, href;
        int num;
        Ep(String t, String h, int n) { text = t; href = h; num = n; }
    }

    // ==================== 搜索 ====================

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        if (TextUtils.isEmpty(cookie)) request(host);
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

    // ==================== 播放（照反编译版：只取 iframe.src，parse=1） ====================

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (TextUtils.isEmpty(cookie)) request(host);
        String playPageUrl = id.startsWith("http") ? id : (host + id);

        String html = request(playPageUrl);

        String iframeSrc = "";
        if (!TextUtils.isEmpty(html)) {
            Document doc = Jsoup.parse(html);
            Element iframe = doc.selectFirst("iframe");
            if (iframe != null) iframeSrc = iframe.attr("src");
        }
        if (TextUtils.isEmpty(iframeSrc)) iframeSrc = playPageUrl;

        JSONObject headerObj = new JSONObject();
        headerObj.put("User-Agent", UA);
        headerObj.put("Referer", playPageUrl);
        if (!TextUtils.isEmpty(cookie)) headerObj.put("Cookie", cookie);

        JSONObject result = new JSONObject();
        result.put("parse", 1);
        result.put("jx", 0);
        result.put("url", iframeSrc);
        result.put("header", headerObj.toString());
        return result.toString();
    }
}