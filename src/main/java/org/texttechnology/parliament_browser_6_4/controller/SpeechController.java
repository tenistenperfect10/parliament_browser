package org.texttechnology.parliament_browser_6_4.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mongodb.client.AggregateIterable;
import freemarker.template.Configuration;
import freemarker.template.SimpleHash;
import freemarker.template.TemplateException;
import org.apache.commons.lang3.StringEscapeUtils;
import org.bson.Document;
import org.texttechnology.parliament_browser_6_4.data.InsightFactory;
import org.texttechnology.parliament_browser_6_4.data.configuration.FreemarkerBasedRoute;
import org.texttechnology.parliament_browser_6_4.helper.Result;
import org.texttechnology.parliament_browser_6_4.helper.SessionsUtils;
import spark.Request;
import spark.Response;

import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.*;

import static spark.Spark.get;
import static spark.Spark.post;

public class SpeechController {

    private final InsightFactory insightFactory;
    private final Configuration cfg;

    public SpeechController( InsightFactory insightFactory, Configuration cfg)
            throws IOException {
        this.insightFactory = insightFactory;
        this.cfg = cfg;
        initializeRoutes();
    }

    private void initializeRoutes() throws IOException {
        get("/speech", new FreemarkerBasedRoute("/speech", "speech.ftl", cfg) {
            @Override
            protected void doHandle(Request request, Response response, Writer writer)
                    throws IOException, TemplateException {
                SessionsUtils.redirectIfNotLogin(request, response);
                // 构建聚合查询
                AggregateIterable<Document> speechList = insightFactory.aggregate();

                SimpleHash root = new SimpleHash();

                // 将AggregateIterable转换为List
                List<Document> resultList = new ArrayList<>();
                speechList.into(resultList);
                System.out.println("total speech num is " + resultList.size());
                // 转化为级联的多级菜单形式
                Map<String, Map<String, List<Document>>> resultMap = convertCascadeMap(resultList);
                root.put("speechMap", resultMap);
//                root.put("speechList", resultList);

                this.getTemplate().process(root, writer);
            }
        });

        get("/speechDetail/:id", new FreemarkerBasedRoute("/speechDetail/:id", "speechDetail.ftl", cfg) {
            @Override
            protected void doHandle(Request request, Response response, Writer writer)
                    throws IOException, TemplateException {

                SessionsUtils.redirectIfNotLogin(request, response);
                Integer canEdit = SessionsUtils.getSessionByKey(request, "canEdit");

                String id = StringEscapeUtils.escapeHtml4(request.params(":id"));
                Document speech = insightFactory.findBySpeechId(id);

                Document speaker = new Document();
                if(StrUtil.isNotEmpty(speech.getString("speaker"))){
                    speaker = insightFactory.findById(speech.getString("speaker"));
                }
                List<String>  commentIds = speech.getList("comments", String.class);

                List<Document> commentList = new ArrayList<>();
                if(CollUtil.isNotEmpty(commentIds)){
                    AggregateIterable<Document> aggregateIterable = insightFactory.findByIdsWithSpeaker(commentIds);
                    aggregateIterable.into(commentList);
                }
                Date startDate = new Date(speech.get("starttime", Long.class));
                Date endDate = new Date(speech.get("endtime", Long.class));

                // 创建一个SimpleDateFormat对象，指定所需的格式
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");

                // 使用format方法将Date对象转换为字符串
                String formattedStartDate = sdf.format(startDate);
                String formattedEndDate = sdf.format(endDate);

                SimpleHash root = new SimpleHash();

                root.put("speech", speech);
                root.put("speaker", speaker);
                root.put("commentList", commentList);

                root.put("startDate", formattedStartDate);
                root.put("endDate", formattedEndDate);
                root.put("canEdit", canEdit);

                this.getTemplate().process(root, writer);
            }
        });

        post("/speech/search", new FreemarkerBasedRoute("/speech", "speech.ftl", cfg) {
            @Override
            protected void doHandle(Request request, Response response, Writer writer)
                    throws IOException, TemplateException {

                SessionsUtils.redirectIfNotLogin(request, response);

                String starttime = StringEscapeUtils.escapeHtml4(request.queryParams("startTime"));
                String endtime = StringEscapeUtils.escapeHtml4(request.queryParams("endTime"));

                Date startDate = null;
                Date endDate = null;
                String errorMsg = "";
                if(StrUtil.isNotEmpty(starttime)){
                    try {
                        startDate = DateUtil.parse(starttime);
                    }catch (Exception e){
                        errorMsg += "Start time format error!";
                    }
                }
                if(StrUtil.isNotEmpty(endtime)){
                    try {
                        endDate = DateUtil.parse(endtime);
                    }catch (Exception e){
                        errorMsg += "End time format error!";
                    }
                }

                if(startDate != null && endDate != null && startDate.after(endDate)){
                    errorMsg += "The start time cannot be greater than the end time!";
                }
                SimpleHash root = new SimpleHash();

                if(StrUtil.isNotBlank(errorMsg)){
                    root.put("errorMsg", errorMsg);
                    this.getTemplate().process(root, writer);
                    return;
                }

                AggregateIterable<Document> speechList = insightFactory.searchSpeech(startDate, endDate);

                // 将AggregateIterable转换为List
                List<Document> resultList = new ArrayList<>();
                speechList.into(resultList);
                System.out.println("search speech num is " + resultList.size());
                // 转化为级联的多级菜单形式
                Map<String, Map<String, List<Document>>> resultMap = convertCascadeMap(resultList);

                // 创建一个SimpleDateFormat对象，指定所需的格式
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");

                // 使用format方法将Date对象转换为字符串
                String formattedStartDate = sdf.format(startDate);
                String formattedEndDate = sdf.format(endDate);

                root.put("speechMap", resultMap);
                root.put("errorMsg", errorMsg);
                root.put("startDate", formattedStartDate);
                root.put("endDate", formattedEndDate);

                this.getTemplate().process(root, writer);
            }
        });

        get("/speech/query", new FreemarkerBasedRoute("/speech", "speech.ftl", cfg) {
            @Override
            protected void doHandle(Request request, Response response, Writer writer)
                    throws IOException, TemplateException {

                SessionsUtils.redirectIfNotLogin(request, response);
                String keyword = request.queryParams("keyword");
                List<Document> speeches = insightFactory.globalQueryByKeyword(keyword).into(new ArrayList<>());

                Map<String, Map<String, List<Document>>> resultMap = convertCascadeMap(speeches);

                SimpleHash root = new SimpleHash();
                root.put("speechMap", resultMap);
                this.getTemplate().process(root, writer);
            }
        });

        post("/api/speech/update", (request, response) -> {
            response.type("application/json");
            try {
                JSONObject obj = JSONUtil.parseObj(request.body());
                String id = (String) obj.remove("_id");
                boolean success = insightFactory.updateSpeechById(id, JSONUtil.toJsonStr(obj));
                return success ? Result.buildSuccess() : Result.buildError();
            } catch (Exception e) {
                return Result.buildError(e.getMessage());
            }
        });

    }

    /**
     * 转化为多级级联的map，用于折叠和展开显示
     * @param resultList
     * @return
     */
    private Map<String, Map<String, List<Document>>> convertCascadeMap(List<Document> resultList) {
        Map<String, Map<String, List<Document>>> resultMap = new HashMap<>();
        resultList.stream().forEach(result -> {
            String key = result.getString("speakerId") + "&" + result.getString("speakerName");
            if (!resultMap.containsKey(key)) {
                Map<String, List<Document>> speakerMap = new HashMap<>();
                List<Document> documents = new ArrayList<>();
                documents.add(result);
                speakerMap.put(result.getString("title"), documents);
                resultMap.put(key, speakerMap);
            } else {
                Map<String, List<Document>> speakerMap = resultMap.get(key);
                if (!speakerMap.containsKey(result.getString("title"))) {
                    List<Document> documents = new ArrayList<>();
                    documents.add(result);
                    speakerMap.put(result.getString("title"), documents);
                } else {
                    speakerMap.get(result.getString("title")).add(result);
                }
            }
        });
        return resultMap;
    }
}