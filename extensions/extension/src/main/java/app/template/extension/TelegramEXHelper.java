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
import java.util.Iterator;
import java.util.Set;

public final class TelegramEXHelper {

    private static final String PREFS_NAME = "telegram_ex_prefs";
    private static final String KEY_HIDDEN_CHATS = "hidden_chat_ids";
    private static final String KEY_HIDDEN_CONTACTS = "hidden_contact_ids";
    private static final String KEY_GHOST_MODE = "ghost_mode";

    private static volatile long lastOpenedDialogId = 0L;
    private static volatile boolean showHiddenChatsTemp = false;
    private static volatile boolean showHiddenContactsTemp = false;
    private static volatile long lastCommandTime = 0L;

    private static Set<String> cachedHiddenChats = null;
    private static Set<String> cachedHiddenContacts = null;
    private static Boolean cachedGhostMode = null;

    private static Context getAppContext() {
        try {
            Class<?> loader = Class.forName("org.telegram.messenger.ApplicationLoader");
            Field field = loader.getField("applicationContext");
            return (Context) field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SharedPreferences getPrefs() {
        Context ctx = getAppContext();
        return ctx != null ? ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) : null;
    }

    private static synchronized Set<String> getSet(String key, boolean isContacts) {
        Set<String> cache = isContacts ? cachedHiddenContacts : cachedHiddenChats;
        if (cache != null) return cache;
        cache = new HashSet<>();
        SharedPreferences prefs = getPrefs();
        if (prefs != null) {
            Set<String> saved = prefs.getStringSet(key, null);
            if (saved != null) cache.addAll(saved);
        }
        if (isContacts) cachedHiddenContacts = cache;
        else cachedHiddenChats = cache;
        return cache;
    }

    private static synchronized void saveSet(String key, Set<String> ids, boolean isContacts) {
        Set<String> copy = new HashSet<>(ids);
        if (isContacts) cachedHiddenContacts = copy;
        else cachedHiddenChats = copy;
        SharedPreferences prefs = getPrefs();
        if (prefs != null) {
            prefs.edit().putStringSet(key, new HashSet<>(copy)).apply();
        }
    }

    public static synchronized boolean isGhostMode() {
        if (cachedGhostMode != null) return cachedGhostMode;
        SharedPreferences prefs = getPrefs();
        cachedGhostMode = prefs != null && prefs.getBoolean(KEY_GHOST_MODE, false);
        return cachedGhostMode;
    }

    private static synchronized void toggleGhostMode() {
        boolean next = !isGhostMode();
        cachedGhostMode = next;
        SharedPreferences prefs = getPrefs();
        if (prefs != null) prefs.edit().putBoolean(KEY_GHOST_MODE, next).apply();
        showToast(next ? "Hayalet Mod: AÇIK (Yazıyor gizli)" : "Hayalet Mod: KAPALI");
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
            if (id != 0L) lastOpenedDialogId = id;
        } catch (Throwable ignored) {}
    }

    public static void onSearchQuery(String query) {
        if (query == null) return;
        String cmd = query.trim().toLowerCase();
        if (!cmd.startsWith("#")) return;

        long now = System.currentTimeMillis();
        if (now - lastCommandTime < 1500) return;

        switch (cmd) {
            case "#gizle":
                lastCommandTime = now;
                if (lastOpenedDialogId == 0L) {
                    showToast("Önce gizlemek istediğin sohbete girip çık.");
                    return;
                }
                Set<String> chatsAdd = new HashSet<>(getSet(KEY_HIDDEN_CHATS, false));
                chatsAdd.add(String.valueOf(lastOpenedDialogId));
                saveSet(KEY_HIDDEN_CHATS, chatsAdd, false);
                showToast("Sohbet gizlendi.");
                break;

            case "#cikar":
                lastCommandTime = now;
                if (lastOpenedDialogId == 0L) {
                    showToast("Önce çıkaracağın sohbete girip çık.");
                    return;
                }
                Set<String> chatsRem = new HashSet<>(getSet(KEY_HIDDEN_CHATS, false));
                chatsRem.remove(String.valueOf(lastOpenedDialogId));
                saveSet(KEY_HIDDEN_CHATS, chatsRem, false);
                showToast("Sohbet gizliden çıkarıldı.");
                break;

            case "#ac":
                lastCommandTime = now;
                showHiddenChatsTemp = !showHiddenChatsTemp;
                showToast(showHiddenChatsTemp ? "Gizli sohbetler: AÇIK" : "Gizli sohbetler: KAPALI");
                break;

            case "#kisigizle":
                lastCommandTime = now;
                if (lastOpenedDialogId <= 0L) {
                    showToast("Önce gizlemek istediğin kişinin sohbetine girip çık.");
                    return;
                }
                Set<String> contAdd = new HashSet<>(getSet(KEY_HIDDEN_CONTACTS, true));
                contAdd.add(String.valueOf(lastOpenedDialogId));
                saveSet(KEY_HIDDEN_CONTACTS, contAdd, true);
                showToast("Kişi rehberden gizlendi.");
                break;

            case "#kisicikar":
                lastCommandTime = now;
                if (lastOpenedDialogId <= 0L) {
                    showToast("Önce çıkaracağın kişinin sohbetine girip çık.");
                    return;
                }
                Set<String> contRem = new HashSet<>(getSet(KEY_HIDDEN_CONTACTS, true));
                contRem.remove(String.valueOf(lastOpenedDialogId));
                saveSet(KEY_HIDDEN_CONTACTS, contRem, true);
                showToast("Kişi gizliden çıkarıldı.");
                break;

            case "#kisiac":
                lastCommandTime = now;
                showHiddenContactsTemp = !showHiddenContactsTemp;
                showToast(showHiddenContactsTemp ? "Gizli kişiler: AÇIK" : "Gizli kişiler: KAPALI");
                break;

            case "#hayalet":
                lastCommandTime = now;
                toggleGhostMode();
                break;

            case "#temizle":
                lastCommandTime = now;
                saveSet(KEY_HIDDEN_CHATS, new HashSet<>(), false);
                saveSet(KEY_HIDDEN_CONTACTS, new HashSet<>(), true);
                showHiddenChatsTemp = false;
                showHiddenContactsTemp = false;
                showToast("Tüm gizli sohbet ve kişiler sıfırlandı.");
                break;
        }
    }

    public static ArrayList<?> filterDialogs(ArrayList<?> original) {
        if (original == null || original.isEmpty() || showHiddenChatsTemp) return original;
        Set<String> hidden = getSet(KEY_HIDDEN_CHATS, false);
        if (hidden.isEmpty()) return original;

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

    public static void filterContacts(Object contactsActivity) {
        if (contactsActivity == null || showHiddenContactsTemp) return;
        Set<String> hidden = getSet(KEY_HIDDEN_CONTACTS, true);
        if (hidden.isEmpty()) return;

        try {
            int currentAccount = 0;
            Class<?> cls = contactsActivity.getClass();
            while (cls != null) {
                try {
                    Field f = cls.getDeclaredField("currentAccount");
                    f.setAccessible(true);
                    currentAccount = f.getInt(contactsActivity);
                    break;
                } catch (NoSuchFieldException e) {
                    cls = cls.getSuperclass();
                }
            }

            Class<?> controllerClass = Class.forName("org.telegram.messenger.ContactsController");
            Method getInstance = controllerClass.getMethod("getInstance", int.class);
            Object controller = getInstance.invoke(null, currentAccount);
            if (controller == null) return;

            Field contactsField = controllerClass.getField("contacts");
            ArrayList<?> contacts = (ArrayList<?>) contactsField.get(controller);
            if (contacts == null || contacts.isEmpty()) return;

            Iterator<?> it = contacts.iterator();
            while (it.hasNext()) {
                Object contact = it.next();
                if (contact == null) continue;
                try {
                    Field userIdField = contact.getClass().getField("user_id");
                    long uid = userIdField.getLong(contact);
                    if (hidden.contains(String.valueOf(uid))) {
                        it.remove();
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
}
