package com.lxfly2000.animeschedule.spider;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.Toast;
import com.lxfly2000.animeschedule.AnimeJson;
import com.lxfly2000.animeschedule.R;
import com.lxfly2000.animeschedule.URLUtility;
import com.lxfly2000.animeschedule.Values;
import com.lxfly2000.animeschedule.data.AnimeItem;
import com.lxfly2000.utilities.AndroidDownloadFileTask;
import com.lxfly2000.utilities.StreamUtility;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BilibiliSpider extends Spider {
    public BilibiliSpider(Context context){
        super(context);
        preferences=Values.GetPreference(ctx);
    }

    private AnimeItem item=new AnimeItem();
    private SharedPreferences preferences;

    @Override
    public void Execute(String url){
        ReadBilibiliURL_OnCallback(url);
    }

    int redirectCount=0;

    private void ReadBilibiliURL_OnCallback(final String urlString){//2018-11-14：B站原来的两个JSON的API均已失效，现在改为了HTML内联JS代码
        /*输入URL：parsableLinkRegex中的任何一个B站URL
         *
         * 在返回的HTML文本（转换成小写）里找ss#####, season_id:#####, "season_id":#####, ssid:#####, "ssid":#####
         * 得到的数值均为 Season ID, 然后就可以从ss##### URL里获取信息了。
         * */
        if(URLUtility.IsBilibiliSeasonBangumiLink(urlString)&&!URLUtility.IsBilibiliVideoLink(urlString)){
            ReadBilibiliSSID_OnCallback(URLUtility.GetBilibiliSeasonIdString(urlString));
            return;
        }
        item.title=ctx.getString(R.string.message_fetching_bilibili_ssid);
        onReturnDataFunction.OnReturnData(item,STATUS_ONGOING,null);
        AndroidDownloadFileTask task=new AndroidDownloadFileTask() {
            @Override
            public void OnReturnStream(ByteArrayInputStream stream, boolean success, int response, Object extra, Object additionalReturned) {
                if(!success){
                    onReturnDataFunction.OnReturnData(item,STATUS_FAILED,ctx.getString(R.string.message_unable_to_fetch_episode_id));
                    return;
                }
                if(response==301||response==302){
                    redirectCount++;
                    if(redirectCount>preferences.getInt(ctx.getString(R.string.key_redirect_max_count), Values.vDefaultRedirectMaxCount)){
                        onReturnDataFunction.OnReturnData(item,STATUS_ONGOING,response+"\n"+ctx.getString(R.string.message_too_many_redirect));
                    }else {
                        ReadBilibiliURL_OnCallback((String) additionalReturned);
                        return;
                    }
                }else {
                    if (response == 403) {
                        onReturnDataFunction.OnReturnData(item,STATUS_ONGOING,ctx.getString(R.string.message_http_403));
                    } else if (response == 404) {
                        onReturnDataFunction.OnReturnData(item,STATUS_ONGOING,ctx.getString(R.string.message_http_404));
                    }
                }
                redirectCount=0;
                try{
                    String htmlString= StreamUtility.GetStringFromStream(stream);
                    Matcher m= Pattern.compile("ss[0-9]+").matcher(htmlString);
                    if(m.find()){
                        ReadBilibiliSSID_OnCallback(htmlString.substring(m.start()+2,m.end()));
                        return;
                    }
                    m=Pattern.compile("season_id:[0-9]+").matcher(htmlString);
                    if(m.find()){
                        ReadBilibiliSSID_OnCallback(htmlString.substring(m.start()+10,m.end()));
                        return;
                    }
                    m=Pattern.compile("\"season_id\":[0-9]+").matcher(htmlString);
                    if(m.find()){
                        ReadBilibiliSSID_OnCallback(htmlString.substring(m.start()+12,m.end()));
                        return;
                    }
                    m=Pattern.compile("ssId:[0-9]+").matcher(htmlString);
                    if(m.find()){
                        ReadBilibiliSSID_OnCallback(htmlString.substring(m.start()+5,m.end()));
                        return;
                    }
                    m=Pattern.compile("\"ssId\":[0-9]+").matcher(htmlString);
                    if(m.find()){
                        ReadBilibiliSSID_OnCallback(htmlString.substring(m.start()+7,m.end()));
                        return;
                    }
                    String ssid_not_found_string=ctx.getString(R.string.message_bilibili_ssid_not_found);
                    if(urlString.startsWith("http:"))
                        ssid_not_found_string+="\n"+ctx.getString(R.string.message_bilibili_ssid_not_found_advise);
                    onReturnDataFunction.OnReturnData(item,STATUS_FAILED,ssid_not_found_string);
                }catch (IOException e){
                    onReturnDataFunction.OnReturnData(item,STATUS_FAILED,ctx.getString(R.string.message_io_exception,e.getLocalizedMessage()));
                }
            }
        };
        task.SetUserAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.1 (KHTML, like Gecko) Chrome/22.0.1207.1 Safari/537.1");
        task.execute(urlString);
    }

    private void ReadBilibiliSSID_OnCallback(final String idString){
        item.title="SSID:"+idString;
        onReturnDataFunction.OnReturnData(item,STATUS_ONGOING,null);
        final String requestUrl="https://bangumi.bilibili.com/view/web_api/season?season_id="+idString;
        AndroidDownloadFileTask task=new AndroidDownloadFileTask() {
            @Override
            public void OnReturnStream(ByteArrayInputStream stream, boolean success, int response, Object extra,Object additionalReturned) {
                if(!success){
                    onReturnDataFunction.OnReturnData(item,STATUS_FAILED,ctx.getString(R.string.message_unable_to_fetch_anime_info));
                    return;
                }
                try {
                    //2019-1-19：B站网页更新，原有查询方法失效。
                    //参考：https://github.com/xiaoyaocz/BiliAnimeDownload/blob/master/BiliAnimeDownload/BiliAnimeDownload/Helpers/Api.cs
                    String jsonString=StreamUtility.GetStringFromStream(stream);
                    if(jsonString==null){
                        onReturnDataFunction.OnReturnData(item,STATUS_FAILED,ctx.getString(R.string.message_bilibili_ssid_code_not_found,idString));
                        return;
                    }
                    JSONObject htmlJson=new JSONObject(jsonString).getJSONObject("result");
                    try {
                        item.coverUrl=htmlJson.getString("cover");
                    }catch (JSONException e){/*Ignore*/}
                    try {
                        item.title=htmlJson.getString("series_title");
                    }catch (JSONException e){
                        //2019-2-16：经检查发现番剧《天使降临到我身边》（SSID：26291）不存在“series_title”属性
                        try {
                            item.title=htmlJson.getString("title");
                        }catch (JSONException ee){/*Ignore*/}
                    }
                    try {
                        item.description=htmlJson.getString("evaluate");
                    }catch (JSONException e){/*Ignore*/}
                    try {
                        item.actors=htmlJson.getString("actors");
                    }catch (JSONException e){/*Ignore*/}
                    try {
                        item.staff=htmlJson.getString("staff");
                    }catch (JSONException e){/*Ignore*/}
                    try {
                        String[] pubTimeParts = htmlJson.getJSONObject("publish").getString("pub_time").split(" ");
                        item.startDate=pubTimeParts[0];
                        //Bug:SSID:22574 pub_time字段不存在时间（2019-4-8）
                        if (pubTimeParts.length > 1)
                            item.updateTime=pubTimeParts[1];
                    }catch (JSONException e){/*Ignore*/}
                    try {
                        if (htmlJson.getJSONObject("publish").getString("weekday").contentEquals("-1")) {
                            item.updatePeriod=1;
                            item.updatePeriodUnit= AnimeJson.unitMonth;
                            onReturnDataFunction.OnReturnData(item,STATUS_ONGOING,ctx.getString(R.string.message_check_update_period),R.id.editDialogUpdatePeriod);
                        } else if ("0123456".contains(htmlJson.getJSONObject("publish").getString("weekday"))) {
                            item.updatePeriod=7;
                            item.updatePeriodUnit=AnimeJson.unitDay;
                        }
                    }catch (JSONException e){/*Ignore*/}
                    try {
                        String countString = htmlJson.getString("total_ep");
                        if (countString.contentEquals("0"))
                            item.episodeCount=-1;
                        else
                            item.episodeCount=Integer.parseInt(countString);
                    }catch (JSONException e){/*Ignore*/}
                    try {
                        StringBuilder tagString = new StringBuilder();
                        for (int i = 0; i < htmlJson.getJSONArray("style").length(); i++) {
                            if (i != 0)
                                tagString.append(",");
                            tagString.append(htmlJson.getJSONArray("style").getString(i));
                        }
                        item.categories=tagString.toString().split(",");
                    }catch (JSONException e){/*Ignore*/}
                    item.watchUrl="https://www.bilibili.com/bangumi/play/ss"+idString;//为避免输入的URL无法被客户端打开把URL统一改成SSID形式
                    item.rank=(int)Math.round(htmlJson.getJSONObject("rating").getDouble("score")/2);
                }catch (JSONException e){
                    onReturnDataFunction.OnReturnData(null,STATUS_FAILED,ctx.getString(R.string.message_json_exception,e.getLocalizedMessage()));
                }catch (IOException e){
                    onReturnDataFunction.OnReturnData(null,STATUS_FAILED,ctx.getString(R.string.message_error_on_reading_stream,e.getLocalizedMessage()));
                }
                onReturnDataFunction.OnReturnData(item,STATUS_OK,null);
            }
        };
        task.execute(requestUrl);
    }
}
