package xin.bbtt.mcbot.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DummyPlugin implements Plugin {
    private static final Logger log = LoggerFactory.getLogger(DummyPlugin.class);
    
    public static int loadCount = 0;
    public static int enableCount = 0;
    public static int disableCount = 0;
    public static int unloadCount = 0;

    @Override
    public void onLoad() {
        loadCount++;
        log.info("DummyPlugin loaded");
    }

    @Override
    public void onEnable() {
        enableCount++;
        log.info("DummyPlugin enabled");
    }

    @Override
    public void onDisable() {
        disableCount++;
        log.info("DummyPlugin disabled");
    }

    @Override
    public void onUnload() {
        unloadCount++;
        log.info("DummyPlugin unloaded");
    }
    
    public static void reset() {
        loadCount = 0;
        enableCount = 0;
        disableCount = 0;
        unloadCount = 0;
    }
}
