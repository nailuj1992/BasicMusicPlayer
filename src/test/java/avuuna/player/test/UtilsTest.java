package avuuna.player.test;

import avuuna.player.utils.Utils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class UtilsTest {

    @Test(timeout = 1000)
    public void randomWithExclusionSkipsExcludedValues() {
        for (int i = 0; i < 200; i++) {
            int r = Utils.getRandomWithExclusion(0, 4, 1, 3);
            assertNotEquals(1, r);
            assertNotEquals(3, r);
        }
    }

    @Test(timeout = 1000)
    public void randomWithExclusionReturnsOnlyCandidate() {
        assertEquals(2, Utils.getRandomWithExclusion(0, 2, 0, 1));
    }

    @Test(timeout = 1000, expected = IllegalArgumentException.class)
    public void randomWithExclusionFailsFastWhenEverythingExcluded() {
        // Previously this looped forever.
        Utils.getRandomWithExclusion(0, 0, 0);
    }
}
