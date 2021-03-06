package com.flutter_webview_plugin;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by lejard_h on 20/12/2017.
 */

public class BrowserClient extends WebViewClient {
    private Pattern invalidUrlPattern = null;
    private boolean allowCustomSchema = false;

    public BrowserClient() {
        this(null);
    }

    public BrowserClient(String invalidUrlRegex) {
        super();
        if (invalidUrlRegex != null) {
            invalidUrlPattern = Pattern.compile(invalidUrlRegex);
        }
    }

    public void updateInvalidUrlRegex(String invalidUrlRegex) {
        if (invalidUrlRegex != null) {
            invalidUrlPattern = Pattern.compile(invalidUrlRegex);
        } else {
            invalidUrlPattern = null;
        }
    }

    public BrowserClient(boolean allowCustomSchema) {
        super();
        this.allowCustomSchema = allowCustomSchema;
    }

    private boolean checkInvalidUrl(String url) {
        if (invalidUrlPattern == null) {
            return false;
        } else {
            Matcher matcher = invalidUrlPattern.matcher(url);
            return matcher.lookingAt();
        }
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);
        data.put("type", "startLoad");
        FlutterWebviewPlugin.channel.invokeMethod("onState", data);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        Map<String, Object> data = new HashMap<>();
        data.put("url", url);

        FlutterWebviewPlugin.channel.invokeMethod("onUrlChanged", data);

        data.put("type", "finishLoad");
        FlutterWebviewPlugin.channel.invokeMethod("onState", data);

    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request.getUrl().toString();
        String scheme = Uri.parse(url).getScheme();

        if (allowCustomSchema && (!scheme.equals("http") && !scheme.equals("file") && !scheme.equals("about")) ) {
            Map<String, Object> data = new HashMap<>();

            data.put("url", url);

            FlutterWebviewPlugin.channel.invokeMethod("onUrlChanged", data);
            return true;
        }
        else {
            // returning true causes the current WebView to abort loading the URL,
            // while returning false causes the WebView to continue loading the URL as usual.
            boolean isInvalid = checkInvalidUrl(url);
            Map<String, Object> data = new HashMap<>();
            data.put("url", url);
            data.put("type", isInvalid ? "abortLoad" : "shouldStart");

            FlutterWebviewPlugin.channel.invokeMethod("onState", data);
            return isInvalid;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (allowCustomSchema && (!url.startsWith("http") && !url.startsWith("file") && !url.startsWith("about")) ) {
            Map<String, Object> data = new HashMap<>();

            data.put("url", url);

            FlutterWebviewPlugin.channel.invokeMethod("onUrlChanged", data);
            return true;
        }
        else {
            // returning true causes the current WebView to abort loading the URL,
            // while returning false causes the WebView to continue loading the URL as usual.
            boolean isInvalid = checkInvalidUrl(url);
            Map<String, Object> data = new HashMap<>();
            data.put("url", url);
            data.put("type", isInvalid ? "abortLoad" : "shouldStart");

            FlutterWebviewPlugin.channel.invokeMethod("onState", data);
            return isInvalid;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        super.onReceivedHttpError(view, request, errorResponse);
        Map<String, Object> data = new HashMap<>();
        data.put("url", request.getUrl().toString());
        data.put("code", Integer.toString(errorResponse.getStatusCode()));
        FlutterWebviewPlugin.channel.invokeMethod("onHttpError", data);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        super.onReceivedError(view, errorCode, description, failingUrl);
        Map<String, Object> data = new HashMap<>();
        data.put("url", failingUrl);
        data.put("code", errorCode);
        FlutterWebviewPlugin.channel.invokeMethod("onHttpError", data);
    }
}