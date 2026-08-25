import com.xq.jvmtestkit.junit.Xq;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class CleanConsumerTest {
    @Test
    void resolvesPublishedKitApi() {
        assertNotNull(Xq.class);
    }
}
