package org.birdwatch.wear;
import android.content.Context;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.DataType;
final class GeoModel {
    static boolean enabled(Context context){return context.getSharedPreferences("birdwatch_settings",0).getBoolean("geo_filter",true);}
    static void setEnabled(Context context,boolean enabled){context.getSharedPreferences("birdwatch_settings",0).edit().putBoolean("geo_filter",enabled).apply();}
    static boolean[] predict(Context context,double latitude,double longitude,int week) throws IOException {
        if(!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude)>90 || Math.abs(longitude)>180 || week<1 || week>48)throw new IllegalArgumentException("Invalid location/date");
        try(var fd=context.getAssets().openFd("geo-model.tflite");var stream=new FileInputStream(fd.getFileDescriptor());
            var interpreter=new Interpreter(stream.getChannel().map(FileChannel.MapMode.READ_ONLY,fd.getStartOffset(),fd.getDeclaredLength()),new Interpreter.Options().setNumThreads(1))) {
            if(!Arrays.equals(interpreter.getInputTensor(0).shape(),new int[]{1,3}) ||
                !Arrays.equals(interpreter.getOutputTensor(0).shape(),new int[]{1,6522}) || interpreter.getInputTensor(0).dataType()!=DataType.FLOAT32)
                throw new IOException("Incompatible geo model");
            float[][] result=new float[1][6522];interpreter.run(new float[][]{{(float)latitude,(float)longitude,week}},result);
            return GeoPolicy.mask(result[0]);
        }
    }
}
