package com.kooritea.fcmfix.xposed;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import com.kooritea.fcmfix.R;
import com.kooritea.fcmfix.util.XposedUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static android.content.Context.NOTIFICATION_SERVICE;

public class ReconnectManagerFix extends XposedModule {

    private Class<?> GcmChimeraService;
    private String GcmChimeraServiceLogMethodName;
    private Boolean startHookFlag = false;


    public ReconnectManagerFix(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        super(loadPackageParam);
        this.startHookGcmServiceStart();
    }

    @Override
    protected void onCanReadConfig() throws Exception {
        if(startHookFlag){
            this.checkVersion();
        }else {
            startHookFlag = true;
        }

    }

    private void startHookGcmServiceStart() {
        this.GcmChimeraService = XposedHelpers.findClass("com.google.android.gms.gcm.GcmChimeraService", loadPackageParam.classLoader);
        try{
            for(Method method : this.GcmChimeraService.getMethods()){
                if(method.getParameterTypes().length == 2){
                    if(method.getParameterTypes()[0] == String.class && method.getParameterTypes()[1] == Object[].class){
                        this.GcmChimeraServiceLogMethodName = method.getName();
                        break;
                    }
                }
            }
            XposedHelpers.findAndHookMethod(this.GcmChimeraService, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction("com.kooritea.fcmfix.log");
                    context.registerReceiver(logBroadcastReceive, intentFilter);
                    if(startHookFlag){
                        checkVersion();
                    }else {
                        startHookFlag = true;
                    }
                }
            });
            XposedHelpers.findAndHookMethod(this.GcmChimeraService, "onDestroy", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    context.unregisterReceiver(logBroadcastReceive);
                }
            });
        }catch (Exception e){
            XposedBridge.log("GcmChimeraService hook 失败");
        }
    }

    private void checkVersion() throws Exception {
        final SharedPreferences sharedPreferences = context.getSharedPreferences("fcmfix_config", Context.MODE_PRIVATE);
        String versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        long versionCode = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        if(versionCode < 213916046){
            printLog("当前为旧版GMS，请使用0.4.1版本FCMFIX，禁用重连修复功能");
            return;
        }
        if (!sharedPreferences.getBoolean("isInit", false) || !sharedPreferences.getString("config_version", "").equals("v2")) {
            printLog("fcmfix_config init");
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("isInit", true);
            editor.putBoolean("enable", false);
            editor.putLong("heartbeatInterval", 0L);
            editor.putLong("reconnInterval", 0L);
            editor.putString("gms_version", versionName);
            editor.putLong("gms_version_code", versionCode);
            editor.putString("config_version", "v2");
            editor.putString("timer_class", "");
            editor.putString("timer_settimeout_method", "");
            editor.putString("timer_alarm_type_property", "");
            editor.apply();
            printLog("正在更新hook位置");
            findAndUpdateHookTarget(sharedPreferences);
            return;
        }
        if (!sharedPreferences.getString("gms_version", "").equals(versionName) ) {
            printLog("gms已更新: " + sharedPreferences.getString("gms_version", "") + "(" + sharedPreferences.getLong("gms_version_code", 0) + ")" + "->" + versionName + "(" +versionCode + ")");
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("gms_version", versionName);;
            editor.putLong("gms_version_code", versionCode);
            editor.putBoolean("enable", false);
            editor.apply();
            printLog("正在更新hook位置");
            findAndUpdateHookTarget(sharedPreferences);
            return;
        }
        if (!sharedPreferences.getBoolean("enable", false)) {
            printLog("当前配置文件enable标识为false，FCMFIX退出");
            return;
        }
        startHook();
    }

    protected void startHook() throws Exception {
        final SharedPreferences sharedPreferences = context.getSharedPreferences("fcmfix_config", Context.MODE_PRIVATE);
        printLog("timer_class: "+ sharedPreferences.getString("timer_class", ""));
        printLog("timer_alarm_type_property: "+ sharedPreferences.getString("timer_alarm_type_property", ""));
        printLog("timer_settimeout_method: "+ sharedPreferences.getString("timer_settimeout_method", ""));
        final Class<?> timerClazz = XposedHelpers.findClass(sharedPreferences.getString("timer_class", ""), loadPackageParam.classLoader);
        XposedHelpers.findAndHookMethod(timerClazz, "toString", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                String alarmType = (String) XposedUtils.getObjectFieldByPath(param.thisObject,  sharedPreferences.getString("timer_alarm_type_property", ""));
                if("GCM_HB_ALARM".equals(alarmType) || "GCM_CONN_ALARM".equals(alarmType)){
                    long hinterval = sharedPreferences.getLong("heartbeatInterval", 0L);
                    long cinterval = sharedPreferences.getLong("reconnInterval", 0L);
                    if((hinterval != 0L && hinterval > 1000) || (cinterval != 0L && cinterval > 1000)){
                        param.setResult(param.getResult() + "[fcmfix locked]");
                    }
                }
            }
        });
        XposedHelpers.findAndHookMethod(timerClazz, sharedPreferences.getString("timer_settimeout_method", ""), long.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                // 修改心跳间隔
                String alarmType = (String) XposedUtils.getObjectFieldByPath(param.thisObject,  sharedPreferences.getString("timer_alarm_type_property", ""));
                if ("GCM_HB_ALARM".equals(alarmType)) {
                    long interval = sharedPreferences.getLong("heartbeatInterval", 0L);
                    if(interval != 0L && interval > 1000){
                        param.args[0] = interval;
                    }
                }
                if ("GCM_CONN_ALARM".equals(alarmType)) {
                    long interval = sharedPreferences.getLong("reconnInterval", 0L);
                    if(interval != 0L && interval > 1000){
                        param.args[0] = interval;
                    }
                }
            }

            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                // 防止计时器出现负数计时,分别是心跳计时和重连计时
                String alarmType = (String) XposedUtils.getObjectFieldByPath(param.thisObject,  sharedPreferences.getString("timer_alarm_type_property", ""));
                if ("GCM_HB_ALARM".equals(alarmType) || "GCM_CONN_ALARM".equals(alarmType)) {
                    Field maxField = null;
                    long maxFieldValue = 0L;
                    for(Field field : timerClazz.getDeclaredFields()){
                        if(field.getType() == long.class){
                            long fieldValue = (long)XposedHelpers.getObjectField(param.thisObject,field.getName());
                            if(maxField == null || fieldValue > maxFieldValue){
                                maxField = field;
                                maxFieldValue = fieldValue;
                            }
                        }
                    }
                    final Timer timer = new Timer("ReconnectManagerFix");
                    final Field finalMaxField = maxField;
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            long nextConnectionTime = XposedHelpers.getLongField(param.thisObject, finalMaxField.getName());
                            if (nextConnectionTime != 0 && nextConnectionTime - SystemClock.elapsedRealtime() < 0) {
                                context.sendBroadcast(new Intent("com.google.android.intent.action.GCM_RECONNECT"));
                                printLog("Send broadcast GCM_RECONNECT");
                            }
                            timer.cancel();
                        }
                    }, (long) param.args[0] + 5000);
                }
            }
        });
    }

    private void sendUpdateNotification(String title) {
        sendUpdateNotification(title,null);
    }

    private void sendUpdateNotification(String title, String content) {
        printLog(title);
        title = "[fcmfix]" + title;
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        this.createFcmfixChannel(notificationManager);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "fcmfix");
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setAutoCancel(true);
        builder.setContentTitle(title);
        if(content != null){
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(content));
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClassName("com.google.android.gms","com.google.android.gms.gcm.GcmDiagnostics");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        builder.setContentIntent(PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT));
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createFcmfixChannel(NotificationManager notificationManager) {
        List<NotificationChannel> channelList = notificationManager.getNotificationChannels();
        for (NotificationChannel item : channelList) {
            if (item.getId() == "fcmfix") {
                item.setName("fcmfix");
                item.setImportance(NotificationManager.IMPORTANCE_HIGH);
                item.setDescription("fcmfix");
                return;
            }
        }
        NotificationChannel channel = new NotificationChannel("fcmfix", "fcmfix", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("[xposed] fcmfix更新通知");
        notificationManager.createNotificationChannel(channel);
    }

    private BroadcastReceiver logBroadcastReceive = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.kooritea.fcmfix.log".equals(action)) {
                try{
                    XposedHelpers.callStaticMethod(GcmChimeraService,GcmChimeraServiceLogMethodName , new Class<?>[]{String.class, Object[].class}, "[fcmfix] " + intent.getStringExtra("text"), null);
                }catch (Throwable e){
                    XposedBridge.log("输出日志到fcm失败： "+"[fcmfix] " + intent.getStringExtra("text"));
                }
            }
        }
    };

    private void findAndUpdateHookTarget(final SharedPreferences sharedPreferences){
        final SharedPreferences.Editor editor = sharedPreferences.edit();
        try{
            Class<?> heartbeatChimeraAlarm =  XposedHelpers.findClass("com.google.android.gms.gcm.connection.HeartbeatChimeraAlarm",loadPackageParam.classLoader);
            final Class<?> timerClass = heartbeatChimeraAlarm.getConstructors()[0].getParameterTypes()[3];
            editor.putString("timer_class", timerClass.getName());
            for(Method method : timerClass.getDeclaredMethods()){
                if(method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == long.class && Modifier.isFinal(method.getModifiers()) && Modifier.isPublic(method.getModifiers())){
                    editor.putString("timer_settimeout_method", method.getName());
                    break;
                }
            }
            for(final Field timerClassField : timerClass.getDeclaredFields()){
                if(Modifier.isFinal(timerClassField.getModifiers()) && Modifier.isPublic(timerClassField.getModifiers())){
                    final Class<?> alarmClass = timerClassField.getType();
                    final Boolean[] isFinish = {false};
                    XposedHelpers.findAndHookConstructor(alarmClass, Context.class, int.class, String.class, String.class, String.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                            if(isFinish[0] != true){
                                for(Field field : alarmClass.getDeclaredFields()){
                                    if(field.getType() == String.class && Modifier.isFinal(field.getModifiers()) && Modifier.isPrivate(field.getModifiers())){
                                        if(param.args[2] != null && XposedHelpers.getObjectField(param.thisObject, field.getName()) == param.args[2]){
                                            SharedPreferences.Editor editor = sharedPreferences.edit();
                                            editor.putString("timer_alarm_type_property", timerClassField.getName() + "." + field.getName());
                                            editor.apply();
                                            isFinish[0] = true;
                                            printLog("更新hook位置成功");
                                            sendUpdateNotification("自动更新配置文件成功");
                                            startHook();
                                            return;
                                        }
                                    }
                                }
                                printLog("自动寻找hook点失败: 未找到目标方法");
                            }
                        }
                    });
                    break;
                }
            }
        }catch (Throwable e){
            editor.putBoolean("enable", false);
            printLog("自动寻找hook点失败"+e.getMessage());
            this.sendUpdateNotification("自动更新配置文件失败", "未能找到hook点，已禁用重连修复和固定心跳功能。");
            e.printStackTrace();
        }
        editor.apply();
    }
}
