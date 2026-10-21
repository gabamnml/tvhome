package com.binario.tvhome;

/** One weather reading. Temperatures are already in the configured unit. */
final class Weather {
    final float temp, max, min;
    final int code;          // WMO weather interpretation code

    Weather(float temp, float max, float min, int code) {
        this.temp = temp;
        this.max = max;
        this.min = min;
        this.code = code;
    }

    /**
     * WMO code to a string resource, grouped so we carry eleven phrases instead
     * of the thirty-odd distinct codes.
     *
     * @return 0 when the code is unrecognised, in which case show the
     *         temperature on its own rather than inventing a description.
     */
    static int describe(int code) {
        switch (code) {
            case 0:  return R.string.wx_clear;
            case 1:  return R.string.wx_mainly_clear;
            case 2:  return R.string.wx_partly_cloudy;
            case 3:  return R.string.wx_overcast;

            case 45: case 48:
                return R.string.wx_fog;

            case 51: case 53: case 55: case 56: case 57:
                return R.string.wx_drizzle;

            case 61: case 63: case 65: case 66: case 67:
                return R.string.wx_rain;

            case 80: case 81: case 82:
                return R.string.wx_showers;

            case 71: case 73: case 75: case 77:
                return R.string.wx_snow;

            case 85: case 86:
                return R.string.wx_snow_showers;

            case 95: case 96: case 99:
                return R.string.wx_thunderstorm;

            default:
                return 0;
        }
    }
}
