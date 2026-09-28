package org.birdwatch.wear;

/** Silence and repeated species leave the background selection unchanged. */
final class PhotoSelection {
    private String scientific="";
    boolean accept(String species,float score) {
        if(species==null || species.isEmpty() || !DetectionPolicy.isVisible(score) || species.equals(scientific))return false;
        scientific=species;return true;
    }
    boolean matches(String species){return scientific.equals(species);}
    void clear(){scientific="";}
}
