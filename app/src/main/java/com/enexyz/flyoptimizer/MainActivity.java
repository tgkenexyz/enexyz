package com.enexyz.flyoptimizer;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Display;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;
import android.view.View;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int REQUEST_SHIZUKU = 4491;
    private static final String[] ANIMATIONS = {
        "window_animation_scale", "transition_animation_scale", "animator_duration_scale"
    };
    private static final String NO_VALUE = "null";
    private final int bg = Color.rgb(13,18,30);
    private final int fg = Color.rgb(239,244,255);
    private final int secondary = Color.rgb(156,178,201);
    private final int accent = Color.rgb(83,219,177);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<String> gameIds = new ArrayList<>();
    private LinearLayout body;
    private TextView status;
    private TextView diagnostics;
    private Spinner games;
    private EditText gamePackage;
    private SharedPreferences backup;
    private volatile IFlyService service;
    private volatile boolean binding;
    private final Shizuku.OnRequestPermissionResultListener permissionListener =
        (requestCode, result) -> {
            if (requestCode == REQUEST_SHIZUKU
                && result == PackageManager.PERMISSION_GRANTED) bindShell();
            else report("Разрешение Shizuku не выдано.");
        };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = IFlyService.Stub.asInterface(binder);
            binding = false;
            report("Подключён Shizuku. Можешь применять профиль и выполнять откат.");
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            binding = false;
            report("Служба Shizuku отключена. Для отката подключи снова.");
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backup = getSharedPreferences("saved_settings_v2", MODE_PRIVATE);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        body = new LinearLayout(this);
        body.setPadding(dp(20), dp(16), dp(20), dp(32));
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body);
        setContentView(scroll);

        add("FLY // OPTIMIZER", 27, accent, true);
        add("Android " + Build.VERSION.RELEASE + " • без ROOT • версия 0.3", 13, secondary, false);
        add("Диагностика", 19, fg, true);
        diagnostics = add("", 13, fg, false);
        button("Обновить диагностику", this::diagnose);
        add("ИГРОВОЙ ПРОФИЛЬ", 19, fg, true);
        add("Устройство по системным данным: " + deviceLabel() + ". Цель — уменьшить скачки времени кадра. Профили 120 и 60 FPS только ограничивают кадры, если Android Game Mode и игра поддерживают это. Термозащита не изменяется.", 13, secondary, false);

        add("Выбери игру", 14, fg, true);
        games = new Spinner(this);
        ArrayList<String> labels = new ArrayList<>();
        labels.add("Выбрать из установленных приложений...");
        gameIds.add("");
        try {
            Intent launcher = new Intent(Intent.ACTION_MAIN);
            launcher.addCategory(Intent.CATEGORY_LAUNCHER);
            ArrayList<ResolveInfo> apps = new ArrayList<>(
                getPackageManager().queryIntentActivities(launcher, 0));
            apps.sort(Comparator.comparing(a -> a.loadLabel(getPackageManager()).toString().toLowerCase(Locale.ROOT)));
            HashSet<String> seen = new HashSet<>();
            for (ResolveInfo app : apps) {
                if (app.activityInfo == null) continue;
                String id = app.activityInfo.packageName;
                if (id.equals(getPackageName()) || !seen.add(id)) continue;
                gameIds.add(id);
                labels.add(app.loadLabel(getPackageManager()) + "  •  " + id);
            }
        } catch (Exception ignored) { }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        games.setAdapter(adapter);
        body.addView(games);
        gamePackage = new EditText(this);
        gamePackage.setTextColor(fg);
        gamePackage.setHintTextColor(secondary);
        gamePackage.setSingleLine(true);
        gamePackage.setHint("Пакет игры, например com.example.game");
        gamePackage.setTextSize(14);
        body.addView(gamePackage);
        games.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && position < gameIds.size()) gamePackage.setText(gameIds.get(position));
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        add("Необходимо активировать Shizuku с помощью беспроводной отладки или ADB. Без него доступны только диагностика и настройки. Записываем исходную конфигурацию до любого изменения.",13,secondary,false);
        button("1 • Подключить Shizuku", this::bindShell);
        button("2 • Стабилизировать: цель 120 FPS", () -> applyGame(120));
        button("3 • Экономный тест: цель 60 FPS", () -> applyGame(60));
        add("Game Mode должен поддерживать режим производительности, а игра — системные вмешательства. Если FPS не меняется, это ограничение игры/прошивки, а не признак успешного применения.", 12, secondary, false);

        add("АНИМАЦИИ СИСТЕМЫ (не FPS игры)", 19, fg, true);
        button("Анимации интерфейса: 0.5×", () -> applyAnimations("0.5"));
        button("Анимации интерфейса: 0×", () -> applyAnimations("0"));
        add("ОТКАТ", 19, accent, true);
        button("↶ BACK / ВОССТАНОВИТЬ ИСХОДНОЕ", this::rollback);
        add("BACK восстанавливает только изменённые FLY Optimizer настройки Android Game Mode для выбранной игры и 3 масштаба анимаций. Снимок остаётся до успешного восстановления. Не удаляй приложение или его данные до отката.", 12, secondary, false);
        add("ШТАТНЫЕ НАСТРОЙКИ", 19, fg, true);
        button("Частота экрана", () -> openSettings(Settings.ACTION_DISPLAY_SETTINGS));
        button("Настройки приложений", () -> openSettings(Settings.ACTION_APPLICATION_SETTINGS));
        button("Энергосбережение", () -> openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS));
        add("Не меняем частоты CPU/GPU/RAM, термозащиту, системные службы и управление питанием производителей. Нет постоянной службы, рекламы и сетевых разрешений.", 12, secondary, false);
        status = add("Готово. Изменений ещё нет.",14,accent,true);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        diagnose();
    }

    private String deviceLabel() {
        String device = Build.DEVICE == null ? "" : Build.DEVICE.toLowerCase(Locale.ROOT);
        if ("peridot".equals(device)) return "POCO F6 (peridot / Snapdragon 8s Gen 3), 120 Гц";
        if ("vermeer".equals(device)) return "POCO F6 Pro (vermeer / Snapdragon 8 Gen 2), 120 Гц";
        return Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + "); проверь модель";
    }
    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }
    private TextView add(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, dp(8), 0, dp(8));
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(t);
        return t;
    }
    private Button button(String text, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextColor(bg);
        b.setTextSize(14);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin=dp(7);
        body.addView(b,p);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private void openSettings(String action) {
        try { startActivity(new Intent(action)); }
        catch (Exception e) { report("Настройки недоступны: "+e.getMessage()); }
    }
    private void report(String s) {
        runOnUiThread(() -> { if (status!=null) status.setText(s); });
    }
    private void diagnose() {
        try {
            ActivityManager am = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memory);
            StatFs storage = new StatFs(Environment.getDataDirectory().getPath());
            Intent battery = registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int temp = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            String thermal = Build.VERSION.SDK_INT>=29 ? String.valueOf(pm.getCurrentThermalStatus()) + " (0=нет ограничения, 3=сильный нагрев)" : "недоступно";
            StringBuilder modes = new StringBuilder();
            for (Display.Mode m : getWindowManager().getDefaultDisplay().getSupportedModes()) {
                String s = String.format(Locale.ROOT,"%.0f", m.getRefreshRate());
                if (modes.indexOf(s+" ")<0) modes.append(s).append(" Гц  ");
            }
            double gib=1024.0*1024.0*1024.0;
            DecimalFormat f = new DecimalFormat("0.0");
            String soc = Build.VERSION.SDK_INT>=31?Build.SOC_MODEL:Build.HARDWARE;
            String text = "Модель: "+Build.MANUFACTURER+" "+Build.MODEL
                +"\nКод устройства: "+Build.DEVICE+" / "+Build.HARDWARE
                +"\nЧип (по системе): "+soc
                +"\nAndroid "+Build.VERSION.RELEASE+" • SDK "+Build.VERSION.SDK_INT
                +"\nСборка: "+Build.DISPLAY
                +"\nЭкран, доступные режимы: "+modes
                +"\nRAM свободно: "+f.format(memory.availMem/gib)+" / "+f.format(memory.totalMem/gib)+" ГиБ"
                +"\nНакопитель свободно: "+f.format(storage.getAvailableBytes()/gib)+" ГиБ"
                +"\nЯдер CPU: "+Runtime.getRuntime().availableProcessors()
                +"\nБатарея: "+(temp<0?"нет данных":(temp/10.0)+" °C")
                +"\nТермостатус Android: "+thermal
                +"\nСнимки: "+(backup.getBoolean("game_active",false)?"Game Mode ":"")
                           +(backup.getBoolean("anim_active",false)?"анимации":"")
                           +(!backup.getBoolean("game_active",false)&&!backup.getBoolean("anim_active",false)?"нет":"");
            diagnostics.setText(text);
        } catch (Exception e) { diagnostics.setText("Сбор диагностики не удался: "+e.getMessage()); }
    }
    private Shizuku.UserServiceArgs args() {
        return new Shizuku.UserServiceArgs(
            new ComponentName(getPackageName(), FlyShellService.class.getName()))
            .daemon(false).processNameSuffix("optimizer").debuggable(BuildConfig.DEBUG).version(2);
    }
    private void bindShell() {
        if (binding) { report("Подключение уже запрошено."); return; }
        try {
            if (!Shizuku.pingBinder()) {
                report("Shizuku выключен. Запусти приложение Shizuku через беспроводную отладку.");
                return;
            }
            if (Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(REQUEST_SHIZUKU);
                return;
            }
            binding=true;
            report("Подключение к Shizuku...");
            Shizuku.bindUserService(args(), connection);
        } catch (Exception | LinkageError e) {
            binding=false;
            report("Ошибка Shizuku: "+e.getMessage());
        }
    }
    private IFlyService requireService() {
        IFlyService s=service;
        if(s==null) throw new IllegalStateException("Сначала подключи Shizuku.");
        return s;
    }
    private void background(String what, Task task) {
        report(what+"...");
        executor.execute(() -> {
            try { report(task.run()); }
            catch (Exception e) { report("Ошибка • "+e.getMessage()+" • резервная копия сохранена, если создавалась."); }
            runOnUiThread(this::diagnose);
        });
    }
    private interface Task { String run() throws Exception; }
    private static String norm(String value) {
        return value == null || value.trim().isEmpty() ? NO_VALUE : value.trim();
    }
    private static boolean same(String expected,String actual) {
        return norm(expected).equals(norm(actual));
    }
    private static void restoreOne(IFlyService s, String category, String key, String value) throws Exception {
        if (NO_VALUE.equals(value)) s.delete(category,key);
        else s.put(category,key,value);
        if(!same(value,s.get(category,key))) throw new IllegalStateException("Не удалось проверить восстановление "+key);
    }
    private String currentPackage() {
        String pkg = gamePackage.getText().toString().trim();
        if(!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
            throw new IllegalArgumentException("Выбери игру из списка или укажи полный идентификатор пакета.");
        return pkg;
    }
    private void applyGame(int fps) {
        final String pkg;
        try { pkg=currentPackage(); }
        catch (Exception e) { report(e.getMessage()); return; }
        background("Сохранение игрового профиля", () -> {
            IFlyService s=requireService();
            if(backup.getBoolean("game_active",false)) {
                if(!pkg.equals(backup.getString("game_pkg","")))
                    throw new IllegalStateException("Сначала откати профиль предыдущей игры: "+backup.getString("game_pkg",""));
            } else {
                String before=norm(s.get("overlay",pkg));
                if(!backup.edit().putString("game_pkg",pkg)
                    .putString("game_before",before).putBoolean("game_active",true).commit())
                    throw new IllegalStateException("Не удалось записать резервную копию.");
            }
            String config="mode=2,fps="+fps;
            s.put("overlay",pkg,config);
            if(!same(config,s.get("overlay",pkg)))
                throw new IllegalStateException("Android не подтвердил запись Game Mode. Сделай BACK.");
            return "Профиль "+fps+" FPS записан для "+pkg+". Это не гарантия лимита в игре: активируй Game Mode «Производительность» и перезапусти игру. BACK восстановит исходную конфигурацию.";
        });
    }
    private void applyAnimations(String scale) {
        background("Применение анимаций", () -> {
            IFlyService s=requireService();
            if(!backup.getBoolean("anim_active",false)) {
                String[] values=new String[ANIMATIONS.length];
                for(int i=0;i<ANIMATIONS.length;i++) values[i]=norm(s.get("animation",ANIMATIONS[i]));
                SharedPreferences.Editor ed=backup.edit();
                for(int i=0;i<ANIMATIONS.length;i++) ed.putString("anim_before_"+i,values[i]);
                if(!ed.putBoolean("anim_active",true).commit())
                    throw new IllegalStateException("Не удалось сохранить резервную копию анимаций");
            }
            for(String key:ANIMATIONS) {
                s.put("animation",key,scale);
                String now=s.get("animation",key);
                try {
                    if(Math.abs(Double.parseDouble(now)-Double.parseDouble(scale))>0.0001)
                        throw new IllegalStateException("Система не применила "+key);
                } catch(NumberFormatException e) { throw new IllegalStateException("Некорректный ответ системы: "+now); }
            }
            return "Анимации "+scale+"× применены. FPS в игре не изменён; BACK вернёт исходные масштабы.";
        });
    }
    private void rollback() {
        background("Восстановление исходного состояния", () -> {
            IFlyService s=requireService();
            boolean game=backup.getBoolean("game_active",false);
            boolean anim=backup.getBoolean("anim_active",false);
            if(!game&&!anim) return "Нечего откатывать: изменений FLY Optimizer нет.";
            StringBuilder result=new StringBuilder();
            if(game) {
                String pkg=backup.getString("game_pkg","");
                String old=backup.getString("game_before",null);
                if(old==null || pkg.isEmpty()) throw new IllegalStateException("Игровой снимок отсутствует. Откат остановлен.");
                try {
                    restoreOne(s,"overlay",pkg,old);
                    if(!backup.edit().remove("game_pkg").remove("game_before").putBoolean("game_active",false).commit())
                        throw new IllegalStateException("Игра восстановлена, но не удалось очистить снимок");
                    result.append("Game Mode восстановлен. ");
                } catch(Exception e) { result.append("Game Mode: ").append(e.getMessage()).append(". "); }
            }
            if(anim) {
                boolean success=true;
                for(int i=0;i<ANIMATIONS.length;i++) {
                    String old=backup.getString("anim_before_"+i,null);
                    if(old==null) { success=false; result.append("Потерян снимок ").append(i).append(". "); continue; }
                    try { restoreOne(s,"animation",ANIMATIONS[i],old); }
                    catch(Exception e) { success=false; result.append("Анимация ").append(i).append(": ").append(e.getMessage()).append(". "); }
                }
                if(success) {
                    SharedPreferences.Editor ed=backup.edit().putBoolean("anim_active",false);
                    for(int i=0;i<ANIMATIONS.length;i++) ed.remove("anim_before_"+i);
                    if(ed.commit()) result.append("Анимации восстановлены. ");
                    else result.append("Настройки восстановлены, но снимок не очищен. ");
                }
            }
            result.append("Неудавшиеся пункты остаются в резервной копии.");
            return result.toString();
        });
    }
    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        executor.shutdown();
        super.onDestroy();
    }
}
