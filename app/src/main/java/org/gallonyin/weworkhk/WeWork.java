package org.gallonyin.weworkhk;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.SystemClock;
import android.telephony.CellLocation;
import android.util.Log;
import android.view.View;

import org.gallonyin.weworkhk.util.ShellUtils;
import org.gallonyin.weworkhk.util.SignUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import static de.robv.android.xposed.XposedBridge.log;

/**
 * Created by gallonyin on 2018/6/13.
 */

public class WeWork {
    private static final String TAG = "WeWork";

    public static ClassLoader classLoader;
    public static Context context; //weworkApplication
    private static Handler sHandler = new Handler(Looper.getMainLooper());

    private float salt = 0.00005f;
    private float defLa = 32.041713f;
    private float defLo = 118.784308f;
    private float la = 0;
    private float lo = 0;
    private boolean isOpen = true;


    public void start(ClassLoader classLoader) {
        WeWork.classLoader = classLoader;

        hkStart();
    }

    private void initReceiver(Context context, final SharedPreferences sp) {
        final SignUtils signUtils = new SignUtils((Application) context, sp);
        BroadcastReceiver receiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                Log.e(TAG, "onReceive: " + intent.getAction());
                String action = intent.getAction();
                if (action == null) return;
                switch (action) {
                    case "weworkdk_gps":
                        String data = intent.getStringExtra("data");
                        String[] split = data.split("#");
                        if (split.length != 2) return;
                        la = Float.parseFloat(split[0]);
                        lo = Float.parseFloat(split[1]);
                        sp.edit().putFloat("GPSLatitude", la)
                                .putFloat("GPSLongitude", lo)
                                .apply();
                        break;
                    case "weworkdk_open":
                        isOpen = intent.getBooleanExtra("open", true);
                        sp.edit().putBoolean("open", isOpen).apply();
                        break;
                    case "weworkdk_activity":
                        int status = intent.getIntExtra("status", -1);
                        final Class<?> ParamClass = WeWorkClazz.getParamClass();
                        signUtils.sign(status);
                        break;
                    case Intent.ACTION_TIME_TICK:
                        Log.e("APPTEST2", "onReceive2");
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter("weworkdk_gps");
        filter.addAction("weworkdk_open");
        filter.addAction("weworkdk_activity");
        filter.addAction("weworkdk_screenshot");
        filter.addAction(Intent.ACTION_TIME_TICK);
        context.registerReceiver(receiver, filter);
    }

    private void hkStart() {
        Log.e(TAG, "hkStart()");
        XposedHelpers.findAndHookMethod("android.app.Application", classLoader, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                context = (Application) param.thisObject;

                final SharedPreferences sp = context.getSharedPreferences("hkWeWork", Context.MODE_PRIVATE);
                la = sp.getFloat("GPSLatitude", defLa);
                lo = sp.getFloat("GPSLongitude", defLo);
                isOpen = sp.getBoolean("open", true);

                hkGPS(classLoader);

                initReceiver(context, sp);

//                hkActivity(context);
            }
        });
    }

    private void hkActivity(final Context context) {
        XposedHelpers.findAndHookMethod(Activity.class, "startActivity", Intent.class, new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Intent intent = (Intent) param.args[0];
                Log.e(TAG, "startActivity " + param.thisObject.getClass());
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    final Class<?> ParamClass = WeWorkClazz.getParamClass();
                    final Class<?> AttendanceActivity2Class = WeWorkClazz.getAttendanceActivity2Class();
                    for (String s : extras.keySet()) {
                        Object value = extras.get(s);
                        Log.e(TAG, "k: " + s + " v: " + value);
                        if (value != null && value.getClass().equals(ParamClass)) { //进入打卡页
                            for (Field field : ParamClass.getFields()) {
                                Log.e(TAG, "f: " + field + " v: " + field.get(value));
                            }

                            sHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    ShellUtils.execCmd("screencap -p /sdcard/autoshot.png", true);
                                }
                            }, 3000);


                            sHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    Log.e(TAG, "o: ");
//                                    ShellUtils.execCmd("am start -n com.tencent.wework/com.tencent.wework.launch.LaunchSplashActivity", true); //启动wework
                                    Parcelable o = null;
                                    try {
                                        o = (Parcelable) ParamClass.newInstance();
                                        ParamClass.getField("dMq").set(o, true);
                                        ParamClass.getField("dMr").set(o, true);
                                        ParamClass.getField("from").set(o, 2);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    Intent i = new Intent(context, AttendanceActivity2Class);
                                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    i.putExtra("data", o);
                                    context.startActivity(i);
                                    Log.e(TAG, "ooo: ");
                                }
                            }, 10000);
                        }
                    }
                }

            }
        });

        XposedHelpers.findAndHookMethod(View.class, "performClick", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Log.e(TAG, "performClick: " + param.thisObject);
                Log.e(TAG, "performClick1: " + param.thisObject.getClass());
                Log.e(TAG, "performClick2: " + param.thisObject.getClass().getName());
            }
        });
    }

    private float saltedLa(float f) {
        if (f > 0) {
            return (float) (f + 0.002082f + salt * (1 - (Math.random() * 2)));
        }
        return f;
    }

    private float saltedLo(float f) {
        if (f > 0) {
            return (float) (f + -0.005203f + salt * (1 - (Math.random() * 2)));
        }
        return f;
    }

    private void hkGPS(ClassLoader classLoader) {
        Log.d(TAG, "hkGPS: " + la + "#" + lo);

        XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", classLoader,
                "getCellLocation", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isOpen) return;
                        param.setResult(null);
                        Log.d(TAG, "getCellLocation");
                    }
                });

        XposedHelpers.findAndHookMethod("android.telephony.PhoneStateListener", classLoader,
                "onCellLocationChanged", CellLocation.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isOpen) return;
                        param.setResult(null);
                        Log.d(TAG, "onCellLocationChanged");
                    }
                });

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", classLoader,
                    "getPhoneCount", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!isOpen) return;
                            param.setResult(1);
                            Log.d(TAG, "getPhoneCount");
                        }
                    });
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", classLoader,
                    "getNeighboringCellInfo", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!isOpen) return;
                            param.setResult(new ArrayList<>());
                            Log.d(TAG, "getNeighboringCellInfo");
                        }
                    });
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
            XposedHelpers.findAndHookMethod("android.telephony.TelephonyManager", classLoader,
                    "getAllCellInfo", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!isOpen) return;
                            param.setResult(null);
                            Log.d(TAG, "getAllCellInfo");
                        }
                    });
            XposedHelpers.findAndHookMethod("android.telephony.PhoneStateListener", classLoader,
                    "onCellInfoChanged", List.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (!isOpen) return;
                            param.setResult(null);
                            Log.d(TAG, "onCellInfoChanged");
                        }
                    });
        }

        XposedHelpers.findAndHookMethod("android.net.wifi.WifiManager", classLoader, "getScanResults", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isOpen) return;
                param.setResult(new ArrayList<>());
                Log.d(TAG, "getScanResults");
            }
        });

        XposedHelpers.findAndHookMethod("android.net.wifi.WifiManager", classLoader, "getWifiState", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isOpen) return;
                param.setResult(1);
                Log.d(TAG, "getWifiState");
            }
        });

        XposedHelpers.findAndHookMethod("android.net.wifi.WifiManager", classLoader, "isWifiEnabled", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isOpen) return;
                param.setResult(true);
                Log.d(TAG, "isWifiEnabled");
            }
        });

        XposedHelpers.findAndHookMethod("android.net.wifi.WifiInfo", classLoader, "getMacAddress", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isOpen) return;
                param.setResult("00-00-00-00-00-00-00-00");
                Log.d(TAG, "getMacAddress");
            }
        });

        XposedHelpers.findAndHookMethod("android.net.wifi.WifiInfo", classLoader, "getSSID", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isOpen) return;
                param.setResult("null");
                Log.d(TAG, "getSSID");
            }
        });

        XposedHelpers.findAndHookMethod("android.net.wifi.WifiInfo", classLoader, "getBSSID", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isOpen) return;
                param.setResult("00-00-00-00-00-00-00-00");
                Log.d(TAG, "getBSSID");
            }
        });


        XposedHelpers.findAndHookMethod("android.net.NetworkInfo", classLoader,
                "getTypeName", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isOpen) return;
                        param.setResult("WIFI");
                        Log.d(TAG, "getTypeName");
                    }
                });
        XposedHelpers.findAndHookMethod("android.net.NetworkInfo", classLoader,
                "isConnectedOrConnecting", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isOpen) return;
                        param.setResult(true);
                        Log.d(TAG, "isConnectedOrConnecting");
                    }
                });

        XposedHelpers.findAndHookMethod("android.net.NetworkInfo", classLoader,
                "isConnected", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isOpen) return;
                        param.setResult(true);
                        Log.d(TAG, "isConnected");
                    }
                });

        XposedHelpers.findAndHookMethod("android.net.NetworkInfo", classLoader,
                "isAvailable", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isOpen) return;
                        param.setResult(true);
                        Log.d(TAG, "isAvailable");
                    }
                });

        XposedHelpers.findAndHookMethod("android.telephony.CellInfo", classLoader,
                "isRegistered", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isOpen) return;
                        param.setResult(true);
                        Log.d(TAG, "isRegistered");
                    }
                });

        XposedHelpers.findAndHookMethod(LocationManager.class, "getLastLocation", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isOpen) return;
                Location l = new Location(LocationManager.GPS_PROVIDER);
                l.setLatitude(saltedLa(la));
                l.setLongitude(saltedLo(lo));
                l.setAccuracy(100f);
                l.setTime(System.currentTimeMillis());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                }
                param.setResult(l);
                Log.d(TAG, "getLastLocation");
            }
        });

        XposedHelpers.findAndHookMethod(LocationManager.class, "getLastKnownLocation", String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isOpen) return;
                Location l = new Location(LocationManager.GPS_PROVIDER);
                l.setLatitude(saltedLa(la));
                l.setLongitude(saltedLo(lo));
                l.setAccuracy(100f);
                l.setTime(System.currentTimeMillis());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                }
                param.setResult(l);
                Log.d(TAG, "getLastKnownLocation");
            }
        });


        XposedBridge.hookAllMethods(LocationManager.class, "getProviders", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isOpen) return;
                ArrayList<String> arrayList = new ArrayList<>();
                arrayList.add("gps");
                param.setResult(arrayList);
                Log.d(TAG, "getProviders");
            }
        });

        XposedHelpers.findAndHookMethod(LocationManager.class, "getBestProvider", Criteria.class, Boolean.TYPE, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isOpen) return;
                param.setResult("gps");
                Log.d(TAG, "getBestProvider");
            }
        });

        XposedHelpers.findAndHookMethod(LocationManager.class, "addGpsStatusListener", GpsStatus.Listener.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!isOpen) return;
                if (param.args[0] != null) {
                    XposedHelpers.callMethod(param.args[0], "onGpsStatusChanged", 1);
                    XposedHelpers.callMethod(param.args[0], "onGpsStatusChanged", 3);
                }
                Log.d(TAG, "addGpsStatusListener");
            }
        });

        XposedHelpers.findAndHookMethod(LocationManager.class, "addNmeaListener", GpsStatus.NmeaListener.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!isOpen) return;
                param.setResult(false);
                Log.d(TAG, "addNmeaListener");
            }
        });

        XposedHelpers.findAndHookMethod("android.location.LocationManager", classLoader,
                "getGpsStatus", GpsStatus.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isOpen) return;
                        Log.d(TAG, "getGpsStatus");
                        GpsStatus gss = (GpsStatus) param.getResult();
                        if (gss == null)
                            return;

                        Class<?> clazz = GpsStatus.class;
                        Method m = null;
                        for (Method method : clazz.getDeclaredMethods()) {
                            if (method.getName().equals("setStatus")) {
                                if (method.getParameterTypes().length > 1) {
                                    m = method;
                                    break;
                                }
                            }
                        }
                        if (m == null)
                            return;

                        //access the private setStatus function of GpsStatus
                        m.setAccessible(true);

                        //make the apps belive GPS works fine now
                        int svCount = 5;
                        int[] prns = {1, 2, 3, 4, 5};
                        float[] snrs = {0, 0, 0, 0, 0};
                        float[] elevations = {0, 0, 0, 0, 0};
                        float[] azimuths = {0, 0, 0, 0, 0};
                        int ephemerisMask = 0x1f;
                        int almanacMask = 0x1f;

                        //5 satellites are fixed
                        int usedInFixMask = 0x1f;

                        XposedHelpers.callMethod(gss, "setStatus", svCount, prns, snrs, elevations, azimuths, ephemerisMask, almanacMask, usedInFixMask);
                        param.args[0] = gss;
                        param.setResult(gss);
                        try {
                            m.invoke(gss, svCount, prns, snrs, elevations, azimuths, ephemerisMask, almanacMask, usedInFixMask);
                            param.setResult(gss);
                        } catch (Exception e) {
                            log(e);
                        }
                    }
                });

        for (Method method : LocationManager.class.getDeclaredMethods()) {
            if (method.getName().equals("requestLocationUpdates")
                    && !Modifier.isAbstract(method.getModifiers())
                    && Modifier.isPublic(method.getModifiers())) {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isOpen) return;
                        Log.d(TAG, "requestLocationUpdates");
                        if (param.args.length >= 4 && (param.args[3] instanceof LocationListener)) {

                            LocationListener ll = (LocationListener) param.args[3];

                            Class<?> clazz = LocationListener.class;
                            Method m = null;
                            for (Method method : clazz.getDeclaredMethods()) {
                                if (method.getName().equals("onLocationChanged") && !Modifier.isAbstract(method.getModifiers())) {
                                    m = method;
                                    break;
                                }
                            }
                            Location l = new Location(LocationManager.GPS_PROVIDER);
                            l.setLatitude(saltedLa(la));
                            l.setLongitude(saltedLo(lo));
                            l.setAccuracy(10.00f);
                            l.setTime(System.currentTimeMillis());
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                                l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                            }
                            XposedHelpers.callMethod(ll, "onLocationChanged", l);
                            try {
                                if (m != null) {
                                    m.invoke(ll, l);
                                }
                            } catch (Exception e) {
                                log(e);
                            }
                        }
                    }
                });
            }

            if (method.getName().equals("requestSingleUpdate ")
                    && !Modifier.isAbstract(method.getModifiers())
                    && Modifier.isPublic(method.getModifiers())) {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isOpen) return;
                        Log.d(TAG, "requestSingleUpdate");
                        if (param.args.length >= 3 && (param.args[1] instanceof LocationListener)) {

                            LocationListener ll = (LocationListener) param.args[3];

                            Class<?> clazz = LocationListener.class;
                            Method m = null;
                            for (Method method : clazz.getDeclaredMethods()) {
                                if (method.getName().equals("onLocationChanged") && !Modifier.isAbstract(method.getModifiers())) {
                                    m = method;
                                    break;
                                }
                            }

                            try {
                                if (m != null) {
                                    Location l = new Location(LocationManager.GPS_PROVIDER);
                                    l.setLatitude(saltedLa(la));
                                    l.setLongitude(saltedLo(lo));
                                    l.setAccuracy(100f);
                                    l.setTime(System.currentTimeMillis());
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                                        l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                                    }
                                    m.invoke(ll, l);
                                }
                            } catch (Exception e) {
                                log(e);
                            }
                        }
                    }
                });
            }
        }
    }

}
