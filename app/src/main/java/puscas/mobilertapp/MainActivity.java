package puscas.mobilertapp;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import java8.util.Optional;
import java8.util.stream.IntStreams;
import java8.util.stream.StreamSupport;
import puscas.mobilertapp.exceptions.FailureException;
import puscas.mobilertapp.utils.Accelerator;
import puscas.mobilertapp.utils.ConstantsMethods;
import puscas.mobilertapp.utils.ConstantsRenderer;
import puscas.mobilertapp.utils.ConstantsToast;
import puscas.mobilertapp.utils.ConstantsUI;
import puscas.mobilertapp.utils.Scene;
import puscas.mobilertapp.utils.Shader;
import puscas.mobilertapp.utils.State;
import puscas.mobilertapp.utils.Utils;

import static puscas.mobilertapp.utils.ConstantsMethods.FINISHED;

/**
 * The main {@link Activity} for the Android User Interface.
 */
public final class MainActivity extends Activity {

    /**
     * The {@link Logger} for this class.
     */
    private static final Logger LOGGER = Logger.getLogger(MainActivity.class.getName());

    /**
     * The global counter of how many times the button was clicked.
     */
    private static long clickCounter = 0L;

    /**
     * The latest version of Android API which needs the old method of getting
     * the number of CPU cores.
     */
    private static final int OLD_API_GET_CORES = 17;

    static {
        try {
            System.loadLibrary("MobileRT");
            System.loadLibrary("Components");
            System.loadLibrary("AppMobileRT");
        } catch (final RuntimeException ex) {
            throw new FailureException(ex);
        } catch (final UnsatisfiedLinkError ex) {
            LOGGER.severe("MainActivity exception: " + ex.getClass().getName());
            LOGGER.severe("MainActivity exception: " + Strings.nullToEmpty(ex.getMessage()));
        }
    }

    /**
     * The request code for the new {@link Activity} to open an OBJ file.
     */
    private static final int OPEN_FILE_REQUEST_CODE = 1;

    /**
     * The custom {@link GLSurfaceView} for displaying OpenGL rendering.
     */
    private DrawView drawView = null;

    /**
     * The {@link NumberPicker} to select the scene to render.
     */
    private NumberPicker pickerScene = null;

    /**
     * The {@link NumberPicker} to select the shader.
     */
    private NumberPicker pickerShader = null;

    /**
     * The {@link NumberPicker} to select the number of threads.
     */
    private NumberPicker pickerThreads = null;

    /**
     * The {@link NumberPicker} to select the accelerator.
     */
    private NumberPicker pickerAccelerator = null;

    /**
     * The {@link NumberPicker} to select the number of samples per pixel.
     */
    private NumberPicker pickerSamplesPixel = null;

    /**
     * The {@link NumberPicker} to select the number of samples per light.
     */
    private NumberPicker pickerSamplesLight = null;

    /**
     * The {@link NumberPicker} to select the desired resolution for the
     * rendered image.
     */
    private NumberPicker pickerResolutions = null;

    /**
     * The {@link CheckBox} to select whether should render a preview of the
     * scene (rasterize) or not.
     */
    private CheckBox checkBoxRasterize = null;

    /**
     * The path to a directory containing the OBJ and MTL files of a scene.
     */
    private String sceneFilePath = null;

    /**
     * Auxiliary method to readjust the width and height of the image by
     * rounding down the value to a multiple of the number of tiles in the
     * Ray Tracer engine.
     *
     * @param size The value to be rounded down to a multiple of the number of
     *             tiles in the Ray Tracer engine.
     * @return The highest value that is smaller than the size passed by
     *         parameter and is a multiple of the number of tiles.
     */
    private native int rtResize(int size);

    /**
     * Helper method that gets the number of CPU cores in the Android device for
     * devices with the SDK API version < {@link #OLD_API_GET_CORES}.
     *
     * @return The number of CPU cores.
     */
    private int getNumCoresOldAndroid() {
        final String cpuInfoPath = readTextAsset("Utils" + ConstantsUI.FILE_SEPARATOR + "cpuInfoDeviceSystemPath.txt");
        final File cpuTopologyPath = new File(cpuInfoPath.trim());
        final File[] files = cpuTopologyPath.listFiles(pathname -> Pattern.matches("cpu[0-9]+", pathname.getName()));
        return Optional.ofNullable(files).map(filesInPath -> filesInPath.length).get();
    }

    /**
     * Helper method that checks if the system is a 64 device or not.
     *
     * @return Whether the system is 64 bit.
     */
    private boolean is64BitDevice() {
        final String cpuInfoPath = readTextAsset("Utils" + ConstantsUI.FILE_SEPARATOR + "cpuInfoPath.txt");
        try (InputStream inputStream = new FileInputStream(cpuInfoPath.trim())) {
            final String text = Utils.readTextFromInputStream(inputStream);
            if (text.matches("64.*bit")) {
                return true;
            }
        } catch (final IOException ex) {
            throw new FailureException(ex);
        }
        return false;
    }

    /**
     * Helper method which gets the number of CPU cores.
     *
     * @return The number of CPU cores.
     */
    private int getNumOfCores() {
        final int cores = (Build.VERSION.SDK_INT < OLD_API_GET_CORES)
            ? getNumCoresOldAndroid()
            : Runtime.getRuntime().availableProcessors();

        final String message = String.format(Locale.US, "Number of cores: %d", cores);
        LOGGER.info(message);
        return cores;
    }

    /**
     * Helper method which starts or stops the rendering process.
     *
     * @param scenePath The path to a directory containing the OBJ and MTL files
     *                  of a scene to render.
     */
    private void startRender(@Nonnull final String scenePath) {
        LOGGER.info(ConstantsMethods.START_RENDER);

        final int scene = this.pickerScene.getValue();
        final int shader = this.pickerShader.getValue();
        final int accelerator = this.pickerAccelerator.getValue();
        final int samplesPixel = Integer.parseInt(this.pickerSamplesPixel.getDisplayedValues()
                [this.pickerSamplesPixel.getValue() - 1]);
        final int samplesLight = Integer.parseInt(this.pickerSamplesLight.getDisplayedValues()
                [this.pickerSamplesLight.getValue() - 1]);
        final String strResolution = this.pickerResolutions.getDisplayedValues()[this.pickerResolutions.getValue() - 1];
        final int width = Integer.parseInt(strResolution.substring(0, strResolution.indexOf('x')));
        final int height = Integer.parseInt(strResolution.substring(strResolution.indexOf('x') + 1));
        final String objFilePath = scenePath + ".obj";
        final String mtlFilePath = scenePath + ".mtl";
        final String camFilePath = scenePath + ".cam";

        final int threads = this.pickerThreads.getValue();
        final boolean rasterize = this.checkBoxRasterize.isChecked();

        final Config config = new Config.Builder()
            .withScene(scene)
            .withShader(shader)
            .withAccelerator(accelerator)
            .withConfigSamples(
                new ConfigSamples.Builder()
                    .withSamplesPixel(samplesPixel)
                    .withSamplesLight(samplesLight)
                    .build()
            )
            .withWidth(width)
            .withHeight(height)
            .withOBJ(objFilePath)
            .withMAT(mtlFilePath)
            .withCAM(camFilePath)
            .build();

        this.drawView.renderScene(config, threads, rasterize);

        LOGGER.info(ConstantsMethods.START_RENDER + FINISHED);
    }

    /**
     * Helper method which calls a new {@link Activity} with a file manager to
     * select the OBJ file for the Ray Tracer engine to load.
     */
    private void callFileManager() {
        LOGGER.info("callFileManager");

        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*" + ConstantsUI.FILE_SEPARATOR + "*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            final Intent intentChooseFile = Intent.createChooser(intent, "Select an OBJ file to load.");
            startActivityForResult(intentChooseFile, OPEN_FILE_REQUEST_CODE);
        } catch (final android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, ConstantsToast.PLEASE_INSTALL_FILE_MANAGER, Toast.LENGTH_LONG).show();
        }

        LOGGER.info("callFileManager" + FINISHED);
    }

    /**
     * Helper method which asks the user for permission to read the external SD
     * card if it doesn't have yet.
     */
    private void checksStoragePermission() {
        final int permissionStorageCode = 1;
        final int permissionCheckRead = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        );
        if (permissionCheckRead != PackageManager.PERMISSION_GRANTED) {
            final String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE
            };
            ActivityCompat.requestPermissions(this, permissions, permissionStorageCode);
        }
    }

    /**
     * Helper method which reads a text based asset file.
     *
     * @param filePath The path to the file (relative to the asset directory).
     * @return A {@link String} containing the contents of the asset file.
     */
    @Nonnull
    private String readTextAsset(final String filePath) {
        final AssetManager assetManager = getAssets();
        final String text;
        try (InputStream inputStream = assetManager.open(filePath)) {
            text = Utils.readTextFromInputStream(inputStream);
        } catch (final IOException ex) {
            throw new FailureException(ex);
        }
        return text;
    }

    /**
     * Helper method which checks if the Android device has support for
     * OpenGL ES 2.0.
     *
     * @return {@code True} if the device has support for OpenGL ES 2.0 or
     *         {@code False} otherwise.
     */
    private static boolean checkGL20Support() {
        final EGL10 egl = (EGL10) EGLContext.getEGL();
        final EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        final int[] version = new int[2];
        egl.eglInitialize(display, version);

        final int[] configAttribs = {
            EGL10.EGL_RED_SIZE, 4,
            EGL10.EGL_GREEN_SIZE, 4,
            EGL10.EGL_BLUE_SIZE, 4,
            EGL10.EGL_RENDERABLE_TYPE, 4,
            EGL10.EGL_NONE
        };

        final EGLConfig[] configs = new EGLConfig[10];
        final int[] numConfig = new int[1];
        egl.eglChooseConfig(display, configAttribs, configs, 10, numConfig);
        egl.eglTerminate(display);
        return numConfig[0] > 0;
    }

    /**
     * Starts the rendering process when the user clicks the render
     * {@link Button}.
     *
     * @param view The view of the {@link Activity}.
     */
    public void startRender(@Nonnull final View view) {
        final String message = String.format(Locale.US, "%s: %s", ConstantsMethods.START_RENDER, view.toString());
        LOGGER.info(message);

        this.sceneFilePath = "";
        final Scene scene = Scene.values()[this.pickerScene.getValue()];
        final MainRenderer renderer = this.drawView.getRenderer();
        final State state = renderer.getState();

        final String message2 = String.format(Locale.US, "%s: %s", ConstantsMethods.START_RENDER, state.toString());
        LOGGER.info(message2);

        ++clickCounter;
        final String counterMessage = String.format(Locale.US,
            "BUTTON CLICKED: %d (%s)", clickCounter, state.toString());
        LOGGER.info(counterMessage);

        if (state == State.BUSY) {
            this.drawView.stopDrawing();
        } else {
            switch (scene) {
                case OBJ:
                    callFileManager();
                    break;

                case TEST:
                    final String scenePath = "CornellBox" + ConstantsUI.FILE_SEPARATOR + "CornellBox-Water";
                    final String sdCardPath = getSDCardPath();
                    this.sceneFilePath = sdCardPath + ConstantsUI.FILE_SEPARATOR + "WavefrontOBJs" + ConstantsUI.FILE_SEPARATOR + scenePath;
                    startRender(this.sceneFilePath);
                    break;

                default:
                    startRender(this.sceneFilePath);
            }
        }
        LOGGER.info(ConstantsMethods.START_RENDER + FINISHED);
    }

    /**
     * Gets the path to the SD card.
     * <br>
     * This method should get the correct path independently of the
     * device / emulator used.
     *
     * @return The path to the SD card.
     * @implNote This method still uses the deprecated method
     * {@link Environment#getExternalStorageDirectory()} in order to be
     * compatible with Android 4.1.
     */
    @Nonnull
    private String getSDCardPath() {
        LOGGER.info("Getting SD card path");
        final File[] dirs = ContextCompat.getExternalFilesDirs(getApplicationContext(), null);
        final String sdCardPath = Optional.ofNullable(dirs.length > 1? dirs[1] : dirs[0])
            .map(File::getAbsolutePath)
            .orElse(Environment.getExternalStorageDirectory().getAbsolutePath());
        return cleanSDCardPath(sdCardPath);
    }

    /**
     * Helper method that cleans the path to the external SD Card.
     * This is useful for some devices since the {@link #getSDCardPath()} method
     * might get the SD Card path with some extra paths at the end.
     *
     * @param sdCardPath The path to the external SD Card to clean.
     * @return A cleaned SD Card path.
     */
    @Nonnull
    private static String cleanSDCardPath(final String sdCardPath) {
        final int removeIndex = sdCardPath.indexOf("Android");
        if (removeIndex >= 1) {
            return sdCardPath.substring(0, removeIndex - 1);
        }
        return sdCardPath;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Optional<Bundle> instance = Optional.ofNullable(savedInstanceState);
        final int defaultPickerScene = instance.map(x -> x.getInt(ConstantsUI.PICKER_SCENE)).orElse(0);
        final int defaultPickerShader = instance.map(x -> x.getInt(ConstantsUI.PICKER_SHADER)).orElse(0);
        final int defaultPickerThreads = instance.map(x -> x.getInt(ConstantsUI.PICKER_THREADS)).orElse(1);
        final int defaultPickerAccelerator = instance.map(x -> x.getInt(ConstantsUI.PICKER_ACCELERATOR)).orElse(1);
        final int defaultPickerSamplesPixel = instance.map(x -> x.getInt(ConstantsUI.PICKER_SAMPLES_PIXEL)).orElse(1);
        final int defaultPickerSamplesLight = instance.map(x -> x.getInt(ConstantsUI.PICKER_SAMPLES_LIGHT)).orElse(1);
        final int defaultPickerSizes = instance.map(x -> x.getInt(ConstantsUI.PICKER_SIZES)).orElse(4);
        final boolean defaultCheckBoxRasterize = instance.map(x -> x.getBoolean(ConstantsUI.CHECK_BOX_RASTERIZE)).orElse(true);

        try {
            setContentView(R.layout.activity_main);
        } catch (final RuntimeException ex) {
            throw new FailureException(ex);
        }

        initializeViews();
        final TextView textView = findViewById(R.id.timeText);
        final Button renderButton = findViewById(R.id.renderButton);
        final ActivityManager assetManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        Preconditions.checkNotNull(textView);
        Preconditions.checkNotNull(renderButton);
        Preconditions.checkNotNull(assetManager);

        final ConfigurationInfo configurationInfo = assetManager.getDeviceConfigurationInfo();
        final boolean supportES2 = (configurationInfo.reqGlEsVersion >= ConstantsRenderer.REQUIRED_OPENGL_VERSION);

        if (!supportES2 || !MainActivity.checkGL20Support()) {
            final String msg = "Your device doesn't support ES 2. (" + configurationInfo.reqGlEsVersion + ')';
            LOGGER.severe(msg);
            throw new FailureException(msg);
        }

        this.drawView.setVisibility(View.INVISIBLE);
        this.drawView.setEGLContextClientVersion(2);
        this.drawView.setEGLConfigChooser(8, 8, 8, 8, 3 * 8, 0);

        final MainRenderer renderer = this.drawView.getRenderer();
        final String vertexShader = readTextAsset(ConstantsUI.PATH_SHADERS + ConstantsUI.FILE_SEPARATOR + "VertexShader.glsl");
        final String fragmentShader = readTextAsset(ConstantsUI.PATH_SHADERS + ConstantsUI.FILE_SEPARATOR + "FragmentShader.glsl");
        final String vertexShaderRaster = readTextAsset(ConstantsUI.PATH_SHADERS + ConstantsUI.FILE_SEPARATOR + "VertexShaderRaster.glsl");
        final String fragmentShaderRaster = readTextAsset(ConstantsUI.PATH_SHADERS + ConstantsUI.FILE_SEPARATOR + "FragmentShaderRaster.glsl");
        renderer.setBitmap(1, 1, 1, 1, false);
        renderer.setVertexShaderCode(vertexShader);
        renderer.setFragmentShaderCode(fragmentShader);
        renderer.setVertexShaderCodeRaster(vertexShaderRaster);
        renderer.setFragmentShaderCodeRaster(fragmentShaderRaster);

        final ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        this.drawView.setViewAndActivityManager(textView, activityManager);
        this.drawView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        this.drawView.setVisibility(View.VISIBLE);

        renderButton.setOnLongClickListener((final View view) -> {
            recreate();
            return false;
        });
        renderer.setButtonRender(renderButton);

        this.drawView.setPreserveEGLContextOnPause(true);

        initializePickerScene(defaultPickerScene);
        initializePickerShader(defaultPickerShader);
        initializePickerSamplesPixel(defaultPickerSamplesPixel);
        initializePickerSamplesLight(defaultPickerSamplesLight);
        initializePickerAccelerator(defaultPickerAccelerator);
        initializePickerThreads(defaultPickerThreads);
        initializeCheckBoxRasterize(defaultCheckBoxRasterize);

        final int maxSizes = 9;
        initializePickerResolutions(defaultPickerSizes, maxSizes);

        final ViewTreeObserver vto = this.drawView.getViewTreeObserver();
        // We can only set the resolutions after the views are shown.
        vto.addOnGlobalLayoutListener(() -> {
            final double widthView = (double) this.drawView.getWidth();
            final double heightView = (double) this.drawView.getHeight();

            final String[] resolutions = IntStreams.rangeClosed(2, maxSizes)
                .mapToDouble(value -> (double) value)
                .map(value -> (value + 1.0) * 0.1)
                .map(value -> value * value)
                .mapToObj(value -> {
                    final int width = rtResize((int) Math.round(widthView * value));
                    final int height = rtResize((int) Math.round(heightView * value));
                    return String.valueOf(width) + 'x' + height;
                })
                .toArray(String[]::new);

            this.pickerResolutions.setDisplayedValues(resolutions);
        });
        checksStoragePermission();
    }

    /**
     * Initializes the {@link #checkBoxRasterize} field.
     *
     * @param checkBoxRasterize The default value to put in the
     *                          {@link #checkBoxRasterize} field.
     */
    private void initializeCheckBoxRasterize(final boolean checkBoxRasterize) {
        this.checkBoxRasterize.setChecked(checkBoxRasterize);
        final int scale = Math.round(getResources().getDisplayMetrics().density);
        this.checkBoxRasterize.setPadding(
            this.checkBoxRasterize.getPaddingLeft() - (5 * scale),
            this.checkBoxRasterize.getPaddingTop(),
            this.checkBoxRasterize.getPaddingRight(),
            this.checkBoxRasterize.getPaddingBottom()
        );
    }

    /**
     * Initializes the {@link #pickerResolutions} field.
     *
     * @param pickerSizes The default value to put in the
     *                    {@link #pickerResolutions} field.
     * @param maxSizes    The maximum size value for the {@link NumberPicker}.
     */
    private void initializePickerResolutions(final int pickerSizes, final int maxSizes) {
        this.pickerResolutions.setMinValue(1);
        this.pickerResolutions.setMaxValue(maxSizes - 1);
        this.pickerResolutions.setWrapSelectorWheel(true);
        this.pickerResolutions.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        this.pickerResolutions.setValue(pickerSizes);
    }

    /**
     * Initializes the {@link #pickerThreads} field.
     *
     * @param pickerThreads The default value to put in the
     *                      {@link #pickerThreads} field.
     */
    private void initializePickerThreads(final int pickerThreads) {
        final int maxCores = getNumOfCores();
        this.pickerThreads.setMinValue(1);
        this.pickerThreads.setMaxValue(maxCores);
        this.pickerThreads.setWrapSelectorWheel(true);
        this.pickerThreads.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        this.pickerThreads.setValue(pickerThreads);
    }

    /**
     * Initializes the {@link #pickerAccelerator} field.
     *
     * @param pickerAccelerator The default value to put in the
     *                          {@link #pickerAccelerator} field.
     */
    private void initializePickerAccelerator(final int pickerAccelerator) {
        final String[] accelerators = Accelerator.getNames();
        this.pickerAccelerator.setMinValue(0);
        this.pickerAccelerator.setMaxValue(accelerators.length - 1);
        this.pickerAccelerator.setWrapSelectorWheel(true);
        this.pickerAccelerator.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        this.pickerAccelerator.setValue(pickerAccelerator);
        this.pickerAccelerator.setDisplayedValues(accelerators);
    }

    /**
     * Initializes the {@link #pickerSamplesLight} field.
     *
     * @param pickerSamplesLight The default value to put in the
     *                           {@link #pickerSamplesLight} field.
     */
    private void initializePickerSamplesLight(final int pickerSamplesLight) {
        final int maxSamplesLight = 100;
        final String[] samplesLight = IntStreams.range(0, maxSamplesLight)
            .map(value -> value + 1)
            .mapToObj(String::valueOf)
            .toArray(String[]::new);
        this.pickerSamplesLight.setMinValue(1);
        this.pickerSamplesLight.setMaxValue(maxSamplesLight);
        this.pickerSamplesLight.setWrapSelectorWheel(true);
        this.pickerSamplesLight.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        this.pickerSamplesLight.setValue(pickerSamplesLight);
        this.pickerSamplesLight.setDisplayedValues(samplesLight);
    }

    /**
     * Initializes the {@link #pickerSamplesPixel} field.
     *
     * @param pickerSamplesPixel The default value to put in the
     *                           {@link #pickerSamplesPixel} field.
     */
    private void initializePickerSamplesPixel(final int pickerSamplesPixel) {
        final int maxSamplesPixel = 99;
        final String[] samplesPixel = IntStreams.range(0, maxSamplesPixel)
            .map(value -> (value + 1) * (value + 1))
            .mapToObj(String::valueOf)
            .toArray(String[]::new);
        this.pickerSamplesPixel.setMinValue(1);
        this.pickerSamplesPixel.setMaxValue(maxSamplesPixel);
        this.pickerSamplesPixel.setWrapSelectorWheel(true);
        this.pickerSamplesPixel.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        this.pickerSamplesPixel.setValue(pickerSamplesPixel);
        this.pickerSamplesPixel.setDisplayedValues(samplesPixel);
    }

    /**
     * Initializes the {@link #pickerShader} field.
     *
     * @param pickerShader The default value to put in the
     *                     {@link #pickerShader} field.
     */
    private void initializePickerShader(final int pickerShader) {
        final String[] shaders = Shader.getNames();
        this.pickerShader.setMinValue(0);
        this.pickerShader.setMaxValue(shaders.length - 1);
        this.pickerShader.setWrapSelectorWheel(true);
        this.pickerShader.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        this.pickerShader.setValue(pickerShader);
        this.pickerShader.setDisplayedValues(shaders);
    }

    /**
     * Initializes the {@link #pickerScene} field.
     *
     * @param pickerScene The default value to put in the {@link #pickerScene}
     *                    field.
     */
    private void initializePickerScene(final int pickerScene) {
        final String[] scenes = Scene.getNames();
        this.pickerScene.setMinValue(0);
        this.pickerScene.setMaxValue(scenes.length - 1);
        this.pickerScene.setWrapSelectorWheel(true);
        this.pickerScene.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        this.pickerScene.setValue(pickerScene);
        this.pickerScene.setDisplayedValues(scenes);
    }

    /**
     * Helper method that initializes the fields that are {@link View}s.
     */
    private void initializeViews() {
        this.drawView = findViewById(R.id.drawLayout);
        this.pickerScene = findViewById(R.id.pickerScene);
        this.pickerShader = findViewById(R.id.pickerShader);
        this.pickerSamplesPixel = findViewById(R.id.pickerSamplesPixel);
        this.pickerSamplesLight = findViewById(R.id.pickerSamplesLight);
        this.pickerAccelerator = findViewById(R.id.pickerAccelerator);
        this.pickerThreads = findViewById(R.id.pickerThreads);
        this.pickerResolutions = findViewById(R.id.pickerSize);
        this.checkBoxRasterize = findViewById(R.id.preview);
        validateViews();
    }

    /**
     * Helper method that validates the fields that are {@link View}s.
     */
    private void validateViews() {
        Preconditions.checkNotNull(this.pickerResolutions);
        Preconditions.checkNotNull(this.pickerThreads);
        Preconditions.checkNotNull(this.pickerAccelerator);
        Preconditions.checkNotNull(this.pickerSamplesLight);
        Preconditions.checkNotNull(this.pickerSamplesPixel);
        Preconditions.checkNotNull(this.pickerShader);
        Preconditions.checkNotNull(this.pickerScene);
        Preconditions.checkNotNull(this.drawView);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        if (!Strings.isNullOrEmpty(this.sceneFilePath)) {
            startRender(this.sceneFilePath);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        this.drawView.onResume();
        this.drawView.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LOGGER.info("onPause");

        final boolean interrupted = Thread.interrupted();
        final String message = String.format("onPause: %s", interrupted);
        LOGGER.severe(message);
        this.drawView.setPreserveEGLContextOnPause(true);
        this.drawView.onPause();
        this.drawView.setVisibility(View.INVISIBLE);
        this.sceneFilePath = null;

        LOGGER.info("onPause" + FINISHED);
    }

    @Override
    protected void onRestoreInstanceState(@Nonnull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        LOGGER.info("onRestoreInstanceState");

        final int scene = savedInstanceState.getInt(ConstantsUI.PICKER_SCENE);
        final int shader = savedInstanceState.getInt(ConstantsUI.PICKER_SHADER);
        final int threads = savedInstanceState.getInt(ConstantsUI.PICKER_THREADS);
        final int accelerator = savedInstanceState.getInt(ConstantsUI.PICKER_ACCELERATOR);
        final int samplesPixel = savedInstanceState.getInt(ConstantsUI.PICKER_SAMPLES_PIXEL);
        final int samplesLight = savedInstanceState.getInt(ConstantsUI.PICKER_SAMPLES_LIGHT);
        final int sizes = savedInstanceState.getInt(ConstantsUI.PICKER_SIZES);
        final boolean rasterize = savedInstanceState.getBoolean(ConstantsUI.CHECK_BOX_RASTERIZE);

        this.pickerScene.setValue(scene);
        this.pickerShader.setValue(shader);
        this.pickerThreads.setValue(threads);
        this.pickerAccelerator.setValue(accelerator);
        this.pickerSamplesPixel.setValue(samplesPixel);
        this.pickerSamplesLight.setValue(samplesLight);
        this.pickerResolutions.setValue(sizes);
        this.checkBoxRasterize.setChecked(rasterize);
    }

    @Override
    protected void onSaveInstanceState(@Nonnull final Bundle outState) {
        super.onSaveInstanceState(outState);
        LOGGER.info("onSaveInstanceState");

        final int scene = this.pickerScene.getValue();
        final int shader = this.pickerShader.getValue();
        final int threads = this.pickerThreads.getValue();
        final int accelerator = this.pickerAccelerator.getValue();
        final int samplesPixel = this.pickerSamplesPixel.getValue();
        final int samplesLight = this.pickerSamplesLight.getValue();
        final int sizes = this.pickerResolutions.getValue();
        final boolean rasterize = this.checkBoxRasterize.isChecked();

        outState.putInt(ConstantsUI.PICKER_SCENE, scene);
        outState.putInt(ConstantsUI.PICKER_SHADER, shader);
        outState.putInt(ConstantsUI.PICKER_THREADS, threads);
        outState.putInt(ConstantsUI.PICKER_ACCELERATOR, accelerator);
        outState.putInt(ConstantsUI.PICKER_SAMPLES_PIXEL, samplesPixel);
        outState.putInt(ConstantsUI.PICKER_SAMPLES_LIGHT, samplesLight);
        outState.putInt(ConstantsUI.PICKER_SIZES, sizes);
        outState.putBoolean(ConstantsUI.CHECK_BOX_RASTERIZE, rasterize);

        final MainRenderer renderer = this.drawView.getRenderer();
        renderer.rtFinishRender();
        renderer.freeArrays();
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @Nonnull final String[] permissions,
                                           @Nonnull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        LOGGER.info("onRequestPermissionsResult");
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        LOGGER.info(ConstantsMethods.ON_DETACHED_FROM_WINDOW);

        this.drawView.onDetachedFromWindow();
        LOGGER.info(ConstantsMethods.ON_DETACHED_FROM_WINDOW + FINISHED);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LOGGER.info(ConstantsMethods.ON_DESTROY);

        this.drawView.onDetachedFromWindow();
        this.drawView.setVisibility(View.INVISIBLE);

        LOGGER.info(ConstantsMethods.ON_DESTROY + FINISHED);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && requestCode == OPEN_FILE_REQUEST_CODE) {
            this.sceneFilePath = Optional.ofNullable(data)
                .map(Intent::getData)
                .map(this::getPathFromFile)
                .orElse("");
        }
    }

    /**
     * Gets the path of a file that was loaded with an external file manager.
     * <br/>
     * This method basically translates an {@link Uri} path to a {@link String}
     * but also tries to be compatible with any device / emulator available.
     *
     * @param uri The URI reference for the file.
     * @return The path to the file.
     */
    @Nonnull
    private String getPathFromFile(final Uri uri) {
        final String filePath = StreamSupport.stream(uri.getPathSegments())
            .skip(1L)
            .reduce("", (accumulator, segment) -> accumulator + ConstantsUI.FILE_SEPARATOR + segment)
            .replace(ConstantsUI.FILE_SEPARATOR + "sdcard" + ConstantsUI.FILE_SEPARATOR, ConstantsUI.FILE_SEPARATOR);

        final int removeIndex = filePath.indexOf(ConstantsUI.PATH_SEPARATOR);
        final String startFilePath = removeIndex >= 0 ? filePath.substring(removeIndex) : filePath;
        final String cleanedFilePath = startFilePath.replace(ConstantsUI.PATH_SEPARATOR, ConstantsUI.FILE_SEPARATOR);
        final String filePathWithoutExtension = cleanedFilePath.substring(0, cleanedFilePath.lastIndexOf('.'));

        final String sdCardPath = getSDCardPath();
        return sdCardPath + filePathWithoutExtension;
    }
}
