/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.watchface;

import android.graphics.Color;

public final class SunshineWatchFaceUtil {
    private static final String TAG = "SunshineWatchFaceUtil";
    public static final String PATH_WITH_FEATURE = "/watch_face_config/Digital";

    /**
     * Name of the default interactive mode background color and the ambient mode background color.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND = "#03A9F4";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND =
        parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_BACKGROUND);

    /**
     * Name of the default interactive mode hour digits color and the ambient mode hour digits
     * color.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_HOUR_DIGITS = "White";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS =
        parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_HOUR_DIGITS);

    /**
     * Name of the default interactive mode minute digits color and the ambient mode minute digits
     * color.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_MINUTE_DIGITS = "White";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS =
        parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);

    /**
     * Name of the default interactive mode second digits color and the ambient mode second digits
     * color.
     */
    public static final String COLOR_NAME_DEFAULT_AND_AMBIENT_SECOND_DIGITS = "White";
    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS =
        parseColor(COLOR_NAME_DEFAULT_AND_AMBIENT_SECOND_DIGITS);


    //FIXME Not sure why using R.color doesn't work. Looks like we need to use parseColor to render it properly.
    //FIXME Udacity Discussion : https://discussions.udacity.com/t/android-wear-what-am-i-missing-here-i-am-seeing-a-blank-watch-face-need-help/227573
//    /**
//     * Name of the default interactive mode background color and the ambient mode background color.
//     */
//    @ColorInt
//    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND = R.color.black;
//
//    /**
//     * Name of the default interactive mode hour digits color and the ambient mode hour digits
//     * color.
//     */
//    @ColorInt
//    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS = R.color.white;
//
//    /**
//     * Name of the default interactive mode minute digits color and the ambient mode minute digits
//     * color.
//     */
//    @ColorInt
//    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS = R.color.white;
//
//    /**
//     * Name of the default interactive mode second digits color and the ambient mode second digits
//     * color.
//     */
//    @ColorInt
//    public static final int COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS = R.color.grey;
//

    private static int parseColor(String colorName) {
        return Color.parseColor(colorName.toLowerCase());
    }

    private SunshineWatchFaceUtil() {
    }
}
