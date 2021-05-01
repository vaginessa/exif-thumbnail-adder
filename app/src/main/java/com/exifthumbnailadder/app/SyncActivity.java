/*
 * Copyright (C) 2021 Fab Stz <fabstz-it@yahoo.fr>
 *
 * This file is part of Exif Thumbnail Adder. An android app that adds
 * thumbnails in EXIF tags of your pictures that don't have one yet.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.exifthumbnailadder.app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.List;
import java.util.TreeSet;

public class SyncActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    SharedPreferences prefs = null;
    TextView textViewLog, textViewDirList;
    Handler mHandler;
    public final static SpannableStringBuilder log = new SpannableStringBuilder("");
    ScrollView scrollview = null;
    private boolean stopProcessing = false;
    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        findViewById(R.id.sync_button_list_files).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                displayStopButton();
                doSync(true);
            }
        });

        findViewById(R.id.sync_button_del_files).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                displayStopButton();
                doSync(false);
            }
        });

        findViewById(R.id.sync_button_stop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopProcessing = true;
                displayStartButton();
            }
        });

        textViewLog = (TextView)findViewById(R.id.sync_textview_log);
        textViewDirList = (TextView)findViewById(R.id.sync_textview_dir_list);
        scrollview = ((ScrollView)findViewById(R.id.sync_scrollview));
        FirstFragment.updateTextViewDirList(this, textViewDirList);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ( key.equals("srcUris")) {
            FirstFragment.updateTextViewDirList(this, textViewDirList);
        }
    }

    public void doSync(boolean dryRun) {
        isProcessing = true;
        stopProcessing = false;

        mHandler = new Handler();
        new Thread(new Runnable() {
            @Override
            public void run() {
                log.clear();
                updateUiLog(getString(R.string.frag1_log_starting));

                {
                    updateUiLog(Html.fromHtml(getString(R.string.frag1_log_checking_workingdir_perm), 1));
                    if (!WorkingDirPermActivity.isWorkingDirPermOk(SyncActivity.this)) {
                        updateUiLog(Html.fromHtml("<span style='color:red'>"+getString(R.string.frag1_log_ko)+"</span><br>", 1));
                        setIsProcessFalse();
                        stopProcessing = false;
                        return;
                    }
                    updateUiLog(Html.fromHtml("<span style='color:green'>"+getString(R.string.frag1_log_ok)+"</span><br>", 1));
                }

                InputDirs inputDirs = new InputDirs(prefs.getString("srcUris", ""));
                Object[] srcDirs;
                if (prefs.getBoolean("useSAF", true)) {
                    srcDirs = inputDirs.toUriArray(); // Uri[]
                } else {
                    srcDirs = inputDirs.toFileArray(SyncActivity.this); // File[]
                }

                // Iterate on folders containing source images
                for (int j = 0; j < srcDirs.length; j++) {
                    ETASrcDir etaSrcDir = null;
                    if (srcDirs[j] instanceof Uri) {
                        etaSrcDir = new ETASrcDirUri(SyncActivity.this, (Uri)srcDirs[j]);
                    } else if (srcDirs[j] instanceof File) {
                        etaSrcDir = new ETASrcDirFile(SyncActivity.this, (File)srcDirs[j]);
                    }
                    if (etaSrcDir == null) throw new UnsupportedOperationException();

                    updateUiLog(Html.fromHtml("<br><u><b>"+getString(R.string.frag1_log_processing_dir, etaSrcDir.getFSPath()) + "</b></u><br>",1));

                    // Check permission in case we use SAF...
                    // If we don't have permission, continue to next srcDir
                    updateUiLog(Html.fromHtml(getString(R.string.frag1_log_checking_perm), 1));
                    if (! etaSrcDir.isPermOk()) {
                        updateUiLog(Html.fromHtml("<span style='color:red'>"+getString(R.string.frag1_log_not_granted)+"</span><br>", 1));
                        continue;
                    }
                    updateUiLog(Html.fromHtml("<span style='color:green'>"+getString(R.string.frag1_log_ok)+"</span><br>", 1));


                    ETADoc etaDocSrc = null;
                    if (etaSrcDir instanceof ETASrcDirUri) {
                        DocumentFile baseDf = DocumentFile.fromTreeUri(SyncActivity.this, (Uri)srcDirs[j]);
                        etaDocSrc = new ETADocDf(baseDf, SyncActivity.this, (ETASrcDirUri)etaSrcDir, false);
                    } else if (etaSrcDir instanceof ETASrcDirFile) {
                        etaDocSrc = new ETADocFile((File)srcDirs[j], SyncActivity.this, (ETASrcDirFile)etaSrcDir, true);
                    }
                    if (etaDocSrc == null) throw new UnsupportedOperationException();

                    // Process backupUri
                    ETASrcDir etaSrcDirBackup = null;
                    if (etaDocSrc instanceof ETADocDf) {
                        etaSrcDirBackup = new ETASrcDirUri(
                                SyncActivity.this,
                                etaDocSrc.getBackupUri());
                    } else if (etaDocSrc instanceof ETADocFile) {
                        etaSrcDirBackup = new ETASrcDirFile(
                                SyncActivity.this,
                                etaDocSrc.getBackupPath().toFile());
                    }
                    doSyncForUri(etaSrcDirBackup, etaDocSrc, dryRun);

                    // Process outputUri
                    if (!PreferenceManager.getDefaultSharedPreferences(SyncActivity.this).getBoolean("writeThumbnailedToOriginalFolder", false)) {
                        updateUiLog(Html.fromHtml("<br>",1));
                        ETASrcDir etaSrcDirDest = null;
                        if (etaDocSrc instanceof ETADocDf) {
                            etaSrcDirDest = new ETASrcDirUri(
                                    SyncActivity.this,
                                    etaDocSrc.getDestUri());
                        } else if (etaDocSrc instanceof ETADocFile) {
                            etaSrcDirDest = new ETASrcDirFile(
                                    SyncActivity.this,
                                    etaDocSrc.getDestPath().toFile());
                        }
                        doSyncForUri(etaSrcDirDest, etaDocSrc, dryRun);
                    }
                }

                updateUiLog(getString(R.string.frag1_log_finished));

                setIsProcessFalse();
            }
        }).start();
    }

    private void doSyncForUri(ETASrcDir workingDirDocs, ETADoc srcDirEtaDoc, boolean dryRun ) {

        TreeSet<Object> docsInWorkingDir = (TreeSet<Object>)workingDirDocs.getDocsSet();

        updateUiLog(getString(R.string.sync_log_checking, workingDirDocs.getFSPathWithoutRoot()));
        updateUiLog("\n");
        updateUiLog(Html.fromHtml("<u>" + getString(R.string.sync_log_files_to_remove) + "</u><br>",1));

        for (Object _doc : docsInWorkingDir) {

            // Convert (Object)_doc to (Uri)doc or (File)doc
            ETADoc doc = null;
            if (workingDirDocs.getDocsRoot() instanceof Uri) {
                doc = new ETADocDf((DocumentFile) _doc, SyncActivity.this, (ETASrcDirUri) workingDirDocs, false);
            } else if (workingDirDocs.getDocsRoot() instanceof File) {
                doc = new ETADocFile((File) _doc, SyncActivity.this, (ETASrcDirFile)workingDirDocs, true);
            }
            if (doc == null) throw new UnsupportedOperationException();

            if (stopProcessing) {
                setIsProcessFalse();
                stopProcessing = false;
                updateUiLog(Html.fromHtml("<br><br>"+getString(R.string.frag1_log_stopped_by_user),1));
                return;
            }

            // Skip some files
            if (doc.isFile()) {
                if (!doc.isJpeg()) continue;
                if (doc.getName().equals(".nomedia")) continue;
            }
            if (doc.isDirectory()) continue;

            boolean srcFileExists = true;
            try {
                if (workingDirDocs.getDocsRoot() instanceof Uri) {
                    Uri srcUri = doc.getSrcUri(srcDirEtaDoc.getMainDir(), srcDirEtaDoc.getTreeId());
                    srcFileExists = DocumentFile.fromTreeUri(this, srcUri).exists();
                } else if (workingDirDocs.getDocsRoot() instanceof File) {
                    File srcFile = doc.getSrcPath(srcDirEtaDoc.getFullFSPath()).toFile();
                    srcFileExists = srcFile.exists();
                }
            } catch (Exception e) {
                updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_skipping_error, e.toString()) + "</span><br>", 1));
                e.printStackTrace();
                continue;
            }

            // Delete file if it doesn't exist in source directory
            if (!srcFileExists) {
                updateUiLog("⋅ " + doc.getDPath() + "... ");
                if (!dryRun) {
                    boolean deleted = doc.delete();
                    if (deleted)
                        updateUiLog(Html.fromHtml("<span style='color:green'>"+getString(R.string.frag1_log_done)+"</span>",1));
                    else
                        updateUiLog(Html.fromHtml("<span style='color:green'>"+getString(R.string.sync_log_failure_to_delete_file)+"</span>",1));
                }
                updateUiLog(Html.fromHtml("<br>",1));
            }
        }
    }

    public void updateUiLog(String text) {
        if (MainApplication.enableLog) Log.i(MainApplication.TAG, text);
        log.append(text);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewLog.setText(log);
                // Stuff that updates the UI
                scrollview.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollview.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    public void updateUiLog(Spanned text) {
        if (MainApplication.enableLog) Log.i(MainApplication.TAG, text.toString());
        log.append(text);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewLog.setText(log);
                // Stuff that updates the UI
                scrollview.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollview.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    public void setIsProcessFalse() {
        isProcessing = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                displayStartButton();
            }
        });
    }

    public void displayStopButton() {
        Button list = (Button) findViewById(R.id.sync_button_list_files);
        Button start = (Button) findViewById(R.id.sync_button_del_files);
        Button stop = (Button) findViewById(R.id.sync_button_stop);
        list.setVisibility(Button.GONE);
        start.setVisibility(Button.GONE);
        stop.setVisibility(Button.VISIBLE);
    }

    public void displayStartButton() {
        Button list = (Button) findViewById(R.id.sync_button_list_files);
        Button start = (Button) findViewById(R.id.sync_button_del_files);
        Button stop = (Button) findViewById(R.id.sync_button_stop);
        list.setVisibility(Button.VISIBLE);
        start.setVisibility(Button.VISIBLE);
        stop.setVisibility(Button.GONE);
    }

}
