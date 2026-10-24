package com.enexyz.flyoptimizer;

import android.os.RemoteException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public final class FlyShellService extends IFlyService.Stub {
    public FlyShellService() { }

    private static boolean validAnimation(String key) {
        return "window_animation_scale".equals(key)
            || "transition_animation_scale".equals(key)
            || "animator_duration_scale".equals(key);
    }
    private static boolean validPackage(String key) {
        return key != null && key.length() <= 180
            && key.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    }
    private static void checkKey(String category, String key) {
        if ("animation".equals(category) && validAnimation(key)) return;
        if ("overlay".equals(category) && validPackage(key)) return;
        throw new SecurityException("Недопустимая команда или идентификатор");
    }
    private static String run(String... cmd) throws RemoteException {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(7, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("Превышено время выполнения команды");
            }
            StringBuilder s = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (s.length() > 0) s.append('\n');
                    s.append(line);
                }
            }
            if (p.exitValue() != 0) throw new IllegalStateException("Код " + p.exitValue() + ": " + s);
            return s.toString().trim();
        } catch (Exception e) {
            throw new RemoteException(e.toString());
        }
    }
    @Override public String get(String category, String key) throws RemoteException {
        checkKey(category,key);
        if ("animation".equals(category)) return run("settings","get","global",key);
        return run("device_config","get","game_overlay",key);
    }
    @Override public void put(String category, String key, String value) throws RemoteException {
        checkKey(category,key);
        if (value == null || value.length()>2048 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new SecurityException("Недопустимое значение");
        if ("animation".equals(category)) {
            if (!value.matches("[0-9]+(\\.[0-9]{1,5})?")) throw new SecurityException("Неверная шкала анимации");
            run("settings","put","global",key,value);
        } else {
            // only platform-supported 60/120 profiles, or an original value to be restored.
            if (!value.matches("[A-Za-z0-9_=.,:;+\\-]+"))
                throw new SecurityException("Недопустимая конфигурация Game Mode");
            run("device_config","put","game_overlay",key,value);
        }
    }
    @Override public void delete(String category, String key) throws RemoteException {
        checkKey(category,key);
        if ("animation".equals(category)) run("settings","delete","global",key);
        else run("device_config","delete","game_overlay",key);
    }
    public void destroy() { System.exit(0); }
}
