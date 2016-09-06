package com.viewittapp;

import android.content.Context;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class GifWebView extends WebView {

    private static String wrapperHtml = loadWrapperHtml();

    public GifWebView(Context context, String path) {
        super(context);

        WebSettings settings = getSettings();

        // zoom out to see entire image
        setInitialScale(1);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        // make zoomable
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // make background transparent
        setBackgroundColor(0x00000000);

        // might be necessary for HTML5 video & Youtube
        getSettings().setJavaScriptEnabled(true);
        settings.setSupportMultipleWindows(true);

        setWebViewClient(new WebViewClient() {
            // autoplay when finished loading via javascript injection
            public void onPageFinished(WebView view, String url) {
                loadUrl("javascript:(function() { document.getElementsByTagName('video')[0].play(); })()");
            }
        });
        setWebChromeClient(new WebChromeClient());

        // load WebView wrapper
        if (wrapperHtml == null) {
            wrapperHtml = loadWrapperHtml();
        }
        String html = wrapperHtml.replace("SOURCE", path);
        // loadData(html, "text/html", "UTF-8");
        loadUrl(path.replace("gifv", "webm"));
    }

    private static String loadWrapperHtml() {
        try {
            StringBuilder buf = new StringBuilder();
            InputStream html = ThisApp.getSingleton().getAssets().open("web_view.html");
            BufferedReader in = new BufferedReader(new InputStreamReader(html, "UTF-8"));
            String str;
            while ((str = in.readLine()) != null) {
                buf.append(str);
            }
            in.close();
            return buf.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
