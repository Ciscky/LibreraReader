package com.foobnix.pdf.info.widget;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.foobnix.LibreraApp;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.dao2.FileMeta;
import com.foobnix.model.AppData;
import com.foobnix.model.AppProfile;
import com.foobnix.model.AppSP;
import com.foobnix.model.AppState;
import com.foobnix.model.SimpleMeta;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.IMG;
import com.foobnix.pdf.info.R;
import com.foobnix.pdf.info.wrapper.UITab;
import com.foobnix.pdf.search.activity.HorizontalViewActivity;
import com.foobnix.sys.ImageExtractor;
import com.foobnix.tts.TTSEngine;
import com.foobnix.tts.TTSNotification;
import com.foobnix.tts.TTSService;
import com.foobnix.ui2.AppDB;
import com.foobnix.ui2.MainTabs2;

import org.ebookdroid.ui.viewer.VerticalViewActivity;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class TTSWidget extends AppWidgetProvider {


    String textUpdate;
    String bookPath;

    @Override
    public synchronized void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        //super.onUpdate(context, appWidgetManager, appWidgetIds);
        LOG.d("TTSWidget", "onUpdate");

        if (TxtUtils.isEmpty(bookPath)) {
            AppProfile.init(context);
            List<FileMeta> list = AppData.get().getAllRecent(false);
            if (list != null && list.size() > 0) {
                FileMeta fileMeta = list.get(0);
                textUpdate = TxtUtils.getFileMetaBookName(fileMeta);
                bookPath = fileMeta.getPath();

            }
        }
        textUpdate = TxtUtils.nullToEmpty(textUpdate);

        if (TxtUtils.isNotEmpty(bookPath)) {
            String url = IMG.toUrl(bookPath, ImageExtractor.COVER_PAGE_WITH_EFFECT, IMG.getImageSize());
            Glide.with(context).asBitmap().load(url).into(new CustomTarget<Bitmap>() {
                @Override
                public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                    for (int i = 0; i < appWidgetIds.length; i++) {
                        int appWidgetId = appWidgetIds[i];
                        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.notification_tts_line);
                        views.setImageViewBitmap(R.id.ttsIcon, resource);

                        views.setInt(R.id.rootView, "setBackgroundColor", Color.argb(100, 255, 255, 255));
                        views.setViewPadding(R.id.rootView, 0, 0, 0, 0);
                        views.setViewVisibility(R.id.ttsDialog, View.GONE);
                        views.setViewVisibility(R.id.ttsPrevTrack, View.GONE);
                        views.setViewVisibility(R.id.ttsNextTrack, View.GONE);
                        views.setViewVisibility(R.id.ttsStop, View.GONE);
                        //views.setViewVisibility(R.id.ttsPrev, View.GONE);

                        PendingIntent next = PendingIntent.getService(context, 0, new Intent(TTSNotification.TTS_NEXT, null, context, TTSService.class), PendingIntent.FLAG_IMMUTABLE);
                        PendingIntent prev = PendingIntent.getService(context, 0, new Intent(TTSNotification.TTS_PREV, null, context, TTSService.class), PendingIntent.FLAG_IMMUTABLE);
                        PendingIntent playPause = PendingIntent.getService(context, 0, new Intent(TTSNotification.TTS_PLAY_PAUSE, null, context, TTSService.class), PendingIntent.FLAG_IMMUTABLE);


                        views.setOnClickPendingIntent(R.id.ttsPlay, playPause);
                        views.setOnClickPendingIntent(R.id.ttsPrev, prev);
                        views.setOnClickPendingIntent(R.id.ttsNext, next);
                        views.setTextViewText(R.id.bookInfo, "" + textUpdate);
                        //views.setViewLayoutMargin(R.id.ttsPrev,RemoteViews.MARGIN_LEFT,0.0f,0);


                        if (TTSEngine.get().isPlaying()) {
                            views.setImageViewResource(R.id.ttsPlay, R.drawable.glyphicons_174_pause);
                        } else {
                            views.setImageViewResource(R.id.ttsPlay, R.drawable.glyphicons_175_play);
                        }


                        int tab = UITab.getCurrentTabIndex(UITab.RecentFragment);
                        Intent mainTabs = new Intent(context, MainTabs2.class);
                        mainTabs.putExtra(MainTabs2.EXTRA_SHOW_TABS, true);
                        mainTabs.putExtra(MainTabs2.EXTRA_PAGE_NUMBER, tab);
                        PendingIntent mainTabsIntent = PendingIntent.getActivity(
                                context,
                                0,
                                mainTabs,
                                PendingIntent.FLAG_IMMUTABLE
                        );
                        views.setOnClickPendingIntent(R.id.ttsIcon, mainTabsIntent);


                        final int color = AppState.get().isUiTextColor ? AppState.get().uiTextColor : AppState.get().tintColor;


                        views.setInt(R.id.ttsPlay, "setColorFilter", color);
                        views.setInt(R.id.ttsNext, "setColorFilter", color);
                        views.setInt(R.id.ttsPrev, "setColorFilter", color);
                        views.setInt(R.id.ttsStop, "setColorFilter", color);

                        appWidgetManager.updateAppWidget(appWidgetId, views);
                    }
                }

                @Override
                public void onLoadCleared(@Nullable Drawable placeholder) {
                }
            });
        }


    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals("android.appwidget.action.APPWIDGET_UPDATE")) {
            try {
                if (intent.getExtras() != null) {
                    textUpdate = intent.getExtras().getString(Intent.EXTRA_TEXT);
                    bookPath = intent.getExtras().getString("bookPath");
                }
            } catch (Exception e) {
                LOG.e(e);
            }
            int[] appWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, TTSWidget.class));
            onUpdate(context, AppWidgetManager.getInstance(context), appWidgetIds);

        }
        super.onReceive(context, intent);
    }
}
