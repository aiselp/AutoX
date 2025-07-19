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
