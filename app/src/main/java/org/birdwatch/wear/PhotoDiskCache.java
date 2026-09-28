package org.birdwatch.wear;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONObject;

/** Atomic photo + attribution records, bounded by bytes and least-recently-used eviction. */
final class PhotoDiskCache {
    private static final Object LOCK=new Object();
    private static final int MAGIC=0x42575031, MAX_IMAGE=2*1024*1024;
    private final File directory;
    private final long budget;
    record Entry(BirdPhotos.Metadata metadata,byte[] image) {}
    record Stats(int count,long bytes) {}
    PhotoDiskCache(File directory){this(directory,20L*1024*1024);}
    PhotoDiskCache(File directory,long budget){this.directory=directory;this.budget=budget;}
    private File path(String scientific) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(scientific.getBytes(StandardCharsets.UTF_8));
        StringBuilder name=new StringBuilder();for(byte b:hash)name.append(String.format(Locale.ROOT,"%02x",b&255));
        return new File(directory,name+".photo");
    }
    Entry get(String scientific) {
        synchronized(LOCK) {
            File file=null;
            try {
                file=path(scientific);if(!file.isFile())return null;
                if(file.length()>MAX_IMAGE+65548)throw new IOException("Oversized cache entry");
                try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                    if(in.readInt()!=MAGIC)throw new IOException("Unknown cache format");
                    JSONObject json=new JSONObject(in.readUTF());
                    if(!scientific.equals(json.optString("scientific")))throw new IOException("Species mismatch");
                    BirdPhotos.Metadata metadata=BirdPhotos.metadata(json,scientific);
                    int length=in.readInt();
                    if(metadata==null || length<1 || length>MAX_IMAGE)throw new IOException("Invalid cached metadata");
                    byte[] image=new byte[length];in.readFully(image);
                    if(in.read()!=-1)throw new IOException("Trailing data");
                    file.setLastModified(System.currentTimeMillis());
                    return new Entry(metadata,image);
                }
            }catch(Exception e){if(file!=null)file.delete();return null;}
        }
    }
    void put(BirdPhotos.Metadata metadata,byte[] image) throws Exception {
        synchronized(LOCK) {
            if(image.length==0 || image.length>MAX_IMAGE)return;
            if(!directory.isDirectory() && !directory.mkdirs())throw new IOException("Cannot create photo cache");
            JSONObject json=new JSONObject().put("scientific",metadata.scientific()).put("attribution",metadata.attribution())
                .put("attribution_name",metadata.author()).put("license_code",metadata.license()).put("id",metadata.id()).put("medium_url",metadata.url());
            File temporary=File.createTempFile("download-",".tmp",directory);
            try {
                try(FileOutputStream stream=new FileOutputStream(temporary);DataOutputStream out=new DataOutputStream(stream)) {
                    out.writeInt(MAGIC);out.writeUTF(json.toString());out.writeInt(image.length);out.write(image);out.flush();stream.getFD().sync();
                }
                Files.move(temporary.toPath(),path(metadata.scientific()).toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
                trim();
            }finally{temporary.delete();}
        }
    }
    void remove(String scientific){synchronized(LOCK){try{path(scientific).delete();}catch(Exception ignored){}}}
    Stats stats(){synchronized(LOCK){File[] files=entries();long bytes=0;for(File file:files)bytes+=file.length();return new Stats(files.length,bytes);}}
    void clear() throws IOException {
        synchronized(LOCK) {
            File[] files=directory.listFiles();if(files==null)return;
            for(File file:files)if(!file.delete() && file.exists())throw new IOException("Cannot clear photo cache");
        }
    }
    private File[] entries(){File[] abandoned=directory.listFiles((dir,name)->name.endsWith(".tmp"));if(abandoned!=null)for(File file:abandoned)file.delete();File[] files=directory.listFiles((dir,name)->name.endsWith(".photo"));return files==null?new File[0]:files;}
    private void trim() {
        File[] files=entries();Arrays.sort(files,Comparator.comparingLong(File::lastModified));
        long total=0;for(File file:files)total+=file.length();int count=files.length;
        for(File file:files) {
            if(total<=budget && count<=100)break;
            long size=file.length();if(file.delete()){total-=size;count--;}
        }
    }
}
