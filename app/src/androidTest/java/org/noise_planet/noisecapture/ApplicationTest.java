package org.noise_planet.noisecapture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Intent;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.LargeTest;


import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;


/**
 * Unit testing
 * Source https://github.com/googlesamples/android-testing/blob/master/ui/espresso/BasicSample/app/src/androidTest/java/com/example/android/testing/espresso/BasicSample/ChangeTextBehaviorTest.java
 * Run with gradle connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ApplicationTest {
    /**
     * A JUnit {@link Rule @Rule} to launch your activity under test. This is a replacement
     * for {@link ActivityInstrumentationTestCase2}.
     * <p>
     * Rules are interceptors which are executed for each test method and will run before
     * any of your setup code in the {@link Before @Before} method.
     * <p>
     * {@link ActivityTestRule} will create and launch of the activity for you and also expose
     * the activity under test. To get a reference to the activity you can use
     * the {@link ActivityTestRule#getActivity()} method.
     */
    @Rule
    public ActivityTestRule<Measurement> mActivityRule = new ActivityTestRule<>(
            Measurement.class);

    @Test
    public void launchRecord() {
        Intent intent = new Intent();
        mActivityRule.launchActivity(intent);

        assertEquals(-1, mActivityRule.getActivity().getRecordId());


        // Click on launch record
        onView(withId(R.id.recordBtn)).perform(click());

        // Check that record id is defined
        assertNotEquals(-1, mActivityRule.getActivity().getRecordId());
    }

}