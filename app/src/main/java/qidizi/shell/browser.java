package qidizi.shell;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.*;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.content.pm.*;
import android.os.*;
import java.io.File;
import android.content.*;
import java.io.*;
import android.net.*;
import org.json.*;

public class browser extends Activity
{
    private WebView webView;
    final static private int  MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
		// 要先执行这个，否则toast无法使用
        super.onCreate(savedInstanceState);

		if (showIntent()) {
			return;
		}
		
		bashInit();
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
		
        String path; 

		if ("xiaomi".equalsIgnoreCase(android.os.Build.MANUFACTURER))
		{
			path = "file://" + Environment.getExternalStorageDirectory().toString() + File.separator + getString(R.string.htmlPath);
		}
		else
		{
			path = "https://iguoyi.qidizi.iguoyi.cn/cache/shell/index.html";
		}
		
		webView.loadUrl(path);
    }
	
	private void bashInit(){
		String bin = this.getFilesDir().getAbsolutePath() 
			+ File.separator + "bin" + File.separator;
		
		File binFile = new File(bin);
		
		if (!binFile.exists()){
			binFile.mkdir();
		}
		
		binFile = new File(bin + "busybox");
		
		if (!binFile.exists()){
			InputStream input;
			try{
				input = this.getAssets().open("busybox");
				OutputStream output = new FileOutputStream(binFile);  
				byte[] buffer = new byte[1024];  
				int length = input.read(buffer);
				while(length > 0)
				{
					output.write(buffer, 0, length); 
					length = input.read(buffer);
				}

				output.flush();  
				input.close();  
				output.close();  
				binFile.setExecutable(true,false);
			} catch (Exception e){
				showToast("复制busybox失败:" + e.getMessage());
				return;
			}
			
			try {
				Runtime.getRuntime().exec(binFile.getAbsolutePath() + " --install -s " + bin);
				showToast("busybox安装完成");
			}catch(Exception e){
				showToast("安装busybox异常:" + e.getMessage());
			}
		}
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

    private void showToast(String text)
	{
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
	

    /**
	 显示intent相关信息
	 */
    private boolean showIntent(){
        Intent intent = getIntent();
        String action = intent.getAction();

        if (!intent.ACTION_VIEW.equals(action)) {
            return false;
        }

		String path = intent.getDataString();
		if (null == path || "".equals(path)) return false;
		//path = path.replaceAll(".+com.android.fileexplorer.myprovider.+external_files","") + "#" + path;
        //path = "file://" + path;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        // 将文本内容放到系统剪贴板里。
        cm.setText(path);
        Toast.makeText(this.getApplication(),"路径已经复制成功，可以尝试在浏览器地址栏中粘贴打开。",Toast.LENGTH_LONG).show();
		// 调用后就直接结束app
		this.finish();
		return true;
    }
}
