
namespace app {
    interface AppUtils {
        launchPackage(packageName: string): boolean
        launchApp(appName: string): boolean
        getPackageName(appName: string): string?;
        getAppName(packageName: string): string?;
        openAppSetting(packageName: string?): boolean
        uninstall(packageName: string)
        viewFile(path: string)
        editFile(path: string)
        openUrl(url: string)
        getUriForFile(path: string): android.Uri
        getInstalledPackages(flags: number): Array<android.PackageInfo>
    }

    function getAppUtils(): AppUtils
}