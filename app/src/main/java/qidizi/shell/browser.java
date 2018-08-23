package qidizi.shell;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.*;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.*;
import android.content.pm.*;
import android.os.*;
import java.io.*;

public class browser extends Activity
{
    private WebView webView;
    final static private int  MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // 修改标题栏左边logo与app不同
        //requestWindowFeature(Window.FEATURE_LEFT_ICON);
        //getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.status_bar_logo);
        webView = new WebView(this);
        // 加载进度显示在标题栏上
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        webView.setWebContentsDebuggingEnabled(true);
        //设置WebView属性，能够执行Javascript脚本
        webView.getSettings().setJavaScriptEnabled(true);
        // 启用 window.localStorage
        webView.getSettings().setDomStorageEnabled(true);
        //webView.getSettings().setDefaultTextEncodingName("UTF-8");
        //webView.getSettings().setSaveFormData(true);
        webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress)
                {
                    // 显示实时进度
                    setProgress(newProgress * 100);
                    super.onProgressChanged(view, newProgress);
                }
            });
        webView.getSettings().setUserAgentString(webView.getSettings().getUserAgentString() + " Shell/20180808");
        // WebChromeClient 这个是用来handle js的调用，类似于js的debugger，比如获得所有js的调用结果
        // 解决这个问题： Denied starting an intent without a user gesture, URI http://www.iguoyi.cn/；这个是在页面html render完成时回调
        webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url)
                {
                    // 页面加载完成，显示当前页标题
                    setTitle(webView.getTitle());
                    super.onPageFinished(view, url);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url)
                {
                    //false 表示点击的url交webview来处理，而不是外面浏览器，但是不会拦post请求
                    return false;
                }
            });
        webView.addJavascriptInterface(new JavascriptApi(this), "apk");
        setContentView(webView);
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED)
        {

            // Should we show an explanation?
            if (shouldShowRequestPermissionRationale(
                    Manifest.permission.READ_EXTERNAL_STORAGE))
            {
                // Explain to the user why we need to read the contacts
            }

            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                               MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);

            // MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE is an
            // app-defined int constant that should be quite unique

            return;
        }
        String path = "file://"+Environment.getExternalStorageDirectory().toString() +File.separator+ getString(R.string.htmlPath);
        //path = "https://iguoyi.qidizi.iguoyi.cn/cache/shell/index.html";
         webView.loadUrl(path);

    }

    /**
     * 获取webView
     *
     * @return WebView
     */
    public WebView getWebView()
    {
        return webView;
    }

    /**
     * 绑定标题栏上右上角菜单显示
     *
     * @param menu 菜单
     * @return boolean
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action, menu);
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * 标题栏上右上角菜单点击处理
     *
     * @param item
     * @return
     */

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.help:
                // 打开国艺首页
                Toast.makeText(this, R.string.help, Toast.LENGTH_LONG).show();
                return true;
            case R.id.refresh:
                webView.reload();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * 点击硬件返回键时，如果还能继续后退就执行后退动作，否则弹出结束对话框
     *
     * @param keyCode 按键
     * @param event   事件
     * @return bool
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack())
        {
            webView.goBack(); //goBack()表示返回WebView的上一页面
            return true;
        }
        // 不允许直接退出程序

        new AlertDialog.Builder(this).setTitle("确定退出吗？")
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    // 点击“确认”后的操作
                    browser.this.finish();
                }
            })
            .setNegativeButton("点错了", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    // 点击“返回”后的操作,这里不设置没有任何操作
                }
            }).show();
        return false;
    }
}
