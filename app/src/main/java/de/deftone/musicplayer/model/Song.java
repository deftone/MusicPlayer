package de.deftone.musicplayer.model;

import java.io.File;
import java.io.Serializable;

/**
 * Created by deftone on 02.04.18.
 */

public class Song implements Serializable {
    private long id;
    private String title;
    private String artist;
    private File file;
    private String fileName;

    public Song(long id, String title, String artist, File file, String fileName) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.file = file;
        this.fileName = fileName;
    }

    public String getArtist() {
        return artist;
    }

    public String getTitle() {
        return title;
    }

    public long getID() {
        return id;
    }

    public File getFile() {
        return file;
    }

    public String getFileName() {
        return fileName;
    }
}
