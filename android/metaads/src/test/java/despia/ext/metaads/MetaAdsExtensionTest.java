package despia.ext.metaads;
import com.despia.nativeext.*;
import org.junit.Test;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

public class MetaAdsExtensionTest {
    private ExtResult call(MetaAdsExtension extension, String route) {
        final ExtResult[] result = new ExtResult[1];
        extension.handle(new ExtCall("metaads", route, Collections.emptyMap(), null), r -> {
            assertNull("exactly one answer", result[0]); result[0] = r;
        });
        assertNotNull(result[0]); return result[0];
    }
    @Test public void noSdkUseBeforeConfiguration() throws Exception {
        MetaAdsExtension extension = new MetaAdsExtension();
        assertFalse(call(extension, "status").data().getBoolean("configured"));
        for (String route : new String[]{"event", "purchase", "flush", "user", "consent"}) {
            assertEquals("not_configured", call(extension, route).code());
        }
        assertEquals("not_configured", call(extension, "configure").code());
        assertFalse(call(extension, "status").data().getBoolean("configured"));
    }
    private Object invoke(String name, Class<?> type, Object value) throws Exception {
        Method method = MetaAdsExtension.class.getDeclaredMethod(name, type);
        method.setAccessible(true); return method.invoke(null, value);
    }
    private void rejects(String name, String value) throws Exception {
        try { invoke(name, Object.class, value); fail("accepted invalid " + name); }
        catch (InvocationTargetException expected) { assertTrue(expected.getCause() instanceof JSONException || expected.getCause() instanceof IllegalArgumentException); }
    }
    @Test public void finiteNumbersOnly() throws Exception {
        assertEquals(12.5, (Double)invoke("number", Object.class, "12.50"), 0.0);
        for (String value : new String[]{"NaN", "Infinity", "1e999", "invalid"}) rejects("number", value);
    }
    @Test public void flatPropertiesOnly() throws Exception {
        assertEquals(3, ((JSONObject)invoke("properties", Object.class, "{\"text\":\"ok\",\"count\":2,\"flag\":true}")).length());
        for (String value : new String[]{"[]", "null", "{\"nested\":{}}", "{\"array\":[]}", "{\"null\":null}", "{\"\":1}"}) rejects("properties", value);
        JSONObject many = new JSONObject(); for (int i=0;i<26;i++) many.put("p"+i, i);
        rejects("properties", many.toString());
    }
    @Test public void currencyFormat() throws Exception {
        assertEquals("USD", invoke("currency", Object.class, "USD"));
        rejects("currency", "usd"); rejects("currency", "US"); rejects("currency", "123");
    }
}
