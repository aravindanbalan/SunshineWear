package com.example.android.sunshine;

import com.example.android.sunshine.data.WeatherContract;

/**
 * Created by arbalan on 3/8/17.
 */

public interface IConstants {

    /*
    * The columns of data that we are interested in displaying within our notification to let
    * the user know there is new weather data available.
    */
    String[] WEATHER_NOTIFICATION_PROJECTION = {
        WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
        WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
        WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
    };

    /*
    * We store the indices of the values in the array of Strings above to more quickly be able
    * to access the data from our query. If the order of the Strings above changes, these
    * indices must be adjusted to match the order of the Strings.
    */
    int INDEX_WEATHER_ID = 0;
    int INDEX_MAX_TEMP = 1;
    int INDEX_MIN_TEMP = 2;

    String PATH_WITH_FEATURE = "/watch_face_config/sunshine";
    String WEATHER_IMAGE_PATH = "/watch_face_config/sunshine/weather_image";
    String WEATHER_FORMATTED_HIGH_LOW = "/watch_face_config/sunshine/high_low";
}
