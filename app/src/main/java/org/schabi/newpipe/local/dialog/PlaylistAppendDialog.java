package org.schabi.newpipe.local.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.LocalItemListAdapter;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public final class PlaylistAppendDialog extends PlaylistDialog {
    private static final String TAG = PlaylistAppendDialog.class.getCanonicalName();

    private RecyclerView playlistRecyclerView;
    private LocalItemListAdapter playlistAdapter;

    private final CompositeDisposable playlistDisposables = new CompositeDisposable();

    /**
     * Create a new instance of {@link PlaylistAppendDialog}.
     *
     * @param streamEntities    a list of {@link StreamEntity} to be added to playlists
     * @return a new instance of {@link PlaylistAppendDialog}
     */
    public static PlaylistAppendDialog newInstance(final List<StreamEntity> streamEntities) {
        final PlaylistAppendDialog dialog = new PlaylistAppendDialog();
        dialog.setStreamEntities(streamEntities);
        return dialog;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle - Creation
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_playlists, container);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final LocalPlaylistManager playlistManager =
                new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()));

        playlistAdapter = new LocalItemListAdapter(getActivity());
        playlistAdapter.setSelectedListener(selectedItem -> {
            final List<StreamEntity> entities = getStreamEntities();
            if (selectedItem instanceof PlaylistDuplicatesEntry && entities != null) {
                onPlaylistSelected(playlistManager,
                        (PlaylistDuplicatesEntry) selectedItem, entities);
            }
        });

        playlistRecyclerView = view.findViewById(R.id.playlist_list);
        playlistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        playlistRecyclerView.setAdapter(playlistAdapter);

        final View newPlaylistButton = view.findViewById(R.id.newPlaylist);
        newPlaylistButton.setOnClickListener(ignored -> openCreatePlaylistDialog());

        playlistDisposables.add(playlistManager
                .getPlaylistDuplicates(getStreamEntities().get(0).getUrl())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onPlaylistsReceived));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle - Destruction
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        playlistDisposables.dispose();
        if (playlistAdapter != null) {
            playlistAdapter.unsetSelectedListener();
        }

        playlistDisposables.clear();
        playlistRecyclerView = null;
        playlistAdapter = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    //////////////////////////////////////////////////////////////////////////*/

    /** Display create playlist dialog. */
    public void openCreatePlaylistDialog() {
        if (getStreamEntities() == null || !isAdded()) {
            return;
        }

        final PlaylistCreationDialog playlistCreationDialog =
                PlaylistCreationDialog.newInstance(getStreamEntities());
        // Move the dismissListener to the new dialog.
        playlistCreationDialog.setOnDismissListener(this.getOnDismissListener());
        this.setOnDismissListener(null);

        playlistCreationDialog.show(getParentFragmentManager(), TAG);
        requireDialog().dismiss();
    }

    private void onPlaylistsReceived(@NonNull final List<PlaylistDuplicatesEntry> playlists) {
        if (playlistAdapter != null && playlistRecyclerView != null) {
            playlistAdapter.clearStreamItemList();
            playlistAdapter.addItems(playlists);
            playlistRecyclerView.setVisibility(View.VISIBLE);
            setDuplicateIndicatorExplanation(playlists);
        }
    }
    private void setDuplicateIndicatorExplanation(final List<PlaylistDuplicatesEntry> playlists) {
        for (final PlaylistDuplicatesEntry entry : playlists) {
            if (entry.timesStreamIsContained > 0) {
                final View indicatorExplanation = getView()
                        .findViewById(R.id.playlist_duplicate);
                indicatorExplanation.setVisibility(View.VISIBLE);
                return;
            }
        }

    }

    private void onPlaylistSelected(@NonNull final LocalPlaylistManager manager,
                                    @NonNull final PlaylistDuplicatesEntry playlist,
                                    @NonNull final List<StreamEntity> streams) {

        final String toastText;
        if (playlist.timesStreamIsContained > 0) {
            toastText = getString(R.string.playlist_add_stream_success_duplicate,
                    playlist.timesStreamIsContained);
        } else {
            toastText = getString(R.string.playlist_add_stream_success);
        }

        final Toast successToast = Toast.makeText(getContext(), toastText, Toast.LENGTH_SHORT);

        if (playlist.thumbnailUrl
                .equals("drawable://" + R.drawable.placeholder_thumbnail_playlist)) {
            playlistDisposables.add(manager
                    .changePlaylistThumbnail(playlist.uid, streams.get(0).getThumbnailUrl(), false)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(ignored -> successToast.show()));
        }

        playlistDisposables.add(manager.appendToPlaylist(playlist.uid, streams)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> successToast.show()));

        requireDialog().dismiss();
    }
}
