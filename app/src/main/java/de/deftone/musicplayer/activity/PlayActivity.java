package de.deftone.musicplayer.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.deftone.musicplayer.R;
import de.deftone.musicplayer.dialog.SpinnerDialog;
import de.deftone.musicplayer.model.Song;
import de.deftone.musicplayer.service.MusicService;

import static de.deftone.musicplayer.activity.MainActivity.INTENT_SONGLIST;
import static de.deftone.musicplayer.activity.MainActivity.INTENT_SONG_ID;
import static de.deftone.musicplayer.activity.MainActivity.NO_ALBUM_COVER;
import static de.deftone.musicplayer.service.MusicService.REPEAT_ALL;
import static de.deftone.musicplayer.service.MusicService.REPEAT_NONE;
import static de.deftone.musicplayer.service.MusicService.REPEAT_ONE;

/**
 * Created by deftone on 02.04.18.
 */

/**
 * equalizer
 * //http://isbellj008.blogspot.com/2014/05/android-how-to-use-android-equalizer.html
 */

public class PlayActivity extends AppCompatActivity {

    private List<Song> songList;
    private int songId;
    private Handler seekHandler = new Handler();
    private MusicService musicService;
    private Intent musicServiceIntent;
    private boolean musicBound = false;
    private int fromTime;
    private int toTime = 30_000;  //5 min
    private static boolean innerLoopMode = false;
    private int songDuration;
    private int displayWidth;
    private int paddingSeekbarStart = 110;
    private int paddingSeekbarEnd = 150; //warum ist das hier groesser???
    private Equalizer equalizer;
    private SpinnerDialog spinnerDialog;
    private BiMap<Short, String> equalizerMap = HashBiMap.create();
    private List<String> equalizerList;

    @BindView(R.id.play_pause_button)
    ImageButton playPauseButton;
    @BindView(R.id.repeatSong_button)
    ImageButton repeatSongButton;
    @BindView(R.id.shuffle_button)
    ImageButton shuffleButton;
    @BindView(R.id.play_previous_button)
    ImageButton prevButton;
    @BindView(R.id.play_next_button)
    ImageButton nexButton;
    @BindView(R.id.song_album)
    TextView albumTextView;
    @BindView(R.id.song_artist)
    TextView artistTextView;
    @BindView(R.id.song_title)
    TextView songTextView;
    @BindView(R.id.song_position)
    TextView positionTextView;
    @BindView(R.id.song_length)
    TextView songLengthTextView;
    @BindView(R.id.album_cover)
    ImageView albumCover;
    @BindView(R.id.song_position_range)
    TextView positionTextViewRange;
    @BindView(R.id.song_length_range)
    TextView songLengthTextViewRange;
    @BindView(R.id.range_seekbar)
    RelativeLayout rangeSeekbarLayout;
    @BindView(R.id.ball)
    ImageView ball;
    @BindView(R.id.thumbFrom)
    LinearLayout thumbFrom;
    @BindView(R.id.thumbTo)
    LinearLayout thumbTo;
    @BindView(R.id.textToTime)
    TextView textToTime;
    @BindView(R.id.textFromTime)
    TextView textFromTime;
    @BindView(R.id.seekbar)
    SeekBar seekBar;
    @BindView(R.id.seekbar_layout)
    LinearLayout seekbarLayout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);
        ButterKnife.bind(this);

        initLayout();

        ServiceConnection musicConnection = createMusicConnection();
        //Intent an MusicService "hallo ich bin da, gib mir deinen service", wenn er noch nicht da ist, verbinde mich damit
        if (musicServiceIntent == null) {
            musicServiceIntent = new Intent(this, MusicService.class);
            bindService(musicServiceIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(musicServiceIntent);
        }

        //add thread to update seekbar (zyklische hintergrundprozesse sollten immer im on create starten )
        PlayActivity.this.runOnUiThread(new SeekbarRunnable());
        //add listener to seekbar
        seekBar.setOnSeekBarChangeListener(new SeekbarChangeListener());

    }

    /**
     * create connection for music service
     **/
    private ServiceConnection createMusicConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
                //get service
                musicService = binder.getService();
                //pass list
                musicService.setList(songList);
                musicService.setSongPosnInList(songId);
                musicService.setSongTextView(songTextView);
                musicService.setArtistTextView(artistTextView);
                musicService.setPositionTextView(positionTextView);
                musicService.setPositionTextView(positionTextViewRange);
                musicService.setSongLengthTextView(songLengthTextView);
                musicService.setSongLengthTextView(songLengthTextViewRange);
                musicService.setAlbumCoverImageView(albumCover);
                musicBound = true;
                //song direkt abspielen
                playPauseButton.setImageResource(R.drawable.icon_pause);
                musicService.playSong(songId, true);

                equalizer = new Equalizer(0, musicService.getPlayer().getAudioSessionId());
                equalizer.setEnabled(true);
                equalizer.getNumberOfBands(); //it tells you the number of equalizer in device.
                equalizer.getNumberOfPresets();
                int noPresets = equalizer.getNumberOfPresets();
                for (short presetValue = 0; presetValue < noPresets; presetValue++) {
                    equalizerMap.put(presetValue, equalizer.getPresetName(presetValue));
                }
                Stream<String> stream = equalizerMap.values().stream();
                equalizerList = stream.collect(Collectors.toList());
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                musicBound = false;
            }
        };
    }

    /**
     * init and update methods
     **/

    private void initLayout() {
        //don't show range seekbar
        rangeSeekbarLayout.setVisibility(View.GONE);

        songList = new ArrayList<>((ArrayList<Song>) getIntent().getSerializableExtra(INTENT_SONGLIST));
        songId = getIntent().getIntExtra(INTENT_SONG_ID, 1);

        //init album text and  cover (rest is done in musicService
        String album = songList.get(songId).getAlbum();
        if (album.length() > 45)
            album = album.substring(0, 40) + " ...";
        albumTextView.setText(album);
        songLengthTextView.setText(MusicService.convertMilliSecondsToMMSS(songList.get(songId).getSongLength()));
        songDuration = songList.get(songId).getSongLength();

        Bitmap albumCoverBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.default_cover);
        if (!songList.get(songId).getAlbumCover().equals(NO_ALBUM_COVER))
            albumCoverBitmap = BitmapFactory.decodeFile(songList.get(songId).getAlbumCover());
        albumCover.setImageBitmap(albumCoverBitmap);
    }

    private void updateLayoutInnerLoop() {
        int visibility = innerLoopMode ? View.GONE : View.VISIBLE;
        repeatSongButton.setVisibility(visibility);
        shuffleButton.setVisibility(visibility);
        nexButton.setVisibility(visibility);
        prevButton.setVisibility(visibility);
        seekbarLayout.setVisibility(visibility);
        visibility = innerLoopMode ? View.VISIBLE : View.GONE;
        rangeSeekbarLayout.setVisibility(visibility);
    }

    /**
     * eigenen range seekbar initialisieren
     **/
    private void initRangeSeekbar() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        displayWidth = dm.widthPixels;
        //put thumbs to 1/3 and 2/3 of width
        fromTime = getPositionInMilliSec(displayWidth / 3);
        thumbFrom.setX(displayWidth / 3);
        toTime = getPositionInMilliSec(displayWidth * 2 / 3);
        thumbTo.setX(displayWidth * 2 / 3);
        //ball am anfang der seekbar
        ball.setX(paddingSeekbarStart);
        //ball am ende der seekbar
//        ball.setX(displayWidth - paddingSeekbarEnd);

        //also init inner loop seekbar:
        textFromTime.setText(getDisplayTime(thumbFrom.getX()));
        textToTime.setText(getDisplayTime(thumbTo.getX()));

        final PointF startPointFrom = new PointF(thumbFrom.getX(), thumbFrom.getY()); // Record Start Position of thumbFrom
        final PointF startPointTo = new PointF(thumbTo.getX(), thumbTo.getY()); // Record Start Position of thumbTo

        thumbFrom.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                float newX = startPointFrom.x + motionEvent.getX();
                if (newX >= paddingSeekbarStart && newX < (thumbTo.getX() - 20)) {
                    thumbFrom.setX(newX);
                    startPointFrom.set(thumbFrom.getX(), thumbFrom.getY());
                    textFromTime.setText(getDisplayTime(thumbFrom.getX()));
                    fromTime = getPositionInMilliSec(thumbFrom.getX());
                }
                return true;
            }
        });


        thumbTo.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_MOVE:
                        float newX = startPointTo.x + motionEvent.getX();
                        if (newX <= (displayWidth - paddingSeekbarEnd) && newX > (thumbFrom.getX() + 20)) {
                            thumbTo.setX(newX);
                            startPointTo.set(thumbTo.getX(), thumbTo.getY());
                            textToTime.setText(getDisplayTime(thumbTo.getX()));
                            toTime = getPositionInMilliSec(thumbTo.getX());
                        }
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
    }

    /**
     * util methods
     **/

    private String getDisplayTime(float x) {
        return MusicService.convertMilliSecondsToMMSS(getPositionInMilliSec(x));
    }

    private int getPositionInMilliSec(float x) {
        return (int) (
                (x - paddingSeekbarStart)
                        /
                        (displayWidth - (paddingSeekbarStart + paddingSeekbarEnd))
                        * songDuration);
    }

    private float getPostionFromTime() {
        return (getCurrentPosition())
                *
                ((displayWidth - (paddingSeekbarStart + paddingSeekbarEnd))
                        / (float) songDuration)
                + paddingSeekbarStart;
    }

    //aktuelle Stelle des Songs aus musicService (fuer seekbar)
    public int getCurrentPosition() {
        if (musicBound && musicService.isPlaying()) {
            return musicService.getCurrentPositionInSong();
        } else {
            return 0;
        }
    }

    public boolean isPlaying() {
        return musicService != null && musicBound && musicService.isPlaying();
    }

    /**
     * android overwrites (menu, start, destroy, back)
     **/

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_play, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.inner_loop:
                innerLoopMode = !innerLoopMode;
                updateLayoutInnerLoop();
                initRangeSeekbar();
                if (isPlaying()) {
                    playPauseButton.setImageResource(R.drawable.icon_play);
                    musicService.pausePlayer();
                }
                return true;
            case R.id.equalizer:
                if (spinnerDialog == null)
                    spinnerDialog = new SpinnerDialog(this, equalizerList, new SpinnerDialog.DialogListener() {
                        public void cancelled() {
                            // do nothing
                        }

                        public void clickOk(int index) {
                            String eqName = equalizerList.get(index);
                            short eqBand = equalizerMap.inverse().get(eqName);
                            equalizer.usePreset(eqBand);
                        }
                    });
                spinnerDialog.show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (isPlaying())
            playPauseButton.setImageResource(R.drawable.icon_pause);
        else
            playPauseButton.setImageResource(R.drawable.icon_play);
    }

    @Override
    protected void onDestroy() {
        stopService(musicServiceIntent);
        musicService = null;
        musicBound = false;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (innerLoopMode) {
            innerLoopMode = false;
            updateLayoutInnerLoop();
        } else {
            super.onBackPressed();
        }
    }


    /**
     * buttons
     **/

    @OnClick(R.id.play_next_button)
    void onPlayNextButton() {
        String songtitle = musicService.playNext(true);
        songTextView.setText(songtitle);
        //no matter what is was before, player ist im zustand playing
        playPauseButton.setImageResource(R.drawable.icon_pause);
    }

    @OnClick(R.id.play_previous_button)
    void onPlayPrevButton() {
        String songtitle = musicService.playPrev(true);
        songTextView.setText(songtitle);
        //no matter what is was before, player ist im zustand playing
        playPauseButton.setImageResource(R.drawable.icon_pause);
    }

    @OnClick(R.id.play_pause_button)
    void onPlayPauseButton() {
        if (isPlaying()) {
            //playback hat gespielt, d.h. jetzt pausieren
            playPauseButton.setImageResource(R.drawable.icon_play);
            musicService.pausePlayer();
        } else {
            //playback war pausiert, d.h. jetzt wieder abspielen
            playPauseButton.setImageResource(R.drawable.icon_pause);
            musicService.playSong(songId, true);
            if (innerLoopMode) {
                musicService.setToFrom(fromTime);
            }
        }
    }

    @OnClick(R.id.shuffle_button)
    void onShuffleButton() {
        if (musicService.setShuffle()) {
            shuffleButton.setImageResource(R.drawable.icon_shuffle);
        } else {
            shuffleButton.setImageResource(R.drawable.icon_inorder);
        }
    }

    @OnClick(R.id.repeatSong_button)
    void onRepeatSongButton() {
        int stateRepeat = musicService.setRepeatSong();
        switch (stateRepeat) {
            case REPEAT_NONE:
                repeatSongButton.setImageResource(R.drawable.icon_repeat_none);
                break;
            case REPEAT_ONE:
                repeatSongButton.setImageResource(R.drawable.icon_repeat_one);
                break;
            case REPEAT_ALL:
                repeatSongButton.setImageResource(R.drawable.icon_repeat_all);
                break;
        }
    }

    //innere KLasse fuer das runable zum seekbar aktualisieren
    private class SeekbarRunnable implements Runnable {

        @Override
        public void run() {
            if (musicBound && musicService.isPlaying()) {
                if (!innerLoopMode) {
                    //wenn playback paused ist, dann soll seekbar nicht aktualisiert werden
                    seekBar.setMax(songDuration);
                    seekBar.setProgress(getCurrentPosition());
                    positionTextView.setText(musicService.getSongPosnAnzeige());
                }
                //eine stelle im lied unendlich loopen lassen:
                else {
                    if (getCurrentPosition() >= toTime) {
                        musicService.setToFrom(fromTime);
                    } else {
                        ball.setX(getPostionFromTime());
                        positionTextViewRange.setText(musicService.getSongPosnAnzeige());
                    }
                }
            }
            seekHandler.postDelayed(this, 500);
        }
    }


    //innere Klasse fuer den seekbarChangeListener, da der musicService sonst null ist
    private class SeekbarChangeListener implements SeekBar.OnSeekBarChangeListener {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        //diese methode wird immer dann ausgefuehrt, wenn finger auf seekbar spielt
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            //nur playing evtl mal rausnehmen
            if (musicService != null && musicService.isPlaying() && fromUser) {
                musicService.seek(progress);
            }
        }
    }
}
