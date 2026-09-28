package org.birdwatch.wear;
import org.junit.Test;
import static org.junit.Assert.*;
public class PhotoSelectionTest {
    @Test public void silenceRepeatAndLowScoresKeepLastBird() {
        PhotoSelection selection=new PhotoSelection();
        assertTrue(selection.accept("Bird A",.35f));
        assertFalse(selection.accept("",0));
        assertFalse(selection.accept("Bird A",.8f));
        assertFalse(selection.accept("Bird B",.349f));
        assertFalse(selection.accept("Bird B",Float.NaN));
        assertTrue(selection.matches("Bird A"));
        assertTrue(selection.accept("Bird B",.35f));
        assertFalse(selection.matches("Bird A"));
        assertTrue(selection.matches("Bird B"));
        selection.clear();
        assertTrue(selection.accept("Bird B",.4f));
    }
}
