package com.companyname;

import android.app.Activity;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.filter.logging.LogLevel;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.transport.socket.DatagramSessionConfig;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.NioDatagramConnector;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.egret.runtime.launcherInterface.INativePlayer;
import org.egret.egretnativeandroid.EgretNativeAndroid;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

//Android项目发布设置详见doc目录下的README_ANDROID.md

public class MainActivity extends Activity {
    private final String TAG = "MainActivity";
    private EgretNativeAndroid nativeAndroid;
    // 对egret添加原生android功能

    private boolean engineInited = false; // 判断是否初始化

//    private static String serverUrl = "";
//    private String gameConfig = "game/gameConfig.json";
//    private String gameUrl = "";//GameUrl
//    private String preloadPath = "";//预加载路径

    private PowerManager.WakeLock mWakelock; // 设置常亮 需要权限

    //----------------------------------------------------------------------native登录相关---------------------------------------------------------------



    private Handler hidePreBGHandler; //  Android 中用于实现线程间通信的类，通过它可以在后台线程和主线程之间传递消息和执行代码。它是处理异步任务和定时操作的重要工具。
    private Runnable hideRunnable;


    private TextView launchText = null;//提示信息Text

    private int processCode = 0;//加载进度编号

    private float progressLevel;
    private Timer progressTimer;
    private TimerTask progressTimerTask;
    private Handler viewHandler; //  Android 中用于实现线程间通信的类，通过它可以在后台线程和主线程之间传递消息和执行代码。它是处理异步任务和定时操作的重要工具。

    // TCP活跃计时器
    private Timer tcpSocketTimer;
    private TimerTask tcpSocketTimerTask;

    // UDP活跃计时器
    private Timer udpSocketTimer;
    private TimerTask udpSocketTimerTask;

    private boolean closeSocketFlg = false;//前台关闭socket标志

//    SensorManager mSensorManager;// 定义Sensor管理器
//
//    Sensor rotationVectorSensor;// 旋转矢量传感器
//    SensorEventListener rotationVectorSensorListener;// 旋转矢量传感器监听器

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 对于活动进行分配
        AppConst.addActivity(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//屏幕保持常亮 后续需要移除 getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AppConst.MAIN_ACTIVITY = this;

        nativeAndroid = new EgretNativeAndroid(this);

        if (!nativeAndroid.checkGlEsVersion()) {
            Toast.makeText(this, "This device does not support OpenGL ES 2.0.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        nativeAndroid.config.showFPS = true;    // 将在屏幕上显示FPS 每秒帧数
        nativeAndroid.config.fpsLogTime = 30;      // 日志加载时间间隔
        nativeAndroid.config.disableNativeRender = false; // 不启用 native 渲染
        nativeAndroid.config.clearCache = false; // 将会在启动应用时 保留 Egret 引擎的缓存数据
        nativeAndroid.config.loadingTimeout = 0; // 没有加载超时限制
        nativeAndroid.config.immersiveMode = true; // 沉浸式模式
        nativeAndroid.config.useCutout = true; // 适应屏幕的切口区域


        setExternalInterfaces();
        
        if (!nativeAndroid.initialize("http://tool.egret-labs.org/Weiduan/game/index.html")) {
            Toast.makeText(this, "Initialize native failed.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        setContentView(nativeAndroid.getRootFrameLayout());
    }

    @Override
    protected void onPause() {
        super.onPause();
        nativeAndroid.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        nativeAndroid.resume();
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent keyEvent) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            nativeAndroid.exitGame();
        }

        return super.onKeyDown(keyCode, keyEvent);
    }

    private void setExternalInterfaces() {
        nativeAndroid.setExternalInterface("sendToNative", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String message) {
                Log.d(TAG, "Get message: " + message);
                nativeAndroid.callExternalInterface("sendToJS", "Get message: " + message);
            }
        });
        nativeAndroid.setExternalInterface("@onState", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String message) {
                Log.e(TAG, "Get @onState: " + message);
            }
        });
        nativeAndroid.setExternalInterface("@onError", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String message) {
                Log.e(TAG, "Get @onError: " + message);
            }
        });
        nativeAndroid.setExternalInterface("@onJSError", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String message) {
                Log.e(TAG, "Get @onJSError: " + message);
            }
        });

        // 线下手柄
///////////////////////////////////////////tcp socket////////////////////////////////////////////

        nativeAndroid.setExternalInterface("startTCPSocket", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String s) {
                try {
                    startTCPSocket(s);
                } catch (IOException e) {
                    e.printStackTrace();
                    nativeAndroid.callExternalInterface("tcpSocketConnectError", "");
                }
            }
        });

        nativeAndroid.setExternalInterface("closeTCPSocket", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String s) {
                closeTCPSocket();
            }
        });

        nativeAndroid.setExternalInterface("tcpSocketSendMessage", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String s) {
                tcpSocketSendMessage(s);
            }
        });

        nativeAndroid.setExternalInterface("startActivityMsgToTCPSocketTimer", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String message) {
//                Log.i("MainActivity","*******************startActivityMsgToTv1SocketTimer");
                startActivityMsgToTCPSocketTimer();
            }
        });
        nativeAndroid.setExternalInterface("stopActivityMsgToTCPSocketTimer", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String message) {
//                Log.i("MainActivity","*******************stopActivityMsgToTv1SocketTimer");
                stopActivityMsgToTCPSocketTimer();
            }
        });

        ///////////////////////////////////////////udp socket////////////////////////////////////////////

        nativeAndroid.setExternalInterface("startSocketUdp", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String s) {
                try {
                    startSocketUdp(s);
                } catch (IOException e) {
                    e.printStackTrace();
                    nativeAndroid.callExternalInterface("socketUdpConnectError", "");
                }
            }
        });

        //发送活跃计时器
        nativeAndroid.setExternalInterface("startActivityMsgToUdpSocket", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String s) {
                startActivityMsgToUdpSocket();
            }
        });

        //停止活跃计时器
        nativeAndroid.setExternalInterface("stopActivityMsgToUdpSocket", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String s) {
                stopActivityMsgToUdpSocket();
            }
        });

        nativeAndroid.setExternalInterface("closeSocketUdp", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String s) {
                closeSocketUdp();
            }
        });

        nativeAndroid.setExternalInterface("sendXmlToUdpServer", new INativePlayer.INativeInterface() {
            @Override
            public void callback(String s) {
                socketUdpSendMessage(s);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * 线程池，避免阻塞主线程，与服务器建立连接使用，创建一个只有单线程的线程池，尽快执行线程的线程池
     */
    private static ExecutorService executorService = Executors.newSingleThreadExecutor();


    /******************************************** Socket start********************************************/
    // 启动gameserver心跳计时器 判断是否断开
    private void startActivityMsgToTCPSocketTimer() {
        Log.i("MainActivity", "*****************************startActivityMsgToTCPSocketTimer aaa");
        //防止多次开启计时器
        if (tcpSocketTimer != null) {
            tcpSocketTimer.cancel();
            tcpSocketTimer = null;
        }
        if (tcpSocketTimerTask != null) {
            tcpSocketTimerTask = null;
        }

        tcpSocketTimerTask = new TimerTask() {

            @Override
            public void run() {
                Log.i("MainActivity", "*****************************startActivityMsgToTCPSocketTimer bbb");
                String activityXml = "<A><B><N>test001</N></B></A>" + "\n";
                tcpSocketSendMessage(activityXml);
            }
        };

        tcpSocketTimer = new Timer();
        tcpSocketTimer.schedule(tcpSocketTimerTask, 0, 30000);
    }

    // 停止gameserver心跳计时器
    private void stopActivityMsgToTCPSocketTimer() {
        Log.i("MainActivity", "*****************************stopActivityMsgToTCPSocketTimer");
        if (tcpSocketTimer != null) {
            tcpSocketTimer.cancel();
            tcpSocketTimer = null;
        }
        if (tcpSocketTimerTask != null) {
            tcpSocketTimerTask.cancel();
            tcpSocketTimerTask = null;
        }
    }

    /*------------------------------------- TCP Socket----------------------------------------*/
    /**
     * 线程池，避免阻塞主线程，与服务器建立连接使用，创建一个只有单线程的线程池，尽快执行线程的线程池
     */

    private static ExecutorService executorService1 = Executors.newSingleThreadExecutor();

    // 连接对象
    private NioSocketConnector tcpConnection;

    // session对象
    private IoSession tcpSession;

    // 连接服务器的地址
    private InetSocketAddress tcpAddress;

    private ConnectFuture tcpConnectFuture;

    // 连接 TCP Socket 并且判断是否存在 tcp连接 并关闭
    // 传输数据 是 地址 + _ + 端口
    private void startTCPSocket(String info) throws IOException {
        if (tcpSession != null && tcpSession.isConnected()) {
            tcpSession.closeNow(); // 进行关闭 但是并没有修改状态
        } else {
            Log.i("melog", "----------------------------------------TCP socket获取连接信息");
            String[] array = info.split("_");
            String socketAddress = array[0];
            String socketPort = array[1];
            Log.e("MinaSocket", "startSocket socketAddress = " + socketAddress + "   :   socketPort = " + socketPort);

            //初始化配置信息
            initTCPSocket(socketAddress, socketPort);

            //开始连接
            connectTCPSocket();

            Log.e("MinaSocket", "startSocket End");
        }
    }

    /**
     * 初始化电视1 Mina配置信息
     */
    private void initTCPSocket(String socketIp, String socketPort) {
        try {
            tcpAddress = new InetSocketAddress(socketIp, Integer.parseInt(socketPort));//连接地址,此数据可改成自己要连接的IP和端口号
            tcpConnection = new NioSocketConnector();// 创建连接
            // 设置读取数据的缓存区大小
            SocketSessionConfig socketSessionConfig = tcpConnection.getSessionConfig();
            socketSessionConfig.setReadBufferSize(1024);
            socketSessionConfig.setWriteTimeout(120);//设置写超时为120秒
            socketSessionConfig.setIdleTime(IdleStatus.BOTH_IDLE, 15 * 60);//设置15分钟没有读写操作进入空闲状态
            LoggingFilter loggingFilter = new LoggingFilter();
            loggingFilter.setMessageSentLogLevel(LogLevel.DEBUG);
            loggingFilter.setMessageReceivedLogLevel(LogLevel.DEBUG);
            tcpConnection.getFilterChain().addLast("logging", loggingFilter);//logging过滤器
            tcpConnection.getFilterChain().addLast("codec", new ProtocolCodecFilter(new TextLineCodecFactory(Charset.forName("UTF-8"))));//自定义解编码器
            tcpConnection.setHandler(new TCPDefaultHandler());//设置handler
            tcpConnection.setDefaultRemoteAddress(tcpAddress);//设置地址
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建连接 电视1 Socket
     */
    private void connectTCPSocket() {

        FutureTask<Void> futureTask = new FutureTask<>(new Callable<Void>() {
            @Override
            public Void call() {//

                try {
                    while (true) {
                        tcpConnectFuture = tcpConnection.connect();

                        tcpConnectFuture.awaitUninterruptibly();//一直等到他连接为止
                        tcpSession = tcpConnectFuture.getSession();//获取session对象
                        if (tcpSession != null && tcpSession.isConnected()) {
//                            Log.e("TV1 MinaSocket", "连接成功");

                            // 发送TCP Socket连接成功到前台
                            nativeAndroid.callExternalInterface("tcpSocketConnectSuccess", "");

                            break;
                        }
                        Thread.sleep(3000);//每隔三秒循环一次
                    }
                } catch (Exception e) {//连接异常
//                    Log.e("TV1 MinaSocket", "socketConnectError " + e.getMessage());
                    nativeAndroid.callExternalInterface("tcpSocketConnectError", "");
                }
                return null;
            }
        });
        executorService.execute(futureTask);//执行连接线程
    }

    // 前台主动断开TCP socket
    private void closeTCPSocket() {
        if (tcpSession != null && tcpSession.isConnected()) {
            closeSocketFlg = true;
            tcpSession.closeNow();
        }
    }

    //发送消息 通过进行调用
    private void tcpSocketSendMessage(Object message) {
        if (tcpSession != null && tcpSession.isConnected()) {//与服务器连接上
            try {
//                Log.e("MinaSocket", "socketSendMessage = " + message);
                tcpSession.write(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Mina处理消息的handler,从服务端返回的消息一般在这里处理
     */
    private class TCPDefaultHandler extends IoHandlerAdapter {
        @Override
        public void sessionOpened(IoSession session) throws Exception {
            super.sessionOpened(session);
//            Log.e("MinaSocket", "TV1 sessionOpened");
        }

        /**
         * 接收到服务器端消息
         *
         * @param session
         * @param message
         * @throws
         */
        @Override
        public void messageReceived(IoSession session, Object message) throws Exception {

//            Log.e("MinaSocket", "TV1  messageReceived   message = " + message.toString());

            nativeAndroid.callExternalInterface("tcpSocketDataHandler", message.toString()); // 需要js进行相应的设置

        }

        @Override
        public void sessionIdle(IoSession session, IdleStatus status) throws Exception {//客户端进入空闲状态.
            super.sessionIdle(session, status);
//            Log.e("MinaSocket", "TV1 sessionIdle");

        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {//链接关闭调用
//            Log.e("MinaSocket", "TV1 sessionClosed");
            if(closeSocketFlg == false){//若不是前台主动关闭,则重连
//                connectMinaSocket();
            }else{
                closeSocketFlg = false;
                //nativeAndroid.callExternalInterface("socketClose", "");
            }
            if(tcpSession != null){
                tcpSession.closeNow();//关闭tcp连接
            }

            nativeAndroid.callExternalInterface("tcpSocketClose", ""); // 需要js进行相应的设置

        }
    }
    /******************************************** TCP Socket end********************************************/

    /******************************************** UDP Socket start********************************************/
    ////////////////////////////////UDPSocket////////////////////////////////////

    // 连接对象
    private NioDatagramConnector udpConnection;

    // session对象
    private IoSession udpSession;

    // 连接服务器的地址
    private InetSocketAddress udpAddress;

    private ConnectFuture udpConnectFuture;

    // 启动gameserver心跳计时器
    private void startActivityMsgToUdpSocket() {
        Log.i("MainActivity", "*****************************startActivityMsgToUdpSocket aaa");
        //防止多次开启计时器
        if (udpSocketTimer != null) {
            udpSocketTimer.cancel();
            udpSocketTimer = null;
        }
        if (udpSocketTimerTask != null) {
            udpSocketTimerTask = null;
        }

        udpSocketTimerTask = new TimerTask() {

            @Override
            public void run() {
                Log.i("MainActivity", "*****************************startActivityMsgToUdpSocket bbb");
                String activityXml = "<A><B><N>test001</N></B></A>" + "\n";
                socketUdpSendMessage(activityXml);
            }
        };

        udpSocketTimer = new Timer();
        udpSocketTimer.schedule(udpSocketTimerTask, 0, 30000);
    }

    // 停止gameserver心跳计时器
    private void stopActivityMsgToUdpSocket() {
        Log.i("MainActivity", "*****************************stopActivityMsgToUdpSocket");
        if (udpSocketTimer != null) {
            udpSocketTimer.cancel();
            udpSocketTimer = null;
        }
        if (udpSocketTimerTask != null) {
            udpSocketTimerTask.cancel();
            udpSocketTimerTask = null;
        }
    }

    // 连接电视1 Socket
    private void startSocketUdp(String info) throws IOException {
        if (udpSession != null && udpSession.isConnected()) {
            udpSession.closeNow();
        } else {
            Log.i("melog", "----------------------------------------UDP socket获取连接信息");
            String[] array = info.split("_");
            String socketAddress = array[0];
            String socketPort = array[1];
            Log.e("MinaSocket", "startSocket socketAddress = " + socketAddress + "   :   socketPort = " + socketPort);

            //初始化配置信息
            initMinaUdpSocket(socketAddress, socketPort);

            //开始连接
            connectMinaUdpSocket();

//            Log.e("MinaSocket", "startSocket End");
        }
    }

    /**
     * 初始化电视1 Mina配置信息
     */
    private void initMinaUdpSocket(String socketIp, String socketPort) {
        try {
            udpAddress = new InetSocketAddress(socketIp, Integer.parseInt(socketPort));//连接地址,此数据可改成自己要连接的IP和端口号
            udpConnection = new NioDatagramConnector();// 创建连接
            // 设置读取数据的缓存区大小
            DatagramSessionConfig socketSessionConfig = udpConnection.getSessionConfig();
            socketSessionConfig.setReadBufferSize(1024);
            socketSessionConfig.setWriteTimeout(120);//设置写超时为120秒
            socketSessionConfig.setIdleTime(IdleStatus.BOTH_IDLE, 15 * 60);//设置15分钟没有读写操作进入空闲状态
            LoggingFilter loggingFilter = new LoggingFilter();
            loggingFilter.setMessageSentLogLevel(LogLevel.DEBUG);
            loggingFilter.setMessageReceivedLogLevel(LogLevel.DEBUG);
            udpConnection.getFilterChain().addLast("logging", loggingFilter);//logging过滤器
            udpConnection.getFilterChain().addLast("codec", new ProtocolCodecFilter(new TextLineCodecFactory(Charset.forName("UTF-8"))));//自定义解编码器
            udpConnection.setHandler(new UdpDefaultHandler());//设置handler
            udpConnection.setDefaultRemoteAddress(udpAddress);//设置地址
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建连接 电视1 Socket
     */
    private void connectMinaUdpSocket() {

        FutureTask<Void> futureTask = new FutureTask<>(new Callable<Void>() {
            @Override
            public Void call() {//

                try {
                    while (true) {
                        udpConnectFuture = udpConnection.connect();

                        udpConnectFuture.awaitUninterruptibly();//一直等到他连接为止
                        udpSession = udpConnectFuture.getSession();//获取session对象
                        if (udpSession != null && udpSession.isConnected()) {
//                            Log.e("TV1 MinaSocket", "连接成功");
                            // 发送TV1 Socket连接成功到前台
                            Log.i("melog", "----------------------------------------TV1 socket获取信息1");
                            nativeAndroid.callExternalInterface("socketUdpConnectSuccess", "");
                            Log.i("melog", "----------------------------------------TV1 socket获取信息2");
                                break;
                        }
                        Thread.sleep(3000);//每隔三秒循环一次
                    }
                } catch (Exception e) {//连接异常
//                    Log.e("TV1 MinaSocket", "socketConnectError " + e.getMessage());
                    nativeAndroid.callExternalInterface("socketUdpConnectError", "");
                }
                return null;
            }
        });
        executorService.execute(futureTask);//执行连接线程
    }

    // 前台主动断开TV1 socket
    private void closeSocketUdp() {
        if (udpSession != null && udpSession.isConnected()) {
            closeSocketFlg = true;
            udpSession.closeNow();
        }
    }

    //发送消息
    private void socketUdpSendMessage(Object message) {
        if (udpSession != null && udpSession.isConnected()) {//与服务器连接上
            try {
//                Log.e("MinaSocket", "socketSendMessage = " + message);
                udpSession.write(message);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Mina处理消息的handler,从服务端返回的消息一般在这里处理
     */
    private class UdpDefaultHandler extends IoHandlerAdapter {
        @Override
        public void sessionOpened(IoSession session) throws Exception {
            super.sessionOpened(session);
//            Log.e("MinaSocket", "TV1 sessionOpened");
        }

        /**
         * 接收到服务器端消息
         *
         * @param session
         * @param message
         * @throws
         */
        @Override
        public void messageReceived(IoSession session, Object message) throws Exception {

//            Log.e("MinaSocket", "TV1  messageReceived   message = " + message.toString());
            nativeAndroid.callExternalInterface("socketUdpDataHandler", message.toString()); // 进行udp信息的处理
        }

        @Override
        public void sessionIdle(IoSession session, IdleStatus status) throws Exception {//客户端进入空闲状态.
            super.sessionIdle(session, status);
//            Log.e("MinaSocket", "TV1 sessionIdle");
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {//链接关闭调用
//            Log.e("MinaSocket", "TV1 sessionClosed");
            if(closeSocketFlg == false){//若不是前台主动关闭,则重连
//                connectMinaSocket();
            }else{
                closeSocketFlg = false;
                //nativeAndroid.callExternalInterface("socketClose", "");
            }
            if(udpSession != null){
                udpSession.closeNow();
            }
            nativeAndroid.callExternalInterface("socketUdpClose", "");
        }

    }
    /******************************************** Socket end********************************************/



}
