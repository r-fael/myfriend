package com.myfriend;

import android.content.ComponentName;
import android.os.IBinder;
import java.lang.reflect.Method;

/**
 * Ferramenta de linha de comando (não roda dentro do app) para ativar o mostrador via ADB:
 *
 * <pre>
 * adb shell 'CLASSPATH=$(pm path com.myfriend | cut -d: -f2) app_process / com.myfriend.SetWatchFaceTool'
 * </pre>
 */
public final class SetWatchFaceTool {

    private SetWatchFaceTool() {
    }

    public static void main(String[] args) {
        try {
            IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class)
                    .invoke(null, "wallpaper");
            Object wallpaperManager = Class.forName("android.app.IWallpaperManager$Stub")
                    .getMethod("asInterface", IBinder.class)
                    .invoke(null, binder);

            ComponentName component = new ComponentName("com.myfriend", FriendWatchFaceService.class.getName());
            Class<?> builderClass = Class.forName("android.app.wallpaper.WallpaperDescription$Builder");
            Object builder = builderClass.getDeclaredConstructor().newInstance();
            builderClass.getMethod("setComponent", ComponentName.class).invoke(builder, component);
            Object description = builderClass.getMethod("build").invoke(builder);

            for (Method method : wallpaperManager.getClass().getDeclaredMethods()) {
                if (method.getName().equals("setWallpaperComponentChecked")) {
                    method.invoke(wallpaperManager, description, "com.android.shell", 1, 0);
                    System.out.println("SUCCESS: watch face ativado!");
                    return;
                }
            }
            System.out.println("FAIL: setWallpaperComponentChecked não encontrado");
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }
}
