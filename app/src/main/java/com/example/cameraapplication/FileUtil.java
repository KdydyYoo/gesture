package com.example.cameraapplication;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtil {
    public static String getAssetPath(Context context, String assetName) throws IOException {
        File outFile = new File(context.getFilesDir(), assetName);
        if (!outFile.exists()) {
            InputStream in = context.getAssets().open(assetName);
            FileOutputStream out = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.flush();
            out.close();
        }
        return outFile.getAbsolutePath();
    }
}
