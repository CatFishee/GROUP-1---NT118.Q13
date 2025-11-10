package com.example.metube.utils;
import android.app.Application;
import com.cloudinary.android.MediaManager;
import java.util.HashMap;
import java.util.Map;

public class MyApplication extends Application{
    @Override
    public void onCreate() {
        super.onCreate();

        Map config = new HashMap();
        config.put("cloud_name", "dnv8lpqcq");
        config.put("api_key", "416475473695696");
        config.put("api_secret", "rVfwA-ZUWSRdNgqiMIZjek1FiUo");
        MediaManager.init(this, config);
    }
}
