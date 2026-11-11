package example;

import com.sma.extension.api.BusinessPlugin;
import com.sma.extension.api.PluginCall;
import com.sma.extension.api.PluginResult;

/** Stateless SDK example. No Spring, host beans, file access or side effects. */
public final class EchoPlugin implements BusinessPlugin {
    @Override public PluginResult execute(PluginCall call) {
        if (!"echo".equals(call.entryId())) throw new IllegalArgumentException("Unsupported entry");
        return new PluginResult(call.input());
    }
}
