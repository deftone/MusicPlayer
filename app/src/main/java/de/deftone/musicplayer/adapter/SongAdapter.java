package de.deftone.musicplayer.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import de.deftone.musicplayer.R;
import de.deftone.musicplayer.model.Song;

/**
 * Created by deftone on 02.04.18.
 */

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.ViewHolder> {

    private List<Song> songs;
    private Listener listener;

    public interface Listener {
        void onClick(int position);
    }

    public SongAdapter(List<Song> theSongs) {
        songs = theSongs;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        CardView cv = (CardView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.card_view_song, parent, false);
        return new ViewHolder(cv);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {
        CardView cardView = holder.cardView;
// das bringt gar nichts, weil ja erst wirklich die songs wissen wie ihr album aussieht, aber nicht die drueber geordneten ordner...
//        ImageView imageView = cardView.findViewById(R.id.card_view_cover);
//        imageView.setImageBitmap(songs.get(position).getAlbumCover());
        TextView textViewArtist = cardView.findViewById(R.id.card_view_artist);
        TextView textViewTitle = cardView.findViewById(R.id.card_view_song);
        textViewTitle.setText(songs.get(position).getTitle());
        textViewArtist.setText(songs.get(position).getArtist());
        cardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null) {
                    listener.onClick(position);
                }
            }
        });
    }

    @Override
    public long getItemId(int arg0) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getItemCount() {
        return songs.size();
    }


    public static class ViewHolder extends RecyclerView.ViewHolder {

        private CardView cardView;

        public ViewHolder(CardView cardView) {
            super(cardView);
            this.cardView = cardView;
        }
    }

}
