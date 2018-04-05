package de.deftone.musicplayer.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.Random;

import de.deftone.musicplayer.R;
import de.deftone.musicplayer.activity.PlayActivity;
import de.deftone.musicplayer.model.Song;

import static de.deftone.musicplayer.activity.MainActivity.NO_ALBUM_COVER;

/**
 * Created by deftone on 12.05.17.
 */


public class MusicService extends IntentService implements MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {

    private static final int NOTIFY_ID = 1;

    private MediaPlayer player;
    private final IBinder musicBind = new MusicBinder();
    private List<Song> songs;
    private int songPosnInList = 0;
    private String songTitle = "";
    private Random rand;
    private boolean shuffle = false;
    private boolean repeat = false;

    //todo: das muss hier raus!!
    private TextView songTextView;
    private TextView positionTextView;
    private ImageView albumCoverImageView;

    public MusicService() {
        super("Heinz");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        player = new MediaPlayer();
        rand = new Random();
        initMusicPlayer();
    }

    public void initMusicPlayer() {
        //set player properties
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
    }


    /** Eigenschaften des Players, die die PlayActivity setzt - das kann evtl besser gehandhabt werden? **/
    public void setList(List<Song> theSongs) {
        songs = theSongs;
    }

    public void setSongTextView(TextView songTextView) {
        this.songTextView = songTextView;
    }

    public void setPositionTextView(TextView positionTextView) {
        this.positionTextView = positionTextView;
    }

    public void setAlbumCoverImageView(ImageView albumCoverImageView) {
        this.albumCoverImageView = albumCoverImageView;
    }

    public boolean setShuffle() {
        if (shuffle) shuffle = false;
        else shuffle = true;
        return shuffle;
    }

    public boolean setRepeatSong() {
        if (repeat) repeat = false;
        else repeat = true;
        return repeat;
    }


    /** Eigenschaften des Players, die die PlayActivity benoetigt **/
    public int getCurrentPositionInSong() {
        return player.getCurrentPosition();
    }

    public String getSongPosnAnzeige() {
        if (isPng()) {
            int time = player.getCurrentPosition();
            //aktuelle Position in Sekunden
            int secAbsolute = time / 1000;
            //minuten und sekunden berechnen
            int min = secAbsolute / 60;
            int sec = secAbsolute % 60;
            if (sec < 10)
                return min + ":0" + sec;
            else
                return min + ":" + sec;
        } else return "0:00";
    }

    public int getDur() {
        return player.getDuration();
    }

    public boolean isPng() {
        return player.isPlaying();
    }

    public void seek(int posn) {
        player.seekTo(posn);
    }


    /** play, pause, next, prev funktionalitaet fuer den player, wird in PlayActivity aufgerufen **/

    public String playPrev(boolean updateViewComponents) {
        songPosnInList--;
        //spielt immer wieder das erste lied, wenn prev beim ersten lied angekommen ist
        //bevor die lieder starten stehen alle ordner (../ und evtl auch noch mehr)
        //daher nur pruefen ob bei prev ein ordner angetroffen wird, falls ja, dann -- wieder rueckgaengig
        if (songs.get(songPosnInList).getID() == -1)
            songPosnInList++;
        songTitle = playSong(songPosnInList, updateViewComponents);
        return songTitle;
    }

    public String playNext(boolean updateViewComponents) {
        if (shuffle) {
            int newSong = songPosnInList;
            while (newSong == songPosnInList) {
                newSong = rand.nextInt(songs.size());
                //wenn newSong ein Ordner ist, hochzahlen bis kein Ordner mehr
                while (songs.get(newSong).getID() == -1)
                    newSong++;
            }
            songPosnInList = newSong;
        } else if (repeat) {
            //hier ist songPosnInList unveraendert, damit der Song nocheinmal gespielt wird
        } else {
            songPosnInList++;
            //beim letzten lied angekommen wird wieder das letzte lied gestartet
            //voellig egal wieviele ordner am anfang der liste sind, einfach das ++ rueckgaengig machen
            if (songPosnInList >= songs.size() - 1) {
                player.stop();
                return "";
            }
        }
        songTitle = playSong(songPosnInList, updateViewComponents);
        return songTitle;
    }

    public String playSong(int songId, boolean updateViewComponentes) {
        songPosnInList = songId;

        //gui darf nur im app modus angepasst werden, wenn im Screensaver oder androidmenu der next/prev button gedrueckt wird, darf die gui nicht angepasst werden
        if (updateViewComponentes) {
            songTextView.setText(songs.get(songId).getTitle());
            positionTextView.setText(getSongPosnAnzeige());
            String albumCover = songs.get(songId).getAlbumCover();
            if (albumCover.equals(NO_ALBUM_COVER)) {
                //todo show default
            } else {
                Bitmap bm = BitmapFactory.decodeFile(albumCover);
                albumCoverImageView.setImageBitmap(bm);
            }
        }

        player.reset();

        //wir brauchen den songtitel fuer den screenlock bzw. das androidmenu
        songTitle = songs.get(songId).getTitle();

        //set uri and start player
        Uri trackUri = ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                songs.get(songId).getID());
        try {
            player.setDataSource(getApplicationContext(), trackUri);
            player.prepare();
            player.start();
            buildNotification();
        } catch (Exception e) {
            Log.e("MUSIC SERVICE", "Error setting data source", e);
        }
        return songTitle;
    }

    public void pausePlayer(){
        player.pause();
    }

    //wenn lied vorbei ist
    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        if (player.getCurrentPosition() > 0) {
            mediaPlayer.reset();
            playNext(true);
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mp.reset();
        return false;
    }

    /**
     * damit der Player auch ausm Screenlock bedient werden kann das funktioniert nicht mehr :((((
     **/
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        String action = intent != null ? intent.getAction() : "";
        if (action != null) {
            switch (action) {
                //bei Pause und Play muss die Notification neu gebaut werden
                //bei Next und Previous darf die Oberflaeche nicht angepasst werden
                case "Pause":
                    player.pause();
                    buildNotification();
                    break;
                case "Play":
                    player.start();
                    buildNotification();
                    break;
                case "Previous":
                    playPrev(false);
                    break;
                case "Next":
                    playNext(false);
                    break;
            }
        }
    }

    //diese Notification wird im Screenslock angezeigt
    private void buildNotification() {
        String playPause;
        int iconId;
        if (player.isPlaying()) {
            playPause = "Pause";
            iconId = R.drawable.ic_pause_black_24dp;
        } else {
            playPause = "Play";
            iconId = R.drawable.ic_play_circle_outline_black_24dp;
        }

        Intent notIntent = new Intent(this, PlayActivity.class);
        notIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendInt = PendingIntent.getActivity(this, 0,
                notIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        //playPause mit variabeln damit die Anzeige entsprechend angepasst werden kann
        Intent playPauseIntent = new Intent(this, MusicService.class);
        playPauseIntent.setAction(playPause);
        PendingIntent playPausePendingIntent = PendingIntent.getService(this, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent previousIntent = new Intent(this, MusicService.class);
        previousIntent.setAction("Previous");
        PendingIntent previousPendingIntent = PendingIntent.getService(this, 0, previousIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        //hier dann die neue funktion ohne oberflaecheanpassen aufrufen
        Intent nextIntent = new Intent(this, MusicService.class);
        nextIntent.setAction("Next");
        PendingIntent nextPendingIntent = PendingIntent.getService(this, 0, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentIntent(pendInt)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSmallIcon(R.drawable.android_music_player_play)
                .addAction(R.drawable.ic_skip_previous_black_24dp, "Previous", previousPendingIntent)
                //playPause muss vorher entsprechend gesetzt werden
                .addAction(iconId, playPause, playPausePendingIntent)
                .addAction(R.drawable.ic_skip_next_black_24dp, "Next", nextPendingIntent)
                .setStyle(new NotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1, 2))
                .setTicker(songTitle)
                .setOngoing(true)
                .setContentTitle("Playing")
                .setContentText(songTitle)
                .build();

        startForeground(NOTIFY_ID, notification);
    }


    //Binder... innere Klasse... fuer...?

    public class MusicBinder extends Binder {
        public MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        player.stop();
        player.release();
        return false;
    }

}
