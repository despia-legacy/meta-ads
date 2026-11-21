package despia.ext.metaads;

import android.os.Bundle;
import com.despia.nativeext.*;
import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import org.json.*;
import java.math.BigDecimal;
import java.util.Currency;

public final class MetaAdsExtension implements NativeExtension {
    // App SDK identifiers, never a server-side Graph API token.
    private static final String APP_ID = "REPLACE_WITH_META_APP_ID";
    private static final String CLIENT_TOKEN = "REPLACE_WITH_META_CLIENT_TOKEN";
    private ExtContext ctx;
    private AppEventsLogger logger;
    private boolean configured, enabled;
    @Override public void onCreate(ExtContext context) { ctx = context; }
    @Override public void handle(ExtCall call, ExtResult.Callback reply) {
        try {
            if (call.route.equals("status")) {
                reply.reply(ExtResult.ok(answer(configured, enabled).put("sdk", "19.0.1"))); return;
            }
            if (call.route.equals("configure")) {
                if (configured) { reply.reply(ExtResult.ok(answer(true, enabled))); return; }
                if (APP_ID.startsWith("REPLACE_") || CLIENT_TOKEN.startsWith("REPLACE_")) {
                    reply.reply(ExtResult.error("not_configured", "Set native app SDK configuration before approval")); return;
                }
                if (FacebookSdk.isInitialized()) {
                    reply.reply(ExtResult.error("already_configured", "Another integration already owns Meta SDK")); return;
                }
                FacebookSdk.setApplicationId(APP_ID);
                FacebookSdk.setClientToken(CLIENT_TOKEN);
                FacebookSdk.setAutoInitEnabled(false);
                FacebookSdk.setAutoLogAppEventsEnabled(false);
                FacebookSdk.setAdvertiserIDCollectionEnabled(false);
                FacebookSdk.sdkInitialize(ctx.app());
                logger = AppEventsLogger.newLogger(ctx.app());
                configured = true;
                reply.reply(ExtResult.ok(answer(true, false))); return;
            }
            if (!configured) { reply.reply(ExtResult.error("not_configured", "Call configure first")); return; }
            if (call.route.equals("consent")) {
                enabled = Boolean.TRUE.equals(call.params.get("enabled"));
                FacebookSdk.setAutoLogAppEventsEnabled(enabled && Boolean.TRUE.equals(call.params.get("automatic_events")));
                FacebookSdk.setAdvertiserIDCollectionEnabled(enabled && Boolean.TRUE.equals(call.params.get("advertiser_id")));
                reply.reply(ExtResult.ok(answer(true, enabled))); return;
            }
            if (!enabled) { reply.reply(ExtResult.error("consent_required", "Enable event collection first")); return; }
            switch (call.route) {
                case "event": {
                    Bundle values = bundle(properties(call.params.get("properties")));
                    String name = (String) call.params.get("name");
                    if (call.params.containsKey("value")) logger.logEvent(name, number(call.params.get("value")), values);
                    else logger.logEvent(name, values);
                    break;
                }
                case "purchase":
                    number(call.params.get("amount"));
                    logger.logPurchase(new BigDecimal((String)call.params.get("amount")), Currency.getInstance(currency(call.params.get("currency"))), bundle(properties(call.params.get("properties")))); break;
                case "flush": logger.flush(); break;
                case "activate": AppEventsLogger.activateApp((android.app.Application)ctx.app()); break;
                case "user":
                    if (call.params.containsKey("id")) AppEventsLogger.setUserID((String)call.params.get("id"));
                    else AppEventsLogger.clearUserID();
                    break;
                default: reply.reply(ExtResult.error("not_registered", call.route)); return;
            }
            reply.reply(ExtResult.ok(new JSONObject().put("accepted", true)));
        } catch (JSONException | IllegalArgumentException ex) {
            reply.reply(ExtResult.error("bad_request", "Expected finite numbers, ISO currency and a flat JSON properties object"));
        } catch (RuntimeException ex) { reply.reply(ExtResult.error("sdk_error", "Meta SDK rejected the operation")); }
    }
    private static Bundle bundle(JSONObject properties) throws JSONException {
        Bundle bundle = new Bundle(); java.util.Iterator<String> keys = properties.keys();
        while (keys.hasNext()) {
            String key = keys.next(); Object value = properties.get(key);
            if (value instanceof String) bundle.putString(key, (String)value);
            else if (value instanceof Boolean) bundle.putInt(key, (Boolean)value ? 1 : 0);
            else bundle.putDouble(key, ((Number)value).doubleValue());
        }
        return bundle;
    }
    private static JSONObject properties(Object raw) throws JSONException {
        JSONObject values = new JSONObject(raw instanceof String ? (String) raw : "{}");
        if (values.length() > 25) throw new JSONException("Too many properties");
        java.util.Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next(); Object value = values.get(key);
            if (key.isEmpty() || key.length() > 40 || !(value instanceof String || value instanceof Number || value instanceof Boolean)) throw new JSONException("Flat properties required");
            if (value instanceof Number && !Double.isFinite(((Number)value).doubleValue())) throw new JSONException("Finite numbers required");
        }
        return values;
    }
    private static double number(Object raw) {
        double value = Double.parseDouble(String.valueOf(raw));
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Finite number required");
        return value;
    }
    private static String currency(Object raw) {
        String value = String.valueOf(raw);
        if (!value.matches("[A-Z]{3}")) throw new IllegalArgumentException("ISO currency required");
        return value;
    }
    private static JSONObject answer(boolean configured, boolean enabled) throws JSONException {
        return new JSONObject().put("configured", configured).put("enabled", enabled);
    }
}
