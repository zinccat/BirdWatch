package org.birdwatch.wear;
import java.time.LocalDate;
final class GeoPolicy {
    static final float MIN_OCCURRENCE=.03f;
    // BirdNET uses four periods per month, not ISO calendar weeks.
    static int week(LocalDate date){return (date.getMonthValue()-1)*4+Math.min(4,(date.getDayOfMonth()-1)/7+1);}
    static boolean[] mask(float[] occurrence) {
        boolean[] allowed=new boolean[occurrence.length];int count=0;
        for(int i=0;i<occurrence.length;i++) {
            if(!Float.isFinite(occurrence[i]) || occurrence[i]<0 || occurrence[i]>1)throw new IllegalArgumentException("Invalid occurrence score");
            allowed[i]=occurrence[i]>=MIN_OCCURRENCE;if(allowed[i])count++;
        }
        if(count==0)throw new IllegalArgumentException("Empty location filter");
        return allowed;
    }
}
