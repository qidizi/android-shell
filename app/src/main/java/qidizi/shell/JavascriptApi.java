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
import java.util.*;
import android.content.pm.*;
import android.content.*;

/**
 * 实现js api给web调用
 */
class JavascriptApi
{
    // 猜测loadUrl可能会有长度限制，使用js 拉 java方式来得到结果
    private static ArrayList<String> result = new ArrayList<String>();
    private browser myBrowser;
    private static BashTask task = null;
	private static Process ps = null;

    /**
     * Instantiate the interface and set the context
     */
    JavascriptApi(browser c)
	{
        myBrowser = c;
    }

    @JavascriptInterface
    public String bash(String cmd)
	{
        String usage = "用法：apk.bash(['ls','-a', '/data/data/qidizi.shell'])";

        if (null != task && AsyncTask.Status.FINISHED != task.getStatus())
		{
            return "不支持并发！上一bash命令未完成";
        }

        if (null == cmd)
		{
            return usage;
        }

		JSONArray json ;

		try
		{
			json = new JSONArray(cmd);
		}
		catch (Exception e)
		{
			return "bash参数不是 json-encode 字符串";
		}

		if (json.length() < 1)
		{
			return "bash 参数array长度不能小于1";
		}

		List<String> cmds = new ArrayList<String>();

		try
		{
			for (int i = 0;i < json.length();i++)
			{
				String line = json.getString(i).trim();
				if ("".equals(line)) continue;
				cmds.add(line);
			}
		}
		catch (Exception e)
		{
			return "bash 参数array某元素不是字符串类型：" + e.getMessage();
		}

		if (cmds.size() < 1) return "bash 参数不能为空";
        task = new BashTask(myBrowser);
        // 如果 thumb 为空字符串，doInBackground就不会执行
        task.execute(cmds);
        // 只能返回这个值才表示执行成功
        return "SUCCESS";
    }

    @JavascriptInterface
    public String ctrlC()
	{
        if (null != task && AsyncTask.Status.FINISHED != task.getStatus())
		{
			if (null != ps) {
				ps.destroy();
				ps = null;
			}
			
            return task.cancel(true) ? "SUCCESS" : "无法取消";
        }

        return "未运行";
    }

    @JavascriptInterface
    public String popResult()
	{
        if (result.size() > 0)
		{
            String line = result.get(0);
            result.remove(0);
            return line;
        }

        return null;
    }

    /**
     * <参数类型,进度值,任务最终值>
     */
    static private class BashTask extends AsyncTask<List<String>, String, Void>
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
        protected Void doInBackground(List<String>... params)
		{
            ProcessBuilder pb = new ProcessBuilder(params[0]);
            pb.redirectErrorStream(true);
			Map<String,String> env = pb.environment();
			String files = myBrowser.get().getApplicationContext().getFilesDir().getAbsolutePath() + "/bin";
			env.put("PATH",files + ":" + env.get("PATH"));
			pb.directory(new File(files));

            try
			{
				if (null != ps){
					ps.destroy();
					ps = null;
				}
				
                ps = pb.start();
            }
			catch (Exception e)
			{
                JavascriptApi.result.add("error>运行bash失败：" + e.getMessage());
				return null;
            }

            InputStream inputstream = ps.getInputStream();
            InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
            BufferedReader bufferedreader = new BufferedReader(inputstreamreader);
            String line;

            try
			{
                while ((line = bufferedreader.readLine()) != null)
				{
                    JavascriptApi.result.add("out>" +line);

                    if (isCancelled())
					{
                        // 要求退出
                        ps.destroy();
						JavascriptApi.result.add("cancell>检测到退出信号");
						return null;
                    }
                }
            }
			catch (Exception e)
			{
                ps.destroy();
                JavascriptApi.result.add("error>读取bash输出异常：" + e.getMessage());
				return null;
            }

            //使用exec执行不会等执行成功以后才返回,它会立即返回
            //所以在某些情况下是很要命的(比如复制文件的时候)
            //使用waitFor()可以等待命令执行完成以后才返回

            try
			{
                // 如果这个子进程已经被其它线程要求waitFor时，就会出现异常
                if (0 != ps.waitFor())
				{
					ps.destroy();
                    JavascriptApi.result.add("error>bash异常退出码：" + ps.exitValue());
					return null;
                }
            }
			catch (Exception e)
			{
                ps.destroy();
                JavascriptApi.result.add("error>尝试hold直到bash退出失败：" + e.getMessage());
				return null;
            }

            JavascriptApi.result.add("exit>" + ps.exitValue());
			return null;
        }
    }
}
