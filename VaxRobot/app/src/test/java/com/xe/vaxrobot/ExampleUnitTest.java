package com.xe.vaxrobot;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
        String base = "Sonic: [L: 0; R: 14; F: 56]";
        float angleRad = (float) Math.toRadians(45);

        // Calculate delta in cm
//        float dx = (float) (distanceCm * Math.sin(angleRad));
        System.out.println("Math.sin(angleRad): " + Math.sin(angleRad));

    }
}