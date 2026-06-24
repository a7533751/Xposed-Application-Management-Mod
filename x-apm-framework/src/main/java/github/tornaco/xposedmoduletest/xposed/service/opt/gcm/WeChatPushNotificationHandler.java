package github.tornaco.xposedmoduletest.xposed.service.opt.gcm;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.RequiresApi;
import android.util.Log;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import github.tornaco.xposedmoduletest.model.PushMessage;
import github.tornaco.xposedmoduletest.service.PushMessageNotificationService;
import github.tornaco.xposedmoduletest.xposed.service.notification.UniqueIdFactory;
import github.tornaco.xposedmoduletest.xposed.util.XposedLog;

/**
 * Created by Tornaco on 2018/4/10 17:30.
 * God bless no bug!
 */
public class WeChatPushNotificationHandler extends BasePushNotificationHandler {

    public static final String WECHAT_PKG_NAME = "com.tencent.mm";

    private static final String NOTIFICATION_CHANNEL_ID_WECHAT = "dev.tornaco.notification.channel.id.X-APM-WECHAT";
    private static final String NOTIFICATION_CHANNEL_NAME_WECHAT = "WeChat";

    private static final String WECHAT_INTENT_KEY_ALERT = "alert";
    private static final String WECHAT_INTENT_KEY_BADGE = "badge";
    private static final String WECHAT_INTENT_KEY_FROM = "from";
    private static final String WECHAT_INTENT_KEY_MSG_TYPE = "msgType";
    private static final String WECHAT_INTENT_KEY_SEQ = "seq";

    private static final String GCM_INTENT_KEY_MESSAGE_ID = "google.message_id";

    private static final long STRONG_PUSH_DEDUP_TTL_MILLIS = 6 * 60 * 60 * 1000L;
    private static final int MAX_SEEN_STRONG_PUSH_COUNT = 256;

    private final RecentPushCache recentStrongPushCache =
            new RecentPushCache(STRONG_PUSH_DEDUP_TTL_MILLIS, MAX_SEEN_STRONG_PUSH_COUNT);

    public WeChatPushNotificationHandler(Context context, NotificationHandlerSettingsRetriever retriever) {
        super(context, retriever);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public static boolean launchNotificationChannelSettingsForOreo(Context context,
                                                                   boolean android/*App layer or FW layer*/) {
        try {
            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, android ? "android" : context.getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, NOTIFICATION_CHANNEL_ID_WECHAT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Example. Assets/wechat_intent_dump
    @Override
    public boolean handleIncomingIntent(String targetPackage, Intent intent) {
        if (!isEnabled()) {
            XposedLog.verbose("WeChatPushNotificationHandler not enabled");
            return false;
        }
        if (XposedLog.isVerboseLoggable()) {
            XposedLog.verbose("handleIncomingIntent:" + intent);
        }
        if (!WECHAT_PKG_NAME.equals(targetPackage)) {
            return false;
        }

        if (isDuplicateStrongPush(intent)) {
            XposedLog.verbose("WeChatPushNotificationHandler drop duplicate strong push");
            return true;
        }

        if (isTargetPackageRunningOnTop()) {
            // Reset all when package is in front.
            XposedLog.verbose("WeChatPushNotificationHandler target is running on top");
            clearBadge();
            return true;
        }

        PushMessage pushMessage = resolveWeChatPushIntent(intent);
        if (isNotificationPostByAppEnabled() && PushMessageNotificationService.start(getContext(), pushMessage)) {
            XposedLog.verbose("WeChatPushNotificationHandler posted by app!");
        } else {
            postNotification(pushMessage);
        }


        return false;
    }

    @Override
    public String getTargetPackageName() {
        return WECHAT_PKG_NAME;
    }

    private PushMessage resolveWeChatPushIntent(Intent intent) {

        try {

            // If this is a test.
            boolean isTestMessage = intent.hasExtra(KEY_MOCK_MESSAGE);
            if (isTestMessage) {
                return createAlertMessage("X-APM", intent.getStringExtra(KEY_MOCK_MESSAGE));
            }

            String from = intent.getStringExtra(WECHAT_INTENT_KEY_FROM);

            if (from == null) {
                return createDefaultPushMessage();
            }

            // Increase message count.
            updateBadge(from);

            if (!isShowContentEnabled()) {
                return createSecretPushMessage(from);
            }

            String alert = intent.getStringExtra(WECHAT_INTENT_KEY_ALERT);

            if (alert == null) {
                return createSecretPushMessage(from);
            }

            return createAlertMessage(from, alert);
        } catch (Throwable e) {
            XposedLog.wtf("Fail resolveWeChatPushIntent, use default: " + Log.getStackTraceString(e));
            return createDefaultPushMessage();
        }
    }

    private boolean isDuplicateStrongPush(Intent intent) {
        if (intent == null || intent.hasExtra(KEY_MOCK_MESSAGE)) {
            return false;
        }

        String fingerprint = createStrongPushFingerprint(intent);
        return fingerprint != null && recentStrongPushCache.isDuplicate(fingerprint);
    }

    private String createStrongPushFingerprint(Intent intent) {
        String seq = getExtraValue(intent, WECHAT_INTENT_KEY_SEQ);
        if (seq != null) {
            return "wechat-seq:"
                    + valueOrEmpty(getExtraValue(intent, WECHAT_INTENT_KEY_FROM))
                    + ":" + seq
                    + ":" + valueOrEmpty(getExtraValue(intent, WECHAT_INTENT_KEY_MSG_TYPE));
        }

        String messageId = getExtraValue(intent, GCM_INTENT_KEY_MESSAGE_ID);
        if (messageId != null) {
            return "gcm:" + messageId;
        }

        return null;
    }

    private static String getExtraValue(Intent intent, String key) {
        if (intent == null || intent.getExtras() == null || !intent.getExtras().containsKey(key)) {
            return null;
        }
        Object value = intent.getExtras().get(key);
        if (value == null) {
            return null;
        }
        String valueString = String.valueOf(value);
        return valueString.length() == 0 ? null : valueString;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private PushMessage createAlertMessage(String from, String alert) {
        return PushMessage.builder()
                .channelId(NOTIFICATION_CHANNEL_ID_WECHAT)
                .channelName(NOTIFICATION_CHANNEL_NAME_WECHAT)
                .smallIconResName("ic_wechat_2_fill")
                .largeIconResName("ic_stat_large_wechat")
                .title(String.format("%s条消息", getBadgeFrom(from)))
                .message(alert)
                // Diff sender with diff id.
                .from(MessageIdWrapper.id(from))
                .targetPackageName(getTargetPackageName())
                .build();
    }

    // No content.
    private PushMessage createSecretPushMessage(String from) {
        return PushMessage.builder()
                .channelId(NOTIFICATION_CHANNEL_ID_WECHAT)
                .channelName(NOTIFICATION_CHANNEL_NAME_WECHAT)
                .smallIconResName("ic_wechat_2_fill")
                .largeIconResName("ic_stat_large_wechat")
                .title("微信")
                .message(String.format("%s个联系人发来%s条消息", getFromCount(), getAllBadge()))
                .from(MessageIdWrapper.id(WECHAT_PKG_NAME)) // Do not split for diff sender...
                .targetPackageName(getTargetPackageName())
                .build();
    }


    // Some err occurred, we post this message.
    private PushMessage createDefaultPushMessage() {
        return PushMessage.builder()
                .channelId(NOTIFICATION_CHANNEL_ID_WECHAT)
                .channelName(NOTIFICATION_CHANNEL_NAME_WECHAT)
                .smallIconResName("ic_wechat_2_fill")
                .largeIconResName("ic_stat_large_wechat")
                .message("你收到了一条新消息")
                .title("微信")
                .from(MessageIdWrapper.id(WECHAT_PKG_NAME)) // Do not split for diff sender...
                .targetPackageName(getTargetPackageName())
                .build();
    }

    static class MessageIdWrapper {
        static final Map<String, Integer> idMap = new HashMap<>();

        static int id(String messageIdString) {
            Integer cache = idMap.get(messageIdString);
            if (cache != null) return cache;
            int idNew = UniqueIdFactory.getNextId();
            idMap.put(messageIdString, idNew);
            return idNew;
        }
    }

    private static class RecentPushCache {
        private final long ttlMillis;
        private final int maxSize;
        private final LinkedHashMap<String, Long> fingerprints = new LinkedHashMap<>();

        RecentPushCache(long ttlMillis, int maxSize) {
            this.ttlMillis = ttlMillis;
            this.maxSize = maxSize;
        }

        synchronized boolean isDuplicate(String fingerprint) {
            long now = System.currentTimeMillis();
            trimExpired(now);

            Long lastSeen = fingerprints.get(fingerprint);
            if (lastSeen != null) {
                fingerprints.put(fingerprint, now);
                return true;
            }

            fingerprints.put(fingerprint, now);
            trimOverflow();
            return false;
        }

        private void trimExpired(long now) {
            Iterator<Map.Entry<String, Long>> iterator = fingerprints.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Long> entry = iterator.next();
                long lastSeen = entry.getValue();
                if (now < lastSeen || now - lastSeen > ttlMillis) {
                    iterator.remove();
                }
            }
        }

        private void trimOverflow() {
            Iterator<Map.Entry<String, Long>> iterator = fingerprints.entrySet().iterator();
            while (fingerprints.size() > maxSize && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }
}
