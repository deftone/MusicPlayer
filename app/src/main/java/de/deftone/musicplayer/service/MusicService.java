package de.deftone.musicplayer.service;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
//v4 ist die Superklasse, v7 ist eine Unterklasse, die aber ab API 26 deprecated ist, daher jetzt wieder v4 nehmen!
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.media.session.MediaSessionCompat;
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

    private static final String CHANNEL_ID = "chanelId";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_PLAY = "Play";
    private static final String ACTION_PAUSE = "Pause";
    private static final String ACTION_PREV = "Previous";
    private static final String ACTION_NEXT = "Next";

    private MediaPlayer player;
    private final IBinder musicBind = new MusicBinder();
    private NotificationManager notificationManager;
    private List<Song> songs;
    private int songPosnInList;
    private String songTitle = "";
    private Song songPlaying;
    private Random rand;
    private boolean shuffle = false;
    private boolean repeat = false;
    private boolean isPausing = false;
    //todo: das geht evtl besser, s.u.
    private TextView songTextView;
    private TextView artistTextView;
    private TextView positionTextView;
    private TextView songLengthTextView;
    private ImageView albumCoverImageView;
    private Bitmap albumCoverBitmap;

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


    /**
     * Eigenschaften des Players, die die PlayActivity setzt - das kann evtl besser gehandhabt werden?
     **/
    public void setSongPosnInList(int id) {
        this.songPosnInList = id;
    }

    public void setList(List<Song> theSongs) {
        this.songs = theSongs;
    }

    public void setSongTextView(TextView songTextView) {
        this.songTextView = songTextView;
    }

    public void setArtistTextView(TextView artistTextView) {
        this.artistTextView = artistTextView;
    }

    public void setPositionTextView(TextView positionTextView) {
        this.positionTextView = positionTextView;
    }

    public void setSongLengthTextView(TextView songLengthTextView) {
        this.songLengthTextView = songLengthTextView;
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


    /**
     * Eigenschaften des Players, die die PlayActivity benoetigt
     **/
    public int getCurrentPositionInSong() {
        return player.getCurrentPosition();
    }

    public String getSongPosnAnzeige() {
        if (isPlaying()) {
            return convertMilliSecondsToMMSS(player.getCurrentPosition());
        } else return "0:00";
    }

    public static String convertMilliSecondsToMMSS(int time) {
        //aktuelle Position in Sekunden
        int secAbsolute = time / 1000;
        //minuten und sekunden berechnen
        int min = secAbsolute / 60;
        int sec = secAbsolute % 60;
        if (sec < 10)
            return min + ":0" + sec;
        else
            return min + ":" + sec;
    }

    public int getDur() {
        return player.getDuration();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public void seek(int posn) {
        player.seekTo(posn);
    }


    /**
     * play, pause, next, prev funktionalitaet fuer den player, wird in PlayActivity aufgerufen
     **/

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
        if (isPausing) {
            //wenn vorher pause war und wieder auf play gedrueckt wird muss keine gui angepasst werden
            //und der player nur gestartet werden
            isPausing = false;
            player.start();
        } else {
            //wenn es das erste mal ist (weil aus liste ausgewaehlt oder next/prev, dann ist der ganze kram hier noetig:
            songPosnInList = songId;
            songPlaying = songs.get(songId);
            //wir brauchen den songtitel fuer den screenlock bzw. das androidmenu
            songTitle = songs.get(songId).getTitle();
            //gui darf nur im app modus angepasst werden, wenn im Screensaver oder androidmenu der next/prev button gedrueckt wird, darf die gui nicht angepasst werden
            if (updateViewComponentes) {
                artistTextView.setText(songs.get(songId).getArtist());
                songTextView.setText(songs.get(songId).getTitle());
                positionTextView.setText(getSongPosnAnzeige());
                songLengthTextView.setText(convertMilliSecondsToMMSS(songs.get(songId).getSongLength()));
                albumCoverBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_cover);
                if (!songs.get(songId).getAlbumCover().equals(NO_ALBUM_COVER))
                    albumCoverBitmap = BitmapFactory.decodeFile(songs.get(songId).getAlbumCover());
                albumCoverImageView.setImageBitmap(albumCoverBitmap);
            }
            //set uri and start player
            Uri trackUri = ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    songs.get(songId).getID());
            try {
                player.reset();
                player.setDataSource(getApplicationContext(), trackUri);
                player.prepare();
                player.start();
                buildNotification();
            } catch (Exception e) {
                Log.e("MUSIC SERVICE", "Error setting data source", e);
            }
        }
        return songTitle;
    }

    public void pausePlayer() {
        player.pause();
        isPausing = true;
        buildNotification();
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
     * damit der Player auch ausm Screenlock bedient werden kann
     * <p>
     * https://developer.android.com/training/notify-user/build-notification.html
     * <p>
     * achtung: man muss evtl in die einstellungen und die notifiications anschalten!
     * <p>
     * buttons sind nur bei importancd high in der notifiaction aber nicht im screenlock :(
     **/
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        String action = intent != null ? intent.getAction() : "";
        if (action != null) {
            switch (action) {
                //bei Pause und Play muss die Notification neu gebaut werden
                //bei Next und Previous darf die Oberflaeche nicht angepasst werden
                case ACTION_PAUSE:
                    player.pause();
                    buildNotification();
                    break;
                case ACTION_PLAY:
                    player.start();
                    buildNotification();
                    break;
                case ACTION_PREV:
                    playPrev(false);
                    break;
                case ACTION_NEXT:
                    playNext(false);
                    break;
            }
        }
    }

    //diese Notification wird im Screenslock angezeigt
    private void buildNotification() {
        String actionPlayPause;
        int titleIconId, actionIconId;
        if (isPlaying()) {
            actionPlayPause = ACTION_PAUSE;
            actionIconId = R.drawable.ic_pause_black_24dp;
            titleIconId = R.drawable.ic_play_circle_outline_black_24dp;
        } else {
            actionPlayPause = ACTION_PLAY;
            actionIconId = R.drawable.ic_play_circle_outline_black_24dp;
            titleIconId = R.drawable.ic_pause_black_24dp;
        }

        Intent notificationIntent = new Intent(this, PlayActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendInt = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        //playPause mit variabeln damit die Anzeige entsprechend angepasst werden kann
        Intent playPauseIntent = new Intent(this, MusicService.class);
        playPauseIntent.setAction(actionPlayPause);
        PendingIntent playPausePendingIntent = PendingIntent.getService(this, 0, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent previousIntent = new Intent(this, MusicService.class);
        previousIntent.setAction(ACTION_PREV);
        PendingIntent previousPendingIntent = PendingIntent.getService(this, 0, previousIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent nextIntent = new Intent(this, MusicService.class);
        nextIntent.setAction(ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getService(this, 0, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        MediaSessionCompat mediaSession = new MediaSessionCompat(getApplicationContext(), "session tag");
        MediaSessionCompat.Token token = mediaSession.getSessionToken();

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID);
        notificationBuilder
                .setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(token)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .setSmallIcon(titleIconId)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendInt)
                .setContentTitle(songPlaying.getTitle())
                .setContentText(songPlaying.getArtist())
                .setLargeIcon(albumCoverBitmap)
//                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // Add media control buttons that invoke intents in your media service
                .addAction(R.drawable.ic_skip_previous_black_24dp, ACTION_PREV, previousPendingIntent)  // #0
                .addAction(actionIconId, actionPlayPause, playPausePendingIntent)                       // #1
                .addAction(R.drawable.ic_skip_next_black_24dp, ACTION_NEXT, nextPendingIntent);        // #2


//        Before you can deliver the notification on Android 8.0 and higher,
//        you must register your app's notification channel with the system
//        by passing an instance of NotificationChannel to createNotificationChannel().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            //das hier wird in den einstellungen angezeigt:
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            //importance gibt an ob es mit oder ohne ton ist (default macht mit ton), low macht ohne ton
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(description);
            // Register the channel with the system
            getManager().createNotificationChannel(channel);
        }

        /**
         IMPORTANCE_MAX: unused
         IMPORTANCE_HIGH: shows everywhere, makes noise and peeks
         IMPORTANCE_DEFAULT: shows everywhere, makes noise, but does not visually intrude
         IMPORTANCE_LOW: shows everywhere, but is not intrusive  <-- das ist was wir wollen
         IMPORTANCE_MIN: only shows in the shade, below the fold
         IMPORTANCE_NONE: a notification with no importance; does not show in the shade
         */

        //show notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private NotificationManager getManager() {
        if (notificationManager == null) {
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
        return notificationManager;
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