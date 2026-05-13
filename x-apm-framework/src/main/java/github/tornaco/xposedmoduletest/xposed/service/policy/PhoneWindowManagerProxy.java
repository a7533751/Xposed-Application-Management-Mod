package github.tornaco.xposedmoduletest.xposed.service.policy;

import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import de.robv.android.xposed.XposedHelpers;
import github.tornaco.xposedmoduletest.IBooleanCallback1;
import github.tornaco.xposedmoduletest.ISettingsChangeListener;
import github.tornaco.xposedmoduletest.xposed.app.XAPMManager;
import github.tornaco.xposedmoduletest.xposed.repo.SettingsProvider;
import github.tornaco.xposedmoduletest.xposed.service.InvokeTargetProxy;
import github.tornaco.xposedmoduletest.xposed.service.policy.wm.SystemGesturesPointerEventListener;
import github.tornaco.xposedmoduletest.xposed.service.policy.wm.SystemGesturesPointerEventListenerCallbackImpl;
import github.tornaco.xposedmoduletest.xposed.util.XposedLog;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by guohao4 on 2018/1/23.
 * Email: Tornaco@163.com
 */
@InvokeTargetProxy.Target("PhoneWindowManager")
public class PhoneWindowManagerProxy extends InvokeTargetProxy<Object> {

    @Setter
    @Getter
    private boolean haveEnableThreeFingerGesture, haveEnablePGesture, settingsListenerRegistered;

    @Getter
    @Setter
    private Context context;

    @Getter
    @Setter
    private Object windowManagerFuncs;

    private OPGesturesListener mOPGestures;
    private SystemGesturesPointerEventListener mSystemGesturesListener;
    private Object mOPGesturesPointerEventListener;
    private Object mSystemGesturesPointerEventListener;

    public PhoneWindowManagerProxy(Object host) {
        super(host);
    }

    public void enableKeyguard(boolean enabled) {
        invokeMethod("enableKeyguard", enabled);
    }

    public void exitKeyguardSecurely(IBooleanCallback1 result) {
        try {
            Class<?> callbackClass = Class.forName("android.view.WindowManagerPolicy$OnKeyguardExitResult",
                    false, ClassLoader.getSystemClassLoader());
            ClassLoader callbackClassLoader = callbackClass.getClassLoader() == null
                    ? ClassLoader.getSystemClassLoader()
                    : callbackClass.getClassLoader();
            Object callback = Proxy.newProxyInstance(callbackClassLoader,
                    new Class[]{callbackClass}, (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("onKeyguardExitResult".equals(methodName)) {
                            boolean success = args != null
                                    && args.length > 0
                                    && args[0] instanceof Boolean
                                    && (Boolean) args[0];
                            notifyKeyguardExitResult(result, success);
                            return null;
                        }
                        if ("toString".equals(methodName)) return "XAPMKeyguardExitCallback";
                        if ("hashCode".equals(methodName)) return System.identityHashCode(proxy);
                        if ("equals".equals(methodName)) return args != null && args.length > 0 && proxy == args[0];
                        return null;
                    });
            invokeMethod("exitKeyguardSecurely", callback);
        } catch (ClassNotFoundException e) {
            notifyKeyguardExitResult(result, false);
        } catch (Throwable e) {
            XposedLog.wtf("PhoneWindowManagerProxy exitKeyguardSecurely fail: "
                    + Log.getStackTraceString(e));
            notifyKeyguardExitResult(result, false);
        }
    }

    private void notifyKeyguardExitResult(IBooleanCallback1 result, boolean success) {
        if (result == null) {
            return;
        }
        try {
            result.onResult(success);
        } catch (RemoteException e) {
            XposedLog.wtf("PhoneWindowManagerProxy notifyKeyguardExitResult fail: " + e);
        }
    }

    public void dismissKeyguardLw() {
        invokeMethod("dismissKeyguardLw");
    }

    public boolean isKeyguardLocked() {
        return invokeMethod("isKeyguardLocked");
    }

    private void retrieveWindowManagerFuncs() {
        synchronized (this) {
            if (getWindowManagerFuncs() == null) {
                try {
                    setWindowManagerFuncs(XposedHelpers.getObjectField(getHost(), "mWindowManagerFuncs"));
                    XposedLog.verbose("PhoneWindowManagerProxy retrieveWindowManagerFuncs: " + getWindowManagerFuncs());
                } catch (Throwable e) {
                    XposedLog.wtf("PhoneWindowManagerProxy Fail retrieveWindowManagerFuncs: "
                            + Log.getStackTraceString(e));
                }
            }
        }
    }

    private void initThreeFingerGesture(Context context) {
        if (context != null) {
            mOPGestures = new OPGesturesListener(context, () -> {
                XposedLog.verbose("PhoneWindowManagerProxy onSwipeThreeFinger");
                takeScreenshot(0);
            });
            mOPGesturesPointerEventListener = newPointerEventListener(mOPGestures);
        }
        registerSettingsListener();
    }

    private void initPGesture(Context context) {
        if (context != null) {
            mSystemGesturesListener = new SystemGesturesPointerEventListener(context,
                    new SystemGesturesPointerEventListenerCallbackImpl(context));
            mSystemGesturesPointerEventListener = newPointerEventListener(mSystemGesturesListener);
        }
        registerSettingsListener();
    }

    private Object newPointerEventListener(Object delegate) {
        if (delegate == null) {
            return null;
        }
        try {
            Class<?> listenerClass = findPointerEventListenerClass();
            if (listenerClass == null) {
                return null;
            }
            ClassLoader listenerClassLoader = listenerClass.getClassLoader() == null
                    ? ClassLoader.getSystemClassLoader()
                    : listenerClass.getClassLoader();
            Method onPointerEvent = delegate.getClass().getMethod("onPointerEvent", android.view.MotionEvent.class);
            return Proxy.newProxyInstance(listenerClassLoader,
                    new Class[]{listenerClass}, (proxy, method, args) -> {
                        String methodName = method.getName();
                        if ("onPointerEvent".equals(methodName)) {
                            if (args != null && args.length > 0) {
                                onPointerEvent.invoke(delegate, args[0]);
                            }
                            return null;
                        }
                        if ("toString".equals(methodName)) return delegate.toString();
                        if ("hashCode".equals(methodName)) return System.identityHashCode(proxy);
                        if ("equals".equals(methodName)) return args != null && args.length > 0 && proxy == args[0];
                        return null;
                    });
        } catch (Throwable e) {
            XposedLog.wtf("PhoneWindowManagerProxy fail create pointer listener: " + Log.getStackTraceString(e));
            return null;
        }
    }

    private Class<?> findPointerEventListenerClass() {
        Class<?> listenerClass = findPointerEventListenerClassFromWindowManagerFuncs();
        if (listenerClass != null) {
            return listenerClass;
        }
        return findPointerEventListenerClassByName();
    }

    private Class<?> findPointerEventListenerClassFromWindowManagerFuncs() {
        Object windowManagerFuncs = getWindowManagerFuncs();
        if (windowManagerFuncs == null) {
            return null;
        }
        for (Method method : windowManagerFuncs.getClass().getMethods()) {
            if (!"registerPointerEventListener".equals(method.getName())
                    || method.getParameterTypes().length != 1) {
                continue;
            }
            return method.getParameterTypes()[0];
        }
        for (Method method : windowManagerFuncs.getClass().getDeclaredMethods()) {
            if (!"registerPointerEventListener".equals(method.getName())
                    || method.getParameterTypes().length != 1) {
                continue;
            }
            return method.getParameterTypes()[0];
        }
        return null;
    }

    private Class<?> findPointerEventListenerClassByName() {
        String[] classNames = {
                "android.view.WindowManagerPolicyConstants$PointerEventListener",
                "android.view.WindowManagerPolicy$PointerEventListener"
        };
        for (String className : classNames) {
            try {
                return Class.forName(className, false, ClassLoader.getSystemClassLoader());
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void registerSettingsListener() {
        if (settingsListenerRegistered) {
            return;
        }
        // Register listener.
        settingsListenerRegistered = SettingsProvider.get().registerSettingsChangeListener(new ISettingsChangeListener.Stub() {
            @Override
            public void onChange(String name) {
                XposedLog.verbose("PhoneWindowManagerProxy onChange: " + name);
                if (XAPMManager.OPT.THREE_FINGER_GESTURE.name().equals(name)) {
                    boolean enable = SettingsProvider.get().getBoolean(name, false);
                    enableSwipeThreeFingerGesture(enable);
                } else if (XAPMManager.OPT.P_GESTURE.name().equals(name)) {
                    boolean enable = SettingsProvider.get().getBoolean(name, false);
                    enablePGesture(enable);
                }
            }
        });
    }

    private void setPointerEventListenerRegistered(Object listener, boolean registered) {
        if (listener == null || getWindowManagerFuncs() == null) {
            return;
        }
        try {
            XposedHelpers.callMethod(getWindowManagerFuncs(),
                    registered ? "registerPointerEventListener" : "unregisterPointerEventListener",
                    listener);
        } catch (Throwable e) {
            XposedLog.wtf("PhoneWindowManagerProxy fail "
                    + (registered ? "register" : "unregister")
                    + " pointer listener: " + Log.getStackTraceString(e));
        }
    }

    public void enablePGesture(boolean enable) {
        if (getContext() == null) {
            XposedLog.wtf("PhoneWindowManagerProxy enablePGesture called while getContext() is null");
            return;
        }

        retrieveWindowManagerFuncs();
        if (mSystemGesturesListener == null) {
            initPGesture(context);
        } else if (mSystemGesturesPointerEventListener == null) {
            mSystemGesturesPointerEventListener = newPointerEventListener(mSystemGesturesListener);
        }
        if (getWindowManagerFuncs() != null) {
            if (enable) {
                if (haveEnablePGesture) return;
                haveEnablePGesture = true;
                setPointerEventListenerRegistered(mSystemGesturesPointerEventListener, true);
            } else {
                if (!haveEnablePGesture) return;
                haveEnablePGesture = false;
                setPointerEventListenerRegistered(mSystemGesturesPointerEventListener, false);
            }
            XposedLog.verbose("PhoneWindowManagerProxy enablePGesture ok: " + enable);
        } else {
            XposedLog.wtf("PhoneWindowManagerProxy enablePGesture called while getWindowManagerFuncs() is null");
        }
    }

    public void enableSwipeThreeFingerGesture(boolean enable) {
        if (getContext() == null) {
            XposedLog.wtf("PhoneWindowManagerProxy enableSwipeThreeFingerGesture called while getContext() is null");
            return;
        }

        retrieveWindowManagerFuncs();
        if (mOPGestures == null) {
            initThreeFingerGesture(context);
        } else if (mOPGesturesPointerEventListener == null) {
            mOPGesturesPointerEventListener = newPointerEventListener(mOPGestures);
        }
        if (getWindowManagerFuncs() != null) {
            if (enable) {
                if (haveEnableThreeFingerGesture) return;
                haveEnableThreeFingerGesture = true;
                setPointerEventListenerRegistered(mOPGesturesPointerEventListener, true);
            } else {
                if (!haveEnableThreeFingerGesture) return;
                haveEnableThreeFingerGesture = false;
                setPointerEventListenerRegistered(mOPGesturesPointerEventListener, false);
            }
            XposedLog.verbose("PhoneWindowManagerProxy enableSwipeThreeFingerGesture ok: " + enable);
        } else {
            XposedLog.wtf("PhoneWindowManagerProxy enableSwipeThreeFingerGesture called while getWindowManagerFuncs() is null");
        }
    }

    private void takeScreenshot(long delay) {
        try {
            Handler mHandler = (Handler) XposedHelpers.getObjectField(getHost(), "mHandler");
            XposedLog.verbose("PhoneWindowManagerProxy takeScreenshot, handler: " + mHandler);
            if (mHandler != null) {
                Runnable mScreenshotRunnable = (Runnable) XposedHelpers.getObjectField(getHost(), "mScreenshotRunnable");
                XposedLog.verbose("PhoneWindowManagerProxy takeScreenshot, mScreenshotRunnable: " + mScreenshotRunnable);
                if (mScreenshotRunnable != null) {
                    mHandler.postDelayed(mScreenshotRunnable, delay);
                }
            }
        } catch (Throwable e) {
            XposedLog.wtf("PhoneWindowManagerProxy fail takeScreenshot: " + Log.getStackTraceString(e));
        }
    }
}
