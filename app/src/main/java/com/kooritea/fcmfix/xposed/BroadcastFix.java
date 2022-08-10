package com.kooritea.fcmfix.xposed;

import android.content.Intent;
import android.os.Build;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class BroadcastFix extends XposedModule {

    public BroadcastFix(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        super(loadPackageParam);
    }

    @Override
    protected void onCanReadConfig() {
        this.startHook();
    }

    protected void startHook(){
        Class<?> clazz = XposedHelpers.findClass("com.android.server.am.ActivityManagerService",loadPackageParam.classLoader);
        final Method[] declareMethods = clazz.getDeclaredMethods();
        Method targetMethod = null;
        for(Method method : declareMethods){
            if(method.getName().equals("broadcastIntentLocked")){
                if(targetMethod == null || targetMethod.getParameterTypes().length < method.getParameterTypes().length){
                    targetMethod = method;
                }
            }
        }
        if(targetMethod != null){
            int intent_args_index = 0;
            int appOp_args_index = 0;
            Parameter[] parameters = targetMethod.getParameters();
            if(Build.VERSION.SDK_INT == Build.VERSION_CODES.P){
                intent_args_index = 2;
                appOp_args_index = 9;
            }else if(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q){
                intent_args_index = 2;
                appOp_args_index = 9;
            }else if(Build.VERSION.SDK_INT == Build.VERSION_CODES.R){
                intent_args_index = 3;
                appOp_args_index = 10;
            }else if(Build.VERSION.SDK_INT == 31){
                intent_args_index = 3;
                if(parameters[11].getType() == int.class){
                    appOp_args_index = 11;
                }
                if(parameters[12].getType() == int.class){
                    appOp_args_index = 12;
                }
            }else if(Build.VERSION.SDK_INT == 32){
                intent_args_index = 3;
                if(parameters[11].getType() == int.class){
                    appOp_args_index = 11;
                }
                if(parameters[12].getType() == int.class){
                    appOp_args_index = 12;
                }
            }
            if(intent_args_index == 0 || appOp_args_index == 0){
                for(int i = 0; i < parameters.length; i++){
                    if(parameters[i].getName() == "appOp" && parameters[i].getType() == int.class){
                        appOp_args_index = i;
                    }
                    if(parameters[i].getName() == "intent" && parameters[i].getType() == Intent.class){
                        intent_args_index = i;
                    }
                }
            }
            printLog("Android API: " + Build.VERSION.SDK_INT);
            printLog("appOp_args_index: " + appOp_args_index);
            printLog("intent_args_index: " + intent_args_index);
            if(intent_args_index == 0 || appOp_args_index == 0){
                printLog("broadcastIntentLocked hook 位置查找失败，fcmfix将不会工作。");
                return;
            }
            final int finalIntent_args_index = intent_args_index;
            final int finalAppOp_args_index = appOp_args_index;

            XposedBridge.hookMethod(targetMethod,new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    Intent intent = (Intent) methodHookParam.args[finalIntent_args_index];
                    if(intent != null && intent.getPackage() != null && intent.getFlags() != Intent.FLAG_INCLUDE_STOPPED_PACKAGES && "com.google.android.c2dm.intent.RECEIVE".equals(intent.getAction())){
                        String target;
                        if (intent.getComponent() != null) {
                            target = intent.getComponent().getPackageName();
                        } else {
                            target = intent.getPackage();
                        }
                        if(targetIsAllow(target)){
                            int i = ((Integer)methodHookParam.args[finalAppOp_args_index]).intValue();
                            if (i == -1) {
                                methodHookParam.args[finalAppOp_args_index] = Integer.valueOf(11);
                            }
                            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                            printLog("Send Forced Start Broadcast: " + target);
                        }
                    }
                }
            });
        }
    }
}
