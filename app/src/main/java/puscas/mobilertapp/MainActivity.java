package puscas.mobilertapp;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import java8.util.Optional;
import java8.util.stream.IntStreams;
import java8.util.stream.StreamSupport;
import kotlin.Pair;
import puscas.mobilertapp.configs.Config;
import puscas.mobilertapp.configs.ConfigResolution;
import puscas.mobilertapp.configs.ConfigSamples;
import puscas.mobilertapp.constants.Accelerator;
import puscas.mobilertapp.constants.ConstantsMethods;
import puscas.mobilertapp.constants.ConstantsToast;
import puscas.mobilertapp.constants.ConstantsUI;
import puscas.mobilertapp.constants.Scene;
import puscas.mobilertapp.constants.Shader;
import puscas.mobilertapp.constants.State;
import puscas.mobilertapp.exceptions.FailureException;
import puscas.mobilertapp.utils.Utils;
import puscas.mobilertapp.utils.UtilsContext;
import puscas.mobilertapp.utils.UtilsGL;
import puscas.mobilertapp.utils.UtilsLogging;

/**
 * The main {@link Activity} for the Android User Interface.
 */
public final class MainActivity extends Activity {

    /*
     ***********************************************************************
     * Private static fields
     ***********************************************************************
     */

    /**
     * Logger for this class.
     */
    private static final Logger logger = Logger.getLogger(MainActivity.class.getSimpleName());

    /**
     * The request code for the new {@link Activity} to open an OBJ file.
     */
    private static final int OPEN_FILE_REQUEST_CODE = 1;

    /**
     * The OpenGL ES version required to run this application.
     */
    private static final int REQUIRED_OPENGL_VERSION = 0x20000;

    /**
     * The current active instance of {@link MainActivity}.
     * <p>
     * This is probably a very bad idea but got not better solution to simplify the showing of
     * custom messages on the UI so the user can understand better when the system crashes.
     *
     * @implNote This is necessary for the {@link MainActivity#showUiMessage(String)} method to
     * have the application {@link Context} and so the method can be static and used anywhere in the
     * codebase.
     */
    @SuppressWarnings("StaticFieldLeak")
    private static Activity currentInstance = null;

    /*
     ***********************************************************************
     * Private instance fields
     ***********************************************************************
     */

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
     * Loads the MobileRT native library.
     */
    private static void loadMobileRT() {
        System.loadLibrary("MobileRT");
        System.loadLibrary("Components");
        System.loadLibrary("AppMobileRT");
    }

    /**
     * Helper method that calls the UI thread to update the UI with a custom message.
     *
     * @param message The message to show on the UI.
     * @implNote Only the UI thread can update the UI with messages by using {@link Toast}.
     */
    public static void showUiMessage(@NonNull final String message) {
        currentInstance.runOnUiThread(() -> {
            logger.info("showUiMessage: " + message);
            Toast.makeText(currentInstance.getApplicationContext(), message, Toast.LENGTH_LONG)
                .show();
        });
    }

    /**
     * Helper method that calls the UI thread to update the render button on the UI.
     *
     * @implNote Only the UI thread can update the state of the render button.
     */
    public static void resetRenderButton() {
        currentInstance.runOnUiThread(() -> {
            logger.info("updateRenderButton");
            final Button renderButton = currentInstance.findViewById(R.id.renderButton);
            renderButton.setText(R.string.render);
        });
    }

    /**
     * Helper method that checks if the supported OpenGL ES version is
     * the version 2 or greater.
     *
     * @param activityManager The {@link ActivityManager} which contains the
     *                        {@link ConfigurationInfo}.
     */
    private static void checksOpenGlVersion(final ActivityManager activityManager) {
        final ConfigurationInfo configurationInfo = activityManager.getDeviceConfigurationInfo();
        final boolean supportES2 = (configurationInfo.reqGlEsVersion >= REQUIRED_OPENGL_VERSION);

        if (!supportES2 || !UtilsGL.checkGL20Support()) {
            final String msg = "Your device doesn't support ES 2. ("
                + configurationInfo.reqGlEsVersion + ')';
            throw new FailureException(msg);
        }
    }

    /**
     * Initializes a {@link NumberPicker}.
     *
     * @param numberPicker The {@link NumberPicker} to initialize.
     * @param defaultValue The default value to put in the {@link NumberPicker}.
     * @param names        The values to be displayed in the {@link NumberPicker}.
     */
    private static void initializePicker(final NumberPicker numberPicker,
                                         final int defaultValue,
                                         final String[] names) {
        try {
            final int minValue = Integer.parseInt(names[0]);
            numberPicker.setMinValue(minValue);
            numberPicker.setMaxValue(names.length);
        } catch (final NumberFormatException ex) {
            UtilsLogging.logThrowable(ex, "MainActivity#initializePicker");
            numberPicker.setMinValue(0);
            numberPicker.setMaxValue(names.length - 1);
        }

        numberPicker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        numberPicker.setValue(defaultValue);
        numberPicker.setDisplayedValues(names);

        // TODO: For Android API < 15, this method crashes, so it's necessary to investigate it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            numberPicker.setWrapSelectorWheel(true);
        }
    }

    /*
     ***********************************************************************
     * Overloaded methods
     ***********************************************************************
     */
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        try {
            loadMobileRT();
        } catch (final Exception ex) {
            UtilsLogging.logThrowable(ex, "MainActivity#onCreate");
            throw ex;
        }

        setCurrentInstance();
        super.onCreate(savedInstanceState);
        logger.info("onCreate start");

        setContentView(R.layout.activity_main);
        initializeViews();

        final TextView textView = findViewById(R.id.timeText);
        final Button renderButton = findViewById(R.id.renderButton);
        renderButton.setOnClickListener(this::startRender);

        final ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        checksOpenGlVersion(activityManager);
        setupRenderer(textView, renderButton);

        final Optional<Bundle> bundle = Optional.ofNullable(savedInstanceState);
        initializePickers(bundle);
        initializeCheckBoxRasterize(bundle.map(x -> x.getBoolean(ConstantsUI.CHECK_BOX_RASTERIZE))
            .orElse(true));

        UtilsContext.checksStoragePermission(this);

        logger.info("onCreate finish");
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        logger.info("onRestoreInstanceState");

        final int scene = savedInstanceState.getInt(ConstantsUI.PICKER_SCENE);
        final int shader = savedInstanceState.getInt(ConstantsUI.PICKER_SHADER);
        final int threads = savedInstanceState.getInt(ConstantsUI.PICKER_THREADS);
        final int accelerator = savedInstanceState.getInt(ConstantsUI.PICKER_ACCELERATOR);
        final int samplesPixel = savedInstanceState.getInt(ConstantsUI.PICKER_SAMPLES_PIXEL);
        final int samplesLight = savedInstanceState.getInt(ConstantsUI.PICKER_SAMPLES_LIGHT);
        final int sizes = savedInstanceState.getInt(ConstantsUI.PICKER_SIZE);
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
    protected void onResume() {
        super.onResume();
        logger.info("onResume start");

        this.drawView.onResume();
        this.drawView.setVisibility(View.VISIBLE);
        logger.info("onResume end");
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        logger.info("onPostResume start");

        // Start rendering if its resuming from selecting a scene via an
        // external file manager and it was selected a file with a scene to
        // render.
        // This method should be automatically called after `onActivityResult`.
        if (!Strings.isNullOrEmpty(this.sceneFilePath)) {
            try {
                startRender(this.sceneFilePath);
            } catch (final Exception ex) {
                UtilsLogging.logThrowable(ex, "MainActivity#onPostResume");
                showUiMessage(Objects.requireNonNull(ex.getMessage()));
            }
        }
        logger.info("onPostResume end");
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        logger.info("onSaveInstanceState");

        outState.putInt(ConstantsUI.PICKER_SCENE, this.pickerScene.getValue());
        outState.putInt(ConstantsUI.PICKER_SHADER, this.pickerShader.getValue());
        outState.putInt(ConstantsUI.PICKER_THREADS, this.pickerThreads.getValue());
        outState.putInt(ConstantsUI.PICKER_ACCELERATOR, this.pickerAccelerator.getValue());
        outState.putInt(ConstantsUI.PICKER_SAMPLES_PIXEL, this.pickerSamplesPixel.getValue());
        outState.putInt(ConstantsUI.PICKER_SAMPLES_LIGHT, this.pickerSamplesLight.getValue());
        outState.putInt(ConstantsUI.PICKER_SIZE, this.pickerResolutions.getValue());
        outState.putBoolean(ConstantsUI.CHECK_BOX_RASTERIZE, this.checkBoxRasterize.isChecked());

        this.drawView.finishRenderer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        logger.info("onPause");

        Utils.handleInterruption("MainActivity#onPause");

        this.drawView.setPreserveEGLContextOnPause(true);
        this.drawView.onPause();
        this.drawView.setVisibility(View.INVISIBLE);
        this.sceneFilePath = null;

        final String message = "onPause" + ConstantsMethods.FINISHED;
        logger.info(message);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        logger.info(ConstantsMethods.ON_DESTROY);

        MainActivity.resetErrno(); // Avoid 'bad file descriptor' error.
        this.drawView.onDetachedFromWindow();
        this.drawView.setVisibility(View.INVISIBLE);

        final String message = ConstantsMethods.ON_DESTROY + ConstantsMethods.FINISHED;
        logger.info(message);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        logger.info(ConstantsMethods.ON_DETACHED_FROM_WINDOW);

        this.drawView.onDetachedFromWindow();

        final String message = ConstantsMethods.ON_DETACHED_FROM_WINDOW + ConstantsMethods.FINISHED;
        logger.info(message);
    }


    /*
     ***********************************************************************
     * Public methods
     ***********************************************************************
     */

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        logger.info("onRequestPermissionsResult");

        if (permissions.length > 0) {
            logger.info("Requested permissions: " + Arrays.toString(permissions));

            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted. Continue the action or workflow
                // in your app.
                logger.info("Permission granted!");
            }  else {
                // Explain to the user that the feature is unavailable because
                // the features requires a permission that the user has denied.
                // At the same time, respect the user's decision. Don't link to
                // system settings in an effort to convince the user to change
                // their decision.
                logger.severe("Permission NOT granted");
            }
            // Other 'case' lines to check for other
            // permissions this app might request.
        }
    }

    /**
     * Helper method which creates an {@link Intent} with the goal to ask Android System to read
     * some files by an external file manager.
     *
     * @return The {@link Intent} to load files.
     */
    public static Intent createIntentToLoadFiles() {
        final Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
        }
        intent.putExtra(Intent.EXTRA_TITLE, "Select an OBJ file to load.");
        intent.setType("*" + ConstantsUI.FILE_SEPARATOR + "*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        return intent;
    }

    /*
     ***********************************************************************
     * Private methods
     ***********************************************************************
     */


    @Override
    protected void onActivityResult(final int requestCode,
                                    final int resultCode,
                                    @Nullable final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        logger.info("onActivityResult requestCode: " + requestCode + ", resultCode: " + resultCode);

        try {
            if (data != null && Objects.equals(resultCode, Activity.RESULT_OK)) {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && data.getClipData() != null) {
                    final ClipData clipData = data.getClipData();
                    final int numFiles = clipData.getItemCount();
                    logger.info("Will read every selected file: " + numFiles);
                    for (int i = 0; i < numFiles; ++i) {
                        final ClipData.Item item = clipData.getItemAt(i);
                        final Uri uri = item.getUri();
                        if (uri == null) {
                            throw new FailureException("There is no URI to a File! [" + i + "]");
                        }
                        final String filePath = getPathFromFile(uri);
                        readFile(uri);
                        if (filePath.endsWith(".obj")) {
                            this.sceneFilePath = filePath;
                        }
                    }
                } else {
                    logger.info("Will read every file in a path.");
                    final Uri uri = data.getData();
                    if (uri == null) {
                        throw new FailureException("There is no URI to a File!");
                    }
                    final String filePath = getPathFromFile(uri);
                    if (filePath.endsWith(".obj")) {
                        this.sceneFilePath = filePath;
                    }
                    final File baseFile = new File(uri.getPath());
                    final File[] files;
                    if (baseFile.isDirectory()) {
                        files = baseFile.listFiles();
                    } else {
                        files = new File(Objects.requireNonNull(baseFile.getParent())).listFiles();
                    }
                    if (files == null) {
                        throw new FailureException("It couldn't list the files in the selected path. Are you sure the necessary permissions were given?");
                    }
                    for(final File file : files) {
                        readFile(Uri.fromFile(file));
                    }
                }
            } else {
                logger.severe("There is no URI to a File!");
                this.sceneFilePath = null;
            }
        } catch (final Exception ex) {
            UtilsLogging.logThrowable(ex, "MainActivity#onActivityResult");
            MainActivity.showUiMessage(ConstantsToast.COULD_NOT_RENDER_THE_SCENE + ex.getMessage());
        }
        logger.info("onActivityResult finished");
    }

    /**
     * Starts the rendering process when the user clicks the render
     * {@link Button}.
     *
     * @param view The view of the {@link Activity}.
     */
    public void startRender(@NonNull final View view) {
        try {
            final String message = ConstantsMethods.START_RENDER + ": " + view;
            logger.info(message);

            final State state = this.drawView.getRayTracerState();
            if (state == State.BUSY) {
                this.drawView.stopDrawing();
            } else {
                startRenderScene();
            }

            final String messageFinished = ConstantsMethods.START_RENDER + ConstantsMethods.FINISHED;
            logger.info(messageFinished);
        } catch (final Exception ex) {
            UtilsLogging.logThrowable(ex, "MainActivity#startRender");
            MainActivity.showUiMessage(ConstantsToast.COULD_NOT_RENDER_THE_SCENE + ex.getMessage());
        }
    }

    /**
     * Helper method which starts or stops the rendering process.
     *
     * @param scenePath The path to a directory containing the OBJ and MTL files
     *                  of a scene to render.
     */
    private void startRender(@NonNull final String scenePath) {
        logger.info(ConstantsMethods.START_RENDER);

        final Config config = createConfigFromUI(scenePath);
        this.drawView.renderScene(config);

        final String message = ConstantsMethods.START_RENDER + ConstantsMethods.FINISHED;
        logger.info(message);
    }

    /**
     * Helper method which starts the rendering process.
     */
    private void startRenderScene() {
        if (Scene.values()[this.pickerScene.getValue()] == Scene.OBJ) {
            callFileManager();
        } else {
            startRender("");
        }
    }

    /**
     * Auxiliary method to readjust the width and height of the image by
     * rounding down the value to a multiple of the number of tiles in the
     * Ray Tracer engine.
     *
     * @param size The value to be rounded down to a multiple of the number of
     *             tiles in the Ray Tracer engine.
     * @return The highest value that is smaller than the size passed by
     *     parameter and is a multiple of the number of tiles.
     */
    private native int rtResize(int size);

    /**
     * Resets the C `errno` error code to 0.
     */
    public static native void resetErrno();

    /**
     * Reads a file from a file descriptor natively.
     * It uses C functions in JNI to read the file.
     *
     * @param fd       The file descriptor.
     * @param size     The size of the file in bytes.
     * @param type     The type of the file. Where {@code 0} is for OBJ file, {@code 1} is for MTL
     *                 file, {@code 2} is to read a CAM file and {@code 3} is to read a texture file.
     * @param filePath The path to a file, which should be a texture to be used by MobileRT.
     */
    private native void readFile(int fd, long size, int type, String filePath);

    /**
     * Gets the path of a file that was loaded with an external file manager.
     * <br>
     * This method basically translates an {@link Uri} path to a {@link String}
     * but also tries to be compatible with any device / emulator available.
     *
     * @param uri The URI reference for the file.
     * @return The path to the file.
     */
    @NonNull
    private String getPathFromFile(@NonNull final Uri uri) {
        logger.info("Parsing path:" + Arrays.toString(uri.getPathSegments().toArray()));
        validatePathIsAccessible(uri);

        final String filePath = StreamSupport.stream(uri.getPathSegments())
            .skip(1L)
            .reduce("", (accumulator, segment) -> accumulator + ConstantsUI.FILE_SEPARATOR + segment);
        final boolean externalSDCardPath =
                uri.getPathSegments().get(0).matches("sdcard")
            || (uri.getPathSegments().size() > 1 && uri.getPathSegments().get(1).matches("^([A-Za-z0-9]){4}-([A-Za-z0-9]){4}:.+$"))
            || (uri.getPathSegments().size() > 1 && uri.getPathSegments().get(1).matches("^([A-Za-z0-9]){4}-([A-Za-z0-9]){4}$"))
            || (uri.getPathSegments().size() > 2 && uri.getPathSegments().get(2).matches("^([A-Za-z0-9]){4}-([A-Za-z0-9]){4}$"))
            || (uri.getPathSegments().get(0).matches("^mnt$") && uri.getPathSegments().get(1).matches("^sdcard$"))
            || (uri.getPathSegments().get(0).matches("^storage$") && uri.getPathSegments().get(1).matches("^sdcard$"))
            || (uri.getPathSegments().get(0).matches("^storage$") && uri.getPathSegments().get(1).matches("^emulated$") && uri.getPathSegments().get(2).matches("^0$"))
            || filePath.contains(Environment.getExternalStorageDirectory().getAbsolutePath());

        final int removeIndex = filePath.indexOf(ConstantsUI.PATH_SEPARATOR);
        final String startFilePath = removeIndex >= 0 ? filePath.substring(removeIndex) : filePath;
        String cleanedFilePath = startFilePath.replace(ConstantsUI.PATH_SEPARATOR, ConstantsUI.FILE_SEPARATOR);
        cleanedFilePath = cleanedFilePath.replaceFirst("^/sdcard/", "/");
        cleanedFilePath = cleanedFilePath.replaceFirst("^/([A-Za-z0-9]){4}-([A-Za-z0-9]){4}/", "/");
        cleanedFilePath = cleanedFilePath.replaceFirst("^/local/tmp/", "/");

        final String devicePath;
        if (externalSDCardPath) {
            devicePath = UtilsContext.getSdCardPath(this);
        } else {
            devicePath = UtilsContext.getInternalStoragePath(this);
        }

        // SDK API 30 looks like to get the path to the file properly without having to get the
        // SD card path and prefix with it.
        if (cleanedFilePath.startsWith(devicePath)) {
            return cleanedFilePath;
        }
        if (cleanedFilePath.startsWith("/emulated/0/")) {
            return "/storage" + cleanedFilePath;
        }

        return devicePath + cleanedFilePath;
    }

    /**
     * Validates that the user selected path in {@link Uri} can be read safely.
     * If the {@link Uri path} that the user selected can be dangerous, like '/data', '/system',
     * then a {@link SecurityException} is thrown.
     *
     * @param uri The {@link Uri} reference for the file.
     */
    private void validatePathIsAccessible(@NonNull final Uri uri) {
        logger.info("validatePathIsAccessible");
        final String path = uri.getPath();

        boolean externalStorage1 = path.matches("^/document/([A-Za-z0-9]){4}-([A-Za-z0-9]){4}:.+$");
        boolean externalStorage2 = path.matches("^/mnt/sdcard/.+$");
        boolean externalStorage3 = path.matches("^/storage/emulated/0/.+$");
        boolean externalStorage4 = path.matches("^/storage/([A-Za-z0-9]){4}-([A-Za-z0-9]){4}/.+$");
        boolean externalStorage5 = path.matches("^/storage/sdcard/.+$");
        boolean internalStorage = path.matches("^/data/local/tmp/.+$");

        if (externalStorage1 || externalStorage2 || externalStorage3 || externalStorage4 || externalStorage5 || internalStorage) {
            return;
        }

        throw new SecurityException("User shouldn't try to read files from the path: '" + uri.getPath() + "'");
    }

    /**
     * Helper method to read a file natively.
     *
     * @param uri The {@link Uri} which should point to a {@link File}.
     */
    private void readFile(@NonNull final Uri uri) {
        logger.info("readFile");
        final String filePath = getPathFromFile(uri);
        logger.info("Will read the following file: '" + filePath + "'");
        final ContentResolver contentResolver = getContentResolver();

        try (ParcelFileDescriptor parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")) {
            logger.info("Opened AssetFileDescriptor");
            final int fd = parcelFileDescriptor.getFd();
            final long size = parcelFileDescriptor.getStatSize();
            final String typeStr = filePath.substring(filePath.lastIndexOf(".")).toLowerCase();
            final int type = Objects.equals(typeStr, ".obj") ? 0 :
                Objects.equals(typeStr, ".mtl") ? 1 :
                Objects.equals(typeStr, ".cam") ? 2 :
                3;

            logger.info("Will read the following file: '" + filePath + "', [fd: " + fd + ", size: " + size + ", type: " + type + "]");
            // Important: Native layer shouldn't assume ownership of this fd and close it.
            readFile(fd, size, type, filePath);
        } catch (final IOException ex) {
            UtilsLogging.logThrowable(ex, "MainActivity#readFile");
            throw new FailureException(ex);
        }
        logger.info("Path '" + filePath +"' already read.");
    }

    /**
     * Create a Ray Tracer {@link Config} from the selected {@link NumberPicker}s
     * in the Android UI.
     *
     * @param scenePath The path to the OBJ scene file.
     * @return A {@link Config}.
     */
    @NonNull
    private Config createConfigFromUI(@NonNull final String scenePath) {
        final Pair<Integer, Integer> resolution =
            Utils.getResolutionFromPicker(this.pickerResolutions);

        final Config.Builder builder = Config.Builder.Companion.create();
        builder.setScene(this.pickerScene.getValue());
        builder.setShader(this.pickerShader.getValue());
        builder.setAccelerator(this.pickerAccelerator.getValue());

        final ConfigSamples.Builder builderConfigSamples = ConfigSamples.Builder.Companion.create();
        builderConfigSamples.setSamplesPixel(Utils.getValueFromPicker(this.pickerSamplesPixel));
        builderConfigSamples.setSamplesLight(Utils.getValueFromPicker(this.pickerSamplesLight));
        builder.setConfigSamples(builderConfigSamples.build());
        final ConfigResolution.Builder builderConfigRes = ConfigResolution.Builder.Companion.create();
        builderConfigRes.setWidth(resolution.getFirst());
        builderConfigRes.setHeight(resolution.getSecond());
        builder.setConfigResolution(builderConfigRes.build());
        final int startOfExtension = scenePath.lastIndexOf('.');
        final String filePathWithoutExtension;
        if (startOfExtension >= 0) {
            filePathWithoutExtension = scenePath.substring(0, startOfExtension);
        } else {
            filePathWithoutExtension = scenePath;
        }
        builder.setObjFilePath(filePathWithoutExtension + ".obj");
        builder.setMatFilePath(filePathWithoutExtension + ".mtl");
        builder.setCamFilePath(filePathWithoutExtension + ".cam");
        builder.setThreads(this.pickerThreads.getValue());
        builder.setRasterize(this.checkBoxRasterize.isChecked());

        return builder.build();
    }

    /**
     * Helper method which calls a new {@link Activity} with a file manager to
     * select the OBJ file for the Ray Tracer engine to load.
     */
    private void callFileManager() {
        logger.info("callFileManager");
        final Intent intent = createIntentToLoadFiles();
        try {
            startActivityForResult(intent, OPEN_FILE_REQUEST_CODE);
        } catch (final ActivityNotFoundException ex) {
            UtilsLogging.logThrowable(ex, "MainActivity#callFileManager");
            Toast.makeText(this, ConstantsToast.PLEASE_INSTALL_FILE_MANAGER, Toast.LENGTH_LONG)
                .show();
        }

        final String message = "callFileManager" + ConstantsMethods.FINISHED;
        logger.info(message);
    }

    /**
     * Helper method that initializes all the pickers fields of this
     * {@link Activity}.
     *
     * @param bundle The data state of the {@link Activity}.
     */
    private void initializePickers(final Optional<Bundle> bundle) {
        initializePicker(this.pickerScene, bundle.map(x -> x.getInt(ConstantsUI.PICKER_SCENE))
            .orElse(0), Scene.getNames());
        initializePicker(this.pickerShader, bundle.map(x -> x.getInt(ConstantsUI.PICKER_SHADER))
            .orElse(0), Shader.getNames());
        initializePicker(this.pickerAccelerator,
            bundle.map(x -> x.getInt(ConstantsUI.PICKER_ACCELERATOR))
                .orElse(1), Accelerator.getNames());

        final String[] samplesPixel = IntStreams.range(0, 99)
            .map(value -> (value + 1) * (value + 1))
            .mapToObj(String::valueOf)
            .toArray(String[]::new);
        initializePicker(this.pickerSamplesPixel,
            bundle.map(x -> x.getInt(ConstantsUI.PICKER_SAMPLES_PIXEL))
                .orElse(1), samplesPixel);

        final String[] samplesLight = IntStreams.range(0, 100)
            .map(value -> value + 1)
            .mapToObj(String::valueOf)
            .toArray(String[]::new);
        initializePicker(this.pickerSamplesLight,
            bundle.map(x -> x.getInt(ConstantsUI.PICKER_SAMPLES_LIGHT))
                .orElse(1), samplesLight);

        initializePickerThreads(bundle.map(x -> x.getInt(ConstantsUI.PICKER_THREADS))
            .orElse(1));

        initializePickerResolutions(bundle.map(x -> x.getInt(ConstantsUI.PICKER_SIZE))
            .orElse(4));
    }

    /**
     * Helper method that sets up the {@link GLSurfaceView.Renderer} in the
     * {@link DrawView}.
     * This method loads the GLSL shaders and pass them to the
     * {@link GLSurfaceView.Renderer}.
     *
     * @param textView     The {@link TextView} for the {@link GLSurfaceView.Renderer} to pass to
     *                     the {@link RenderTask} when the Ray Tracing process starts.
     * @param renderButton The {@link TextView} for the {@link GLSurfaceView.Renderer} to
     *                     pass to the {@link RenderTask} when the Ray Tracing
     *                     process starts.
     */
    private void setupRenderer(final TextView textView, final Button renderButton) {
        logger.info("setupRenderer start");

        this.drawView.setVisibility(View.INVISIBLE);
        this.drawView.setEGLContextClientVersion(MyEglContextFactory.EGL_CONTEXT_CLIENT_VERSION);
        this.drawView.setEGLConfigChooser(8, 8, 8, 8, 3 * 8, 0);

        final ActivityManager activityManager = (ActivityManager) getSystemService(
            Context.ACTIVITY_SERVICE);
        this.drawView.setViewAndActivityManager(textView, activityManager);
        this.drawView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        renderButton.setOnLongClickListener((final View view) -> {
            onDestroy(); // Necessary to stop any previous render before recreating the Activity.
            recreate();
            return false;
        });

        final String shadersPath = ConstantsUI.PATH_SHADERS + ConstantsUI.FILE_SEPARATOR;
        final ImmutableMap<Integer, String> shadersPaths = ImmutableMap.of(
            GLES20.GL_VERTEX_SHADER, shadersPath + "VertexShader.glsl",
            GLES20.GL_FRAGMENT_SHADER, shadersPath + "FragmentShader.glsl");
        final Map<Integer, String> shadersRayTracing =
            UtilsContext.readShaders(this, shadersPaths);

        final ImmutableMap<Integer, String> shadersPreviewPaths = ImmutableMap.of(
            GLES20.GL_VERTEX_SHADER, shadersPath + "VertexShaderRaster.glsl",
            GLES20.GL_FRAGMENT_SHADER, shadersPath + "FragmentShaderRaster.glsl");
        final Map<Integer, String> shadersPreview =
            UtilsContext.readShaders(this, shadersPreviewPaths);

        this.drawView.setUpShadersCode(shadersRayTracing, shadersPreview);
        this.drawView.setUpButtonRender(renderButton);
        this.drawView.setVisibility(View.VISIBLE);
        this.drawView.setPreserveEGLContextOnPause(true);

        logger.info("setupRenderer finish");
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
     */
    private void initializePickerResolutions(final int pickerSizes) {
        logger.info("initializePickerResolutions start");
        final int maxSizes = 9;
        this.pickerResolutions.setMinValue(1);
        this.pickerResolutions.setMaxValue(maxSizes - 1);
        this.pickerResolutions.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        this.pickerResolutions.setValue(pickerSizes);

        // TODO: For Android API < 15, this method crashes, so it's necessary to investigate it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            this.pickerResolutions.setWrapSelectorWheel(true);
        }

        final ViewTreeObserver vto = this.drawView.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(() -> {
            logger.info("initializePickerResolutions 1");
            final double widthView = this.drawView.getWidth();
            final double heightView = this.drawView.getHeight();

            final String[] resolutions = IntStreams.rangeClosed(2, maxSizes)
                // double.class::cast method throws `ClassCastException: Integer cannot be cast to double`
                .mapToDouble(Double::valueOf)
                .map(value -> (value + 1.0) * 0.1)
                .map(value -> value * value)
                .mapToObj(value -> {
                    resetErrno(); // Necessary to avoid 'EWOULDBLOCK'.
                    final int width = rtResize((int) Math.round(widthView * value));
                    final int height = rtResize((int) Math.round(heightView * value));
                    return String.valueOf(width) + 'x' + height;
                })
                .toArray(String[]::new);

            this.pickerResolutions.setDisplayedValues(resolutions);
            logger.info("initializePickerResolutions 2");
        });
        logger.info("initializePickerResolutions finish");
    }

    /**
     * Initializes the {@link #pickerThreads} field.
     *
     * @param pickerThreads The default value to put in the
     *                      {@link #pickerThreads} field.
     */
    private void initializePickerThreads(final int pickerThreads) {
        final int maxCores = UtilsContext.getNumOfCores(this);
        this.pickerThreads.setMinValue(1);
        this.pickerThreads.setMaxValue(maxCores);
        this.pickerThreads.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        this.pickerThreads.setValue(pickerThreads);

        // TODO: For Android API < 15, this method crashes, so it's necessary to investigate it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            this.pickerThreads.setWrapSelectorWheel(true);
        }
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
        Preconditions.checkNotNull(this.pickerResolutions, "pickerResolutions shouldn't be null");
        Preconditions.checkNotNull(this.pickerThreads, "pickerThreads shouldn't be null");
        Preconditions.checkNotNull(this.pickerAccelerator, "pickerAccelerator shouldn't be null");
        Preconditions.checkNotNull(this.pickerSamplesLight, "pickerSamplesLight shouldn't be null");
        Preconditions.checkNotNull(this.pickerSamplesPixel, "pickerSamplesPixel shouldn't be null");
        Preconditions.checkNotNull(this.pickerShader, "pickerShader shouldn't be null");
        Preconditions.checkNotNull(this.pickerScene, "pickerScene shouldn't be null");
        Preconditions.checkNotNull(this.drawView, "drawView shouldn't be null");
    }

    /**
     * Sets the {@link #currentInstance}.
     */
    private void setCurrentInstance() {
        currentInstance = this;
    }

}
