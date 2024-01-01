package puscas.mobilertapp.constants;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/**
 * The unit tests for the {@link ConstantsToast} util class.
 */
public final class ConstantsToastTest {

    /**
     * Tests that it's not possible to instantiate {@link ConstantsToast}.
     *
     * @throws NoSuchMethodException If Java reflection fails when using the private constructor.
     */
    @Test
    public void testDefaultConstantsToast() throws NoSuchMethodException {
        final Constructor<ConstantsToast> constructor = ConstantsToast.class.getDeclaredConstructor();
        Assertions.assertThat(Modifier.isPrivate(constructor.getModifiers()))
            .as("The constructor is private")
            .isTrue();
        constructor.setAccessible(true);
        Assertions.assertThatThrownBy(() -> constructor.newInstance())
            .as("The default constructor of ConstantsToast")
            .isNotNull()
            .isInstanceOf(InvocationTargetException.class);
    }

}
