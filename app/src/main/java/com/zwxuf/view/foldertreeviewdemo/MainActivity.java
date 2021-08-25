package com.zwxuf.view.foldertreeviewdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;


import com.zwxuf.view.foldertreeview.FolderTreeView;

import java.io.File;

public class MainActivity extends AppCompatActivity implements FolderTreeView.OnNodeClickListener, FolderTreeView.OnNodeCheckedListener, View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int REQUEST_STORAGE = 1;

    private FolderTreeView mFolderTreeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mFolderTreeView = (FolderTreeView) findViewById(R.id.mFolderTreeView);
        findViewById(R.id.bn_ok).setOnClickListener(this);

        mFolderTreeView.setShowHidden(false); //不显示隐藏目录
        mFolderTreeView.setMultiSelect(true); //多选
        //mFolderTreeView.setLineSpace(16); //行间距
        //mFolderTreeView.setSelectedBkDrawable(R.drawable.item_selected); //选择项背景

        mFolderTreeView.setOnNodeClickListener(this); //目录点击监听
        mFolderTreeView.setOnNodeCheckedListener(this); //目录选择监听

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE);
        } else {
            mFolderTreeView.init();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mFolderTreeView.init();
            } else {
                Toast.makeText(this, "no storage permission", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onNodeClick(FolderTreeView view, int position, File dir, boolean isExpanded) {
        Log.e(TAG, "dir name: " + dir.getName() + ", isOpen: " + isExpanded);
    }

    @Override
    public void onNodeChecked(FolderTreeView view, int position, File dir, boolean isChecked) {
        Log.e(TAG, "dir name: " + dir.getName() + ", isChecked: " + isChecked);
    }

    @Override
    public void onClick(View v) {
        StringBuilder builder=new StringBuilder();
        for (File file : mFolderTreeView.getSelectedFolderList()) {
            builder.append(file.getAbsolutePath()).append('\n');
        }
        new AlertDialog.Builder(this)
                .setTitle("已选择：")
                .setMessage(builder)
                .create()
                .show();

    }
}
