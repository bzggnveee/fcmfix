package com.kooritea.fcmfix.xposed;

import android.content.Intent;

import com.kooritea.fcmfix.util.XposedUtils;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class AutoStartFix extends XposedModule {

    public AutoStartFix(XC_LoadPackage.LoadPackageParam loadPackageParam){
        super(loadPackageParam);
        this.startHook();
        this.startHookRemovePowerPolicy();
    }

    protected void startHook(){
        try{
            // miui12
            Class<?> BroadcastQueueInjector = XposedHelpers.findClass("com.android.server.am.BroadcastQueueInjector",loadPackageParam.classLoader);
            XposedUtils.findAndHookMethodAnyParam(BroadcastQueueInjector,"checkApplicationAutoStart",new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) XposedHelpers.getObjectField(methodHookParam.args[2], "intent");
                    if("com.google.android.c2dm.intent.RECEIVE".equals(intent.getAction())){
                        String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
                        if(targetIsAllow(target)){
                            XposedHelpers.callStaticMethod(BroadcastQueueInjector,"checkAbnormalBroadcastInQueueLocked", methodHookParam.args[1], methodHookParam.args[0]);
                            printLog("Allow Auto Start: " + target, true);
                            methodHookParam.setResult(true);
                        }
                    }
                }
            });
        }catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e){
            printLog("No Such Method com.android.server.am.BroadcastQueueInjector.checkApplicationAutoStart");
        }
        try{
            // miui13
            Class<?> BroadcastQueueImpl = XposedHelpers.findClass("com.android.server.am.BroadcastQueueImpl",loadPackageParam.classLoader);
            XposedUtils.findAndHookMethodAnyParam(BroadcastQueueImpl,"checkApplicationAutoStart",new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) XposedHelpers.getObjectField(methodHookParam.args[1], "intent");
                    if("com.google.android.c2dm.intent.RECEIVE".equals(intent.getAction())){
                        String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
                        if(targetIsAllow(target)){
                            XposedHelpers.callMethod(methodHookParam.thisObject, "checkAbnormalBroadcastInQueueLocked", methodHookParam.args[0]);
                            printLog("Allow Auto Start: " + target, true);
                            methodHookParam.setResult(true);
                        }
                    }
                }
            });
        }catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e){
            printLog("No Such Method com.android.server.am.BroadcastQueueImpl.checkApplicationAutoStart");
        }

        try{
            // hyperos
            Class<?> BroadcastQueueImpl = XposedHelpers.findClass("com.android.server.am.BroadcastQueueModernStubImpl",loadPackageParam.classLoader);
            printLog("[fcmfix] start hook com.android.server.am.BroadcastQueueModernStubImpl.checkApplicationAutoStart");
            XposedUtils.findAndHookMethodAnyParam(BroadcastQueueImpl,"checkApplicationAutoStart", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) XposedHelpers.getObjectField(methodHookParam.args[1], "intent");
                    String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
                    if (targetIsAllow(target)) {
                        printLog("Allow Auto Start: " + target, true);
                        methodHookParam.setResult(true);
                    }
                }
            });

            printLog("[fcmfix] start hook com.android.server.am.BroadcastQueueModernStubImpl.checkReceiverIfRestricted");
            XposedUtils.findAndHookMethodAnyParam(BroadcastQueueImpl,"checkReceiverIfRestricted", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) XposedHelpers.getObjectField(methodHookParam.args[1], "intent");
                    String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
                    if(targetIsAllow(target)){
                        //XposedHelpers.callMethod(methodHookParam.thisObject, "checkAbnormalBroadcastInQueueLocked", methodHookParam.args[0]);
                        printLog("BroadcastQueueModernStubImpl.checkReceiverIfRestricted: " + target, true);
                        methodHookParam.setResult(false);
                    }
                }
            });
        }catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e){
            printLog("No Such class com.android.server.am.BroadcastQueueModernStubImpl");
        }

        try {
            Class<?> AutoStartManagerServiceStubImpl = XposedHelpers.findClass("com.android.server.am.AutoStartManagerServiceStubImpl", loadPackageParam.classLoader);
            XC_MethodHook methodHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent service = (Intent) methodHookParam.args[1];
                    String target = service.getComponent().getPackageName();
                    if(targetIsAllow(target)) {
                        printLog("AutoStartManagerServiceStubImpl.isAllowStartService  package_name: " + target, true);
                        methodHookParam.setResult(true);
                    }
                }
            };

            printLog("[fcmfix] start hook com.android.server.am.AutoStartManagerServiceStubImpl.isAllowStartService");
            XposedUtils.findAndHookMethod(AutoStartManagerServiceStubImpl, "isAllowStartService", 3, methodHook);
            XposedUtils.findAndHookMethod(AutoStartManagerServiceStubImpl, "isAllowStartService", 4, methodHook);
        } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e){
            printLog("No Such Class com.android.server.am.AutoStartManagerServiceStubImpl.isAllowStartService");
        }

        try {
            Class<?> SmartPowerService = XposedHelpers.findClass("com.android.server.am.SmartPowerService", loadPackageParam.classLoader);

            printLog("[fcmfix] start hook com.android.server.am.SmartPowerService.isProcessWhiteList");
            XposedUtils.findAndHookMethodAnyParam(SmartPowerService, "isProcessWhiteList", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    String target = (String)methodHookParam.args[1];
                    if(targetIsAllow(target)) {
                        printLog("SmartPowerService.isProcessWhiteList  package_name: " + target, true);
                        methodHookParam.setResult(true);
                    }
                }
            });

            printLog("[fcmfix] start hook com.android.server.am.SmartPowerService.shouldInterceptBroadcast");
            XposedUtils.findAndHookMethodAnyParam(SmartPowerService, "shouldInterceptBroadcast", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) {
                    Intent intent = (Intent) XposedHelpers.getObjectField(methodHookParam.args[1], "intent");
                    String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
                    if(targetIsAllow(target)) {
                        printLog("SmartPowerService.shouldInterceptBroadcast  package_name: " + target, true);
                        methodHookParam.setResult(false);
                    }
                }
            });
        } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e){
            printLog("No Such Class com.android.server.am.SmartPowerService");
        }
    }

    protected void startHookRemovePowerPolicy(){
        try {
            // MIUI13
            Class<?> AutoStartManagerService = XposedHelpers.findClass("com.miui.server.smartpower.SmartPowerPolicyManager",loadPackageParam.classLoader);
            XposedUtils.findAndHookMethodAnyParam(AutoStartManagerService,"shouldInterceptService",new XC_MethodHook() {

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Intent intent = (Intent) param.args[0];
                    if("com.google.firebase.MESSAGING_EVENT".equals(intent.getAction())){
                        String target = intent.getComponent() == null ? intent.getPackage() : intent.getComponent().getPackageName();
                        if(targetIsAllow(target)){
                            printLog("Disable MIUI Intercept: " + target, true);
                            param.setResult(false);
                        }
                    }
                }
            });
        } catch (XposedHelpers.ClassNotFoundError | NoSuchMethodError  e) {
            printLog("No Such Method com.miui.server.smartpower.SmartPowerPolicyManager.shouldInterceptService");
        }
    }
}
