package app.template.extension;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class TelegramHideHelper {

    private static final String PREFS_NAME = "telegram_ex_prefs";
    private static final String KEY_HIDDEN_CHATS = "hidden_chat_ids";

    private static volatile long lastOpenedDialogId = 0L;
    private static volatile boolean showHiddenTemporarily = false;
    private static volatile long lastCommandTime = 0L;
    private static Set<String> cachedHiddenIds = null;

    private static Context getAppContext() {
        try {
            Class<?> loader = Class.forName("org.telegram.messenger.ApplicationLoader");
            Field field = loader.getField("applicationContext");
            return (Context) field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static synchronized Set<String> getHiddenIds() {
        if (cachedHiddenIds != null) {
            return cachedHiddenIds;
        }
        cachedHiddenIds = new HashSet<>();
        Context ctx = getAppContext();
        if (ctx != null) {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> saved = prefs.getStringSet(KEY_HIDDEN_CHATS, null);
            if (saved != null) {
                cachedHiddenIds.addAll(saved);
            }
        }
        return cachedHiddenIds;
    }

    private static synchronized void saveHiddenIds(Set<String> ids) {
        cachedHiddenIds = new HashSet<>(ids);
        Context ctx = getAppContext();
        if (ctx != null) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_HIDDEN_CHATS, new HashSet<>(ids))
                .apply();
        }
    }

    private static void showToast(final String message) {
        final Context ctx = getAppContext();
        if (ctx == null) return;
        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        );
    }

    public static void onChatOpened(Object chatActivity) {
        if (chatActivity == null) return;
        try {
            Method getDialogId = chatActivity.getClass().getMethod("getDialogId");
            Object res = getDialogId.invoke(chatActivity);
            if (res instanceof Long && ((Long) res) != 0L) {
                lastOpenedDialogId = (Long) res;
                return;
            }
        } catch (Throwable ignored) {}

        try {
            Field f = chatActivity.getClass().getDeclaredField("dialog_id");
            f.setAccessible(true);
            long id = f.getLong(chatActivity);
            if (id != 0L) {
                lastOpenedDialogId = id;
            }
        } catch (Throwable ignored) {}
    }

    public static void onSearchQuery(String query) {
        if (query == null) return;
        String cmd = query.trim().toLowerCase();
        if (!cmd.startsWith("#")) return;

        long now = System.currentTimeMillis();
        if (now - lastCommandTime < 1500) return;

        if (cmd.equals("#gizle")) {
            lastCommandTime = now;
            if (lastOpenedDialogId == 0L) {
                showToast("Önce gizlemek istediğin sohbete girip çık.");
                return;
            }
            Set<String> ids = new HashSet<>(getHiddenIds());
            ids.add(String.valueOf(lastOpenedDialogId));
            saveHiddenIds(ids);
            showToast("Son açılan sohbet gizlendi.");
        } else if (cmd.equals("#cikar")) {
            lastCommandTime = now;
            if (lastOpenedDialogId == 0L) {
                showToast("Önce çıkaracağın sohbete girip çık.");
                return;
            }
            Set<String> ids = new HashSet<>(getHiddenIds());
            ids.remove(String.valueOf(lastOpenedDialogId));
            saveHiddenIds(ids);
            showToast("Sohbet gizliden çıkarıldı.");
        } else if (cmd.equals("#ac")) {
            lastCommandTime = now;
            showHiddenTemporarily = !showHiddenTemporarily;
            showToast(showHiddenTemporarily ? "Gizli sohbetler: AÇIK" : "Gizli sohbetler: KAPALI");
        } else if (cmd.equals("#temizle")) {
            lastCommandTime = now;
            saveHiddenIds(new HashSet<>());
            showHiddenTemporarily = false;
            showToast("Tüm gizli sohbetler açıldı.");
        }
    }

    public static ArrayList<?> filterDialogs(ArrayList<?> original) {
        if (original == null || original.isEmpty() || showHiddenTemporarily) {
            return original;
        }
        Set<String> hidden = getHiddenIds();
        if (hidden.isEmpty()) {
            return original;
        }

        ArrayList<Object> filtered = new ArrayList<>(original.size());
        for (Object dialog : original) {
            if (dialog == null) continue;
            try {
                Field idField = dialog.getClass().getField("id");
                long id = idField.getLong(dialog);
                if (!hidden.contains(String.valueOf(id))) {
                    filtered.add(dialog);
                }
            } catch (Throwable ignored) {
                filtered.add(dialog);
            }
        }
        return filtered;
    }
}
