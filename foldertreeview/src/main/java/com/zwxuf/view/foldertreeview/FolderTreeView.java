package com.zwxuf.view.foldertreeview;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by yuanfang235 on 2021/8/24.
 * 文件夹树形Viwe
 */

public class FolderTreeView extends FrameLayout {

    private static final String TAG = "fly";

    private static final int SORT_AUTO = 0;
    private static final int SORT_BY_NAME = 1;
    private static final int SORT_BY_TIME = 2;

    private RecyclerView mRecyclerView;
    private TreeAdapter mTreeAdapter;
    private List<Node> mNodes = new LinkedList<>();
    private LinearLayoutManager mLayoutManager;
    private OnNodeClickListener onNodeClickListener;
    private OnNodeCheckedListener onNodeCheckedListener;
    private float checkIconSize = 16;
    private float nodeIconSize = 12;
    private float folderIconSize = 24;
    private float nodeIndent = -1;
    private float lineSpace = 4;
    private float nodeTextSize = 14;
    private Drawable folderIcon;
    private Drawable nodeOpenIcon;
    private Drawable nodeCloseIcon;
    private Drawable nodeCheckedIcon;
    private Drawable nodeUncheckedIcon;
    private int sortType = SORT_BY_NAME;
    private boolean isSortDescending;
    private boolean isShowHidden = true;
    private boolean isShowRootDir;
    private boolean isShowPublicDir = true;
    private boolean isMultiSelect;
    private List<Node> mSelectedNodes = new LinkedList<>();
    private boolean isSelectedMode;
    private int selectedBkDrawable = 0;

    public FolderTreeView(Context context) {
        super(context);
        initView();
    }

    public FolderTreeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public FolderTreeView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    private void initView() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        nodeOpenIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_node_open);
        nodeCloseIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_node_close);
        nodeCheckedIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_checked);
        nodeUncheckedIcon = ContextCompat.getDrawable(getContext(), R.drawable.ic_unchecked);
        MyScrollView mScrollView = new MyScrollView(getContext());
        mScrollView.setFillViewport(true);
        addView(mScrollView);
        mRecyclerView = new MyRecyclerView(getContext());
        mRecyclerView.setVerticalScrollBarEnabled(true);
        mScrollView.addView(mRecyclerView);
        mLayoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mTreeAdapter = new TreeAdapter();
        mRecyclerView.setAdapter(mTreeAdapter);
    }

    /**
     * 初始化或重置文件夹，获取存储权限后调用该方法
     *
     * @return
     */
    public boolean init() {
        if (!canReadStorage()) return false;
        mNodes.clear();

        if (isShowRootDir) {
            mNodes.add(makeNode(null, "根目录", new File("/")));
        }
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            mNodes.add(makeNode(null, "内部存储", Environment.getExternalStorageDirectory()));
        }
        List<File> dirList = getExternalSDCard(getContext().getApplicationContext());
        if (!dirList.isEmpty()) {
            File innerDir = Environment.getExternalStorageDirectory();
            for (int i = 0; i < dirList.size(); i++) {
                if (innerDir != null && dirList.get(i).getAbsolutePath().equals(innerDir.getAbsolutePath())) {
                    continue;
                }
                mNodes.add(makeNode(null, "扩展存储" + (i + 1), dirList.get(i)));
            }
        }
        if (isShowPublicDir && Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            mNodes.add(makeNode(null, "下载", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)));
            mNodes.add(makeNode(null, "图片", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)));
            mNodes.add(makeNode(null, "相机", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)));
            mNodes.add(makeNode(null, "视频", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)));
            mNodes.add(makeNode(null, "音乐", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)));
            mNodes.add(makeNode(null, "文档", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)));
        }
        refresh();
        return true;
    }

    /**
     * 生成节点
     *
     * @param parent
     * @param dir
     * @return
     */
    private Node makeNode(Node parent, String name, File dir) {
        Node node = new Node(name, dir.getAbsolutePath(), dir.lastModified());
        if (parent != null) {
            node.level = parent.level + 1;
        }
        File[] files = dir.listFiles(mDirFilter);
        if (files != null && files.length > 0) {
            node.isShowNodeIcon = true;
        }
        return node;
    }

    /**
     * 目录过滤器
     */
    private FileFilter mDirFilter = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            return pathname.isDirectory() && (isShowHidden || !pathname.isHidden());
        }
    };

    private class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.MyViewHolder> {

        @Override
        public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new MyViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.tree_item_node, parent, false));
        }

        @Override
        public void onBindViewHolder(final MyViewHolder holder, final int position) {
            final Node node = mNodes.get(position);
            holder.tv_name.setText(node.name);
            holder.tv_name.setTextSize(nodeTextSize);
            if (folderIcon != null) {
                holder.iv_icon.setImageDrawable(folderIcon);
            }
            if (isSelectedMode) {
                holder.iv_node.setVisibility(GONE);
                if (isMultiSelect || mSelectedNodes.contains(node)) {
                    holder.iv_check.setVisibility(VISIBLE);
                } else {
                    holder.iv_check.setVisibility(INVISIBLE);
                }
                if (mSelectedNodes.contains(node)) {
                    holder.iv_check.setImageDrawable(nodeCheckedIcon);
                    holder.vg_item.setBackgroundResource(selectedBkDrawable);
                } else {
                    holder.iv_check.setImageDrawable(nodeUncheckedIcon);
                    holder.vg_item.setBackgroundColor(0);
                }
            } else {
                holder.iv_node.setVisibility(node.isShowNodeIcon ? VISIBLE : INVISIBLE);
                holder.iv_check.setVisibility(GONE);
                holder.iv_check.setImageDrawable(null);
                holder.vg_item.setBackgroundColor(0);
            }
            holder.iv_node.setImageDrawable(node.isExpanded ? nodeOpenIcon : nodeCloseIcon);
            int padding = nodeIndent < 0 ? dip2px(folderIconSize) : dip2px(nodeIndent);
            holder.itemView.setPadding(padding * node.level + dip2px(10), dip2px(lineSpace) / 2, dip2px(10), dip2px(lineSpace) / 2);
            LinearLayout.LayoutParams fiParams = (LinearLayout.LayoutParams) holder.iv_icon.getLayoutParams();
            if (fiParams != null) {
                fiParams.width = dip2px(folderIconSize);
                fiParams.height = dip2px(folderIconSize);
                holder.iv_icon.requestLayout();
            }
            LinearLayout.LayoutParams niParams = (LinearLayout.LayoutParams) holder.iv_node.getLayoutParams();
            if (niParams != null) {
                niParams.width = dip2px(nodeIconSize);
                niParams.height = dip2px(nodeIconSize);
                holder.iv_node.requestLayout();
            }
            LinearLayout.LayoutParams ciParams = (LinearLayout.LayoutParams) holder.iv_check.getLayoutParams();
            if (ciParams != null) {
                ciParams.width = dip2px(checkIconSize);
                ciParams.height = dip2px(checkIconSize);
                holder.iv_check.requestLayout();
            }
            holder.itemView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isSelectedMode) {
                        updateSelected(node, position, true);
                    } else {
                        if (node.isShowNodeIcon) {
                            node.isExpanded = !node.isExpanded;
                            updateNodeState(holder, node);
                            if (onNodeClickListener != null) {
                                onNodeClickListener.onNodeClick(FolderTreeView.this, position, new File(node.path), node.isExpanded);
                            }
                        }
                    }
                }
            });
            holder.itemView.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    updateSelected(node, position, false);
                    return true;
                }
            });

        }

        @Override
        public int getItemCount() {
            return mNodes == null ? 0 : mNodes.size();
        }

        class MyViewHolder extends RecyclerView.ViewHolder {

            ImageView iv_check;
            ImageView iv_node;
            ImageView iv_icon;
            TextView tv_name;
            ViewGroup vg_item;

            public MyViewHolder(View itemView) {
                super(itemView);
                iv_check = itemView.findViewById(R.id.iv_check);
                iv_node = itemView.findViewById(R.id.iv_node);
                iv_icon = itemView.findViewById(R.id.iv_icon);
                tv_name = itemView.findViewById(R.id.tv_name);
                vg_item = itemView.findViewById(R.id.vg_item);
            }
        }
    }

    private void updateSelected(Node node, int position, boolean isClick) {
        boolean isChecked;
        if (mSelectedNodes.contains(node)) {
            mSelectedNodes.remove(node);
            isChecked = false;
        } else {
            if (!isMultiSelect) {
                mSelectedNodes.clear();
            }
            mSelectedNodes.add(node);
            isChecked = true;
        }
        if (isClick && isMultiSelect) {
            isSelectedMode = true;
        } else {
            isSelectedMode = !mSelectedNodes.isEmpty();
        }
        refresh();
        if (onNodeCheckedListener != null) {
            onNodeCheckedListener.onNodeChecked(this, position, new File(node.path), isChecked);
        }
    }

    private void updateNodeState(TreeAdapter.MyViewHolder holder, Node node) {
        if (node.isExpanded) {
            startNodeOpenAnimation(holder.iv_node);
            addNode(node);
        } else {
            startNodeCloseAnimation(holder.iv_node);
            removeNode(node);
        }
    }

    /**
     * 添加节点
     *
     * @param parent
     */
    private void addNode(Node parent) {
        int position = mNodes.indexOf(parent);
        boolean isLast = (position == mNodes.size() - 1);
        File dir = new File(parent.path);
        File[] files = dir.listFiles(mDirFilter);
        if (files != null && files.length > 0) {
            List<Node> nodes = new ArrayList<>();
            for (File file : files) {
                nodes.add(makeNode(parent, file.getName(), file));
            }
            if (sortType != SORT_AUTO) {
                sort(nodes);
            }
            if (position < mNodes.size() - 1) {
                mNodes.addAll(position + 1, nodes);
            } else {
                mNodes.addAll(nodes);
            }
            parent.isShowNodeIcon = true;
            mTreeAdapter.notifyItemRangeInserted(position + 1, nodes.size());
            if (isLast) {
                mLayoutManager.scrollToPositionWithOffset(position, 0);
            }
        } else {
            parent.isShowNodeIcon = false;
            mTreeAdapter.notifyItemChanged(position);
        }
    }

    private void sort(List<Node> nodes) {
        final Collator collator = Collator.getInstance();
        Collections.sort(nodes, new Comparator<Node>() {
            @Override
            public int compare(Node o1, Node o2) {
                if (sortType == SORT_BY_NAME) {
                    return collator.compare(o1.name, o2.name);
                } else if (sortType == SORT_BY_TIME) {
                    if (o1.time < o2.time) {
                        return -1;
                    } else if (o1.time > o2.time) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
                return 0;
            }
        });
        if (isSortDescending) {
            Collections.reverse(nodes);
        }
    }

    private void startNodeOpenAnimation(final ImageView nodeView) {
        RotateAnimation animation = new RotateAnimation(0, 90, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        animation.setDuration(300);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                nodeView.clearAnimation();
                nodeView.setImageDrawable(nodeOpenIcon);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        nodeView.startAnimation(animation);
    }

    private void startNodeCloseAnimation(final ImageView nodeView) {
        RotateAnimation animation = new RotateAnimation(0, -90, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        animation.setDuration(300);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                nodeView.clearAnimation();
                nodeView.setImageDrawable(nodeCloseIcon);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        nodeView.startAnimation(animation);
    }

    /**
     * 删除节点
     *
     * @param parent
     */
    private void removeNode(Node parent) {
        List<Node> nodes = new ArrayList<>();
        int position = mNodes.indexOf(parent);
        for (int i = position + 1; i < mNodes.size(); i++) {
            Node node = mNodes.get(i);
            if (node.level > parent.level) {
                nodes.add(node);
            } else {
                break;
            }
        }
        if (!nodes.isEmpty()) {
            mNodes.removeAll(nodes);
            mTreeAdapter.notifyItemRangeRemoved(position + 1, nodes.size());
        }
    }

    private class Node {
        String name;
        String path;
        long time;
        int level;
        boolean isExpanded;
        boolean isShowNodeIcon;

        public Node(String name, String path, long time) {
            this.name = name;
            this.path = path;
            this.time = time;
        }
    }

    public void refresh() {
        if (mTreeAdapter != null) {
            mTreeAdapter.notifyDataSetChanged();
        }
    }

    /**
     * 存储权限
     *
     * @return
     */
    private boolean canReadStorage() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || ContextCompat.checkSelfPermission(getContext().getApplicationContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 节点点击事件
     *
     * @param onNodeClickListener
     * @return
     */
    public FolderTreeView setOnNodeClickListener(OnNodeClickListener onNodeClickListener) {
        this.onNodeClickListener = onNodeClickListener;
        return this;
    }

    /**
     * 节点选择事件
     *
     * @param onNodeCheckedListener
     * @return
     */
    public FolderTreeView setOnNodeCheckedListener(OnNodeCheckedListener onNodeCheckedListener) {
        this.onNodeCheckedListener = onNodeCheckedListener;
        return this;
    }

    public interface OnNodeClickListener {
        void onNodeClick(FolderTreeView view, int position, File dir, boolean isExpanded);
    }

    public interface OnNodeCheckedListener {
        void onNodeChecked(FolderTreeView view, int position, File dir, boolean isChecked);
    }

    /**
     * 设置check框大小
     *
     * @param checkIconSize
     * @return
     */
    public FolderTreeView setCheckIconSize(float checkIconSize) {
        this.checkIconSize = checkIconSize;
        refresh();
        return this;
    }

    /**
     * 设置节点按钮大小，单位dip
     *
     * @param nodeIconSize
     * @return
     */
    public FolderTreeView setNodeIconSize(float nodeIconSize) {
        this.nodeIconSize = nodeIconSize;
        refresh();
        return this;
    }

    /**
     * 设置文件夹图标大小，单位dip
     *
     * @param folderIconSize
     * @return
     */
    public FolderTreeView setFolderIconSize(float folderIconSize) {
        this.folderIconSize = folderIconSize;
        refresh();
        return this;
    }

    /**
     * 设置缩进，单位dip
     *
     * @param nodeIndent
     * @return
     */
    public FolderTreeView setNodeIndent(float nodeIndent) {
        this.nodeIndent = nodeIndent;
        refresh();
        return this;
    }

    private int dip2px(float dpValue) {
        final float scale = getContext().getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    /**
     * 设置行间距，单位dip
     *
     * @param lineSpace
     * @return
     */
    public FolderTreeView setLineSpace(float lineSpace) {
        this.lineSpace = lineSpace;
        refresh();
        return this;
    }

    /**
     * 设置节点文字大小，单位sp
     *
     * @param nodeTextSize
     */
    public FolderTreeView setNodeTextSize(float nodeTextSize) {
        this.nodeTextSize = nodeTextSize;
        refresh();
        return this;
    }

    /**
     * 文件夹图标
     *
     * @param iconResId
     * @return
     */
    public FolderTreeView setFolderIcon(int iconResId) {
        folderIcon = ContextCompat.getDrawable(getContext(), iconResId);
        refresh();
        return this;
    }

    /**
     * 文件夹图标
     *
     * @param icon
     * @return
     */
    public FolderTreeView setFolderIcon(Drawable icon) {
        folderIcon = icon;
        refresh();
        return this;
    }

    /**
     * 排序方式, 初始化之前执行
     *
     * @param sortType
     */
    public FolderTreeView setSortType(int sortType) {
        this.sortType = sortType;
        return this;
    }

    /**
     * 降序排序, 初始化之前执行
     *
     * @param sortDescending
     */
    public FolderTreeView setSortDescending(boolean sortDescending) {
        isSortDescending = sortDescending;
        return this;
    }

    /**
     * 显示隐藏目录, 初始化之前执行
     *
     * @param showHidden
     */
    public FolderTreeView setShowHidden(boolean showHidden) {
        isShowHidden = showHidden;
        return this;
    }


    /**
     * 设置节点图标
     *
     * @param nodeOpenIcon
     * @param nodeCloseIcon
     */
    public FolderTreeView setNodeIcon(Drawable nodeOpenIcon, Drawable nodeCloseIcon) {
        this.nodeOpenIcon = nodeOpenIcon;
        this.nodeCloseIcon = nodeCloseIcon;
        refresh();
        return this;
    }

    /**
     * 设置节点图标
     *
     * @param nodeOpenIcon
     * @param nodeCloseIcon
     */
    public FolderTreeView setNodeIcon(int nodeOpenIcon, int nodeCloseIcon) {
        return setNodeIcon(ContextCompat.getDrawable(getContext(), nodeOpenIcon),
                ContextCompat.getDrawable(getContext(), nodeCloseIcon));
    }

    /**
     * 设置节点选择图标
     * @param checked
     * @param unchecked
     * @return
     */
    public FolderTreeView setNodeCheckIcon(Drawable checked, Drawable unchecked) {
        this.nodeCheckedIcon = checked;
        this.nodeUncheckedIcon = unchecked;
        return this;
    }

    /**
     * 设置节点选择图标
     * @param checked
     * @param unchecked
     * @return
     */
    public FolderTreeView setNodeCheckIcon(int checked, int unchecked) {
        return setNodeCheckIcon(ContextCompat.getDrawable(getContext(), checked),
                ContextCompat.getDrawable(getContext(), unchecked));
    }

    /**
     * 显示根目录
     *
     * @param showRootDir
     */
    public FolderTreeView setShowRootDir(boolean showRootDir) {
        isShowRootDir = showRootDir;
        return this;
    }

    /**
     * 显示公共目录
     *
     * @param showPublicDir
     */
    public FolderTreeView setShowPublicDir(boolean showPublicDir) {
        isShowPublicDir = showPublicDir;
        return this;
    }

    /**
     * 设置多选模式
     *
     * @param multiSelect
     */
    public FolderTreeView setMultiSelect(boolean multiSelect) {
        isMultiSelect = multiSelect;
        return this;
    }

    /**
     * 是选择模式
     *
     * @return
     */
    public boolean isSelectedMode() {
        return isSelectedMode;
    }

    /**
     * 获取选择的目录列表
     *
     * @return
     */
    public List<File> getSelectedFolderList() {
        List<File> dirList = new ArrayList<>();
        for (Node mSelectedNode : mSelectedNodes) {
            dirList.add(new File(mSelectedNode.path));
        }
        return dirList;
    }

    /**
     * 获取单选目录
     *
     * @return
     */
    public File getSelectedFolder() {
        if (isMultiSelect || mSelectedNodes.isEmpty()) {
            return null;
        } else {
            return new File(mSelectedNodes.get(0).path);
        }
    }

    /**
     * 选定背景
     *
     * @param selectedBkDrawable
     */
    public FolderTreeView setSelectedBkDrawable(int selectedBkDrawable) {
        this.selectedBkDrawable = selectedBkDrawable;
        return this;
    }

    static class MyScrollView extends HorizontalScrollView {

        public MyScrollView(Context context) {
            super(context);
        }

        public MyScrollView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MyScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        private float lastX, lastY;

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {

            boolean intercept = super.onInterceptTouchEvent(e);

            switch (e.getAction() & e.getActionMasked()) {

                case MotionEvent.ACTION_DOWN:
                    lastX = e.getX();
                    lastY = e.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float x = Math.abs(e.getX() - lastX);
                    float y = Math.abs(e.getY() - lastY);
                    if ((x > 0 || y > 0) && y >= x) {
                        requestDisallowInterceptTouchEvent(true);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    intercept = false;
                    break;
            }
            return intercept;
        }

    }

    private static class MyRecyclerView extends RecyclerView {

        public MyRecyclerView(Context context) {
            super(context);
        }

        public MyRecyclerView(Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
        }

        public MyRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

    }

    private static void print(Object... info) {
        if (info == null || info.length == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : info) {
            sb.append(o != null ? o.toString() + ", " : ", ");
        }
        String msg = sb.toString();
        if (msg.endsWith(",")) msg = sb.deleteCharAt(sb.length() - 1).toString();
        Log.e(TAG, msg);
    }

    private static List<File> getExternalSDCard(Context context) {
        List<File> pathsList = new ArrayList<>();
        StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        try {
            Method method = StorageManager.class.getDeclaredMethod("getVolumePaths");
            method.setAccessible(true);
            Object result = method.invoke(storageManager);
            if (result != null && result instanceof String[]) {
                String[] pathes = (String[]) result;
                if (pathes != null && pathes.length > 0) {
                    StatFs statFs;
                    for (String path : pathes) {
                        File file = new File(path);
                        if (!android.text.TextUtils.isEmpty(path) && file.exists()) {
                            statFs = new StatFs(path);
                            if (statFs.getBlockCount() * statFs.getBlockSize() != 0) {
                                pathsList.add(file);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return pathsList;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (isSelectedMode) {
                cancelSelectedMode();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void cancelSelectedMode() {
        mSelectedNodes.clear();
        isSelectedMode = false;
        refresh();
    }
}
