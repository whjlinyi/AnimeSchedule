package com.lxfly2000.animeschedule;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class URLUtility {
    private static final String regexBilibiliSSID="(/[0-9]+)|(/ss[0-9]+)";
    public static boolean IsBilibiliLink(String url){
        Matcher matcher= Pattern.compile(Values.parsableLinksRegex[0]).matcher(url);
        return matcher.find();
    }

    public static boolean IsBilibiliSeasonLink(String url){
        if(!IsBilibiliLink(url))
            return false;
        Matcher matcher=Pattern.compile(regexBilibiliSSID).matcher(url);
        return matcher.find();
    }

    public static String GetBilibiliSeasonIdString(String url){
        if(!IsBilibiliSeasonLink(url))
            return null;
        Matcher matcher=Pattern.compile(regexBilibiliSSID).matcher(url);
        if(!matcher.find())
            return null;
        url=url.substring(matcher.start(),matcher.end());
        matcher=Pattern.compile("[0-9]+").matcher(url);
        if(!matcher.find())
            return null;
        return url.substring(matcher.start(),matcher.end());
    }

    public static boolean IsIQiyiLink(String url){
        Matcher matcher=Pattern.compile(Values.parsableLinksRegex[1]).matcher(url);
        return matcher.find();
    }
}