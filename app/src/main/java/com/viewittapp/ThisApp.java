package com.viewittapp;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThisApp extends Application {

    private static final String TAG = ThisApp.class.getSimpleName();

    private static ThisApp singleton;

    public static ThisApp getSingleton() {
        return singleton;
    }

    private static ThreadPoolExecutor threadPool;

    public static ThreadPoolExecutor getThreadPool() {
        return threadPool;
    }

    private static long bytesDownloaded;
    private static long uniqueImageViews;
    private static boolean showTitle;
    private static boolean showVideo;

    private SharedPreferences settings;

    @Override
    public void onCreate() {
        super.onCreate();
        singleton = this;
        settings = getSharedPreferences("viewitt.config", 0);
        loadSettings();
        initializeThreadPool();
    }

    private void loadSettings() {
        bytesDownloaded = settings.getLong("bytesDownloaded", 0);
        uniqueImageViews = settings.getLong("uniqueImageViews", 0);
        showTitle = settings.getBoolean("showTitle", true);
        showVideo = settings.getBoolean("showVideo", true);
    }

    public void saveSettings() {
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong("bytesDownloaded", bytesDownloaded);
        editor.putLong("uniqueImageViews", uniqueImageViews);
        editor.putBoolean("showTitle", showTitle);
        editor.putBoolean("showVideo", showVideo);
        editor.commit();
    }

    private void initializeThreadPool() {
        // Gets the number of available cores (not always the same as the maximum number of cores)
        final int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
        // Sets the maximum threads in the pool based on the number of cores
        final int MAX_POOL_SIZE = NUMBER_OF_CORES * 20;
        // Instantiates the queue of Runnables as a LinkedBlockingQueue
        final BlockingQueue<Runnable> mDecodeWorkQueue = new LinkedBlockingQueue<Runnable>();
        // Sets the amount of time an idle thread waits before terminating
        final int KEEP_ALIVE_TIME = 1;
        // Sets the Time Unit to seconds
        final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;

        // force thread pool to always have MAX_POOL_SIZE number of threads on hand to receive tasks
        // we do this because we make a lot of independent async calls that should run in parallel
        threadPool = new ThreadPoolExecutor(MAX_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT, mDecodeWorkQueue);
    }

    /**
     * @return Application's version code from the {@code PackageManager}.
     */
    public int getAppVersionCode() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * @return Application's version name from the {@code PackageManager}.
     */
    public String getAppVersionName() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    public static long getBytesDownloaded() {
        return bytesDownloaded;
    }

    public static void incrementBytesDownloaded(long toAdd) {
        bytesDownloaded += toAdd;
    }

    public static long getUniqueImageViews() {
        return uniqueImageViews;
    }

    public static void incrementUniqueImageViews() {
        uniqueImageViews++;
    }

    public static boolean getShowTitle() {
        return showTitle;
    }

    public static void setShowTitle(boolean newShowTitle) {
        showTitle = newShowTitle;
    }

    public static boolean getShowVideo() {
        return showVideo;
    }

    public static void setShowVideo(boolean newShowVideo) {
        showVideo = newShowVideo;
    }

}
