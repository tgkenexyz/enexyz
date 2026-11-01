package com.enexyz.music;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public final class MainActivity extends Activity {
    private static final int PICK_AUDIO = 4102;
    private static final int REQUEST_NOTIFICATIONS = 4103;

    private final int bg = Color.rgb(11, 9, 20);
    private final int panel = Color.rgb(25, 20, 39);
    private final int accent = Color.rgb(183, 124, 255);
    private final int muted = Color.rgb(164, 153, 184);
    private final Handler handler = new Handler();
    private final ArrayList<MediaItem> selectedItems = new ArrayList<>();
    private final ArrayList<String> selectedNames = new ArrayList<>();

    private LinearLayout playlist;
    private TextView nowTitle;
    private TextView nowSubtitle;
    private TextView timeLabel;
    private TextView stateLabel;
    private SeekBar seekBar;
    private Button playButton;
    private MediaController controller;
    private ListenableFuture<MediaController> controllerFuture;
    private boolean draggingSeekBar;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override public void onMediaItemTransition(@Nullable MediaItem item, int reason) {
            refreshPlayerUi();
        }
        @Override public void onIsPlayingChanged(boolean isPlaying) {
            refreshPlayerUi();
        }
        @Override public void onPlaybackStateChanged(int state) {
            refreshPlayerUi();
        }
    };

    private final Runnable progressUpdater = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        buildScreen();
        connectPlaybackService();
        askNotificationPermission();
        handler.post(progressUpdater);
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(28));
        scroll.addView(root);

        TextView brand = text("ENEXYZ  /  MUSIC", 12, accent, true);
        brand.setLetterSpacing(0.16f);
        root.addView(brand, matchWrap());

        TextView heading = text("Твоя музыка.\nТвой ритм.", 31, Color.WHITE, true);
        LinearLayout.LayoutParams headingParams = matchWrap();
        headingParams.topMargin = dp(12);
        root.addView(heading, headingParams);

        TextView description = text("Личная библиотека без лишних экранов", 14, muted, false);
        LinearLayout.LayoutParams descParams = matchWrap();
        descParams.topMargin = dp(7);
        root.addView(description, descParams);

        Button addButton = button("+  ДОБАВИТЬ МУЗЫКУ", true);
        LinearLayout.LayoutParams addParams = matchWrap();
        addParams.topMargin = dp(24);
        root.addView(addButton, addParams);
        addButton.setOnClickListener(v -> openAudioPicker());

        LinearLayout playerCard = card();
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(18);
        root.addView(playerCard, cardParams);

        TextView small = text("СЕЙЧАС ИГРАЕТ", 11, accent, true);
        small.setLetterSpacing(0.12f);
        playerCard.addView(small, matchWrap());

        nowTitle = text("Выберите трек", 21, Color.WHITE, true);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.topMargin = dp(14);
        playerCard.addView(nowTitle, titleParams);

        nowSubtitle = text("Music ENEXYZ", 13, muted, false);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(5);
        playerCard.addView(nowSubtitle, subtitleParams);

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams seekParams = matchWrap();
        seekParams.topMargin = dp(16);
        playerCard.addView(seekBar, seekParams);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) updateTimeLabel(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {
                draggingSeekBar = true;
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                draggingSeekBar = false;
                if (controller != null && controller.getDuration() > 0) {
                    controller.seekTo(controller.getDuration() * bar.getProgress() / 1000L);
                }
            }
        });

        timeLabel = text("00:00  /  00:00", 11, muted, false);
        LinearLayout.LayoutParams timeParams = matchWrap();
        timeParams.topMargin = dp(2);
        playerCard.addView(timeLabel, timeParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams controlsParams = matchWrap();
        controlsParams.topMargin = dp(18);
        playerCard.addView(controls, controlsParams);

        Button previous = button("|◀", false);
        Button next = button("▶|", false);
        playButton = button("▶", true);
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(0, dp(50), 1.25f);
        playParams.leftMargin = dp(10);
        playParams.rightMargin = dp(10);
        controls.addView(playButton, playParams);
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(50), 1));
        previous.setOnClickListener(v -> {
            if (controller != null && controller.getMediaItemCount() > 0) controller.seekToPreviousMediaItem();
        });
        next.setOnClickListener(v -> {
            if (controller != null && controller.getMediaItemCount() > 0) controller.seekToNextMediaItem();
        });
        playButton.setOnClickListener(v -> togglePlayback());

        stateLabel = text("ГОТОВ К ВОСПРОИЗВЕДЕНИЮ", 10, muted, true);
        stateLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stateParams = matchWrap();
        stateParams.topMargin = dp(14);
        playerCard.addView(stateLabel, stateParams);

        TextView playlistHeading = text("Моя библиотека", 20, Color.WHITE, true);
        LinearLayout.LayoutParams playlistHeadingParams = matchWrap();
        playlistHeadingParams.topMargin = dp(26);
        root.addView(playlistHeading, playlistHeadingParams);

        playlist = new LinearLayout(this);
        playlist.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams playlistParams = matchWrap();
        playlistParams.topMargin = dp(12);
        root.addView(playlist, playlistParams);
        renderPlaylist();

        TextView footer = text("@enexyz", 12, muted, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = matchWrap();
        footerParams.topMargin = dp(28);
        root.addView(footer, footerParams);

        setContentView(scroll);
    }

    private void connectPlaybackService() {
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                controller = controllerFuture.get();
                controller.addListener(playerListener);
                refreshPlayerUi();
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Не удалось запустить аудиодвижок", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void openAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_AUDIO || resultCode != RESULT_OK || data == null) return;

        ArrayList<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        for (Uri uri : uris) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
            String name = queryDisplayName(uri);
            MediaMetadata metadata = new MediaMetadata.Builder().setTitle(name).build();
            MediaItem item = new MediaItem.Builder()
                    .setMediaId(uri.toString())
                    .setUri(uri)
                    .setMediaMetadata(metadata)
                    .build();
            selectedItems.add(item);
            selectedNames.add(name);
        }

        renderPlaylist();
        if (controller != null && !selectedItems.isEmpty()) {
            controller.setMediaItems(new ArrayList<>(selectedItems));
            controller.prepare();
            controller.play();
        } else if (!uris.isEmpty()) {
            Toast.makeText(this, "Треки добавлены. Подключаем плеер…", Toast.LENGTH_SHORT).show();
        }
    }

    private String queryDisplayName(Uri uri) {
        String name = "Аудиотрек";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) name = cursor.getString(index);
            }
        } catch (Exception ignored) { }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void togglePlayback() {
        if (controller == null) {
            Toast.makeText(this, "Аудиоплеер ещё запускается", Toast.LENGTH_SHORT).show();
            return;
        }
        if (controller.getMediaItemCount() == 0) {
            openAudioPicker();
            return;
        }
        if (controller.isPlaying()) controller.pause();
        else {
            if (controller.getPlaybackState() == Player.STATE_IDLE) controller.prepare();
            controller.play();
        }
        refreshPlayerUi();
    }

    private void renderPlaylist() {
        if (playlist == null) return;
        playlist.removeAllViews();
        if (selectedNames.isEmpty()) {
            TextView empty = text("Здесь появятся добавленные аудиофайлы", 13, muted, false);
            empty.setPadding(dp(16), dp(18), dp(16), dp(18));
            empty.setBackgroundColor(panel);
            playlist.addView(empty, matchWrap());
            return;
        }
        for (int i = 0; i < selectedNames.size(); i++) {
            final int index = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(13), dp(14), dp(13));
            row.setBackgroundColor(panel);
            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.bottomMargin = dp(7);
            playlist.addView(row, rowParams);

            TextView number = text(String.format(Locale.getDefault(), "%02d", i + 1), 12, accent, true);
            row.addView(number, new LinearLayout.LayoutParams(dp(32), -2));

            TextView name = text(selectedNames.get(i), 14, Color.WHITE, true);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

            TextView action = text("▶", 14, accent, true);
            row.addView(action, new LinearLayout.LayoutParams(dp(28), -2));
            row.setOnClickListener(v -> {
                if (controller != null && controller.getMediaItemCount() > index) {
                    controller.seekTo(index, 0);
                    controller.play();
                }
            });
        }
    }

    private void refreshPlayerUi() {
        if (controller == null) return;
        MediaItem current = controller.getCurrentMediaItem();
        if (current != null) {
            CharSequence title = current.mediaMetadata.title;
            nowTitle.setText(title == null ? "Неизвестный трек" : title);
            int index = controller.getCurrentMediaItemIndex();
            nowSubtitle.setText("Music ENEXYZ  •  " + (index + 1) + " / " + controller.getMediaItemCount());
        }
        playButton.setText(controller.isPlaying() ? "Ⅱ" : "▶");
        stateLabel.setText(controller.isPlaying() ? "ВОСПРОИЗВЕДЕНИЕ" : "ПАУЗА");
        updateProgress();
    }

    private void updateProgress() {
        if (controller == null || seekBar == null) return;
        long duration = Math.max(0, controller.getDuration());
        long position = Math.max(0, controller.getCurrentPosition());
        if (!draggingSeekBar) {
            int progress = duration == 0 ? 0 : (int) Math.min(1000, position * 1000L / duration);
            seekBar.setProgress(progress);
        }
        timeLabel.setText(formatTime(position) + "  /  " + formatTime(duration));
    }

    private void updateTimeLabel(int progress) {
        if (controller == null) return;
        long duration = Math.max(0, controller.getDuration());
        timeLabel.setText(formatTime(duration * progress / 1000L) + "  /  " + formatTime(duration));
    }

    private String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(18), dp(18), dp(18), dp(18));
        view.setBackgroundColor(panel);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(primary ? 14 : 16);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                primary ? accent : Color.rgb(54, 42, 76)));
        button.setMinHeight(dp(48));
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(progressUpdater);
        if (controller != null) {
            controller.removeListener(playerListener);
            controller.release();
        }
        super.onDestroy();
    }
}
