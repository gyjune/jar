package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.okhttp.OkHttpUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Douban extends Spider {

    private static final String API_HOST = "https://frodo.douban.com";
    private static final String API_KEY = "0ac44ae016490db2204ce0a042db2916";
    private static final int COUNT = 30;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat";

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Host", "frodo.douban.com");
        h.put("Connection", "Keep-Alive");
        h.put("Referer", "https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html");
        h.put("User-Agent", USER_AGENT);
        h.put("Accept", "application/json, text/plain, */*");
        h.put("Accept-Language", "zh-CN,zh;q=0.9");
        return h;
    }

    // ============================================================
    // fetch / api_request
    // ============================================================
    private JSONObject fetch(String url) {
        try {
            String body = OkHttpUtil.string(url, headers());
            if (TextUtils.isEmpty(body)) return null;
            return new JSONObject(body);
        } catch (Exception e) {
            SpiderDebug.log("fetch error: " + e.getMessage());
            return null;
        }
    }

    private JSONObject apiRequest(String path, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(API_HOST).append(path).append("?apikey=").append(API_KEY);
        if (params != null) {
            for (Map.Entry<String, String> e : params.entrySet()) {
                sb.append("&").append(e.getKey()).append("=").append(e.getValue());
            }
        }
        return fetch(sb.toString());
    }

    // ============================================================
    // parse_item
    // ============================================================
    private JSONObject parseItem(JSONObject item) throws Exception {
        String type = item.optString("type", "");
        if (!"movie".equals(type) && !"tv".equals(type)) return null;

        String title = item.optString("title", "未知");

        // 评分
        String score = "";
        JSONObject rating = item.optJSONObject("rating");
        if (rating != null) score = rating.optString("value", "");

        // 图片
        String picUrl = "";
        JSONObject pic = item.optJSONObject("pic");
        if (pic != null) picUrl = pic.optString("normal", "");
        if (!TextUtils.isEmpty(picUrl)) {
            if (picUrl.startsWith("//")) {
                picUrl = "https:" + picUrl;
            } else if (!picUrl.startsWith("http://") && !picUrl.startsWith("https://")) {
                picUrl = "https://" + picUrl.replaceFirst("^/+", "");
            }
        }

        JSONObject vod = new JSONObject();
        vod.put("vod_id", "msearch:" + title);
        vod.put("vod_name", title);
        vod.put("vod_pic", picUrl);
        vod.put("vod_remarks", TextUtils.isEmpty(score) ? "" : score + "分");
        return vod;
    }

    // ============================================================
    // filters：写死在代码里
    // ============================================================
    private JSONObject buildFilters() throws Exception {
        JSONObject filters = new JSONObject();

        // hot_gaia
        JSONArray hotGaia = new JSONArray();
        hotGaia.put(filterGroup("sort", "排序", new String[][]{
                {"热度", "recommend"}, {"最新", "time"}, {"评分", "rank"}
        }));
        hotGaia.put(filterGroup("area", "地区", new String[][]{
                {"全部", "全部"}, {"华语", "华语"}, {"欧美", "欧美"},
                {"韩国", "韩国"}, {"日本", "日本"}
        }));
        filters.put("hot_gaia", hotGaia);

        // tv_hot
        JSONArray tvHot = new JSONArray();
        tvHot.put(filterGroup("type", "分类", new String[][]{
                {"综合", "tv_hot"}, {"国产剧", "tv_domestic"}, {"欧美剧", "tv_american"},
                {"日剧", "tv_japanese"}, {"韩剧", "tv_korean"}, {"动画", "tv_animation"}
        }));
        filters.put("tv_hot", tvHot);

        // show_hot
        JSONArray showHot = new JSONArray();
        showHot.put(filterGroup("type", "分类", new String[][]{
                {"综合", "show_hot"}, {"国内", "show_domestic"}, {"国外", "show_foreign"}
        }));
        filters.put("show_hot", showHot);

        // movie
        JSONArray movieFilters = new JSONArray();
        movieFilters.put(filterGroup("type", "类型", new String[][]{
                {"全部类型", ""}, {"喜剧", "喜剧"}, {"爱情", "爱情"}, {"动作", "动作"},
                {"科幻", "科幻"}, {"动画", "动画"}, {"悬疑", "悬疑"}, {"犯罪", "犯罪"},
                {"惊悚", "惊悚"}, {"冒险", "冒险"}, {"音乐", "音乐"}, {"历史", "历史"},
                {"奇幻", "奇幻"}, {"恐怖", "恐怖"}, {"战争", "战争"}, {"传记", "传记"},
                {"歌舞", "歌舞"}, {"武侠", "武侠"}, {"情色", "情色"}, {"灾难", "灾难"},
                {"西部", "西部"}, {"纪录片", "纪录片"}, {"短片", "短片"}
        }));
        movieFilters.put(filterGroup("area", "地区", new String[][]{
                {"全部地区", ""}, {"华语", "华语"}, {"欧美", "欧美"}, {"韩国", "韩国"},
                {"日本", "日本"}, {"中国大陆", "中国大陆"}, {"美国", "美国"},
                {"中国香港", "中国香港"}, {"中国台湾", "中国台湾"}, {"英国", "英国"},
                {"法国", "法国"}, {"德国", "德国"}, {"意大利", "意大利"},
                {"西班牙", "西班牙"}, {"印度", "印度"}, {"泰国", "泰国"},
                {"俄罗斯", "俄罗斯"}, {"加拿大", "加拿大"}, {"澳大利亚", "澳大利亚"},
                {"爱尔兰", "爱尔兰"}, {"瑞典", "瑞典"}, {"巴西", "巴西"}, {"丹麦", "丹麦"}
        }));
        movieFilters.put(filterGroup("sort", "排序", new String[][]{
                {"近期热度", "T"}, {"首映时间", "R"}, {"高分优先", "S"}
        }));
        movieFilters.put(filterGroup("year", "年代", buildYearValues()));
        filters.put("movie", movieFilters);

        // tv
        JSONArray tvFilters = new JSONArray();
        tvFilters.put(filterGroup("type", "类型", new String[][]{
                {"不限", ""}, {"电视剧", "电视剧"}, {"综艺", "综艺"}
        }));
        tvFilters.put(filterGroup("tv_type", "电视剧形式", new String[][]{
                {"不限", ""}, {"喜剧", "喜剧"}, {"爱情", "爱情"}, {"悬疑", "悬疑"},
                {"动画", "动画"}, {"武侠", "武侠"}, {"古装", "古装"}, {"家庭", "家庭"},
                {"犯罪", "犯罪"}, {"科幻", "科幻"}, {"恐怖", "恐怖"}, {"历史", "历史"},
                {"战争", "战争"}, {"动作", "动作"}, {"冒险", "冒险"}, {"传记", "传记"},
                {"剧情", "剧情"}, {"奇幻", "奇幻"}, {"惊悚", "惊悚"}, {"灾难", "灾难"},
                {"歌舞", "歌舞"}, {"音乐", "音乐"}
        }));
        tvFilters.put(filterGroup("show_type", "综艺形式", new String[][]{
                {"不限", ""}, {"真人秀", "真人秀"}, {"脱口秀", "脱口秀"},
                {"音乐", "音乐"}, {"歌舞", "歌舞"}
        }));
        tvFilters.put(filterGroup("area", "地区", new String[][]{
                {"全部地区", ""}, {"华语", "华语"}, {"欧美", "欧美"}, {"国外", "国外"},
                {"韩国", "韩国"}, {"日本", "日本"}, {"中国大陆", "中国大陆"},
                {"中国香港", "中国香港"}, {"美国", "美国"}, {"英国", "英国"},
                {"泰国", "泰国"}, {"中国台湾", "中国台湾"}, {"意大利", "意大利"},
                {"法国", "法国"}, {"德国", "德国"}, {"西班牙", "西班牙"},
                {"俄罗斯", "俄罗斯"}, {"瑞典", "瑞典"}, {"巴西", "巴西"},
                {"丹麦", "丹麦"}, {"印度", "印度"}, {"加拿大", "加拿大"},
                {"爱尔兰", "爱尔兰"}, {"澳大利亚", "澳大利亚"}
        }));
        tvFilters.put(filterGroup("sort", "排序", new String[][]{
                {"近期热度", "T"}, {"首播时间", "R"}, {"高分优先", "S"}
        }));
        tvFilters.put(filterGroup("year", "年代", buildYearValues()));
        tvFilters.put(filterGroup("platform", "平台", new String[][]{
                {"全部", ""}, {"腾讯视频", "腾讯视频"}, {"爱奇艺", "爱奇艺"},
                {"优酷", "优酷"}, {"湖南卫视", "湖南卫视"}, {"Netflix", "Netflix"},
                {"HBO", "HBO"}, {"BBC", "BBC"}, {"NHK", "NHK"}, {"CBS", "CBS"},
                {"NBC", "NBC"}, {"tvN", "tvN"}
        }));
        filters.put("tv", tvFilters);

        // rank_list_movie
        JSONArray rankMovie = new JSONArray();
        rankMovie.put(filterGroup("rank", "榜单", new String[][]{
                {"实时热门电影", "movie_real_time_hotest"},
                {"一周口碑电影榜", "movie_weekly_best"},
                {"豆瓣电影Top250", "movie_top250"}
        }));
        filters.put("rank_list_movie", rankMovie);

        // rank_list_tv
        JSONArray rankTv = new JSONArray();
        rankTv.put(filterGroup("rank", "榜单", new String[][]{
                {"实时热门电视", "tv_real_time_hotest"},
                {"华语口碑剧集榜", "tv_chinese_best_weekly"},
                {"全球口碑剧集榜", "tv_global_best_weekly"},
                {"国内口碑综艺榜", "show_chinese_best_weekly"},
                {"国外口碑综艺榜", "show_global_best_weekly"}
        }));
        filters.put("rank_list_tv", rankTv);

        return filters;
    }

    private String[][] buildYearValues() {
        return new String[][]{
                {"全部年代", ""}, {"2026", "2026"}, {"2025", "2025"}, {"2024", "2024"},
                {"2023", "2023"}, {"2022", "2022"}, {"2021", "2021"}, {"2020", "2020"},
                {"2019", "2019"}, {"2010年代", "2010年代"}, {"2000年代", "2000年代"},
                {"90年代", "90年代"}, {"80年代", "80年代"}, {"70年代", "70年代"},
                {"60年代", "60年代"}, {"更早", "更早"}
        };
    }

    private JSONObject filterGroup(String key, String name, String[][] values) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("key", key);
        obj.put("name", name);
        JSONArray arr = new JSONArray();
        for (String[] v : values) {
            JSONObject item = new JSONObject();
            item.put("n", v[0]);
            item.put("v", v[1]);
            arr.put(item);
        }
        obj.put("value", arr);
        return obj;
    }

    // ============================================================
    // homeContent
    // ============================================================
    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();

        JSONArray classes = new JSONArray();
        String[][] classesConfig = {
                {"🔥热门电影", "hot_gaia"},
                {"📺热播剧集", "tv_hot"},
                {"🎬热播综艺", "show_hot"},
                {"🎞️电影筛选", "movie"},
                {"📺电视筛选", "tv"},
                {"🏆电影榜单", "rank_list_movie"},
                {"📋电视剧榜单", "rank_list_tv"}
        };
        for (String[] c : classesConfig) {
            JSONObject obj = new JSONObject();
            obj.put("type_name", c[0]);
            obj.put("type_id", c[1]);
            classes.put(obj);
        }
        result.put("class", classes);

        if (filter) {
            result.put("filters", buildFilters());
        }

        // 首页推荐
        JSONArray vlist = new JSONArray();
        Map<String, String> params = new HashMap<>();
        params.put("start", "0");
        params.put("count", "20");
        JSONObject data = apiRequest("/api/v2/subject_collection/subject_real_time_hotest/items", params);
        if (data != null) {
            JSONArray items = data.optJSONArray("subject_collection_items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject parsed = parseItem(items.optJSONObject(i));
                    if (parsed != null) vlist.put(parsed);
                }
            }
        }
        result.put("list", vlist);

        return result.toString();
    }

    // ============================================================
    // categoryContent
    // ============================================================
    @Override
    public String categoryContent(String tid, String pg, boolean filter,
                                  HashMap<String, String> extend) throws Exception {
        int page = parseIntSafe(pg, 1);
        int start = (page - 1) * COUNT;

        String path;
        String key;
        switch (tid) {
            case "hot_gaia":
                path = "/api/v2/movie/hot_gaia"; key = "items"; break;
            case "tv_hot":
                path = "/api/v2/subject_collection/tv_hot/items"; key = "subject_collection_items"; break;
            case "show_hot":
                path = "/api/v2/subject_collection/show_hot/items"; key = "subject_collection_items"; break;
            case "movie":
                path = "/api/v2/movie/recommend"; key = "items"; break;
            case "tv":
                path = "/api/v2/tv/recommend"; key = "items"; break;
            case "rank_list_movie":
                path = "/api/v2/subject_collection/movie_real_time_hotest/items"; key = "subject_collection_items"; break;
            case "rank_list_tv":
                path = "/api/v2/subject_collection/tv_real_time_hotest/items"; key = "subject_collection_items"; break;
            default:
                path = "/api/v2/movie/recommend"; key = "items"; break;
        }

        Map<String, String> params = new HashMap<>();
        params.put("start", String.valueOf(start));
        params.put("count", String.valueOf(COUNT));

        if (extend != null) {
            if ("hot_gaia".equals(tid)) {
                if (extend.containsKey("sort") && !TextUtils.isEmpty(extend.get("sort")))
                    params.put("sort", extend.get("sort"));
                if (extend.containsKey("area") && !TextUtils.isEmpty(extend.get("area"))
                        && !"全部".equals(extend.get("area")))
                    params.put("area", extend.get("area"));
            } else if ("tv_hot".equals(tid)) {
                String t = extend.get("type");
                if (!TextUtils.isEmpty(t) && !"tv_hot".equals(t))
                    path = "/api/v2/subject_collection/" + t + "/items";
            } else if ("show_hot".equals(tid)) {
                String t = extend.get("type");
                if (!TextUtils.isEmpty(t) && !"show_hot".equals(t))
                    path = "/api/v2/subject_collection/" + t + "/items";
            } else if ("movie".equals(tid) || "tv".equals(tid)) {
                if (extend.containsKey("sort") && !TextUtils.isEmpty(extend.get("sort")))
                    params.put("sort", extend.get("sort"));
                StringBuilder tags = new StringBuilder();
                String[] tagKeys = {"type", "area", "year", "tv_type", "show_type", "platform"};
                for (String k : tagKeys) {
                    String v = extend.get(k);
                    if (!TextUtils.isEmpty(v)) {
                        if (tags.length() > 0) tags.append(",");
                        tags.append(v);
                    }
                }
                if (tags.length() > 0) params.put("tags", tags.toString());
            } else if ("rank_list_movie".equals(tid) || "rank_list_tv".equals(tid)) {
                String r = extend.get("rank");
                if (!TextUtils.isEmpty(r))
                    path = "/api/v2/subject_collection/" + r + "/items";
            }
        }

        JSONArray videos = new JSONArray();
        JSONObject data = apiRequest(path, params);
        if (data != null) {
            JSONArray items = data.optJSONArray(key);
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject parsed = parseItem(items.optJSONObject(i));
                    if (parsed != null) videos.put(parsed);
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("list", videos);
        result.put("page", page);
        result.put("pagecount", 9999);
        result.put("limit", 90);
        result.put("total", 999999);
        return result.toString();
    }

    // ============================================================
    // detailContent
    // ============================================================
    @Override
    public String detailContent(List<String> ids) throws Exception {
        String vid = ids.isEmpty() ? "" : ids.get(0);
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        if (vid.startsWith("msearch:")) {
            String keyword = vid.replace("msearch:", "");
            JSONObject vod = new JSONObject();
            vod.put("vod_id", "search://" + keyword);
            vod.put("vod_name", "🔍 搜索: " + keyword);
            vod.put("vod_pic", "");
            vod.put("vod_remarks", "点击搜索全部源");
            list.put(vod);
        }

        result.put("list", list);
        return result.toString();
    }

    // ============================================================
    // searchContent
    // ============================================================
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        JSONObject result = new JSONObject();
        result.put("list", new JSONArray());
        result.put("page", 1);
        result.put("pagecount", 1);
        result.put("limit", 0);
        result.put("total", 0);
        return result.toString();
    }

    // ============================================================
    // playerContent
    // ============================================================
    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        if (id != null && id.startsWith("search://")) {
            result.put("parse", 0);
            result.put("url", id);
        } else {
            result.put("parse", 0);
            result.put("url", "");
        }
        return result.toString();
    }

    private int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }
}