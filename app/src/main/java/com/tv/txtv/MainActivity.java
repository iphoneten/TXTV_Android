package com.tv.txtv;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FrameLayout fullScreenContainer;
    private LinearLayout loadingLayout;
    private ProgressBar progressBar;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private WebChromeClient webChromeClient;
    private String pendingFullscreenOrientation = "unlock";
//    private String url = "https://moontv-518.pages.dev?tv=1";
    private final String url = "https://moontv-518.pages.dev/"; // https://moontv-518.pages.dev/  http://192.168.141.10:3000/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 创建根布局 FrameLayout
        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 创建 SwipeRefreshLayout
        swipeRefreshLayout = new SwipeRefreshLayout(this);
        swipeRefreshLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        swipeRefreshLayout.setEnabled(false); // 启用下拉刷新

        // 创建 WebView
        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus();
        swipeRefreshLayout.addView(webView);

        // 清理 WebView 缓存/历史，避免读到旧地址
//        webView.clearCache(true);
//        webView.clearHistory();
//        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);

        // 创建全屏视频容器 FrameLayout
        fullScreenContainer = new FrameLayout(this);
        fullScreenContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        fullScreenContainer.setVisibility(View.GONE);

        // 创建 loading 布局（仅包含 ProgressBar）
        loadingLayout = new LinearLayout(this);
        loadingLayout.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        loadingLayout.setGravity(android.view.Gravity.CENTER);
        loadingLayout.setBackgroundColor(Color.parseColor("#88000000")); // 半透明黑色
        loadingLayout.setVisibility(View.GONE);

        // 创建旋转 ProgressBar
        progressBar = new ProgressBar(this);
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        progressBar.setLayoutParams(pbParams);
        loadingLayout.addView(progressBar);

        // 将控件添加到根布局
        rootLayout.addView(swipeRefreshLayout);
        rootLayout.addView(fullScreenContainer);
        rootLayout.addView(loadingLayout);

        setContentView(rootLayout);

        // WebView 设置
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setMediaPlaybackRequiresUserGesture(false); // 允许视频自动播放
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
        webView.addJavascriptInterface(new OrientationBridge(), "TXTVNative");

        // 确保图片可以加载（部分国产电视 WebView 默认关闭）
        webSettings.setLoadsImagesAutomatically(true);
        webSettings.setBlockNetworkImage(false);

        // 允许 HTTP / HTTPS 混合内容（很多图片 CDN 是 http）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // 允许文件和内容访问（部分电视系统需要）
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            webSettings.setAllowFileAccessFromFileURLs(true);
            webSettings.setAllowUniversalAccessFromFileURLs(true);
        }

        // 伪装浏览器 UA，避免部分网站拦截 Android TV WebView
        webSettings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/100.0 Safari/537.36"
        );

        // 设置 WebViewClient 控制 loading 显示隐藏
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // 接受所有网站的证书
                handler.proceed();
            }

            @Override
            public void onPageStarted(WebView view, String pageUrl, android.graphics.Bitmap favicon) {
                Log.d("WebView", "Page started loading: " + pageUrl);
//                url = pageUrl; // 更新当前 URL
                loadingLayout.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.VISIBLE); // 显示 ProgressBar
                swipeRefreshLayout.setRefreshing(false);
                loadingLayout.bringToFront(); // 确保 loadingLayout 在前
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                Log.d("WebView", "Page finished loading: " + pageUrl);
                // 有些 TV WebView 会在页面加载结束后才允许图片下载
                view.getSettings().setLoadsImagesAutomatically(true);
                view.getSettings().setBlockNetworkImage(false);
//                url = pageUrl; // 更新当前 URL
                loadingLayout.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                rootLayout.invalidate(); // 强制刷新布局
            }
        });

        // 设置 WebChromeClient 处理全屏视频
        webChromeClient = new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                customView = view;
                customViewCallback = callback;

                // 添加全屏视图
                fullScreenContainer.addView(customView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                fullScreenContainer.setVisibility(View.VISIBLE);
                swipeRefreshLayout.setVisibility(View.GONE);

                // 进入沉浸式全屏模式
                setFullScreen(true);
                applyFullscreenOrientation();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) {
                    return;
                }

                // 移除全屏视图
                fullScreenContainer.removeView(customView);
                customView = null;
                fullScreenContainer.setVisibility(View.GONE);
                swipeRefreshLayout.setVisibility(View.VISIBLE);

                // 退出全屏模式
                setFullScreen(false);
                resetOrientation();
//                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // 退出全屏时清除常亮
                // 通知 WebChromeClient 全屏已退出
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
            }

            private void setFullScreen(boolean enabled) {
                Window window = getWindow();
                if (enabled) {
                    // 隐藏状态栏和导航栏，进入沉浸式全屏
                    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                        View decorView = window.getDecorView();
                        decorView.setSystemUiVisibility(
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                                        View.SYSTEM_UI_FLAG_FULLSCREEN);
                    }
                } else {
                    // 显示状态栏和导航栏
                    window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                    window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                        View decorView = window.getDecorView();
                        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                    }
                }
            }
        };
        webView.setWebChromeClient(webChromeClient);

        // 下拉刷新监听
        swipeRefreshLayout.setOnRefreshListener(() -> {
            Log.d("WebView", "Refreshing page: " + url);
            webView.loadUrl(url); // 刷新当前 URL
        });

        // 加载网页
        Log.d("WebView", "Initial page load: " + url);
        webView.loadUrl(url);
    }

    private void applyFullscreenOrientation() {
        if (customView == null) {
            return;
        }

        switch (pendingFullscreenOrientation) {
            case "portrait":
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                break;
            case "landscape":
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                break;
            default:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                break;
        }
    }

    private void resetOrientation() {
        pendingFullscreenOrientation = "unlock";
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private class OrientationBridge {
        @JavascriptInterface
        public void setFullscreenOrientation(String orientation) {
            runOnUiThread(() -> {
                pendingFullscreenOrientation = orientation == null ? "unlock" : orientation;
                if (customView != null) {
                    applyFullscreenOrientation();
                }
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                webChromeClient.onHideCustomView();
                return true;
            } else if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Log.d("ScreenKeepOn", "Applying FLAG_KEEP_SCREEN_ON in onResume");
        webView.onPause(); // 暂停 WebView
        webView.pauseTimers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume(); // 恢复 WebView
        webView.resumeTimers();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy(); // 销毁 WebView
            webView = null;
        }
    }
}
