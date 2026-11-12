package android.content;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/** Imitação das SharedPreferences gravando num arquivo .properties. */
public class SharedPreferences {
    private final File file;
    private final Properties values = new Properties();

    SharedPreferences(File file) {
        this.file = file;
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                values.load(in);
            } catch (IOException ignored) {
                // Começa do zero.
            }
        }
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public int getInt(String key, int def) {
        String v = values.getProperty(key);
        return v == null ? def : Integer.parseInt(v);
    }

    public long getLong(String key, long def) {
        String v = values.getProperty(key);
        return v == null ? def : Long.parseLong(v);
    }

    public float getFloat(String key, float def) {
        String v = values.getProperty(key);
        return v == null ? def : Float.parseFloat(v);
    }

    public boolean getBoolean(String key, boolean def) {
        String v = values.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    public Editor edit() {
        return new Editor();
    }

    public final class Editor {
        private final Properties pending = new Properties();
        private final java.util.Set<String> removed = new java.util.HashSet<>();

        public Editor putInt(String k, int v) { pending.setProperty(k, Integer.toString(v)); return this; }
        public Editor putLong(String k, long v) { pending.setProperty(k, Long.toString(v)); return this; }
        public Editor putFloat(String k, float v) { pending.setProperty(k, Float.toString(v)); return this; }
        public Editor putBoolean(String k, boolean v) { pending.setProperty(k, Boolean.toString(v)); return this; }
        public Editor remove(String k) { removed.add(k); return this; }

        public void apply() {
            for (String k : removed) {
                values.remove(k);
            }
            values.putAll(pending);
            try (OutputStream out = new FileOutputStream(file)) {
                values.store(out, "myfriend (simulador de PC)");
            } catch (IOException ignored) {
                // Sem salvar.
            }
        }

        public boolean commit() {
            apply();
            return true;
        }
    }
}
