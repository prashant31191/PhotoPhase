/*
 * Copyright (C) 2013 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cyanogenmod.wallpapers.photophase.preferences;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.cyanogenmod.wallpapers.photophase.R;
import org.cyanogenmod.wallpapers.photophase.animations.AlbumsFlip3dAnimationController;
import org.cyanogenmod.wallpapers.photophase.model.Album;
import org.cyanogenmod.wallpapers.photophase.widgets.AlbumInfo;
import org.cyanogenmod.wallpapers.photophase.widgets.AlbumPictures;
import org.cyanogenmod.wallpapers.photophase.widgets.CardLayout;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A fragment class for select the picture that will be displayed on the wallpaper
 */
public class ChoosePicturesFragment extends PreferenceFragment {

    private final AsyncTask<Void, Album, Void> mAlbumsLoaderTask = new AsyncTask<Void, Album, Void>() {
        /**
         * {@inheritDoc}
         */
        @Override
        protected Void doInBackground(Void... params) {
            // Query all the external content and classify the pictures in albums and load the cards
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            Album album = null;
            mAlbums.clear();
            Cursor c = mContentResolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            new String[]{ MediaStore.MediaColumns.DATA },
                            null,
                            null,
                            MediaStore.MediaColumns.DATA);
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        // Only valid files (those i can read)
                        String p = c.getString(0);
                        if (p != null) {
                            File f = new File(p);
                            if (f.exists() && f.isFile() && f.canRead()) {
                                  File path = f.getParentFile();
                                  String name = path.getName();
                                  if (album == null || album.getPath().compareTo(path.getAbsolutePath()) != 0) {
                                      if (album != null) {
                                          mAlbums.add(album);
                                          this.publishProgress(album);
                                          try {
                                              Thread.sleep(50L);
                                          } catch (InterruptedException e) {
                                              // Ignore
                                          }
                                      }
                                      album = new Album();
                                      album.setPath(path.getAbsolutePath());
                                      album.setName(name);
                                      album.setDate(df.format(new Date(path.lastModified())));
                                      album.setSelected(isSelectedItem(album.getPath()));
                                      album.setItems(new ArrayList<String>());
                                      album.setSelectedItems(new ArrayList<String>());
                                  }
                                  album.getItems().add(f.getAbsolutePath());
                                  if (isSelectedItem(f.getAbsolutePath())) {
                                      album.getSelectedItems().add(f.getAbsolutePath());
                                  }
                            }
                        }
                    }
                } finally {
                    c.close();
                }
            }

            return null;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void onProgressUpdate(Album... values) {
            for (Album album : values) {
                addAlbum(album);
            }
        }
    };



    List<Album> mAlbums;
    ContentResolver mContentResolver;

    private CardLayout mAlbumsPanel;

    /*package*/ Set<String> mSelectedAlbums;

    /*package*/ boolean mSelectionChanged;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContentResolver = getActivity().getContentResolver();

        // Create an empty album
        mAlbums = new ArrayList<Album>();

        // Change the preference manager
        getPreferenceManager().setSharedPreferencesName(PreferencesProvider.PREFERENCES_FILE);
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_PRIVATE);

        // Load the albums user selection
        mSelectedAlbums = PreferencesProvider.Preferences.Media.getSelectedAlbums();
        mSelectionChanged = false;

        setHasOptionsMenu(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAlbumsLoaderTask.getStatus().compareTo(Status.PENDING) == 0) {
            mAlbumsLoaderTask.cancel(true);
        }
        unbindDrawables(mAlbumsPanel);

        // Notify that the settings was changed
        Intent intent = new Intent(PreferencesProvider.ACTION_SETTINGS_CHANGED);
        if (mSelectionChanged) {
            intent.putExtra(PreferencesProvider.EXTRA_FLAG_MEDIA_RELOAD, Boolean.TRUE);
        }
        getActivity().sendBroadcast(intent);
    }

    /**
     * Method that unbind all the drawables for a view
     *
     * @param view The root view
     */
    private void unbindDrawables(View view) {
        if (view.getBackground() != null) {
            view.getBackground().setCallback(null);
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                unbindDrawables(((ViewGroup) view).getChildAt(i));
            }
            ((ViewGroup) view).removeAllViews();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.choose_picture_fragment, container, false);
        mAlbumsPanel = (CardLayout) v.findViewById(R.id.albums_panel);

        // Load the albums
        mAlbumsLoaderTask.execute();

        return v;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.albums, menu);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.mnu_ok:
                getActivity().finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Method that adds a new album to the card layout
     *
     * @param album The album to add
     */
    void addAlbum(Album album) {
        LayoutInflater li = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View albumView = li.inflate(R.layout.album, mAlbumsPanel, false);
        final AlbumInfo albumInfo = (AlbumInfo)albumView.findViewById(R.id.album_info);
        final AlbumPictures albumPictures = (AlbumPictures)albumView.findViewById(R.id.album_pictures);

        // Load the album info
        albumInfo.updateView(album);
        if (album.isSelected()) {
            albumInfo.setSelected(true);
        }
        albumInfo.addCallBackListener(new AlbumInfo.CallbacksListener() {
            @Override
            public void onAlbumSelected(Album ref) {
                // Remove all pictures of the album and add the album reference
                removeAlbumItems(ref);
                mSelectedAlbums.add(ref.getPath());
                ref.setSelected(true);
                albumPictures.updateView(ref);

                PreferencesProvider.Preferences.Media.setSelectedAlbums(getActivity(), mSelectedAlbums);
                mSelectionChanged = true;
            }

            @Override
            public void onAlbumDeselected(Album ref) {
                // Remove all pictures of the album
                removeAlbumItems(ref);
                ref.setSelected(false);
                albumPictures.updateView(ref);

                PreferencesProvider.Preferences.Media.setSelectedAlbums(getActivity(), mSelectedAlbums);
                mSelectionChanged = true;
            }


        });

        // Load the album picture data
        albumPictures.updateView(album);
        albumPictures.addCallBackListener(new AlbumPictures.CallbacksListener() {
            @Override
            public void onBackButtonClick(View v) {
                // Ignored
            }

            @Override
            public void onSelectionChanged(Album ref) {
                // Remove, add, and persist the selection
                removeAlbumItems(ref);
                mSelectedAlbums.addAll(ref.getSelectedItems());
                ref.setSelected(false);
                albumInfo.updateView(ref);

                PreferencesProvider.Preferences.Media.setSelectedAlbums(getActivity(), mSelectedAlbums);
                mSelectionChanged = true;
            }
        });

        // Register the animation controller
        AlbumsFlip3dAnimationController controller = new AlbumsFlip3dAnimationController(albumInfo, albumPictures);
        controller.register();

        // Add to the panel of cards
        mAlbumsPanel.addCard(albumView);
    }

    /**
     * Method that checks if an item is selected
     *
     * @param item The item
     * @return boolean if an item is selected
     */
    /*package*/ boolean isSelectedItem(String item) {
        Iterator<String> it = mSelectedAlbums.iterator();
        while (it.hasNext()) {
            String albumPath = it.next();
            if (item.compareTo(albumPath) == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method that removes the reference to all the items and itself
     *
     * @param ref The album
     */
    /*package*/ void removeAlbumItems(Album ref) {
        Iterator<String> it = mSelectedAlbums.iterator();
        while (it.hasNext()) {
            String item = it.next();
            String parent = new File(item).getParent();
            if (parent.compareTo(ref.getPath()) == 0) {
                it.remove();
            } else if (item.compareTo(ref.getPath()) == 0) {
                it.remove();
            }
        }
    }
}
