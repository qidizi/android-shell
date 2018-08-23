package qidizi.shell;

import android.os.AsyncTask;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.io.File;

import android.webkit.*;
import org.json.*;

import java.lang.Process;

import android.os.Environment;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.ArrayList;

/**
 * 实现js api给web调用
 */
class JavascriptApi {
    // 只允许一个命令运行
    private static Boolean running = false;
    // 猜测loadUrl可能会有长度限制，使用js 拉 java方式来得到结果
    private static ArrayList<String> result = new ArrayList<String>();
    private browser myBrowser;
    private static BashTask task;

    /**
     * Instantiate the interface and set the context
     */
    JavascriptApi(browser c) {
        myBrowser = c;
    }

    @JavascriptInterface
    public String bash(String cmd) {
        String usage = "用法：apk.bash('ls')";

        if (running) {
            return "不支持并发bash";
        }

        if (null == cmd) {
            return usage;
        }

        task = new BashTask(myBrowser);
        // 如果 thumb 为空字符串，doInBackground就不会执行
        running = true;
        task.execute(cmd);
        // 只能返回这个值才表示执行成功
        return "SUCCESS";
    }

    @JavascriptInterface
    public String CtrlC() {
        if (running) {
            return task.cancel(true) ? "SUCCESS" : "FAIL";
        }

        return "NOT_RUNNING";
    }

    @JavascriptInterface
    public String popLine() {
        if (result.size() > 0) {
            String line = result.get(0);
            result.remove(0);
            return line;
        }

        return null;
    }

    private void showToast(String text) {
        Toast.makeText(myBrowser, text, Toast.LENGTH_LONG).show();
    }

    /**
     * <参数类型,进度值,任务最终值>
     */
    static private class BashTask extends AsyncTask<String, String, String> {
        // 使用弱引用方式，防止内存漏泄
        final private WeakReference<browser> myBrowser;

        BashTask(browser br) {
            this.myBrowser = new WeakReference<browser>(br);
        }

        /**
         * 它的参数是通过new Task().execute(参数)传递的
         */
        protected String doInBackground(String... params) {
            Process ps;
            ProcessBuilder pb = new ProcessBuilder(params[0]);
            pb.redirectErrorStream(true);
            pb.directory(new File(Environment.getExternalStorageDirectory().toString()));

            try {
                ps = pb.start();
            } catch (Exception e) {
                return "无法启动bash线程，原因：" + e.getMessage();
            }

            InputStream inputstream = ps.getInputStream();
            InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
            BufferedReader bufferedreader = new BufferedReader(inputstreamreader);
            String line;

            try {
                while ((line = bufferedreader.readLine()) != null) {
                    publishProgress(line);

                    if (isCancelled()) {
                        // 要求退出
                        ps.destroy();
                    }
                }
            } catch (Exception e) {
                ps.destroy();
                return "读取bash输出行出错信息：" + e.getMessage();
            }

            //使用exec执行不会等执行成功以后才返回,它会立即返回
            //所以在某些情况下是很要命的(比如复制文件的时候)
            //使用waitFor()可以等待命令执行完成以后才返回

            try {
                // 如果这个子进程已经被其它线程要求waitFor时，就会出现异常
                if (0 != ps.waitFor()) {
                    return "bash 线程未能正常结束，状态码：\n" + ps.exitValue();
                }
            } catch (Exception e) {
                ps.destroy();
                return "无法要求bash hold线程：" + e.getMessage();
            }

            return "SUCCESS";
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onCancelled(String s) {
            super.onCancelled(s);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            result.add(values[0]);
            bashEcho("read", values[0]);
        }

        /**
         * resut 来自 doInBackground return
         * ui 更新只能在这里处理
         * 注意，只有签名与状态为发布的才行，否则就会闪下
         */
        protected void onPostExecute(String line) {
            JavascriptApi.running = false;
            bashEcho("SUCCESS".equals(line) ? "SUCCESS" : "FAIL", line);
        }

        private void bashEcho(String status, String line) {
            myBrowser.get().getWebView().loadUrl("javascript:" +
                    "+function(status,line){" +
                    "if('function' !== typeof bashEcho) return alert('function bashEcho(status,line);不存在，无法接收响应');" +
                    "bashEcho(status,line)" +
                    "}('" + status + "'," + (null == line ? "apk.popLine()" : line) + ");");
        }

        private void showToast(String text) {
            Toast.makeText(myBrowser.get(), text, Toast.LENGTH_LONG).show();
        }
    }
}
