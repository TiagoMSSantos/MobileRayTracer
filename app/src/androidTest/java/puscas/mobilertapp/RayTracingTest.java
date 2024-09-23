package puscas.mobilertapp;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Build;

import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.matcher.IntentMatchers;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import puscas.mobilertapp.constants.Accelerator;
import puscas.mobilertapp.constants.Scene;
import puscas.mobilertapp.constants.Shader;

/**
 * The test suite for the Ray Tracing engine used in {@link MainActivity}.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class RayTracingTest extends AbstractTest {

    /**
     * Logger for this class.
     */
    private static final Logger logger = Logger.getLogger(RayTracingTest.class.getSimpleName());

    /**
     * A setup method which is called first.
     */
    @BeforeClass
    public static void setUpAll() {
        final String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        logger.info(methodName);

        logger.info("---------------------------------------------------");
        final String messageDevice = "Device: " + Build.DEVICE;
        logger.info(messageDevice);
        final String messageUser = "User: " + Build.USER;
        logger.info(messageUser);
        final String messageType = "Type: " + Build.TYPE;
        logger.info(messageType);
        final String messageTags = "Tags: " + Build.TAGS;
        logger.info(messageTags);
        final String messageHost = "Host: " + Build.HOST;
        logger.info(messageHost);
        final String messageFingerPrint = "Fingerprint: " + Build.FINGERPRINT;
        logger.info(messageFingerPrint);
        final String messageDisplay = "Display: " + Build.DISPLAY;
        logger.info(messageDisplay);
        final String messageBrand = "Brand: " + Build.BRAND;
        logger.info(messageBrand);
        final String messageModel = "Model: " + Build.MODEL;
        logger.info(messageModel);
        final String messageProduct = "Product: " + Build.PRODUCT;
        logger.info(messageProduct);
        logger.info("---------------------------------------------------");
    }

    /**
     * A tear down method which is called last.
     */
    @AfterClass
    public static void tearDownAll() {
        final String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
        logger.info(methodName);
    }

    /**
     * Tests render a scene from an OBJ file that doesn't exist.
     *
     * @throws TimeoutException If it couldn't render the whole scene in time.
     */
    @Test
    public void testRenderInvalidScene() throws TimeoutException {
        // Mock the reply as the external file manager application, to select an OBJ file that doesn't exist.
        mockFileManagerReply(false,
            "/path/to/OBJ/file/that/doesn't/exist.obj"
        );

        assertRenderScene(Scene.OBJ, Shader.WHITTED, Accelerator.NAIVE, 1, 1, true, true);
    }

    /**
     * Tests not selecting a file when choosing an OBJ scene in the file manager.
     *
     * @throws TimeoutException If there is a timeout while waiting for the engine to become idle.
     */
    @Test
    public void testNotSelectingScene() throws TimeoutException {
        // Mock the reply as the external file manager application, to not select anything.
        final Intent resultData = MainActivity.createIntentToLoadFiles(InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName());
        final Instrumentation.ActivityResult result = new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, resultData);
        Intents.intending(IntentMatchers.filterEquals(resultData)).respondWith(result);

        assertRenderScene(Scene.OBJ, Shader.WHITTED, Accelerator.NAIVE, 1, 1, true, true);
        Intents.intended(IntentMatchers.hasAction(resultData.getAction()));
    }

    /**
     * Tests rendering an OBJ scene from an OBJ file which the path was loaded with an external file
     * manager application. The OBJ is in an internal storage.
     *
     * @throws TimeoutException If it couldn't render the whole scene in time.
     *
     * @implNote E.g. of an URL to file:<br>
     * file:///file/data/local/tmp/MobileRT/WavefrontOBJs/teapot/teapot.obj<br>
     */
    @Test
    public void testRenderSceneFromInternalStorageOBJ() throws TimeoutException {
        mockFileManagerReply(false,
            ConstantsAndroidTests.CORNELL_BOX_WATER_OBJ,
            ConstantsAndroidTests.CORNELL_BOX_WATER_MTL,
            ConstantsAndroidTests.CORNELL_BOX_WATER_CAM
        );

        assertRenderScene(Scene.OBJ, Shader.WHITTED, Accelerator.BVH, 1, 1, false, false);
    }

    /**
     * Tests rendering an OBJ scene from an OBJ file which the path was loaded with an external file
     * manager application. The OBJ is in an external SD card and the scene contains texture(s) in
     * order to also validate that they are properly read.
     *
     * @throws TimeoutException If it couldn't render the whole scene in time.
     *
     * @implNote E.g. of URLs to file:<br>
     * content://com.asus.filemanager.OpenFileProvider/file/storage/1CE6-261B/MobileRT/WavefrontOBJs/CornellBox/CornellBox-Water.obj<br>
     * content://com.asus.filemanager.OpenFileProvider/file/mnt/sdcard/MobileRT/WavefrontOBJs/CornellBox/CornellBox-Water.obj<br>
     */
    @Test
    public void testRenderSceneFromSDCardOBJ() throws TimeoutException {
        mockFileManagerReply(true,
            "/MobileRT/WavefrontOBJs/teapot/teapot.obj",
            "/MobileRT/WavefrontOBJs/teapot/teapot.mtl",
            "/MobileRT/WavefrontOBJs/teapot/teapot.cam",
            "/MobileRT/WavefrontOBJs/teapot/default.png"
        );

        assertRenderScene(Scene.OBJ, Shader.WHITTED, Accelerator.BVH, 1, 1, false, false);
    }

    /**
     * Tests rendering a scene without any {@link Accelerator}.
     * It shouldn't render anything and be just a black image.
     *
     * @throws TimeoutException If it couldn't render the whole scene in time.
     */
    @Test
    public void testRenderSceneWithoutAccelerator() throws TimeoutException {
        assertRenderScene(Scene.CORNELL, Shader.WHITTED, Accelerator.NONE, 1, 1, false, true);
    }

}
