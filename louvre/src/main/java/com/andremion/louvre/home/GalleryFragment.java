/*
 * Copyright (c) 2017. André Mion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andremion.louvre.home;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.SharedElementCallback;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.transition.Transition;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import android.widget.TextView;
import com.andremion.louvre.R;
import com.andremion.louvre.data.MediaLoader;
import com.andremion.louvre.preview.PreviewActivity;
import com.andremion.louvre.util.ItemOffsetDecoration;
import com.andremion.louvre.util.transition.MediaSharedElementCallback;
import com.andremion.louvre.util.transition.TransitionCallback;

import java.util.ArrayList;
import java.util.List;

public class GalleryFragment extends Fragment implements MediaLoader.Callbacks, GalleryAdapter.Callbacks {

    public interface Callbacks {

        void onBucketClick( long bucketid, String label);

        void onMediaClick(@NonNull View imageView, View checkView, long bucketId, int position);

        void onSelectionUpdated(int count);

        void onMaxSelectionReached();

        void onWillExceedMaxSelection();
    }

    public static final String EXTRA_VIEW_TYPE = "extra_view_type";
    public static final String EXTRA_BUCKET_ID = "extra_bucket_id";
    public static final String EXTRA_BUCKET_NAME = "extra_bucket_name";

    private final MediaLoader mMediaLoader;
    private final GalleryAdapter mAdapter;
    private View mEmptyView;
    private GridLayoutManager mLayoutManager;
    private RecyclerView mRecyclerView;
    private Callbacks mCallbacks;
    private boolean mShouldHandleBackPressed;

    private int mViewType = GalleryAdapter.VIEW_TYPE_BUCKET;
    private long bucketid = MediaLoader.ALL_MEDIA_BUCKET_ID;
    private String bucketname;

    public GalleryFragment() {
        mMediaLoader = new MediaLoader();
        mAdapter = new GalleryAdapter();
        mAdapter.setCallbacks(this);
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    public void setMediaTypeFilter(@NonNull String[] mediaTypes) {
        mMediaLoader.setMediaTypes(mediaTypes);
    }

    public void setMaxSelection(@IntRange(from = 0) int maxSelection) {
        mAdapter.setMaxSelection(maxSelection);
    }

    public int getMaxSelection() {
        return mAdapter.getMaxSelection();
    }

    @NonNull
    public String[] getMediaTypeFilter() {
        return mMediaLoader.getFilters();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!(context instanceof Callbacks)) {
            throw new IllegalArgumentException(context.getClass().getSimpleName() + " must implement " + Callbacks.class.getName());
        }
        mCallbacks = (Callbacks) context;
        if (!(context instanceof FragmentActivity)) {
            throw new IllegalArgumentException(context.getClass().getSimpleName() + " must inherit from " + FragmentActivity.class.getName());
        }
        mMediaLoader.onAttach((FragmentActivity) context, this);
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        outState.putInt(EXTRA_VIEW_TYPE, mViewType);
        outState.putLong(EXTRA_BUCKET_ID, bucketid);
        outState.putString(EXTRA_BUCKET_NAME, bucketname);
        super.onSaveInstanceState(outState);
    }

    private TextView badgeTextView;
    private static int BADGE_ID = 111;


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        badgeTextView = new TextView(getActivity());
        badgeTextView.setTextColor(getResources().getColor(R.color.blue));
        badgeTextView.setPadding(5, 0, 5, 0);
        badgeTextView.setBackground(getResources().getDrawable(R.drawable.oval_badge_counter_white));
        badgeTextView.setTextSize(16);
        badgeTextView.setMinWidth(getResources().getDimensionPixelSize(R.dimen.gallery_badge_min_size));
        badgeTextView.setMinHeight(getResources().getDimensionPixelSize(R.dimen.gallery_badge_min_size));
        badgeTextView.setAlpha(1.0f);
        badgeTextView.setGravity(Gravity.CENTER);
        badgeTextView.setPadding(
            getResources().getDimensionPixelSize(R.dimen.gallery_badge_padding), 0,
            getResources().getDimensionPixelSize(R.dimen.gallery_badge_padding), 0);
        menu.add(0, BADGE_ID, 0, "Count")
            .setActionView(badgeTextView)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        inflater.inflate(R.menu.check_menu, menu);
    }

    public int getViewType() {
        return mViewType;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        int count = ((GalleryActivity) getActivity()).getSelectionCount();
        menu.findItem(R.id.menu_check).setVisible(count > 0);
        menu.findItem(BADGE_ID).setVisible(count > 0);
        badgeTextView.setText(count > 100 ? "99+" : count + "");
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getActivity().onBackPressed();
            return true;
        }
        if (item.getItemId() == R.id.action_select_all) {
            mAdapter.selectAll();
            return true;
        }
        if (item.getItemId() == R.id.action_clear) {
            mAdapter.clearSelection();
            return true;
        }
        if (item.getItemId() == R.id.menu_check) {
            ((GalleryActivity) getActivity()).onClick(null);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBucketLoadFinished(@Nullable Cursor data) {
        mViewType = GalleryAdapter.VIEW_TYPE_BUCKET;
        mAdapter.swapData(GalleryAdapter.VIEW_TYPE_BUCKET, data);
        getActivity().invalidateOptionsMenu();
        updateEmptyState();
    }

    @Override
    public void onMediaLoadFinished(@Nullable Cursor data) {
        mViewType = GalleryAdapter.VIEW_TYPE_MEDIA;
        mAdapter.swapData(GalleryAdapter.VIEW_TYPE_MEDIA, data);
        getActivity().invalidateOptionsMenu();
        updateEmptyState();
    }

    private void updateEmptyState() {
        mRecyclerView.setVisibility(mAdapter.getItemCount() > 0 ? View.VISIBLE : View.INVISIBLE);
        mEmptyView.setVisibility(mAdapter.getItemCount() > 0 ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
        mMediaLoader.onDetach();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gallery, container, false);

        mEmptyView = view.findViewById(android.R.id.empty);

        mLayoutManager = new GridLayoutManager(getContext(), 1);
        mAdapter.setLayoutManager(mLayoutManager);

        final int spacing = getResources().getDimensionPixelSize(R.dimen.gallery_item_offset);
        mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setClipToPadding(false);
        mRecyclerView.addItemDecoration(new ItemOffsetDecoration(spacing));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                int size = getResources().getDimensionPixelSize(
                    mViewType == GalleryAdapter.VIEW_TYPE_MEDIA ? R.dimen.gallery_item_media_size
                        : R.dimen.gallery_item_bucket_size);
                int width = mRecyclerView.getMeasuredWidth();
                int columnCount = width / (size + spacing);
                mLayoutManager.setSpanCount(columnCount);
                return false;
            }
        });

        if (savedInstanceState != null) {
            updateEmptyState();
        }
        if (savedInstanceState == null)
            savedInstanceState = getActivity().getIntent().getExtras();

        if (savedInstanceState != null) {
            mViewType = savedInstanceState.getInt(EXTRA_VIEW_TYPE, GalleryAdapter.VIEW_TYPE_BUCKET);
            bucketid = savedInstanceState.getLong(EXTRA_BUCKET_ID, MediaLoader.ALL_MEDIA_BUCKET_ID);
            bucketname = savedInstanceState.getString(EXTRA_BUCKET_NAME);
        }
        mLayoutManager.setSpanCount(mViewType == GalleryAdapter.VIEW_TYPE_MEDIA ? 3 : 2);

        return view;
    }

    public void onActivityReenter(int resultCode, Intent data) {

        final int position = PreviewActivity.getPosition(resultCode, data);
        if (position != RecyclerView.NO_POSITION) {
            mRecyclerView.scrollToPosition(position);
        }

        final MediaSharedElementCallback sharedElementCallback = new MediaSharedElementCallback();
        getActivity().setExitSharedElementCallback(sharedElementCallback);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Listener to reset shared element exit transition callbacks.
            getActivity().getWindow().getSharedElementExitTransition().addListener(new TransitionCallback() {
                @Override
                public void onTransitionEnd(Transition transition) {
                    removeCallback();
                }

                @Override
                public void onTransitionCancel(Transition transition) {
                    removeCallback();
                }

                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                private void removeCallback() {
                    if (getActivity() != null) {
                        getActivity().getWindow().getSharedElementExitTransition().removeListener(this);
                        getActivity().setExitSharedElementCallback((SharedElementCallback) null);
                    }
                }
            });
        }

        //noinspection ConstantConditions
        getActivity().supportPostponeEnterTransition();
        mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);

                RecyclerView.ViewHolder holder = mRecyclerView.findViewHolderForAdapterPosition(position);
                if (holder instanceof GalleryAdapter.MediaViewHolder) {
                    GalleryAdapter.MediaViewHolder mediaViewHolder = (GalleryAdapter.MediaViewHolder) holder;
                    sharedElementCallback.setSharedElementViews(mediaViewHolder.mImageView, mediaViewHolder.mCheckView);
                }

                getActivity().supportStartPostponedEnterTransition();

                return true;
            }
        });
    }

    @Override
    public void onBucketClick(long bucketId, String label) {
        mCallbacks.onBucketClick(bucketId, label);
    }

    @Override
    public void onMediaClick(View imageView, View checkView, long bucketId, int position) {
        mCallbacks.onMediaClick(imageView, checkView, bucketid, position);
    }

    @Override
    public void onSelectionUpdated(int count) {
        mCallbacks.onSelectionUpdated(count);
        getActivity().invalidateOptionsMenu();
    }

    @Override
    public void onMaxSelectionReached() {
        mCallbacks.onMaxSelectionReached();
    }

    @Override
    public void onWillExceedMaxSelection() {
        mCallbacks.onWillExceedMaxSelection();
    }

    /**
     * Load the initial data if it handles the back pressed
     *
     * @return If this Fragment handled the back pressed callback
     */
    public boolean onBackPressed() {
        //if (mShouldHandleBackPressed) {
        //    ((GalleryActivity)getActivity()).setActionBarTitle("Gallery");
        //    loadBuckets();
        //    return true;
        //}
        return false;
    }

    public void loadBuckets() {
        mMediaLoader.loadBuckets();
        mShouldHandleBackPressed = false;
    }

    public void loadByBucket() {
        mMediaLoader.loadByBucket(bucketid);
        mShouldHandleBackPressed = true;
    }

    public List<Uri> getSelection() {
        return new ArrayList<>(mAdapter.getSelection());
    }

    public void setSelection(@NonNull List<Uri> selection) {
        mAdapter.setSelection(selection);
    }

}
