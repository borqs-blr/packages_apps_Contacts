/*
 * Copyright (C) 2013, The Linux Foundation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
 * BORQS Software Solutions Pvt Ltd. CONFIDENTIAL
 * Copyright (c) 2016 All rights reserved.
 *
 * The source code contained or described herein and all documents
 * related to the source code ("Material") are owned by BORQS Software
 * Solutions Pvt Ltd. No part of the Material may be used,copied,
 * reproduced, modified, published, uploaded,posted, transmitted,
 * distributed, or disclosed in any way without BORQS Software
 * Solutions Pvt Ltd. prior written permission.
 *
 * No license under any patent, copyright, trade secret or other
 * intellectual property right is granted to or conferred upon you
 * by disclosure or delivery of the Materials, either expressly, by
 * implication, inducement, estoppel or otherwise. Any license
 * under such intellectual property rights must be express and
 * approved by BORQS Software Solutions Pvt Ltd. in writing.
 *
 */

package com.android.contacts.group.local;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.LocalGroup;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Photo;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.LocalGroups;
import android.provider.LocalGroups.Group;
import android.provider.LocalGroups.GroupColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TabHost;
import android.widget.TextView;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.common.model.account.PhoneAccountType;
import com.android.contacts.common.list.AccountFilterActivity;
import com.android.contacts.common.SimContactsConstants;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.editor.MultiPickContactActivity;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.group.local.GroupNameChangeDialog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class MemberListActivity extends Activity implements OnItemClickListener,
        OnClickListener, OnItemLongClickListener {

    private static final String TAG = MemberListActivity.class.getSimpleName();

    private static final String TAB_TAG = "groups";

    private TabHost mTabHost;

    private Uri mUri;

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
            Contacts._ID, Contacts.DISPLAY_NAME, Contacts.PHOTO_ID, Contacts.LOOKUP_KEY,
            Data.RAW_CONTACT_ID, Contacts.PHOTO_THUMBNAIL_URI, Contacts.DISPLAY_NAME_PRIMARY
    };

    private static final int CODE_PICK_MEMBER = 1;
    private static final int CODE_CHANGE_GROUP_NAME = 2;

    private static final int MENU_GROUP_NAME = 0;
    private static final int MENU_ADD_MEMBER = 1;
    private static final int MENU_DELETE = 2;

    private static final String DATA = "data";
    private static final String RESULT = "result";

    static final int SUMMARY_ID_COLUMN_INDEX = 0;

    static final int SUMMARY_DISPLAY_NAME_INDEX = 1;

    static final int SUMMARY_PHOTO_ID_INDEX = 2;

    static final int SUMMARY_LOOKUP_KEY_INDEX = 3;

    static final int SUMMARY_RAW_CONTACTS_ID_INDEX = 4;

    static final int SUMMARY_PHOTO_URI_INDEX = 5;

    static final int SUMMARY_DISPLAY_NAME_PRIMARY_INDEX = 6;

    private static final int QUERY_TOKEN = 1;

    private QueryHandler mQueryHandler;

    private MemberListAdapter mAdapter;

    private ListView listView;

    private TextView emptyText;

    private Bundle removeSet;

    private View toolsBar;

    private Button deleteBtn;

    private Button moveBtn;

    private Button cancelBtn;

    private DeleteMembersThread mDeleteMembersTask;

    private ContactPhotoManager mContactPhotoManager;

    private boolean mPaused = false;

    private Group mGroup;

    private AddMembersTask mAddMembersTask;

    // indicate whether the task is canceled.
    private boolean mIsAddMembersTaskCanceled;

    /**
     * the max length of applyBatch is 500
     */
    private static final int BUFFER_LENGTH = 499;

    private static final int MSG_CANCEL = 1;

    private Handler mUpdateUiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            mDeleteMembersTask = null;
            removeSet.clear();
            mAdapter.refresh();
            new LocalGroupCountTask(MemberListActivity.this).execute();
        }
    };

    private class DeleteMembersThread extends Thread {
        public static final int TASK_RUNNING = 1;
        public static final int TASK_CANCEL = 2;
        public static final int OPERATION_DELETE = 1;
        public static final int OPERATION_MOVE = 2;
        private static final int BUFFER_LENGTH = 499;
        private Bundle mRemoveSet = null;
        private int status = TASK_RUNNING;
        private int mOperation = OPERATION_DELETE;
        private int mGroupId;

        public DeleteMembersThread(Bundle removeSet) {
            super();
            this.mRemoveSet = removeSet;
        }

        public int getStaus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public void setOperation(int operation) {
            this.mOperation = operation;
        }

        public void setGroupId(int groupId) {
            this.mGroupId = groupId;
        }

        private void deleteMembers(ArrayList<ContentProviderOperation> list, ContentResolver cr) {
            if (cr == null || list.size() == 0) {
                return;
            }

            try {
                cr.applyBatch(ContactsContract.AUTHORITY, list);
            } catch (Exception e) {
                Log.e("DeleteMembersThread", "apply batch by buffer error:" + e);
            }
            list.clear();
        }

        @Override
        public void run() {
            ContentProviderOperation.Builder builder = null;
            ContentResolver cr = getContentResolver();
            final ArrayList<ContentProviderOperation> removeList =
                    new ArrayList<ContentProviderOperation>();
            Set<String> keySet = mRemoveSet.keySet();
            Iterator<String> it = keySet.iterator();
            int i = 0;
            while (it.hasNext() && status == TASK_RUNNING) {
                if (OPERATION_DELETE == mOperation) {
                    builder = ContentProviderOperation.newDelete(Data.CONTENT_URI);
                    builder.withSelection(Data.RAW_CONTACT_ID + "=? and " + Data.MIMETYPE
                            + "=?", new String[] {
                            it.next(), LocalGroup.CONTENT_ITEM_TYPE
                    });
                } else {
                    builder = ContentProviderOperation.newUpdate(Data.CONTENT_URI);
                    builder.withSelection(Data.RAW_CONTACT_ID + "=? and " + Data.MIMETYPE
                            + "=?", new String[] {
                            it.next(), LocalGroup.CONTENT_ITEM_TYPE
                    });
                    builder.withValue(LocalGroup.DATA1, mGroupId);
                }

                removeList.add(builder.build());
                if (++i % BUFFER_LENGTH == 0) {
                    deleteMembers(removeList, cr);
                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        status = TASK_CANCEL;
                        Log.e("DeleteMembersThread", "exception :" + e);
                        break;
                    }
                }
            }

            if (status == TASK_RUNNING && removeList.size() != 0) {
                deleteMembers(removeList, cr);
            } else if (status == TASK_CANCEL) {
                Log.e("DeleteMembersThread", "cancel delete members");
            }
            status = TASK_CANCEL;
            mRemoveSet.clear();
            if (mUpdateUiHandler != null) {
                mUpdateUiHandler.sendEmptyMessage(0);
            }
        }
    }

    @Override
    protected void onStop() {
        if (mDeleteMembersTask != null) {
            mDeleteMembersTask.setStatus(DeleteMembersThread.TASK_CANCEL);
            mDeleteMembersTask = null;
        }
        // dismiss dialog and cancel the task in order to avoid a case that
        // GroupEditActivity does
        // not exist,but task is going on in background, then onPostExecute()
        // will cause Exception.
        if (mAddMembersTask != null && mAddMembersTask.mProgressDialog != null) {
            if (mAddMembersTask.mProgressDialog.isShowing()) {
                mAddMembersTask.mProgressDialog.dismiss();
                mIsAddMembersTaskCanceled = true;
            }
        }
        super.onStop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.group_manage);

        toolsBar = findViewById(R.id.tool_bar);
        deleteBtn = (Button) toolsBar.findViewById(R.id.btn_delete);
        moveBtn = (Button) toolsBar.findViewById(R.id.btn_move);
        cancelBtn = (Button) toolsBar.findViewById(R.id.btn_cancel);
        deleteBtn.setOnClickListener(this);
        moveBtn.setOnClickListener(this);
        cancelBtn.setOnClickListener(this);

        removeSet = new Bundle();
        mQueryHandler = new QueryHandler(this);
        mAdapter = new MemberListAdapter(this);
        mContactPhotoManager = ContactPhotoManager.getInstance(this);
        emptyText = (TextView) findViewById(R.id.emptyText);
        listView = (ListView) findViewById(R.id.member_list);
        listView.setOnItemClickListener(this);
        listView.setOnItemLongClickListener(this);
        listView.setAdapter(mAdapter);
        listView.setItemsCanFocus(true);
        listView.requestFocus();
        mUri = getIntent().getParcelableExtra(DATA);
        getContentResolver().registerContentObserver(
                Uri.withAppendedPath(LocalGroup.CONTENT_FILTER_URI,
                        Uri.encode(mUri.getLastPathSegment())), true, observer);
    }

    private ContentObserver observer = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            mAdapter.refresh();
        }
    };

    @Override
    protected void onPause() {
        super.onPause();

        mPaused = true;
        mAdapter.changeCursor(null);
    }

    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(observer);
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        selectAll();
        return true;
    }

    private void updateDisplay(boolean isEmpty) {
        if (isEmpty) {
            listView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.VISIBLE);
            emptyText.setVisibility(View.GONE);
        }
    }

    private void selectAll() {
        Cursor cursor = mAdapter.getCursor();
        if (cursor == null) {
            return;
        }

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            String contactId = String.valueOf(cursor.getLong(SUMMARY_RAW_CONTACTS_ID_INDEX));
            removeSet.putString(contactId, contactId);
        }
        mAdapter.refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPaused = false;
        mGroup = Group.restoreGroupById(getContentResolver(),
                Long.parseLong(mUri.getLastPathSegment()));
        startQuery();
        initView();
    }

    private void initView() {
        // avoid group is null, only for Monkey test.
        if (mGroup != null) {
            // If the length of group name is more than GROUP_NAME_MAX_LENGTH,
            // the
            // name will be limited here.
            String groupName = mGroup.getTitle();
            if (groupName.length() > GroupNameChangeDialog.GROUP_NAME_MAX_LENGTH) {
                groupName = groupName.substring(0, GroupNameChangeDialog.GROUP_NAME_MAX_LENGTH);
            }
            setTitle(groupName);
        }
    }

    private void showGroupNameChangeDialog() {
        new GroupNameChangeDialog(this,
                mGroup.getTitle(),
                new GroupNameChangeDialog.GroupNameChangeListener() {
            @Override
            public void onGroupNameChange(String name) {
                mGroup.setTitle((String) name);
                if (mGroup.update(getContentResolver()))
                    initView();
            }
        }).show();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_GROUP_NAME, 0, R.string.group_edit_field_hint_text);
        menu.add(0, MENU_ADD_MEMBER, 0, R.string.title_add_members);
        menu.add(0, MENU_DELETE, 0, R.string.menu_option_delete);
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_GROUP_NAME:
                showGroupNameChangeDialog();
                break;
            case MENU_ADD_MEMBER:
                Intent intent = new Intent(MultiPickContactActivity.ACTION_MULTI_PICK);
                intent.putExtra(MultiPickContactActivity.IS_CONTACT,true);
                intent.setClass(this, MultiPickContactActivity.class);
                ContactListFilter filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_ACCOUNT,
                        PhoneAccountType.ACCOUNT_TYPE, SimContactsConstants.PHONE_NAME, null, null);
                intent.putExtra(AccountFilterActivity.KEY_EXTRA_CONTACT_LIST_FILTER, filter);
                startActivityForResult(intent, CODE_PICK_MEMBER);
                break;
            case MENU_DELETE:
                new AlertDialog.Builder(this)
                        .setMessage(R.string.delete_locale_group_dialog_message)
                        .setTitle(R.string.delete_group_dialog_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (mGroup.delete(getContentResolver())) {
                                    finish();
                                }
                            }
                        }).setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK)
            switch (requestCode) {
                case CODE_PICK_MEMBER:
                    Bundle result = data.getExtras().getBundle(RESULT);
                    // define member object mAddMembersTask to use later.
                    mAddMembersTask = new AddMembersTask(result);
                    mAddMembersTask.execute();
            }
    }

    class AddMembersTask extends AsyncTask<Object, Object, Object> {
        private ProgressDialog mProgressDialog;
        private Bundle mResult;
        private int mSize;
        private Handler alertHandler = new Handler() {
            @Override
            public void dispatchMessage(Message message) {
                if (message.what == MSG_CANCEL) {
                    Toast.makeText(MemberListActivity.this, R.string.add_member_task_canceled,
                            Toast.LENGTH_LONG).show();
                } else if (message.what == 0) {
                    Toast.makeText(MemberListActivity.this, R.string.toast_not_add,
                            Toast.LENGTH_LONG)
                            .show();
                }
            }
        };

        AddMembersTask(Bundle result) {
            mSize = result.size();
            mResult = result;
            HandlerThread thread = new HandlerThread("DownloadTask");
            thread.start();
        }

        protected void onPostExecute(Object result) {
            if (mProgressDialog != null && mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            new LocalGroupCountTask(MemberListActivity.this).execute();
        }

        @Override
        protected void onPreExecute() {
            mIsAddMembersTaskCanceled = false;
            mProgressDialog = new ProgressDialog(MemberListActivity.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(mSize);
            mProgressDialog.show();
            mProgressDialog.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {

                    // if dialog is canceled, cancel the task also.
                    mIsAddMembersTaskCanceled = true;
                }
            });
        }

        @Override
        protected Bundle doInBackground(Object... params) {
            proccess();
            return null;
        }

        public void proccess() {
            boolean hasInvalide = false;
            int progressIncrement = 0;
            ContentValues values = new ContentValues();
            // add Non-null protection of group for monkey test
            if (null != mGroup) {
                values.put(LocalGroup.DATA1, mGroup.getId());
            }

            Set<String> keySet = mResult.keySet();
            Iterator<String> iterator = keySet.iterator();

            // add a ContentProviderOperation update list.
            final ArrayList<ContentProviderOperation> updateList =
                    new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;
            mIsAddMembersTaskCanceled = false;
            while (iterator.hasNext()) {
                if (mIsAddMembersTaskCanceled) {
                    alertHandler.sendEmptyMessage(MSG_CANCEL);
                    break;
                }
                if (progressIncrement++ % 2 == 0) {
                    if (mProgressDialog != null) {
                        mProgressDialog.incrementProgressBy(2);
                    } else if (mProgressDialog != null && mProgressDialog.isShowing()) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }
                }
                String id = iterator.next();
                Cursor cursor = null;
                try {
                    cursor = getContentResolver().query(RawContacts.CONTENT_URI, new String[] {
                            RawContacts._ID, RawContacts.ACCOUNT_TYPE
                    }, RawContacts.CONTACT_ID + "=?", new String[] {
                            id
                    }, null);
                    if (cursor.moveToNext()) {
                        String rawId = String.valueOf(cursor.getLong(0));

                        if (!PhoneAccountType.ACCOUNT_TYPE.equals(cursor.getString(1))) {
                            hasInvalide = true;
                            continue;
                        }

                        builder = ContentProviderOperation.newDelete(Data.CONTENT_URI);
                        builder.withSelection(Data.RAW_CONTACT_ID + "=? and " + Data.MIMETYPE
                                + "=?", new String[] {
                                rawId, LocalGroup.CONTENT_ITEM_TYPE
                        });

                        // add the delete operation to the update list.
                        updateList.add(builder.build());

                        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                        // add Non-null protection of group for monkey test
                        if (null != mGroup) {
                            builder.withValue(LocalGroup.DATA1, mGroup.getId());
                        }
                        builder.withValue(Data.RAW_CONTACT_ID, rawId);
                        builder.withValue(Data.MIMETYPE, LocalGroup.CONTENT_ITEM_TYPE);

                        // add the insert operation to the update list.
                        updateList.add(builder.build());
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }

            // if task is canceled ,still update the database with the data in
            // updateList.

            // apply batch to execute the delete and insert operation.
            if (updateList.size() > 0) {
                addMembersApplyBatchByBuffer(updateList, getContentResolver());
            }
            if (hasInvalide) {
                alertHandler.sendEmptyMessage(0);
            }
        }

        private void addMembersApplyBatchByBuffer(ArrayList<ContentProviderOperation> list,
                                                  ContentResolver contentResolver) {
            final ArrayList<ContentProviderOperation> temp =
                    new ArrayList<ContentProviderOperation>(BUFFER_LENGTH);
            int bufferSize = list.size() / BUFFER_LENGTH;
            for (int index = 0; index <= bufferSize; index++) {
                temp.clear();
                if (index == bufferSize) {
                    for (int i = index * BUFFER_LENGTH; i < list.size(); i++) {
                        temp.add(list.get(i));
                    }
                } else {
                    for (int i = index * BUFFER_LENGTH; i < index * BUFFER_LENGTH + BUFFER_LENGTH;
                         i++) {
                        temp.add(list.get(i));
                    }
                }
                if (!temp.isEmpty()) {
                    try {
                        contentResolver.applyBatch(ContactsContract.AUTHORITY, temp);
                        if (mProgressDialog != null) {
                            mProgressDialog.incrementProgressBy(temp.size() / 4);
                        } else if (mProgressDialog != null && mProgressDialog.isShowing()) {
                            mProgressDialog.dismiss();
                            mProgressDialog = null;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "apply batch by buffer error:" + e);
                    }
                }
            }
        }
    }

    private void addEditView() {
        Intent intent = new Intent(this, GroupEditActivity.class);
        intent.setData(mUri);

        mTabHost.addTab(mTabHost.newTabSpec(TAB_TAG)
                .setIndicator(TAB_TAG, getResources().getDrawable(R.drawable.ic_launcher_contacts))
                .setContent(intent));
    }

    private void startQuery() {
        mQueryHandler.cancelOperation(QUERY_TOKEN);

        mQueryHandler.startQuery(
                QUERY_TOKEN,
                null,
                Uri.withAppendedPath(LocalGroup.CONTENT_FILTER_URI,
                        Uri.encode(mUri.getLastPathSegment())), CONTACTS_SUMMARY_PROJECTION, null,
                null, null);
    }

    private class QueryHandler extends AsyncQueryHandler {
        protected final WeakReference<MemberListActivity> mActivity;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<MemberListActivity>((MemberListActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (mPaused) {
                return;
            }
            final MemberListActivity activity = mActivity.get();
            activity.mAdapter.changeCursor(cursor);
            updateDisplay(cursor == null || cursor.getCount() == 0);
        }
    }

    private class MemberListAdapter extends CursorAdapter {

        public MemberListAdapter(Context context) {
            super(context, null);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            long contactId = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
            String lookupId = cursor.getString(SUMMARY_LOOKUP_KEY_INDEX);
            long rawContactsId = cursor.getLong(SUMMARY_RAW_CONTACTS_ID_INDEX);
            String displayName = cursor.getString(SUMMARY_DISPLAY_NAME_INDEX);
            long photoId = cursor.getLong(SUMMARY_PHOTO_ID_INDEX);

            TextView contactNameView = (TextView) view.findViewById(R.id.contact_name);
            QuickContactBadge quickContactBadge = (QuickContactBadge) view
                    .findViewById(R.id.contact_icon);
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.pick_contact_check);

            contactNameView
                    .setText(displayName == null ? getString(R.string.unknown) : displayName);
            loadContactsPhotoByCursor(quickContactBadge, cursor);
            quickContactBadge.assignContactUri(Contacts.getLookupUri(contactId, lookupId));

            String key = String.valueOf(rawContactsId);
            setCheckStatus(key, checkBox);
            view.setTag(key);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return LayoutInflater.from(context).inflate(R.layout.member_item, parent, false);
        }

        private void refresh() {
            super.onContentChanged();
            /**
             * Update the removeSet: When user go into contact details screen by
             * click the avatar, then remove it, the content changed but
             * removeSet not, so the tools bar isn't correct. Needn't do this
             * job if removeSet is empty.
             */
            Cursor cursor = getCursor();
            if (!removeSet.isEmpty() && cursor != null && !cursor.isClosed()) {
                cursor.moveToPosition(-1);
                Bundle newRemoveSet = new Bundle();
                while (cursor.moveToNext()) {
                    String contactId = String.valueOf(
                            cursor.getLong(SUMMARY_RAW_CONTACTS_ID_INDEX));
                    if (removeSet.containsKey(contactId)) {
                        newRemoveSet.putString(contactId, contactId);
                    }
                }
                if (newRemoveSet.size() != removeSet.size()) {
                    removeSet = newRemoveSet;
                }
            }
            updateToolsBar();
            updateDisplay(this.getCount() == 0);
        }

    }

    public Bitmap getContactsPhoto(long photoId) {
        Bitmap contactPhoto = null;
        Cursor cursor = null;
        if (photoId > 0)
            try {
                cursor = getContentResolver().query(Data.CONTENT_URI, new String[] {
                        Photo.PHOTO
                }, Photo._ID + "=?", new String[] {
                        String.valueOf(photoId)
                }, null);

                if (cursor != null) {
                    if (cursor.moveToNext()) {
                        byte[] bytes = cursor.getBlob(0);
                        contactPhoto = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        if (contactPhoto == null) {
            contactPhoto = BitmapFactory.decodeResource(this.getResources(),
                    R.drawable.ic_contact_picture_holo_light);
        }
        return framePhoto(contactPhoto);
    }

    private Bitmap framePhoto(Bitmap photo) {
        final Resources r = getResources();
        final Drawable frame = r.getDrawable(com.android.internal.R.drawable.ic_contact_picture);

        final int width = r.getDimensionPixelSize(R.dimen.contact_shortcut_frame_width);
        final int height = r.getDimensionPixelSize(R.dimen.contact_shortcut_frame_height);

        frame.setBounds(0, 0, width, height);

        final Rect padding = new Rect();
        frame.getPadding(padding);

        final Rect source = new Rect(0, 0, photo.getWidth(), photo.getHeight());
        final Rect destination = new Rect(padding.left, padding.top, width - padding.right, height
                - padding.bottom);

        final int d = Math.max(width, height);
        final Bitmap b = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(b);

        c.translate((d - width) / 2.0f, (d - height) / 2.0f);
        frame.draw(c);
        c.drawBitmap(photo, source, destination, new Paint(Paint.FILTER_BITMAP_FLAG));

        return scaleToAppIconSize(b);
    }

    private Bitmap scaleToAppIconSize(Bitmap photo) {
        final int mIconSize = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
        // Setup the drawing classes
        Bitmap icon = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);

        // Copy in the photo
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);
        Rect src = new Rect(0, 0, photo.getWidth(), photo.getHeight());
        Rect dst = new Rect(0, 0, mIconSize, mIconSize);
        canvas.drawBitmap(photo, src, dst, photoPaint);

        return icon;
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        String contactId = (String) arg1.getTag();
        CheckBox checkBox = (CheckBox) arg1.findViewById(R.id.pick_contact_check);
        if (removeSet.containsKey(contactId)) {
            removeSet.remove(contactId);
        } else {
            removeSet.putString(contactId, contactId);
        }
        setCheckStatus(contactId, checkBox);
        updateToolsBar();
    }

    private void updateToolsBar() {
        if (mDeleteMembersTask != null
                && mDeleteMembersTask.getStaus() == DeleteMembersThread.TASK_RUNNING) {
            if (toolsBar.getVisibility() != View.GONE) {
                toolsBar.setVisibility(View.GONE);
            }
            return;
        }

        if (removeSet.isEmpty() && toolsBar.getVisibility() == View.VISIBLE) {
            toolsBar.setVisibility(View.GONE);
            toolsBar.startAnimation(AnimationUtils.loadAnimation(this, R.anim.tools_bar_disappear));
        } else if (!removeSet.isEmpty() && toolsBar.getVisibility() == View.GONE) {
            toolsBar.setVisibility(View.VISIBLE);
            toolsBar.startAnimation(AnimationUtils.loadAnimation(this, R.anim.tools_bar_appear));
        }
    }

    private void setCheckStatus(String contactId, CheckBox checkBox) {
        checkBox.setChecked(removeSet.containsKey(contactId));
    }

    @Override
    public void onClick(View v) {
        if (v == cancelBtn) {
            removeSet.clear();
            mAdapter.refresh();
        } else if (v == deleteBtn) {
            removeContactsFromGroup();
            mAdapter.refresh();
        } else if (v == moveBtn) {
            chooseGroup();
        }

    }

    private void chooseGroup() {
        final Cursor cursor = this.getContentResolver().query(LocalGroups.CONTENT_URI,
                new String[] {
                        GroupColumns._ID, GroupColumns.TITLE
                }, null, null, null);
        int index = -1;
        int count = -1;
        if (cursor != null && cursor.getCount() > 0) {
            while (cursor.moveToNext()) {
                count++;
                if (mUri.getLastPathSegment().equals(cursor.getString(0))) {
                    index = count;
                    break;
                }
            }
        }
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                cursor.moveToPosition(which);
                moveContactstoGroup(cursor.getInt(0));
                dialog.dismiss();
            }
        };
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setSingleChoiceItems(cursor, index, GroupColumns.TITLE, listener)
                .setTitle(R.string.group_selector)
                .show();
        dialog.setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface dialog) {
                if (cursor != null && !cursor.isClosed())
                    cursor.close();
            }
        });
    }

    private void moveContactstoGroup(int groupId) {
        if (removeSet != null && removeSet.size() > 0) {
            Bundle bundleData = (Bundle) removeSet.clone();
            if (mDeleteMembersTask != null) {
                mDeleteMembersTask.setStatus(DeleteMembersThread.TASK_CANCEL);
                mDeleteMembersTask = null;
            }
            mDeleteMembersTask = new DeleteMembersThread(bundleData);
            mDeleteMembersTask.setOperation(DeleteMembersThread.OPERATION_MOVE);
            mDeleteMembersTask.setGroupId(groupId);
            mDeleteMembersTask.start();
        }
    }

    private void removeContactsFromGroup() {
        if (removeSet != null && removeSet.size() > 0) {
            Bundle bundleData = (Bundle) removeSet.clone();
            if (mDeleteMembersTask != null) {
                mDeleteMembersTask.setStatus(DeleteMembersThread.TASK_CANCEL);
                mDeleteMembersTask = null;
            }
            mDeleteMembersTask = new DeleteMembersThread(bundleData);
            mDeleteMembersTask.setOperation(DeleteMembersThread.OPERATION_DELETE);
            mDeleteMembersTask.start();
        }
    }

    private void loadContactsPhotoByCursor(ImageView view, Cursor cursor) {
        long photoId = cursor.getLong(SUMMARY_PHOTO_ID_INDEX);

        if (photoId != 0) {
            mContactPhotoManager.loadThumbnail(view, photoId, null, false, null);
        } else {
            final String photoUriString = cursor.getString(SUMMARY_PHOTO_URI_INDEX);
            final Uri photoUri = photoUriString == null ? null : Uri.parse(photoUriString);
            DefaultImageRequest request = null;
            if (photoUri == null) {
                request = getDefaultImageRequestFromCursor(cursor,
                        SUMMARY_DISPLAY_NAME_PRIMARY_INDEX, SUMMARY_LOOKUP_KEY_INDEX);
            }
            mContactPhotoManager.loadPhoto(view, photoUri, null, -1, false, request);
        }
    }

    private DefaultImageRequest getDefaultImageRequestFromCursor(Cursor cursor,
            int displayNameColumn, int lookupKeyColumn) {
        final String displayName = cursor.getString(displayNameColumn);
        final String lookupKey = cursor.getString(lookupKeyColumn);
        return new DefaultImageRequest(displayName, lookupKey);
    }

}
