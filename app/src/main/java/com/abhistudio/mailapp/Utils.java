package com.abhistudio.mailapp;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.icu.util.Calendar;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.format.DateFormat;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;


public class Utils {

    private Context mContext;

    public Utils(Context context) {
        this.mContext = context;
    }

    public Context getContext() {
        return mContext;
    }

    public boolean isDeviceOnline() {
        ConnectivityManager connMgr = (ConnectivityManager) this.mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }


    public void showSnackbar(View view, String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
    }

    public int getRandomMaterialColor() {
        int returnColor = Color.GRAY;
        int arrayId = mContext.getResources().getIdentifier("material_colors", "array", mContext.getPackageName());

        if (arrayId != 0) {
            TypedArray colors = mContext.getResources().obtainTypedArray(arrayId);

            Random random = new Random();
            int index = random.nextInt(colors.length());
            returnColor = colors.getColor(index, Color.GRAY);
            colors.recycle();
        }
        return returnColor;
    }


    public String timestampToDate(long time) {


        Date date = new Date(time*1000L);
        // format of the date
        SimpleDateFormat jdf;
        jdf = new SimpleDateFormat("yyyy-MM-dd");
        //jdf.setTimeZone(TimeZone.getTimeZone("GMT-4"));
        String mDate = (jdf.format(date));
        return mDate;


    }


    public boolean isValidEmail(String email) {
        final String EMAIL_PATTERN = "^[a-zA-Z0-9#_~!$&'()*+,;=:.\"(),:;<>@\\[\\]\\\\]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*$";
        return Pattern.compile(EMAIL_PATTERN).matcher(email).matches();
    }
}
