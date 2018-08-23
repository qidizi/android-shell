package qidizi.shell;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;

class Util {

    /**
     * 得到异常字符
     *
     * @param e 异常对象
     * @return 异常字符串
     */
    static String stackTrace(Throwable e) {
        String foo = null;
        try {
            ByteArrayOutputStream ot = new ByteArrayOutputStream();
            e.printStackTrace(new PrintWriter(ot, true));
            foo = ot.toString();
        } catch (Exception f) {
            return "获取异常的堆栈信息时失败了。要获取的异常：" + e.getMessage() + " \n\t 失败原因：" + f.getMessage();
        }
        return foo;
    }

}
