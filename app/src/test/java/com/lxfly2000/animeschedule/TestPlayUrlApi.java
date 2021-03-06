package com.lxfly2000.animeschedule;

import org.junit.Assert;
import org.junit.Test;
import xiaoyaocz.BiliAnimeDownload.Helpers.Api;

public class TestPlayUrlApi {
    @Test
    public void TestPlayUrlApiMain(){
        System.out.println("番剧：孤独地躲在墙角画圈圈");
        String ssid="26818",ep1epid="267811",ep1cid="84763740",avid="48394822";
        System.out.println(Api._playurlApi(ep1cid));
        System.out.println(Api._playurlApi2(ep1cid));
        System.out.println(Api._playurlApi3(ssid,0));
        System.out.println(Api._playurlApi4(ssid,ep1cid,ep1epid));
        System.out.println(BilibiliApi.GetVideoLink(1,0,avid,ep1cid,112));
        System.out.println(BilibiliApi.GetVideoLink(1,1,avid,ep1cid,112));
        System.out.println("番剧：淫乱的青酱不能学习（仅台湾）");
        ssid="26953";ep1epid="267799";ep1cid="84739150";avid="48379841";
        System.out.println(Api._playurlApi(ep1cid));
        System.out.println(Api._playurlApi2(ep1cid));
        System.out.println(Api._playurlApi3(ssid,0));
        System.out.println(Api._playurlApi4(ssid,ep1cid,ep1epid));
        System.out.println(BilibiliApi.GetVideoLink(1,0,avid,ep1cid,112));
        System.out.println(BilibiliApi.GetVideoLink(1,1,avid,ep1cid,112));
        System.out.println("番剧：请问今天要来点兔子吗（多Part）");
        ssid="2762";ep1epid="79193";ep1cid="10734463";avid="3257738";
        System.out.println(Api._playurlApi(ep1cid));
        System.out.println(Api._playurlApi2(ep1cid));//4个分段
        System.out.println(Api._playurlApi3(ssid,0));
        System.out.println(Api._playurlApi4(ssid,ep1cid,ep1epid));
        System.out.println(BilibiliApi.GetVideoLink(1,0,avid,ep1cid,112));//4个分段
        System.out.println(BilibiliApi.GetVideoLink(1,1,avid,ep1cid,112));//4个分段

        //结论：目前橙子提供的API只有2和4是有可能可用的
        //2的API经检测quality参数现已无法使用
        //4的API只提供港澳台区番剧且只有一种清晰度且不分段
        //api.bilibili.com的第一个/x/的貌似没有条件限制
        //而第二个/pgc/的会有区域限制及大会员检测限制

        Assert.assertNotNull(Api._playurlApi2(ep1cid));
    }
}
