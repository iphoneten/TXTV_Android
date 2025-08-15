package com.tv.txtv;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.webkit.WebChromeClient.CustomViewCallback;

public class FullScreenVideoActivity extends Activity {
    private FrameLayout fullScreenContainer;
    private View customView;
    private CustomViewCallback customViewCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 允许横屏
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR);

        FrameLayout rootLayout = new FrameLayout(this);
        setContentView(rootLayout);

        fullScreenContainer = new FrameLayout(this);
        fullScreenContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        rootLayout.addView(fullScreenContainer);

        // 获取 WebView 传过来的 URL 或 View
        WebView webView = new WebView(this);
        rootLayout.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                fullScreenContainer.addView(customView,
                        new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
                fullScreenContainer.setVisibility(View.VISIBLE);
                webView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                fullScreenContainer.setVisibility(View.GONE);
                fullScreenContainer.removeView(customView);
                customViewCallback.onCustomViewHidden();
                customView = null;
                webView.setVisibility(View.VISIBLE);
                finish(); // 关闭 Activity 回到主页面
            }
        });

        // 加载网页或视频
        String url = getIntent().getStringExtra("url");
        webView.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            // 退出全屏
//            webView.getWebChromeClient().onHideCustomView();
        } else {
            super.onBackPressed();
        }
    }
}
