package com.geeknat.spinify.utils;

/**
 * Created by @GeekNat on 5/4/17.
 */

public class Utils {

    public static int getUnsignedInt(int valueToFormat) {
        if (String.valueOf(valueToFormat).contains("-")) {
            return Integer.parseInt(String.valueOf(valueToFormat).substring(1));
        }

        return valueToFormat;
    }

}
