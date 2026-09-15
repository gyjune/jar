package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WangWang extends Spider {

    private String siteUrl = "https://vip.wwgz.cn:5200";

    private static final String UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 13_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0.5 Mobile/15E148 Snapchat/10.77.5.59 (like Safari/604.1)";

    // ============================================================
    // init：读取 extend（站点域名）
    // ============================================================
    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (!TextUtils.isEmpty(extend) && extend.startsWith("http")) {
            siteUrl = extend;
        }
        if (siteUrl.endsWith("/")) siteUrl = siteUrl.substring(0, siteUrl.length() - 1);
    }

    // ============================================================
    // header
    // ============================================================
    private Map<String, String> headers(String referer) {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        h.put("accept-language", "zh-CN,zh;q=0.9");
        h.put("cache-control", "no-cache");
        h.put("pragma", "no-cache");
        h.put("upgrade-insecure-requests", "1");
        h.put("Referer", TextUtils.isEmpty(referer) ? siteUrl + "/" : referer);
        return h;
    }

    // ============================================================
    // fetch
    // ============================================================
    private String fetch(String url, String referer) {
        try {
            return OkHttpUtil.string(url, headers(referer));
        } catch (Exception e) {
            SpiderDebug.log("fetch error: " + e.getMessage());
            return "";
        }
    }

    // ============================================================
    // fixUrl
    // ============================================================
    private String fixUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        if (url.startsWith("//")) return "http:" + url;
        if (url.startsWith("/")) return siteUrl + url;
        return url;
    }

    // ============================================================
    // parse list
    // ============================================================
    private JSONArray parseList(String html) throws Exception {
        JSONArray list = new JSONArray();
        if (TextUtils.isEmpty(html)) return list;

        Document doc = Jsoup.parse(html);
        Elements items = doc.select(".globalPicList li, .resize_list li");
        for (Element item : items) {
            Element a = item.selectFirst("a");
            if (a == null) continue;
            String href = a.attr("href");
            if (TextUtils.isEmpty(href) || !href.contains("vod-detail-id")) continue;

            String pic = a.select("img").attr("data-echo");
            if (TextUtils.isEmpty(pic)) pic = a.select("img").attr("src");

            String name = a.attr("title");
            if (TextUtils.isEmpty(name)) name = a.select(".sTit").text().trim();

            String remark = a.select(".sBottom span").text().replaceAll("<em>.*</em>", "").trim();
            if (TextUtils.isEmpty(remark)) remark = a.select(".covericon").text().trim();

            JSONObject vod = new JSONObject();
            vod.put("vod_id", href);
            vod.put("vod_name", name);
            vod.put("vod_pic", fixUrl(pic));
            vod.put("vod_remarks", remark);
            list.put(vod);
        }
        return list;
    }

    // ============================================================
    // homeContent
    // ============================================================
    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();

        JSONArray classes = new JSONArray();
        String[][] classesConfig = {
                {"1", "电影"}, {"2", "连续剧"}, {"3", "综艺"}, {"4", "动漫"}, {"26", "短剧"}
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

        try {
            String html = fetch(siteUrl + "/", null);
            result.put("list", parseList(html));
        } catch (Exception e) {
            SpiderDebug.log(e);
            result.put("list", new JSONArray());
        }

        return result.toString();
    }

    // ============================================================
    // buildFilters
    // ============================================================
    private JSONObject buildFilters() throws Exception {
        JSONObject filters = new JSONObject();

        JSONArray areaValues = new JSONArray();
        areaValues.put(filterValue("全部", ""));
        String[] areas = {"大陆", "香港", "台湾", "美国", "韩国", "日本", "泰国",
                "新加坡", "马来西亚", "印度", "英国", "法国", "加拿大", "西班牙", "俄罗斯", "其它"};
        for (String a : areas) areaValues.put(filterValue(a, a));

        JSONArray yearValues = new JSONArray();
        yearValues.put(filterValue("全部", ""));
        for (int y = 2026; y >= 1900; y--) {
            yearValues.put(filterValue(String.valueOf(y), String.valueOf(y)));
        }

        JSONArray sortValues = new JSONArray();
        sortValues.put(filterValue("全部", "time"));
        sortValues.put(filterValue("人气", "hits"));
        sortValues.put(filterValue("评分", "score"));

        JSONArray movieClass = new JSONArray();
        movieClass.put(filterValue("全部", ""));
        movieClass.put(filterValue("动作片", "5"));
        movieClass.put(filterValue("喜剧片", "6"));
        movieClass.put(filterValue("爱情片", "7"));
        movieClass.put(filterValue("科幻片", "8"));
        movieClass.put(filterValue("恐怖片", "9"));
        movieClass.put(filterValue("剧情片", "10"));
        movieClass.put(filterValue("战争片", "11"));
        movieClass.put(filterValue("惊悚片", "16"));
        movieClass.put(filterValue("奇幻片", "17"));

        JSONArray tvClass = new JSONArray();
        tvClass.put(filterValue("全部", ""));
        tvClass.put(filterValue("国产剧", "12"));
        tvClass.put(filterValue("港台泰", "13"));
        tvClass.put(filterValue("日韩剧", "14"));
        tvClass.put(filterValue("欧美剧", "15"));

        JSONArray varietyClass = new JSONArray();
        varietyClass.put(filterValue("全部", ""));

        JSONArray animeClass = new JSONArray();
        animeClass.put(filterValue("全部", ""));
        animeClass.put(filterValue("动漫剧", "18"));

        JSONArray shortClass = new JSONArray();
        shortClass.put(filterValue("全部", ""));

        JSONArray movieFilters = new JSONArray();
        movieFilters.put(filterGroup("id", "类型", movieClass));
        movieFilters.put(filterGroup("area", "地区", areaValues));
        movieFilters.put(filterGroup("year", "年份", yearValues));
        movieFilters.put(filterGroup("by", "排序", sortValues));
        filters.put("1", movieFilters);

        JSONArray tvFilters = new JSONArray();
        tvFilters.put(filterGroup("id", "类型", tvClass));
        tvFilters.put(filterGroup("area", "地区", areaValues));
        tvFilters.put(filterGroup("year", "年份", yearValues));
        tvFilters.put(filterGroup("by", "排序", sortValues));
        filters.put("2", tvFilters);

        JSONArray varietyFilters = new JSONArray();
        varietyFilters.put(filterGroup("id", "类型", varietyClass));
        varietyFilters.put(filterGroup("area", "地区", areaValues));
        varietyFilters.put(filterGroup("year", "年份", yearValues));
        varietyFilters.put(filterGroup("by", "排序", sortValues));
        filters.put("3", varietyFilters);

        JSONArray animeFilters = new JSONArray();
        animeFilters.put(filterGroup("id", "类型", animeClass));
        animeFilters.put(filterGroup("area", "地区", areaValues));
        animeFilters.put(filterGroup("year", "年份", yearValues));
        animeFilters.put(filterGroup("by", "排序", sortValues));
        filters.put("4", animeFilters);

        JSONArray shortFilters = new JSONArray();
        shortFilters.put(filterGroup("id", "类型", shortClass));
        shortFilters.put(filterGroup("area", "地区", areaValues));
        shortFilters.put(filterGroup("year", "年份", yearValues));
        shortFilters.put(filterGroup("by", "排序", sortValues));
        filters.put("26", shortFilters);

        return filters;
    }

    // ============================================================
    // categoryContent
    // ============================================================
    @Override
    public String categoryContent(String tid, String pg, boolean filter,
                                  HashMap<String, String> extend) throws Exception {
        int page = parseIntSafe(pg, 1);

        String id = tid;
        String area = "";
        String year = "";
        String by = "time";
        String clazz = "";
        String letter = "";
        String lang = "";

        if (extend != null) {
            if (!TextUtils.isEmpty(extend.get("id"))) id = extractDigits(extend.get("id"));
            if (extend.get("area") != null) area = extend.get("area");
            if (extend.get("year") != null) year = extend.get("year");
            if (extend.get("by") != null) by = extend.get("by");
            if (extend.get("class") != null) clazz = extend.get("class");
            if (extend.get("letter") != null) letter = extend.get("letter");
            if (extend.get("lang") != null) lang = extend.get("lang");
        }
        if (TextUtils.isEmpty(id)) id = extractDigits(tid);
        if (TextUtils.isEmpty(id)) id = "1";

        String url = siteUrl + "/index.php?m=vod-list-id-" + id
                + "-pg-" + page
                + "-order--by-" + (TextUtils.isEmpty(by) ? "time" : by)
                + "-class-" + clazz
                + "-year-" + year
                + "-letter-" + letter
                + "-area-" + URLEncoder.encode(area, "UTF-8")
                + "-lang-" + URLEncoder.encode(lang, "UTF-8")
                + ".html";

        SpiderDebug.log("category url: " + url);

        String html = fetch(url, null);
        JSONArray list = parseList(html);

        JSONObject result = new JSONObject();
        result.put("page", page);
        result.put("pagecount", list.length() > 0 ? page + 1 : 1);
        result.put("list", list);
        return result.toString();
    }

    // ============================================================
    // searchContent
    // ============================================================
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        try {
            String url = siteUrl + "/index.php?m=vod-search";
            Map<String, String> h = headers(siteUrl + "/vod-search");

            Map<String, String> form = new HashMap<>();
            form.put("wd", key);

            String html = postForm(url, form, h);
            Document doc = Jsoup.parse(html);

            JSONArray list = new JSONArray();
            Elements items = doc.select("#data_list li");
            for (Element item : items) {
                Element a = item.selectFirst(".pic a");
                if (a == null) continue;
                String href = a.attr("href");
                if (TextUtils.isEmpty(href) || !href.contains("vod-detail-id")) continue;

                String pic = a.select("img").attr("data-src");
                if (TextUtils.isEmpty(pic)) pic = a.select("img").attr("src");

                String name = item.select(".txt .sTit").text().trim();
                String remark = item.select(".pic .sStyle").text().trim();

                String score = item.select(".txt .sDes:contains(评分)").text().replaceAll("评分[：:]?\\s*", "").trim();
                String actor = item.select(".txt .sDes:contains(主演)").text().replaceAll("主演[：:]?\\s*", "").trim();

                StringBuilder sb = new StringBuilder();
                if (!TextUtils.isEmpty(remark)) sb.append(remark);
                if (!TextUtils.isEmpty(score)) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append(score).append("分");
                }
                if (!TextUtils.isEmpty(actor)) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append(actor);
                }

                JSONObject vod = new JSONObject();
                vod.put("vod_id", href);
                vod.put("vod_name", name);
                vod.put("vod_pic", fixUrl(pic));
                vod.put("vod_remarks", sb.toString());
                list.put(vod);
            }

            JSONObject result = new JSONObject();
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
    // detailContent
    // ============================================================
    @Override
    public String detailContent(List<String> ids) throws Exception {
        try {
            String id = ids.get(0);
            String url = id.startsWith("http") ? id : siteUrl + id;

            String html = fetch(url, null);
            Document doc = Jsoup.parse(html);

            JSONObject vod = new JSONObject();
            vod.put("vod_id", id);
            vod.put("vod_name", doc.select(".page-bd h1.title").text().trim());
            vod.put("vod_pic", fixUrl(doc.select(".page-hd img").attr("src")));
            vod.put("vod_year", doc.select(".desc_item:contains(年代:) a").text().trim());
            vod.put("vod_remarks", doc.select(".desc_item:contains(状态:) font").text().trim());

            Elements actorEls = doc.select(".desc_item:contains(主演:) a");
            StringBuilder actors = new StringBuilder();
            for (Element el : actorEls) {
                if (actors.length() > 0) actors.append(" ");
                actors.append(el.text());
            }
            vod.put("vod_actor", actors.toString().trim());

            String content = doc.select(".detail-con p").text().replaceAll("简[\\s\\S]*?介[：:]\\s*", "").trim();
            vod.put("vod_content", content);

            String playFrom = "";
            String playUrl = "";

            Element greenBtn = doc.selectFirst(".page-btn a.greenBtn");
            if (greenBtn != null) {
                String btnHref = greenBtn.attr("href");
                if (!TextUtils.isEmpty(btnHref)) {
                    String fullHref = btnHref.startsWith("http") ? btnHref : siteUrl + btnHref;
                    String btnHtml = fetch(fullHref, url);

                    Matcher mf = Pattern.compile("mac_from='([^']+)'").matcher(btnHtml);
                    if (mf.find()) playFrom = mf.group(1);

                    Matcher mu = Pattern.compile("mac_url='([^']+)'").matcher(btnHtml);
                    if (mu.find()) playUrl = mu.group(1);

                    if (!TextUtils.isEmpty(playUrl)) {
                        try {
                            playUrl = unescape(playUrl);
                        } catch (Exception ignored) {}
                    }
                }
            }

            vod.put("vod_play_from", playFrom);
            vod.put("vod_play_url", playUrl);

            JSONObject result = new JSONObject();
            JSONArray list = new JSONArray();
            list.put(vod);
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
    // playerContent
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        try {
            int parse = 0;

            String decoded = decodeMacUrl(id);
            SpiderDebug.log("play decoded=" + decoded);

            if (TextUtils.isEmpty(decoded) || !decoded.startsWith("http")) {
                String proxyApi = "https://api.nmvod.me:520/player/?url=";
                String resp = fetch(proxyApi + id, null);
                Matcher m = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(resp);
                if (m.find()) {
                    decoded = m.group(1).replace("\\/", "/");
                } else {
                    decoded = proxyApi + id;
                    parse = 1;
                }
            }

            JSONObject result = new JSONObject();
            result.put("parse", parse);
            result.put("url", decoded);

            JSONObject h = new JSONObject();
            h.put("User-Agent", UA);
            result.put("header", h.toString());

            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("url", id);
            return result.toString();
        }
    }

    // ============================================================
    // ★ 自定义 base64 解码（对齐 JS Crypto.enc.Base64.parse）
    // ============================================================
    private String decodeMacUrl(String n) {
        try {
            if (TextUtils.isEmpty(n)) return "";
            n = n.replaceAll("#+$", "");
            if (n.length() < 66) return "";

            String e = n.substring(0, 66);
            String t = n.substring(66);

            // 前 66 字符隔位取
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < e.length(); i += 2) {
                sb.append(e.charAt(i));
            }

            // 剩余部分替换自定义 token
            String a = sb.toString()
                    + t.replace("O0O0O", "=")
                       .replace("oo00o", "/")
                       .replace("o000o", "+");

            // ★ 清理非 base64 字符（JS parse 会忽略）
            a = a.replaceAll("[^A-Za-z0-9+/=]", "");

            // ★ 补 padding 到 4 的倍数（JS parse 会自动补）
            int mod = a.length() % 4;
            if (mod == 2) a += "==";
            else if (mod == 3) a += "=";

            byte[] bytes = Base64.decode(a, Base64.DEFAULT);
            return new String(bytes, "UTF-8");
        } catch (Exception ex) {
            SpiderDebug.log("decodeMacUrl error: " + ex.getMessage());
            return "";
        }
    }

    // ============================================================
    // unescape
    // ============================================================
    private String unescape(String s) {
        if (TextUtils.isEmpty(s)) return "";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%' && i + 5 < s.length() && s.charAt(i + 1) == 'u') {
                try {
                    String hex = s.substring(i + 2, i + 6);
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 6;
                } catch (Exception e) {
                    sb.append(c);
                    i++;
                }
            } else if (c == '%' && i + 2 < s.length()) {
                try {
                    String hex = s.substring(i + 1, i + 3);
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 3;
                } catch (Exception e) {
                    sb.append(c);
                    i++;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    // ============================================================
    // POST form
    // ============================================================
    private String postForm(String url, Map<String, String> form, Map<String, String> headers) {
        try {
            okhttp3.FormBody.Builder fb = new okhttp3.FormBody.Builder();
            for (Map.Entry<String, String> e : form.entrySet()) {
                fb.add(e.getKey(), e.getValue());
            }
            okhttp3.Request.Builder rb = new okhttp3.Request.Builder().url(url).post(fb.build());
            for (Map.Entry<String, String> e : headers.entrySet()) {
                rb.addHeader(e.getKey(), e.getValue());
            }
            okhttp3.Response resp = OkHttpUtil.defaultClient().newCall(rb.build()).execute();
            if (!resp.isSuccessful() || resp.body() == null) return "";
            byte[] bytes = resp.body().bytes();
            resp.close();
            return new String(bytes, "UTF-8");
        } catch (Exception e) {
            SpiderDebug.log("postForm error: " + e.getMessage());
            return "";
        }
    }

    // ============================================================
    // 工具
    // ============================================================
    private String extractDigits(String s) {
        if (TextUtils.isEmpty(s)) return "";
        Matcher m = Pattern.compile("\\d+").matcher(s);
        return m.find() ? m.group() : "";
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