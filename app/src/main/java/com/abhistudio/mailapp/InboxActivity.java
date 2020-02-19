package com.abhistudio.mailapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.internal.WebMessageAdapter;

import android.Manifest;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;


//import com.dbflow5.query.Delete;
//import com.dbflow5.query.SQLite;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApi;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.api.client.auth.oauth.OAuthCredentialsResponse;
import com.google.api.client.auth.oauth.OAuthGetAccessToken;
import com.google.api.client.auth.oauth.OAuthSigner;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.auth.oauth2.OAuth2Utils;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.MessagePartHeader;

import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.Operator;
import com.raizlabs.android.dbflow.sql.language.SQLite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static com.abhistudio.mailapp.MainActivity.PREF_ACCOUNT_NAME;
import static com.abhistudio.mailapp.MainActivity.REQUEST_AUTHORIZATION;
import static com.abhistudio.mailapp.MainActivity.REQUEST_GOOGLE_PLAY_SERVICES;
import static com.abhistudio.mailapp.MainActivity.SCOPES;
import static com.abhistudio.mailapp.MainActivity.TAG;

public class InboxActivity extends AppCompatActivity {

    CoordinatorLayout lytParent;
    Toolbar toolbar;
    SwipeRefreshLayout refreshMessages;
    RecyclerView listMessages;
    FloatingActionButton fabCompose,fabCompose2;

    List<Message> messageList;
    MessagesAdapter messagesAdapter;

    GoogleAccountCredential mCredential;
    Gmail mService;
    SharedPreferences sharedPref;
    Utils mUtils;
    GoogleApi gapi;

    String pageToken = null;
    boolean isFetching = false;

    public static final int REQUEST_ACCOUNT_PICKER = 1000;
    public static final int REQUEST_AUTHORIZATION = 1001;
    public static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    public static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inbox);

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

        mService = null;
        sharedPref = InboxActivity.this.getSharedPreferences(getString(R.string.preferences_file_name), Context.MODE_PRIVATE);
        mUtils = new Utils(InboxActivity.this);

        String accountName = sharedPref.getString(PREF_ACCOUNT_NAME, null);
        if (accountName != null) {
            mCredential.setSelectedAccountName(accountName);

            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, mCredential)
                    .setApplicationName("MailApp")
                    .build();

        }
        else {
            startActivity(new Intent(InboxActivity.this, MainActivity.class));
            ActivityCompat.finishAffinity(InboxActivity.this);
        }

        messageList = new ArrayList<>();
        messagesAdapter = new MessagesAdapter(InboxActivity.this, messageList);

        initViews();

        getMessagesFromDB();


        fabCompose2 = findViewById(R.id.fabCompose2);

        fabCompose2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){

                mCredential.getGoogleAccountManager().invalidateAuthToken(getString(R.string.preferences_file_name));

                messageList.clear();
                messagesAdapter.notifyDataSetChanged();


                Intent intent = new Intent(InboxActivity.this,MainActivity.class);
                startActivity(intent);

                /*switch (v.getId()) {

                    case R.id.button2:
                        //signOut();

                        break;

                }*/

            }
        });
    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }


    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)) {
            // Start a dialog from which the user can choose an account
            startActivityForResult(
                    mCredential.newChooseAccountIntent(),
                    REQUEST_ACCOUNT_PICKER);
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }
    @Override
    public void onBackPressed() {
       super.onBackPressed();
         }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    if (!isFetching && mUtils.isDeviceOnline()) {
                        getMessagesFromDB();
                    } else
                        mUtils.showSnackbar(lytParent, getString(R.string.device_is_offline));
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(InboxActivity.this);
                    builder.setMessage(R.string.app_requires_auth);
                    builder.setPositiveButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            ActivityCompat.finishAffinity(InboxActivity.this);
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
                break;
        }


        messageList.clear();
        messagesAdapter.notifyDataSetChanged();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }


    private void initViews() {
        lytParent = findViewById(R.id.lytParent);
        toolbar = findViewById(R.id.toolbar);
        refreshMessages = findViewById(R.id.refreshMessages);
        listMessages = findViewById(R.id.listMessages);
        fabCompose = findViewById(R.id.fabCompose);

        toolbar.inflateMenu(R.menu.menu_inbox);

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) toolbar.getMenu().findItem(R.id.action_search).getActionView();
        searchView.setQueryHint(getString(R.string.search));
        searchView.setSearchableInfo(searchManager != null ? searchManager.getSearchableInfo(getComponentName()) : null);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                messagesAdapter.getFilter().filter(query);

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                messagesAdapter.getFilter().filter(newText);

                return true;
            }
        });

        refreshMessages.setColorSchemeResources(R.color.colorPrimary);
        refreshMessages.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!isFetching && mUtils.isDeviceOnline()) {
                    getMessagesFromDB();
                } else
                    mUtils.showSnackbar(lytParent, getString(R.string.device_is_offline));
            }
        });

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(InboxActivity.this);
        listMessages.setLayoutManager(mLayoutManager);
        listMessages.setItemAnimator(new DefaultItemAnimator());
        listMessages.addOnScrollListener(new EndlessRecyclerViewScrollListener((LinearLayoutManager) mLayoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                if (!isFetching && mUtils.isDeviceOnline())
                    new GetEmailsTask(false).execute();
            }
        });
        listMessages.setAdapter(messagesAdapter);

        fabCompose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(InboxActivity.this, ComposeActivity.class));
            }
        });
    }


    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                InboxActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }


    private void getMessagesFromDB() {
        InboxActivity.this.refreshMessages.setRefreshing(true);
        InboxActivity.this.messageList.clear();
        InboxActivity.this.messageList.addAll(SQLite.select().from(Message.class).queryList());
        InboxActivity.this.messagesAdapter.notifyDataSetChanged();
        InboxActivity.this.refreshMessages.setRefreshing(false);

        if (mUtils.isDeviceOnline())
            new GetEmailsTask(true).execute();
        else
            mUtils.showSnackbar(lytParent, getString(R.string.device_is_offline));
    }


    @SuppressLint("StaticFieldLeak")
    private class GetEmailsTask extends AsyncTask<Void, Void, List<Message>> {

        private int itemCount = 0;
        private boolean clear;
        private Exception mLastError = null;

        GetEmailsTask(boolean clear) {
            this.clear = clear;
        }

        @Override
        protected List<Message> doInBackground(Void... voids) {
            isFetching = true;
            List<Message> messageListReceived = null;

            if (clear) {
                Delete.table(Message.class);

                InboxActivity.this.pageToken = null;
            }

            try {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        InboxActivity.this.refreshMessages.setRefreshing(true);
                    }
                });
                String user = "me";
                String query = "in:inbox";
                ListMessagesResponse messageResponse = mService.users().messages().list(user).setQ(query).setMaxResults(20L).setPageToken(InboxActivity.this.pageToken).execute();
                InboxActivity.this.pageToken = messageResponse.getNextPageToken();

                messageListReceived = new ArrayList<>();
                List<com.google.api.services.gmail.model.Message> receivedMessages = messageResponse.getMessages();
                for (com.google.api.services.gmail.model.Message message : receivedMessages) {
                    com.google.api.services.gmail.model.Message actualMessage = mService.users().messages().get(user, message.getId()).execute();

                    Map<String, String> headers = new HashMap<>();
                    for (MessagePartHeader messagePartHeader : actualMessage.getPayload().getHeaders())
                        headers.put(
                                messagePartHeader.getName(), messagePartHeader.getValue()
                        );

                    Message newMessage = new Message(
                            actualMessage.getLabelIds(),
                            actualMessage.getSnippet(),
                            actualMessage.getPayload().getMimeType(),
                            headers,
                            actualMessage.getPayload().getParts(),
                            actualMessage.getInternalDate(),
                            InboxActivity.this.mUtils.getRandomMaterialColor(),
                            actualMessage.getPayload()
                    );

                    newMessage.save();
                    messageListReceived.add(newMessage);

                    itemCount++;
                }
            } catch (Exception e) {
                Log.w(TAG, e);
                mLastError = e;
                cancel(true);
            }

            return messageListReceived;
        }

        @Override
        protected void onPostExecute(List<Message> output) {
            isFetching = false;

            if (output != null && output.size() != 0) {
                if (clear) {
                    InboxActivity.this.messageList.clear();
                    InboxActivity.this.messageList.addAll(output);
                    InboxActivity.this.messagesAdapter.notifyDataSetChanged();
                } else {
                    int listSize = InboxActivity.this.messageList.size();
                    InboxActivity.this.messageList.addAll(output);
                    InboxActivity.this.messagesAdapter.notifyItemRangeInserted(listSize, itemCount);
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        InboxActivity.this.refreshMessages.setRefreshing(false);
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        InboxActivity.this.refreshMessages.setRefreshing(false);
                    }
                });
                InboxActivity.this.mUtils.showSnackbar(lytParent, getString(R.string.fetch_failed));
            }
        }

        @Override
        protected void onCancelled() {
            isFetching = false;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    InboxActivity.this.refreshMessages.setRefreshing(false);
                }
            });
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    mUtils.showSnackbar(lytParent, getString(R.string.an_error_occurred));
                }
            } else {
                mUtils.showSnackbar(lytParent, getString(R.string.an_error_occurred));
            }
        }

    }
}
