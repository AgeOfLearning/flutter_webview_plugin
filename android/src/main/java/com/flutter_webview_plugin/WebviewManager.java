package com.flutter_webview_plugin;

import android.content.Intent;
import android.net.Uri;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import android.database.Cursor;
import android.provider.OpenableColumns;
import android.net.Uri.Builder;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.io.File;
import java.util.Date;
import java.io.IOException;
import java.text.SimpleDateFormat;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

import static android.app.Activity.RESULT_OK;

/**
 * Created by lejard_h on 20/12/2017.
 */

class WebviewManager {

    private ValueCallback<Uri> mUploadMessage;
    private ValueCallback<Uri[]> mUploadMessageArray;
    private final static int FILECHOOSER_RESULTCODE = 1;
    private Uri fileUri;
    private Uri videoUri;

    private long getFileSize(Uri fileUri) {
        Cursor returnCursor = context.getContentResolver().query(fileUri, null, null, null, null);
        returnCursor.moveToFirst();
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        return returnCursor.getLong(sizeIndex);
    }

    @TargetApi(7)
    class ResultHandler {
        public boolean handleResult(int requestCode, int resultCode, Intent intent) {
            boolean handled = false;
            if (Build.VERSION.SDK_INT >= 21) {
                if (requestCode == FILECHOOSER_RESULTCODE) {
                    Uri[] results = null;
                    if (resultCode == Activity.RESULT_OK) {
                        if (fileUri != null && getFileSize(fileUri) > 0) {
                            results = new Uri[]{fileUri};
                        } else if (videoUri != null && getFileSize(videoUri) > 0) {
                            results = new Uri[]{videoUri};
                        } else if (intent != null) {
                            results = getSelectedFiles(intent);
                        }
                    }
                    if (mUploadMessageArray != null) {
                        mUploadMessageArray.onReceiveValue(results);
                        mUploadMessageArray = null;
                    }
                    handled = true;
                }
            } else {
                if (requestCode == FILECHOOSER_RESULTCODE) {
                    Uri result = null;
                    if (resultCode == RESULT_OK && intent != null) {
                        result = intent.getData();
                    }
                    if (mUploadMessage != null) {
                        mUploadMessage.onReceiveValue(result);
                        mUploadMessage = null;
                    }
                    handled = true;
                }
            }
            return handled;
        }
    }

    private Uri[] getSelectedFiles(Intent data) {
        // we have one files selected
        if (data.getData() != null) {
            String dataString = data.getDataString();
            if (dataString != null) {
                return new Uri[]{Uri.parse(dataString)};
            }
        }
        // we have multiple files selected
        if (data.getClipData() != null) {
            final int numSelectedFiles = data.getClipData().getItemCount();
            Uri[] result = new Uri[numSelectedFiles];
            for (int i = 0; i < numSelectedFiles; i++) {
                result[i] = data.getClipData().getItemAt(i).getUri();
            }
            return result;
        }
        return null;
    }

    boolean closed = false;
    WebView webView;
    Activity activity;
    BrowserClient webViewClient;
    ResultHandler resultHandler;
    Context context;

    WebviewManager(final Activity activity, final Context context) {
        this.webView = new ObservableWebView(activity);
        this.activity = activity;
        this.context = context;
        this.resultHandler = new ResultHandler();
        webViewClient = new BrowserClient();

        webView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_BACK:
                            if (webView.canGoBack()) {
                                webView.goBack();
                            } else {
                                FlutterWebviewPlugin.channel.invokeMethod("onBack", null);
                            }
                            return true;
                    }
                }

                return false;
            }
        });

        ((ObservableWebView) webView).setOnScrollChangedCallback(new ObservableWebView.OnScrollChangedCallback() {
            public void onScroll(int x, int y, int oldx, int oldy) {
                Map<String, Object> yDirection = new HashMap<>();
                yDirection.put("yDirection", (double) y);
                FlutterWebviewPlugin.channel.invokeMethod("onScrollYChanged", yDirection);
                Map<String, Object> xDirection = new HashMap<>();
                xDirection.put("xDirection", (double) x);
                FlutterWebviewPlugin.channel.invokeMethod("onScrollXChanged", xDirection);
            }
        });

        webView.setWebViewClient(webViewClient);
        webView.setWebChromeClient(new WebChromeClient() {
            //The undocumented magic method override
            //Eclipse will swear at you if you try to put @Override here
            // For Android 3.0+
            public void openFileChooser(ValueCallback<Uri> uploadMsg) {

                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);

            }

            // For Android 3.0+
            public void openFileChooser(ValueCallback uploadMsg, String acceptType) {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                activity.startActivityForResult(
                        Intent.createChooser(i, "File Browser"),
                        FILECHOOSER_RESULTCODE);
            }

            //For Android 4.1
            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);

            }

            //For Android 5.0+
            public boolean onShowFileChooser(
                    WebView webView, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (mUploadMessageArray != null) {
                    mUploadMessageArray.onReceiveValue(null);
                }
                mUploadMessageArray = filePathCallback;

                final String[] acceptTypes = getSafeAcceptedTypes(fileChooserParams);
                List<Intent> intentList = new ArrayList<Intent>();
                fileUri = null;
                videoUri = null;
                if (acceptsImages(acceptTypes)) {
                    Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    fileUri = getOutputFilename(MediaStore.ACTION_IMAGE_CAPTURE);
                    takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                    intentList.add(takePhotoIntent);
                }
                if (acceptsVideo(acceptTypes)) {
                    Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                    videoUri = getOutputFilename(MediaStore.ACTION_VIDEO_CAPTURE);
                    takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
                    intentList.add(takeVideoIntent);
                }
                Intent contentSelectionIntent;
                if (Build.VERSION.SDK_INT >= 21) {
                    final boolean allowMultiple = fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
                    contentSelectionIntent = fileChooserParams.createIntent();
                    contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
                } else {
                    contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                    contentSelectionIntent.setType("*/*");
                }
                Intent[] intentArray = intentList.toArray(new Intent[intentList.size()]);

                Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
                activity.startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                Map<String, Object> args = new HashMap<>();
                args.put("progress", progress / 100.0);
                FlutterWebviewPlugin.channel.invokeMethod("onProgressChanged", args);
            }
        });
    }

    private Uri getOutputFilename(String intentType) {
        String prefix = "";
        String suffix = "";

        if (intentType == MediaStore.ACTION_IMAGE_CAPTURE) {
            prefix = "image-";
            suffix = ".jpg";
        } else if (intentType == MediaStore.ACTION_VIDEO_CAPTURE) {
            prefix = "video-";
            suffix = ".mp4";
        }

        String packageName = context.getPackageName();
        File capturedFile = null;
        try {
            capturedFile = createCapturedFile(prefix, suffix);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return FileProvider.getUriForFile(context, packageName + ".fileprovider", capturedFile);
    }

    private File createCapturedFile(String prefix, String suffix) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = prefix + "_" + timeStamp;
        File storageDir = context.getExternalFilesDir(null);
        return File.createTempFile(imageFileName, suffix, storageDir);
    }

    private Boolean acceptsImages(String[] types) {
        return isArrayEmpty(types) || arrayContainsString(types, "image");
    }

    private Boolean acceptsVideo(String[] types) {
        return isArrayEmpty(types) || arrayContainsString(types, "video");
    }

    private Boolean arrayContainsString(String[] array, String pattern) {
        for (String content : array) {
            if (content.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private Boolean isArrayEmpty(String[] arr) {
        // when our array returned from getAcceptTypes() has no values set from the
        // webview
        // i.e. <input type="file" />, without any "accept" attr
        // will be an array with one empty string element, afaik
        return arr.length == 0 || (arr.length == 1 && arr[0].length() == 0);
    }

    private String[] getSafeAcceptedTypes(WebChromeClient.FileChooserParams params) {

        // the getAcceptTypes() is available only in api 21+
        // for lower level, we ignore it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return params.getAcceptTypes();
        }

        final String[] EMPTY = {};
        return EMPTY;
    }

    static void setCookies(String url, ArrayList<String> cookies) {
        String cookieUrl = getCookieUrl(url);
        CookieManager cm = CookieManager.getInstance();
        for (String cookie : cookies) {
            cm.setCookie(cookieUrl, cookie);
        }
        cm.flush();
    }

    static String getCookieUrl(String url) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        String authority = uri.getAuthority();

        Builder builder = new Uri.Builder();
        builder.scheme(scheme)
                .authority(authority)
                .path("/");

        return builder.build().toString();
    }

    static void clearCookies() {
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(null);
        cm.flush();
    }

    private void clearCache() {
        webView.clearCache(true);
        webView.clearFormData();
    }

    void openUrl(
            boolean withJavascript,
            boolean clearCache,
            boolean hidden,
            boolean clearCookies,
            ArrayList<String> cookies,
            String userAgent,
            String url,
            Map<String, String> headers,
            boolean withZoom,
            boolean withLocalStorage,
            boolean scrollBar,
            boolean supportMultipleWindows,
            boolean appCacheEnabled,
            boolean allowFileURLs,
            boolean useWideViewPort,
            String invalidUrlRegex,
            boolean geolocationEnabled,
            boolean debuggingEnabled,
            int minFontSize
    ) {
        webView.getSettings().setJavaScriptEnabled(withJavascript);
        webView.getSettings().setBuiltInZoomControls(withZoom);
        webView.getSettings().setSupportZoom(withZoom);
        webView.getSettings().setDomStorageEnabled(withLocalStorage);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(supportMultipleWindows);

        webView.getSettings().setSupportMultipleWindows(supportMultipleWindows);

        webView.getSettings().setAppCacheEnabled(appCacheEnabled);

        webView.getSettings().setAllowFileAccessFromFileURLs(allowFileURLs);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(allowFileURLs);
        webView.getSettings().setMinimumFontSize(minFontSize);
        webView.getSettings().setMinimumLogicalFontSize(minFontSize);

        webView.getSettings().setUseWideViewPort(useWideViewPort);

        // Handle debugging
        webView.setWebContentsDebuggingEnabled(debuggingEnabled);

        webViewClient.updateInvalidUrlRegex(invalidUrlRegex);

        if (geolocationEnabled) {
            webView.getSettings().setGeolocationEnabled(true);
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                    callback.invoke(origin, true, false);
                }
            });
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        if (clearCache) {
            clearCache();
        }

        if (hidden) {
            webView.setVisibility(View.GONE);
        }

        if (clearCookies) {
            clearCookies();
        }

        if (cookies != null && !cookies.isEmpty()) {
            setCookies(url, cookies);
        }

        if (userAgent != null) {
            webView.getSettings().setUserAgentString(userAgent);
        }

        if (!scrollBar) {
            webView.setVerticalScrollBarEnabled(false);
        }

        if (headers != null) {
            webView.loadUrl(url, headers);
        } else {
            webView.loadUrl(url);
        }
    }

    void reloadUrl(String url) {
        webView.loadUrl(url);
    }

    void close(MethodCall call, MethodChannel.Result result) {
        if (webView != null) {
            ViewGroup vg = (ViewGroup) (webView.getParent());
            vg.removeView(webView);
        }
        webView = null;
        if (result != null) {
            result.success(null);
        }

        closed = true;
        FlutterWebviewPlugin.channel.invokeMethod("onDestroy", null);
    }

    void close() {
        close(null, null);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    void eval(MethodCall call, final MethodChannel.Result result) {
        String code = call.argument("code");

        webView.evaluateJavascript(code, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                result.success(value);
            }
        });
    }

    /**
     * Reloads the Webview.
     */
    void reload(MethodCall call, MethodChannel.Result result) {
        if (webView != null) {
            webView.reload();
        }
    }

    /**
     * Navigates back on the Webview.
     */
    void back(MethodCall call, MethodChannel.Result result) {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        }
    }

    /**
     * Navigates forward on the Webview.
     */
    void forward(MethodCall call, MethodChannel.Result result) {
        if (webView != null && webView.canGoForward()) {
            webView.goForward();
        }
    }

    void resize(FrameLayout.LayoutParams params) {
        webView.setLayoutParams(params);
    }

    /**
     * Checks if going back on the Webview is possible.
     */
    boolean canGoBack() {
        return webView.canGoBack();
    }

    /**
     * Checks if going forward on the Webview is possible.
     */
    boolean canGoForward() {
        return webView.canGoForward();
    }

    void hide(MethodCall call, MethodChannel.Result result) {
        if (webView != null) {
            webView.setVisibility(View.GONE);
        }
    }

    void show(MethodCall call, MethodChannel.Result result) {
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
        }
    }

    void stopLoading(MethodCall call, MethodChannel.Result result) {
        if (webView != null) {
            webView.stopLoading();
        }
    }
}
