package github.tornaco.xposedmoduletest.xposed.submodules;

import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Arrays;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import github.tornaco.xposedmoduletest.BuildConfig;
import github.tornaco.xposedmoduletest.xposed.service.notification.NotificationManagerServiceProxy;
import github.tornaco.xposedmoduletest.xposed.util.XposedLog;

/**
 * Created by guohao4 on 2017/10/31.
 * Email: Tornaco@163.com
 */

// FIXME Have not check M L O yet.
// https://github.com/LineageOS/android_frameworks_base/blob/cm-12.1/services/core/java/com/android/server/notification/NotificationManagerService.java
class NotificationManagerServiceSubModule extends AndroidSubModule {

    @Override
    public void handleLoadingPackage(String pkg, XC_LoadPackage.LoadPackageParam lpparam) {
        hookOnStart(lpparam);

        // Listen for the notification post.
        hookNotificationListeners(lpparam);
        hookNotificationListenersRemove(lpparam);
    }

    private void hookOnStart(final XC_LoadPackage.LoadPackageParam lpparam) {
        logOnBootStage("hookOnStart...");
        try {
            Class clz = XposedHelpers.findClass("com.android.server.notification.NotificationManagerService",
                    lpparam.classLoader);
            Set unHooks = XposedBridge.hookAllMethods(clz,
                    "onStart", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            Object service = param.thisObject;
                            if (service != null) {
                                NotificationManagerServiceProxy proxy = new NotificationManagerServiceProxy(service);
                                getBridge().attachNotificationService(proxy);
                            }
                        }
                    });
            logOnBootStage("hookOnStart OK:" + unHooks);
            setStatus(unhooksToStatus(unHooks));
        } catch (Exception e) {
            logOnBootStage("Fail hookOnStart:" + e);
            setStatus(SubModuleStatus.ERROR);
            setErrorMessage(Log.getStackTraceString(e));
        }
    }

    private void hookNotificationListeners(final XC_LoadPackage.LoadPackageParam lpparam) {
        logOnBootStage("hookNotificationListeners...");
        try {
            Class clz = XposedHelpers.findClass("com.android.server.notification.NotificationManagerService$NotificationListeners",
                    lpparam.classLoader);
            Set unHooks = XposedBridge.hookAllMethods(clz, "notifyPostedLocked", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    if (BuildConfig.DEBUG && XposedLog.isVerboseLoggable()) {
                        XposedLog.verbose("NotificationListeners, notifyPosted: " + Arrays.toString(param.args));
                    }
                    dispatchNotificationPosted(param.args[0]);
                }
            });
            logOnBootStage("hookNotificationListeners OK:" + unHooks);
            setStatus(unhooksToStatus(unHooks));
        } catch (Exception e) {
            logOnBootStage("Fail hookNotificationListeners:" + e);
            setStatus(SubModuleStatus.ERROR);
            setErrorMessage(Log.getStackTraceString(e));
        }
    }

    private void hookNotificationListenersRemove(final XC_LoadPackage.LoadPackageParam lpparam) {
        logOnBootStage("hookNotificationListenersRemove...");
        try {
            Class clz = XposedHelpers.findClass("com.android.server.notification.NotificationManagerService$NotificationListeners",
                    lpparam.classLoader);
            Set unHooks = XposedBridge.hookAllMethods(clz, "notifyRemovedLocked", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    if (BuildConfig.DEBUG && XposedLog.isVerboseLoggable()) {
                        XposedLog.verbose("NotificationListeners, notifyRemoved: " + Arrays.toString(param.args));
                    }
                    dispatchNotificationRemoved(param.args[0]);
                }
            });
            logOnBootStage("hookNotificationListenersRemove OK:" + unHooks);
            setStatus(unhooksToStatus(unHooks));
        } catch (Exception e) {
            logOnBootStage("Fail hookNotificationListenersRemove:" + e);
            setStatus(SubModuleStatus.ERROR);
            setErrorMessage(Log.getStackTraceString(e));
        }
    }
    private void dispatchNotificationPosted(Object object) {
        StatusBarNotification sbn = statusBarNotificationFrom(object);
        if (sbn != null) {
            getBridge().onNotificationPosted(sbn);
        } else if (object != null) {
            getBridge().onNotificationPosted(object);
        }
    }

    private void dispatchNotificationRemoved(Object object) {
        StatusBarNotification sbn = statusBarNotificationFrom(object);
        if (sbn != null) {
            getBridge().onNotificationRemoved(sbn);
        } else if (object != null) {
            getBridge().onNotificationRemoved(object);
        }
    }

    private StatusBarNotification statusBarNotificationFrom(Object object) {
        if (object instanceof StatusBarNotification) {
            return (StatusBarNotification) object;
        }
        if (object == null || !"com.android.server.notification.NotificationRecord".equals(object.getClass().getName())) {
            return null;
        }
        try {
            return (StatusBarNotification) XposedHelpers.getObjectField(object, "sbn");
        } catch (Throwable e) {
            XposedLog.wtf("NotificationListeners fail retrieve sbn: " + Log.getStackTraceString(e));
            return null;
        }
    }

}
