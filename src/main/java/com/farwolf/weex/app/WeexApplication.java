package com.farwolf.weex.app;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.multidex.MultiDex;
import android.support.multidex.MultiDexApplication;
import android.util.Log;

import com.alibaba.android.bindingx.plugin.weex.BindingX;
import com.farwolf.util.ActivityManager;
import com.farwolf.util.DateTool;
import com.farwolf.util.RegexBase;
import com.farwolf.util.StringUtil;
import com.farwolf.weex.R;
import com.farwolf.weex.adapter.ImageDownLoader;
import com.farwolf.weex.bean.Config;
import com.farwolf.weex.core.local.Local;
import com.farwolf.weex.event.RefreshEvent;
import com.farwolf.weex.pref.WeexPref_;
import com.farwolf.weex.util.Constants;
import com.farwolf.weex.util.HotRefreshManager;
import com.farwolf.weex.util.Weex;
import com.farwolf.weex.util.Weex_;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.squareup.picasso.Picasso;
import com.taobao.weex.event.ErrorEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Collections;
import java.util.Date;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;

//import android.support.multidex.MultiDex;
//import android.support.multidex.MultiDexApplication;

/**
 * Created by zhengjiangrong on 2018/3/10.
 */

public class WeexApplication extends MultiDexApplication {

    public Handler mWXHandler;


    public Weex weex;



    public WeexPref_ pref;

    long lastrefresh=0;

    public static WeexApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance=this;
        initApp(instance);
    }

    public void initApp(Application instance){
        Local.copyAssetToDisk(this);
        weex= Weex_.getInstance_(this);
        pref= new WeexPref_(this);
        initUnivsalImageloader(this);
        initPicasso(this);
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        String schema= Config.schema(this);
        if(schema==null)
            schema="";
        weex.init(this,"farwolf","farwolf",schema);
        try {
            BindingX.register();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        registRefresh();
        registLifecycle(instance);
        if(Config.debug(this))
            connect();

    }

    public void registRefresh(){
        mWXHandler=new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case Constants.HOT_REFRESH_CONNECT:
                        HotRefreshManager.getInstance().connect(msg.obj.toString());
                        break;
                    case Constants.HOT_REFRESH_DISCONNECT:
                        reconnect(1000);
                        break;
                    case Constants.HOT_REFRESH_REFRESH:
//                        render(WeexActivity.this.url);
                        long now=new Date().getTime();
                        if(now-lastrefresh>300)
                        {
                            Log.i("refresh",now-lastrefresh+"");
                            EventBus.getDefault().post(new RefreshEvent("refresh"));
                            lastrefresh=now;
                        }
//                        else
//                        {
////                            Log.i("??????",now-lastrefresh+"");
//                        }
                        break;
                    case Constants.HOT_REFRESH_CONNECT_ERROR:
                        reconnect(1000);
                        break;
                    default:
                        break;
                }
                return false;
            }
        });


        EventBus.getDefault().register(this);

    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(RefreshEvent event) {

        if("reconnect".equals(event.type))
        {
            connect();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(ErrorEvent event) {

        if("log".equals(event.type))
        {
            String msg=DateTool.Now()+"     "+event.msg;
            String level=event.level;
            HotRefreshManager.getInstance().send("log:"+level+"level:"+msg);
        }



    }

    public static WeexApplication getInstance()
    {
        return instance;
    }


    private void reconnect(final long time) {
        final Handler achandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                connect();

            }
        };
        achandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                achandler.sendEmptyMessage(0);
            }
        }, time);
    }

    public void registLifecycle(Application app){
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

            }

            @Override
            public void onActivityStarted(Activity activity) {

            }

            @Override
            public void onActivityResumed(Activity activity) {
                ActivityManager.getInstance().setCurrentActivity(activity);

            }

            @Override
            public void onActivityPaused(Activity activity) {

            }

            @Override
            public void onActivityStopped(Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {

            }
        });
    }

    public void connect()
    {
        HotRefreshManager.getInstance().disConnect();
        HotRefreshManager.getInstance().setHandler(mWXHandler);
        connect(getIp(),getSocketPort());
    }

    public void connect(String ip,String port)
    {

        String wsUrl = "ws://" + ip+ ":"+port;
        HotRefreshManager.getInstance().connect(wsUrl);
    }

    public String getSocketPort()
    {
        String sp= pref.socketPort().get();
        if(StringUtil.isNullOrEmpty(sp))
        {
            return "9897";
        }
        return sp;
    }

    public String getIp(){
        String url= pref.url().get();
        return getIp(url);
    }

    public String getIp(String url)
    {
        String ipx= RegexBase.regexOne(url,"http://",":");
        if(!StringUtil.isNullOrEmpty(ipx))
        {
            return ipx;
        }
        else
        {
            String entry= Config.debugEntry(this);
            if(!StringUtil.isNullOrEmpty(entry)){
                ipx=RegexBase.regexOne(entry,"http://",":");
            }
            if(!StringUtil.isNullOrEmpty(ipx))
            {
                return ipx;
            }
            return  Config.debugIp(this);

        }
    }

    public static String getSocketPortByUrl(String url)
    {
//        url=url.replace("http://","");
//        url=url.replace("https://","");
//        String port= RegexBase.regexOne(url,":","/");
//        if(!StringUtil.isNullOrEmpty(port))
//        {
//            return port;
//        }
        return "9897";
    }


   public static void initUnivsalImageloader(Context context)
    {
        DisplayImageOptions defaultOptions = new DisplayImageOptions
                .Builder()
                .showImageForEmptyUri(R.drawable.bg_gf_crop_texture)
                .showImageOnFail(R.drawable.bg_gf_crop_texture)
                .cacheInMemory(true)
                .cacheOnDisc(true)
                .build();
        ImageLoaderConfiguration config = new ImageLoaderConfiguration
                .Builder(context)
                .defaultDisplayImageOptions(defaultOptions)
                .discCacheSize(50 * 1024 * 1024)//

                .writeDebugLogs()
                .build();
        com.nostra13.universalimageloader.core.ImageLoader.getInstance().init(config);
    }


    public static void initPicasso(Context context){
        OkHttpClient client = new OkHttpClient.Builder()
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .build();
        Picasso.setSingletonInstance(new Picasso.Builder(context).
                downloader(new ImageDownLoader(client))
                .build());
    }
}
