package org.autojs.autojs.devplugin;

interface IDevPluginService {
    // 连接到电脑
    void connectToComputer(String url);

    // 断开连接
    void disconnectFromComputer();

    // 获取连接状态
    boolean isComputerConnected();

    // 获取保存的服务器地址
    String getSavedServerAddress();

    // 启动/停止 USB 调试
    void startUSBDebug();
    void stopUSBDebug();
    boolean isUSBDebugActive();

}