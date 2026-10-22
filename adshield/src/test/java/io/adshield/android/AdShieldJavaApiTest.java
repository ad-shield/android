package io.adshield.android;

import android.content.Context;
import android.content.ContextWrapper;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AdShieldJavaApiTest {
    @After
    public void restoreEnabled() {
        AdShield.setEnabled(true);
    }

    @Test
    public void enabledByDefault() {
        assertTrue(AdShield.enabled);
    }

    @Test
    public void publicFieldDisablesMeasurementBeforeAccessingContext() {
        AdShield.enabled = false;
        AdShield.measure(unusedContext());
    }

    @Test
    public void staticSetterDisablesMeasurementBeforeAccessingContext() {
        AdShield.setEnabled(false);
        AdShield.measure(unusedContext());
    }

    private Context unusedContext() {
        return new ContextWrapper(null) {
            @Override
            public Context getApplicationContext() {
                throw new AssertionError("Disabled measurements must not access the context");
            }
        };
    }
}
