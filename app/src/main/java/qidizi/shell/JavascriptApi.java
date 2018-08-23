package qidizi.shell;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.io.File;
import android.webkit.*;
import org.json.*;
import java.lang.Process;
import android.os.Environment;
import android.os.*;
import java.io.InputStreamReader;
import java.io.BufferedInputStream;
import java.io.BufferedReader;

/**
 * 实现js api给web调用
 */
class JavascriptApi
{
	// 只允许一个命令运行
	static Boolean runing = false;
    private browser myBrowser;

    /**
     * Instantiate the interface and set the context
     */
    JavascriptApi(browser c)
    {
        myBrowser = c;
    }

    @JavascriptInterface
    public void bash(
        String json
    )
    {
		if (runing){
			showToast("不支持并发bash");
			return;
		}
		try{
        BashTask task = new BashTask(myBrowser);
		JSONArray obj = new JSONArray(json);
		String[] args =new String[ obj.length()];
		
		for(int i= 0;i<obj.length();i++){
			args[i]=obj.getString(i);
		}
		
		// 如果 thumb 为空字符串，doInBackground就不会执行
		runing = true;
		 task.execute(args);
		} catch(Exception e){
			showToast("bash 调用出现异常：" + e.getStackTrace().toString());
		}
    }




    private void showToast(String text)
    {
        Toast.makeText(myBrowser, text, Toast.LENGTH_LONG).show();
    }
    
    /**
     * <参数类型,进度值,任务最终值>
     */
    static private class BashTask extends AsyncTask<String[], Void, String[]>
    {
        // 使用弱引用方式，防止内存漏泄
        final private WeakReference<browser> myBrowser;

        BashTask(browser br)
        {
            this.myBrowser = new WeakReference<browser>(br);
        }
        
        /**
         * 它的参数是通过new Task().execute(参数)传递的
         */
        protected String[] doInBackground(String[]... params)
        {
            String[] result = new String[]{"成功","success"};

            try
            {
                String[] env=new String[]{""};
                File dir = new File(Environment.getExternalStorageDirectory().toString() +File.separator+"-/shell");
                Process ps =Runtime.getRuntime().exec(params[0], env, dir);

                //这句话就是shell与高级语言间的调用
                //如果有参数的话可以用另外一个被重载的exec方法
                //实际上这样执行时启动了一个子进程,它没有父进程的控制台
                //也就看不到输出,所以我们需要用输出流来得到shell执行后的输出
                InputStream inputstream = ps.getInputStream();
                InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
                BufferedReader bufferedreader = new BufferedReader(inputstreamreader);
                // read the ls output
                String line = "";
                StringBuilder sb = new StringBuilder(line);
                while ((line = bufferedreader.readLine()) != null)
                {
                    //System.out.println(line);
                    sb.append(line);
                    sb.append('\n');
                }
                //tv.setText(sb.toString());
                //使用exec执行不会等执行成功以后才返回,它会立即返回
                //所以在某些情况下是很要命的(比如复制文件的时候)
                //使用wairFor()可以等待命令执行完成以后才返回
                if (ps.waitFor() != 0)
                {
                    System.err.println("exit value = " + ps.exitValue());
                } 
                result[0] = sb.toString();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                result[1] = "bash 运行失败：" + e.getMessage();
                result[0] = "fail";
            }

            return result;
        }

        /**
         * resut 来自 doInBackground return
         * ui 更新只能在这里处理
         * 注意，只有签名与状态为发布的才行，否则就会闪下
         */
        protected void onPostExecute(String[] result)
        {
            WebView web = myBrowser.get().getWebView();

            if (null == web)
            {
                showToast("无法获取webview");
                return;
            }

           JSONObject json = new JSONObject();
           String arg = "";
           
           try{
           for (int i = 0;i < result.length;i++){
               json.put(i+"",result[i]);
           }
           
           arg = json.toString();
           }catch(Exception e){
               arg = "获取结果异常";
           }
            web.loadUrl("javascript:try{" +
                "myAlert(" + arg + ");" +
                "} catch(e) { alert('apk 执行js异常:\\n' + e); }");
        }

        private void showToast(String text)
        {
            Toast.makeText(myBrowser.get(), text, Toast.LENGTH_LONG).show();
        }
    }
}
