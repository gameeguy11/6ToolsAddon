package gamerguy11.sixtoolsaddon.efly.pathing;

import gamerguy11.sixtoolsaddon.efly.pathing.EflyGoto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DebugLog {
    private static final Logger LOG = LoggerFactory.getLogger("EflyGoto");

    void info(String msg, Object... args) {
        EflyGoto m = EflyGoto.INSTANCE;
        if (m != null && m.debugLog.get()) LOG.info(msg, args);
    }

    void warn(String msg, Object... args) {
        LOG.warn(msg, args);
    }

    void error(String msg, Object... args) {
        LOG.error(msg, args);
    }
}
