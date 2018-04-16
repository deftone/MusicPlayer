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
    private String album;
    private File file;
    private String fileName;
    private String albumCover;
    private int songLength;

    public Song(long id, String title, String artist, String album, File file, String fileName, String albumCover, int songLength) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.file = file;
        this.fileName = fileName;
        this.albumCover = albumCover;
        this.songLength = songLength;
    }

    public String getArtist() {
        return artist;
    }

    public String getTitle() {
        return title;
    }

    public String getAlbum() {
        return album;
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

    public String getAlbumCover() {
        return albumCover;
    }

    public int getSongLength() {
        return songLength;
    }
}
