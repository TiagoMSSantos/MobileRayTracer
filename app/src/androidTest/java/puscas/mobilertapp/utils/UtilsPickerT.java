package puscas.mobilertapp.utils;

import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.matcher.ViewMatchers;

import org.junit.Assert;

import java.util.logging.Logger;

import puscas.mobilertapp.ViewActionNumberPicker;

/**
 * Helper class which contains helper methods about the {@link NumberPicker}s for the tests.
 */
public final class UtilsPickerT {

    /**
     * Logger for this class.
     */
    private static final Logger logger = Logger.getLogger(UtilsPickerT.class.getSimpleName());

    /**
     * Private constructor to avoid creating instances.
     */
    private UtilsPickerT() {
        throw new UnsupportedOperationException("Not implemented.");
    }

    /**
     * Helper method which changes the {@code value} of a {@link NumberPicker}.
     *
     * @param pickerName    The name of the {@link NumberPicker}.
     * @param pickerId      The identifier of the {@link NumberPicker}.
     * @param expectedValue The new expectedValue for the {@link NumberPicker}.
     */
    public static void changePickerValue(@NonNull final String pickerName,
                                         final int pickerId,
                                         final int expectedValue) {
        logger.info("changePickerValue");
        Espresso.onView(ViewMatchers.withId(pickerId))
            .perform(new ViewActionNumberPicker(expectedValue))
            .check((view, exception) -> {
                final NumberPicker numberPicker = view.findViewById(pickerId);
                Assert.assertEquals("Number picker '" + pickerName + "' with wrong value",
                    expectedValue, numberPicker.getValue()
                );
            });
    }

}
