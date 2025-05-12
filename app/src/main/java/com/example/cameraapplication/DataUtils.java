package com.example.cameraapplication;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

public class DataUtils
{
    private static final String TAG = "say_DataUtils";

    public static boolean isInvalidData( String data )
    {
        if( TextUtils.isEmpty( data ) )
            Log.e( TAG, "warning. data is invalid.error. empty data: " + data );
        if( TextUtils.equals( data,"null" ) )
            Log.e( TAG, "warning. data is invalid.error. equals null: " + data );
        if( TextUtils.equals( data,"0" ) )
            Log.e( TAG, "warning. data is invalid. equals zero: " + data );

        return ( TextUtils.isEmpty( data ) || TextUtils.equals( data,"null" ) || TextUtils.equals( data,"0" ) );
    }

    public static float parseFloatOrDefault( String data, float defaultValue )
    {
        if( isInvalidData( data ) ){
            return defaultValue;
        }
        try{
            return Float.parseFloat( data );
        }catch( NumberFormatException e ){
            return defaultValue;
        }
    }

    public static double parseDoubleOrDefault( String data, int defaultValue )
    {
        if( isInvalidData( data ) ){
            return defaultValue;
        }
        try{
            return Double.parseDouble( data );
        }catch( NumberFormatException e ){
            return defaultValue;
        }
    }

    public static int parseIntOrDefault( String data, int defaultValue )
    {
        if( isInvalidData( data ) ){
            return defaultValue;
        }
        try{
            return Integer.parseInt( data );
        }catch( NumberFormatException e ){
            return defaultValue;
        }
    }

    public static long parseLongOrDefault( String data, long defaultValue )
    {
        if( isInvalidData( data ) ){
            return defaultValue;
        }
        try{
            return Long.parseLong( data );
        }catch( NumberFormatException e ){
            return defaultValue;
        }
    }

    public static float  roundToDecimalPlaces( float value, int decimalPlaces )
    {
        float  scale = (float) Math.pow( 10, decimalPlaces );
        return Math.round( value * scale ) / scale;
    }

    public static void setButtonStyle( Button button )
    {
        int[][] states = new int[][] {
                new int[] { android.R.attr.state_focused },
                new int[] { -android.R.attr.state_focused }
        };
        int[] colors = new int[] {
                Color.parseColor("#FF8C00"),
                Color.parseColor("#FFA500")
        };
        int[] textColors = new int[] {
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#1e2132")
        };
        ColorStateList textColorStateList = new ColorStateList(states, textColors);
        ColorStateList colorStateList = new ColorStateList( states, colors );
        button.setBackgroundTintList( colorStateList );
        button.setTypeface( null, Typeface.BOLD );
        button.setTextColor( textColorStateList );
    }

    public static String convertTimeFormat( long srcTime, String patten )
    {
        Date startDate = new Date( srcTime );
        SimpleDateFormat dateFormat = new SimpleDateFormat( patten );

        return dateFormat.format( startDate );
    }

    public static String getUsbDrivePath()
    {
        File storageDir = new File( "/mnt/media_rw" );
        if( !storageDir.exists() || !storageDir.isDirectory() ){
            Log.e( TAG, "warning. storageDir is null: " + storageDir.toString() );
            return null;
        }

        File[] directories = storageDir.listFiles();
        if( directories == null ){
            Log.e( TAG, "warning. storageDir directories list is null: "  );
            return null;
        }

        Pattern pattern = Pattern.compile("^[A-Z0-9]{4}-[A-Z0-9]{4}$", Pattern.CASE_INSENSITIVE);
        for (File dir : directories) {
            if (dir.isDirectory()) {
                String dirName = dir.getName();
                if (pattern.matcher(dirName).matches() || dirName.toLowerCase().contains("usb")) {
                    return dir.getAbsolutePath();
                }
            }
        }

        return null;
    }
}
