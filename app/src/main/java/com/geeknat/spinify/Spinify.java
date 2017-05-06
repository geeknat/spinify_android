package com.geeknat.spinify;

import android.app.Application;
import android.content.res.Configuration;

import com.geeknat.spinify.utils.TypeFaceUtil;
import com.geeknat.spinify.utils.TypefaceHelper;


/**
 * Created by @GeekNat on 4/17/17.
 */

public class Spinify extends Application {

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        TypefaceHelper.initialize(this);
        TypeFaceUtil.overrideFont(getApplicationContext(), "SERIF", getString(R.string.custom_font));
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

}
