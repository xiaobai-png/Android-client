package com.companyname;

import android.app.Activity;

import java.util.LinkedList;
import java.util.List;

public class AppConst {
    public static MainActivity MAIN_ACTIVITY = null;

//    public static UploadActivity UPLOAD_ACTIVITY = null;

    public static String serverUrl = "";

    public static String swfCdnUrl = "";

    public static String warnStr = "";

    private static List<Activity> activityList=new LinkedList<Activity>();

    //添加activity到容器中
    public static void addActivity(Activity activity){
        activityList.add(activity);
    }
    //遍历所有的Activiy并finish
    public static void exit(){
        for(Activity activity:activityList){
            activity.finish();
        }
        System.exit(0);
    }
}
