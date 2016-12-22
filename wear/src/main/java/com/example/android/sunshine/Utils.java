package com.example.android.sunshine;

import android.util.Log;

/**
 * Created by sanathnandasiri on 12/22/16.
 */

class Utils {

    public static final String TAG = Utils.class.getSimpleName();

    /*
             * Helper method to provide the icon resource id according to the weather condition id returned
             * by the OpenWeatherMap call. This method is very similar to
             *
                     *   {@link #getLargeArtResourceIdForWeatherCondition(int)}.
                    *
                    * The difference between these two methods is that this method provides smaller assets, used
             * in the list item layout for a "future day", as well as
             *
                     * @param weatherId from OpenWeatherMap API response
             *                  See http://openweathermap.org/weather-conditions for a list of all IDs
                    *
                    * @return resource id for the corresponding icon. -1 if no relation is found.
                    */
    public static int getSmallArtResourceIdForWeatherCondition(int weatherId) {

        /*
         * Based on weather code data for Open Weather Map.
         */
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.ic_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.ic_rain;
        } else if (weatherId == 511) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.ic_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.ic_snow;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.ic_fog;
        } else if (weatherId == 761 || weatherId == 771 || weatherId == 781) {
            return R.drawable.ic_storm;
        } else if (weatherId == 800) {
            return R.drawable.ic_clear;
        } else if (weatherId == 801) {
            return R.drawable.ic_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.ic_cloudy;
        } else if (weatherId >= 900 && weatherId <= 906) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 958 && weatherId <= 962) {
            return R.drawable.ic_storm;
        } else if (weatherId >= 951 && weatherId <= 957) {
            return R.drawable.ic_clear;
        }

        Log.e(TAG, "Unknown Weather: " + weatherId);
        return R.drawable.ic_storm;
    }
}
