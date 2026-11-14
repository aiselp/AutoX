package org.autojs.autojs.tool;

import com.jaredrummler.ktsh.Shell;
import com.stardust.autojs.core.util.ProcessShell;

/**
 * Created by Stardust on 2018/1/26.
 */

public class RootTool {

    public static boolean isRootAvailable() {
        Shell shell = new Shell("sh");
        try {
            Shell.Command.Result result = shell.run("command -v su");
            return result.isSuccess();
        } catch (Exception e) {
            return false;
        } finally {
            shell.shutdown();
        }
    }

    /**
     * 真正以 root 身份执行命令，判断「当前进程（主进程）是否已获得 root」。
     * 用于区分两种失败：
     *  1) 应用完全没有 root 权限（主进程也拿不到 root）；
     *  2) 应用有 root，但 :script 进程未被授权（Magisk 按进程名拒绝）。
     */
    public static boolean isRootGranted() {
        try {
            com.stardust.autojs.core.util.ProcessShell.Result result =
                    com.stardust.autojs.core.util.ProcessShell.execCommand("id", true);
            return result.code == 0 && result.result != null && result.result.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    private static final String cmd = "enabled=$(settings get system pointer_location)\n" +
            "if [[ $enabled == 1 ]]\n" +
            "then\n" +
            "settings put system pointer_location 0\n" +
            "else\n" +
            "settings put system pointer_location 1\n" +
            "fi\n";

    public static void togglePointerLocation() {
        try {
            ProcessShell.execCommand(cmd, true);
        } catch (Exception ignored) {
        }
    }

    public static void setPointerLocationEnabled(boolean enabled) {
        try {
            ProcessShell.execCommand("settings put system pointer_location " + (enabled ? 1 : 0), true);
        } catch (Exception ignored) {

        }
    }
}
