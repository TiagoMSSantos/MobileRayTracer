package puscas.mobilertapp.utils;

import lombok.extern.java.Log;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The unit tests for the {@link Accelerator} util class.
 */
@Log
public final class AcceleratorTest {

    /**
     * Setup method called before each test.
     */
    @Before
    public void setUp() {
        final String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        log.info(methodName);
    }

    /**
     * Tear down method called after each test.
     */
    @After
    public void tearDown() {
        final String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        log.info(methodName);
    }

    /**
     * Tests that the {@link Accelerator#getNames()} method contains all the expected accelerators.
     */
    @Test
    public void testGetNames() {
        final String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        log.info(methodName);

        Assertions.assertThat(Accelerator.getNames()).containsExactly(
            "None",
            "Naive",
            "RegGrid",
            "BVH"
        );
    }

}
