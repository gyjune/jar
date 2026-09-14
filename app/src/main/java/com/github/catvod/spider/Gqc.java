package com.github.catvod.spider;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.okhttp.OkHttpUtil;

public class Gqc extends Spider {

    private static final String HOST = "https://gqc7.top";
    private static final String BAOFENG_API = "https://dm.baofeng.la/qcb.php";

    // ============================================================
    // 首页分类
    // ============================================================

    @Override
    public String homeContent(boolean filter) {
        try {
            JSONObject result = new JSONObject();
            JSONArray classes = new JSONArray();

            String[][] cats = {
                {"dianying", "电影"},
                {"lianxuju", "连续剧"},
                {"zongyi", "综艺"},
                {"dongman", "动漫"},
                {"duanju", "短剧"}
            };
            for (String[] c : cats) {
                JSONObject obj = new JSONObject();
                obj.put("type_id", c[0]);
                obj.put("type_name", c[1]);
                classes.put(obj);
            }

            result.put("class", classes);

            if (filter) {
                JSONObject filters = new JSONObject();
                filters.put("dianying", buildFilter());
                filters.put("lianxuju", buildFilter());
                filters.put("zongyi", buildFilter());
                filters.put("dongman", buildFilter());
                filters.put("duanju", buildFilter());
                result.put("filters", filters);
            }

            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    private JSONArray buildFilter() throws Exception {
        JSONArray arr = new JSONArray();

        // 地区
        JSONObject area = new JSONObject();
        area.put("key", "area");
        area.put("name", "地区");
        JSONArray areaVals = new JSONArray();
        String[][] areas = {
            {"", "全部"}, {"内地", "内地"}, {"中国香港", "中国香港"},
            {"中国台湾", "中国台湾"}, {"美国", "美国"}, {"韩国", "韩国"},
            {"日本", "日本"}, {"泰国", "泰国"}, {"英国", "英国"},
            {"法国", "法国"}, {"其它", "其它"}
        };
        for (String[] a : areas) {
            JSONObject v = new JSONObject();
            v.put("n", a[1]);
            v.put("v", a[0]);
            areaVals.put(v);
        }
        area.put("value", areaVals);
        arr.put(area);

        // 年份
        JSONObject year = new JSONObject();
        year.put("key", "year");
        year.put("name", "年份");
        JSONArray yearVals = new JSONArray();
        JSONObject y0 = new JSONObject();
        y0.put("n", "全部");
        y0.put("v", "");
        yearVals.put(y0);
        for (int y = 2026; y >= 2000; y--) {
            JSONObject v = new JSONObject();
            v.put("n", String.valueOf(y));
            v.put("v", String.valueOf(y));
            yearVals.put(v);
        }
        year.put("value", yearVals);
        arr.put(year);

        // 排序
        JSONObject by = new JSONObject();
        by.put("key", "by");
        by.put("name", "排序");
        JSONArray byVals = new JSONArray();
        String[][] bys = {{"time", "时间"}, {"hits", "人气"}, {"score", "评分"}};
        for (String[] b : bys) {
            JSONObject v = new JSONObject();
            v.put("n", b[1]);
            v.put("v", b[0]);
            byVals.put(v);
        }
        by.put("value", byVals);
        arr.put(by);

        return arr;
    }

    // ============================================================
    // 分类（Jsoup 解析）
    // ============================================================

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            int page = Integer.parseInt(pg);
            String area = (extend != null && extend.containsKey("area")) ? extend.get("area") : "";
            String year = (extend != null && extend.containsKey("year")) ? extend.get("year") : "";
            String by = (extend != null && extend.containsKey("by")) ? extend.get("by") : "";

            String url;
            if (page <= 1) {
                url = HOST + "/vodshow/" + tid + "-" + area + "-" + by + "---------" + year + ".html";
            } else {
                url = HOST + "/vodshow/" + tid + "-" + area + "-" + by + "------" + page + "---" + year + ".html";
            }

            SpiderDebug.log("category url: " + url);
            String res = OkHttpUtil.string(url, getHeaders());
            Elements listEl = Jsoup.parse(res).select("li.fed-list-item");

            JSONArray list = new JSONArray();
            for (int i = 0; i < listEl.size(); i++) {
                Element item = listEl.get(i);

                Element aPics = item.selectFirst("a.fed-list-pics");
                Element aTitle = item.selectFirst("a.fed-list-title");
                if (aPics == null || aTitle == null) continue;

                JSONObject vod = new JSONObject();
                vod.put("vod_id", aPics.attr("href"));
                vod.put("vod_name", aTitle.text().trim());
                vod.put("vod_pic", fixUrl(aPics.attr("data-original")));

                Element remark = item.selectFirst("span.fed-list-remarks");
                if (remark != null) vod.put("vod_remarks", remark.text().trim());

                list.put(vod);
            }

            JSONObject result = new JSONObject();
            result.put("page", page);
            result.put("pagecount", page + 1);
            result.put("limit", 48);
            result.put("total", 9999);
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    // ============================================================
    // 详情
    // ============================================================

    @Override
    public String detailContent(List<String> ids) {
        try {
            String id = ids.get(0);
            String url = id.startsWith("http") ? id : HOST + id;
            String res = OkHttpUtil.string(url, getHeaders());
            Document doc = Jsoup.parse(res);

            JSONObject info = new JSONObject();
            info.put("vod_id", id);

            Element h1 = doc.selectFirst("h1.fed-part-eone");
            if (h1 != null) info.put("vod_name", h1.text().trim());

            Element pic = doc.selectFirst("a.fed-list-pics");
            if (pic != null) info.put("vod_pic", fixUrl(pic.attr("data-original")));

            Element desc = doc.selectFirst("div.fed-part-esan");
            if (desc != null) info.put("vod_content", desc.text().trim());

            // 抓线路
            Elements lineEls = doc.select("a.fed-btns-info");
            List<String> froms = new ArrayList<>();
            List<String> urls = new ArrayList<>();

            for (Element line : lineEls) {
                String href = line.attr("href");
                if (!href.startsWith("/bofang/")) continue;

                Matcher m = Pattern.compile("/bofang/(\\d+)-(\\d+)-(\\d+)\\.html").matcher(href);
                if (!m.find()) continue;

                String vid = m.group(1);
                String sid = m.group(2);

                Element span = line.selectFirst("span");
                int count = 1;
                if (span != null) {
                    try { count = Integer.parseInt(span.text().trim()); } catch (Exception e) {}
                }

                String name = line.text();
                if (span != null && !span.text().isEmpty()) {
                    name = name.replace(span.text(), "").trim();
                }

                List<String> eps = new ArrayList<>();
                for (int n = 1; n <= count; n++) {
                    eps.add("第" + n + "集$" + HOST + "/bofang/" + vid + "-" + sid + "-" + n + ".html");
                }
                froms.add(name);
                urls.add(TextUtils.join("#", eps));
            }

            info.put("vod_play_from", TextUtils.join("$$$", froms));
            info.put("vod_play_url", TextUtils.join("$$$", urls));

            JSONObject result = new JSONObject();
            JSONArray listInfo = new JSONArray();
            listInfo.put(info);
            result.put("list", listInfo);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    // ============================================================
    // 搜索
    // ============================================================

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            String url = HOST + "/api.php/provide/vod/?ac=detail&wd=" + java.net.URLEncoder.encode(key, "UTF-8");
            String res = OkHttpUtil.string(url, getHeaders());
            JSONObject j = new JSONObject(res);

            JSONArray list = new JSONArray();
            if (j.has("list")) {
                JSONArray arr = j.getJSONArray("list");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    JSONObject v = new JSONObject();
                    v.put("vod_id", "/neirong/" + o.optString("vod_id") + ".html");
                    v.put("vod_name", o.optString("vod_name"));
                    v.put("vod_pic", o.optString("vod_pic"));
                    v.put("vod_remarks", o.optString("vod_remarks"));
                    list.put(v);
                }
            }

            JSONObject result = new JSONObject();
            result.put("list", list);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return "";
    }

    // ============================================================
    // 播放（先暴风，失败走苹果CMS兜底）
    // ============================================================

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            Matcher m = Pattern.compile("/bofang/(\\d+)-(\\d+)-(\\d+)\\.html").matcher(id);
            if (!m.find()) return emptyPlay();

            String vid = m.group(1);
            String nid = m.group(3);

            String pageUrl = id.startsWith("http") ? id : HOST + id;
            String html = OkHttpUtil.string(pageUrl, getHeaders());

            Matcher im = Pattern.compile("<iframe[^>]*id=\"fed-play-iframe\"[^>]*>").matcher(html);
            if (!im.find()) return emptyPlay();

            String tag = im.group();
            Matcher pm = Pattern.compile("data-play=\"([^\"]+)\"").matcher(tag);
            String playUrl = pm.find() ? pm.group(1).replace("&amp;", "&").trim() : "";

            if (playUrl.isEmpty()) return emptyPlay();
            if (playUrl.contains(".m3u8")) return playResult(playUrl);

            // ① 暴风直连
            try {
                String bf = BAOFENG_API + "?url=" + playUrl;
                String bfResp = OkHttpUtil.string(bf, getHeaders());
                JSONObject j = new JSONObject(bfResp);
                if (j.has("url")) {
                    String m3u8 = j.getString("url");
                    HashMap<String, String> m3u8Headers = new HashMap<>();
                    m3u8Headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                    m3u8Headers.put("Referer", "https://dm.baofeng.la/");
                    String check = OkHttpUtil.string(m3u8, m3u8Headers);
                    if (check != null && check.startsWith("#EXTM3U")) {
                        SpiderDebug.log("暴风直连成功: " + m3u8);
                        return playResult(m3u8);
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log("暴风失败: " + e.getMessage());
            }

            // ② 苹果CMS 兜底
            try {
                String api = HOST + "/api.php/provide/vod/?ac=detail&ids=" + vid;
                String resp = OkHttpUtil.string(api, getHeaders());
                JSONObject j = new JSONObject(resp);
                if (j.has("list") && j.getJSONArray("list").length() > 0) {
                    JSONObject v = j.getJSONArray("list").getJSONObject(0);
                    String urlStr = v.getString("vod_play_url");
                    String[] eps = urlStr.split("#");
                    int n = Integer.parseInt(nid) - 1;
                    if (n >= 0 && n < eps.length) {
                        String[] parts = eps[n].split("\\$");
                        String m3u8 = parts.length > 1 ? parts[1] : parts[0];
                        if (m3u8.contains(".m3u8")) {
                            SpiderDebug.log("苹果CMS兜底成功: " + m3u8);
                            return playResult(m3u8);
                        }
                    }
                }
            } catch (Exception e) {
                SpiderDebug.log("苹果CMS失败: " + e.getMessage());
            }
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return emptyPlay();
    }

    // ============================================================
    // 工具
    // ============================================================

    private String playResult(String m3u8) {
        try {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", m3u8);

            JSONObject header = new JSONObject();
            header.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            header.put("Referer", HOST + "/");
            result.put("header", header);
            return result.toString();
        } catch (Exception e) {
            SpiderDebug.log(e);
        }
        return emptyPlay();
    }

    private String emptyPlay() {
        try {
            JSONObject result = new JSONObject();
            result.put("parse", 0);
            result.put("playUrl", "");
            result.put("url", "");
            result.put("header", "");
            return result.toString();
        } catch (Exception e) {
        }
        return "";
    }

    private String fixUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("http")) return url;
        if (url.startsWith("/")) return HOST + url;
        return HOST + "/" + url;
    }

    private HashMap<String, String> getHeaders() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.put("Referer", HOST + "/");
        return headers;
    }
}