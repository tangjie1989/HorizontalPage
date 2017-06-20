package com.zhuguohui.horizontalpage.view;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.View;

public class MyPagingScrollHelper {

    @Nullable
    private RecyclerView mRecyclerView;

    private int mScrolledX;
    private int mMaxScrollX;
    private int mLastScrolledPosition;

    private int mPageRows;
    private int mPageColumns;

    private static final float MILLISECONDS_PER_INCH = 100f;

    public MyPagingScrollHelper(int pageRows, int pageColumns) {
        mPageRows = pageRows;
        mPageColumns = pageColumns;
    }

    public void setUpRecycleView(@NonNull RecyclerView recycleView) {
        mRecyclerView = recycleView;
        recycleView.setOnFlingListener(new PanelFlingListener());
        recycleView.addOnScrollListener(new PanelScrollListener());
    }

    private class PanelScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE && mRecyclerView != null) {
                int pageWidth = mRecyclerView.getWidth();
                int realScrolledX = mScrolledX % pageWidth;
                if (reachEnd() || realScrolledX == 0) {
                    if (mOnPageChangeListener != null) {
                        mOnPageChangeListener.onPageChange(mScrolledX / pageWidth);
                    }
                } else {
                    if (realScrolledX > pageWidth / 2) {
                        System.out.println("forward scroll x : " + realScrolledX);
                        mRecyclerView.smoothScrollBy(pageWidth - realScrolledX, 0);
                    } else {
                        System.out.println("back scroll x : " + realScrolledX);
                        mRecyclerView.smoothScrollBy(-realScrolledX, 0);
                    }
                }
                System.out.println("mScrolledX : " + mScrolledX + " pageIndex : " + (mScrolledX / pageWidth));
            }
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            mScrolledX = mScrolledX + dx;
        }
    }

    private boolean reachEnd() {
        if (mMaxScrollX == 0 && mRecyclerView != null && mRecyclerView.getAdapter() != null) {
            int pageWidth = mRecyclerView.getWidth();
            int totalCount = mRecyclerView.getAdapter().getItemCount();
            if (totalCount == 0) {
                return true;
            }
            if (totalCount % mPageRows == 0) {
                mMaxScrollX = totalCount / mPageRows * (pageWidth / mPageColumns) - pageWidth;
            } else {
                mMaxScrollX = (totalCount / mPageRows + 1) * (pageWidth / mPageColumns) - pageWidth;
            }
        }
        return mScrolledX == mMaxScrollX;
    }

    private class PanelFlingListener extends RecyclerView.OnFlingListener {

        @Override
        public boolean onFling(int velocityX, int velocityY) {
            if (mRecyclerView == null) {
                return false;
            }
            RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
            if (layoutManager == null) {
                return false;
            }
            RecyclerView.Adapter adapter = mRecyclerView.getAdapter();
            if (adapter == null) {
                return false;
            }
            int minFlingVelocity = mRecyclerView.getMinFlingVelocity();
            return (Math.abs(velocityX) > minFlingVelocity) && snapFromFling(layoutManager, velocityX);
        }
    }

    private boolean snapFromFling(@NonNull RecyclerView.LayoutManager layoutManager, int velocityX) {
        if (!(layoutManager instanceof RecyclerView.SmoothScroller.ScrollVectorProvider)) {
            return false;
        }

        RecyclerView.SmoothScroller smoothScroller = createSnapScroller(layoutManager);
        if (smoothScroller == null) {
            return false;
        }

        int targetPosition = findTargetSnapPosition(velocityX);
        System.out.println("targetPosition : " + targetPosition + " velocityX : " + velocityX);
        if (targetPosition == RecyclerView.NO_POSITION) {
            return false;
        }

        smoothScroller.setTargetPosition(targetPosition);
        layoutManager.startSmoothScroll(smoothScroller);
        return true;
    }

    private synchronized int findTargetSnapPosition(int velocityX) {
        if (mRecyclerView == null || mRecyclerView.getAdapter() == null) {
            return RecyclerView.NO_POSITION;
        }
        if (velocityX > 0) {
            mLastScrolledPosition = mLastScrolledPosition + getPageItemCount();
            int totalCount = mRecyclerView.getAdapter().getItemCount();
            if (mLastScrolledPosition <= totalCount) {
                return mLastScrolledPosition;
            }
        }
        if (mLastScrolledPosition >= getPageItemCount()) {
            mLastScrolledPosition = mLastScrolledPosition - getPageItemCount();
        }
        return mLastScrolledPosition;
    }

    private int getPageItemCount() {
        return mPageRows * mPageColumns;
    }

    @Nullable
    private LinearSmoothScroller createSnapScroller(RecyclerView.LayoutManager layoutManager) {
        if (mRecyclerView == null) {
            return null;
        }
        if (!(layoutManager instanceof RecyclerView.SmoothScroller.ScrollVectorProvider)) {
            return null;
        }
        return new LinearSmoothScroller(mRecyclerView.getContext()) {
            @Override
            protected void onTargetFound(View targetView, RecyclerView.State state, Action action) {
                if (mRecyclerView == null) {
                    return;
                }
                RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
                if (layoutManager == null) {
                    return;
                }
                int[] snapDistances = calculateDistanceToFinalSnap(layoutManager, targetView);
                final int dx = snapDistances[0];
                final int dy = snapDistances[1];
                final int time = calculateTimeForDeceleration(Math.max(Math.abs(dx), Math.abs(dy)));
                if (time > 0) {
                    action.update(dx, dy, time, mDecelerateInterpolator);
                }
//                final int time = calculateTimeForScrolling(Math.max(Math.abs(dx), Math.abs(dy)));
//                if (time > 0) {
//                    action.update(dx, dy, time, mLinearInterpolator);
//                }
            }

            @Override
            protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                return MILLISECONDS_PER_INCH / displayMetrics.densityDpi;
            }
        };
    }

    private int[] calculateDistanceToFinalSnap(@NonNull RecyclerView.LayoutManager layoutManager,
                                               @NonNull View targetView) {
        int[] out = new int[2];
        if (layoutManager.canScrollHorizontally()) {
            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                    targetView.getLayoutParams();
            out[0] = (layoutManager.getDecoratedLeft(targetView) - params.leftMargin);
            out[1] = 0;
        }
        return out;
    }

    @Nullable
    private onPageChangeListener mOnPageChangeListener;

    public void setOnPageChangeListener(@NonNull onPageChangeListener listener) {
        mOnPageChangeListener = listener;
    }

    public interface onPageChangeListener {
        void onPageChange(int index);
    }
}
