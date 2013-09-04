package org.commcare.dalvik.activities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.commcare.android.database.SqlStorage;
import org.commcare.android.database.user.models.FormRecord;
import org.commcare.android.framework.CommCareActivity;
import org.commcare.android.framework.DeviceDetailFragment;
import org.commcare.android.framework.DeviceListFragment;
import org.commcare.android.framework.UiElement;
import org.commcare.android.framework.DeviceDetailFragment.FileServerAsyncTask;
import org.commcare.android.framework.DeviceListFragment.DeviceActionListener;
import org.commcare.android.models.notifications.NotificationMessageFactory;
import org.commcare.android.models.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.android.tasks.SendTask;
import org.commcare.android.tasks.WiFiDirectTask;
import org.commcare.android.tasks.templates.CommCareTask;
import org.commcare.android.util.AndroidStreamUtil;
import org.commcare.android.util.FileUtil;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.services.FormTransferService;
import org.commcare.dalvik.services.WiFiDirectBroadcastReceiver;
import org.javarosa.core.services.locale.Localization;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/*
 * Copyright (C) 2011 The Android Open Source Project
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

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class CommCareWiFiDirectActivity extends CommCareActivity<CommCareWiFiDirectActivity> implements ChannelListener, DeviceActionListener, ConnectionInfoListener, ActionListener {
	
	public static final String TAG = "cc-wifidirect";
	
	WifiP2pManager mManager;
	Channel mChannel;
	BroadcastReceiver mReceiver;
	
	IntentFilter mIntentFilter;
	
	Button discoverButton;
	Button sendButton;
	Button hostButton;
	Button unzipButton;
	Button submitButton;
	
	public static String baseDirectory;
	public static String sourceDirectory;
	public static String sourceZipDirectory;
	public static String receiveDirectory;
	public static String receiveZipDirectory;
	public static String writeDirectory;
	
	WifiP2pInfo info;
	
	private boolean isWifiP2pEnabled = false;
	
	@UiElement(value = R.id.wifi_direct_status_text, locale="bulk.form.messages")
	public TextView txtInteractiveMessages;
	public TextView ownerStatusText;
	public TextView myStatusText;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.wifi_direct_main);
		
		ownerStatusText= (TextView)this.findViewById(R.id.owner_status_text);
		
		myStatusText = (TextView)this.findViewById(R.id.my_status_text);
		
		try{
			ArrayList<String> externalMounts = FileUtil.getExternalMounts();
			String baseDir = externalMounts.get(0);
			
			baseDirectory = baseDir + "/" + "wifidirect";
			sourceDirectory = baseDirectory + "/source";
			sourceZipDirectory = baseDirectory + "/zipSource.zip";
			receiveDirectory = baseDirectory + "/receive";
			receiveZipDirectory = receiveDirectory + "/zipDest.zip";
			writeDirectory = baseDirectory + "/write";
		} catch(NullPointerException npe){
			myStatusText.setText("Can't access external SD Card");
			TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
		}
		
		discoverButton = (Button)this.findViewById(R.id.discover_button);
		discoverButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				discoverPeers();
			}
			
		});
		
		sendButton = (Button)this.findViewById(R.id.send_button);
		sendButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				prepareFileTransfer();
			}
			
		});
		
		hostButton = (Button)this.findViewById(R.id.host_button);
		hostButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				hostGroup();
			}
			
		});
		
		unzipButton = (Button)this.findViewById(R.id.unzip_button);
		unzipButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				Log.d(TAG, "onClick unzip");
				unzipFiles();
			}
			
		});
		
		submitButton = (Button)this.findViewById(R.id.submit_button);
		submitButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				submitFiles();
			}
			
		});
		
	    mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
	    mChannel = mManager.initialize(this, getMainLooper(), null);
	    
	    mIntentFilter = new IntentFilter();
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
	    mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

	}
	
	/*register the broadcast receiver */
	protected void onResume() {
	    super.onResume();
	    mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(mReceiver, mIntentFilter);
	    updateStatusText();
	}
	/* unregister the broadcast receiver */
	@Override
	protected void onPause() {
	    super.onPause();
	    unregisterReceiver(mReceiver);
	}
	
	public void hostGroup(){
		new FileServerAsyncTask(this, this.findViewById(R.id.wifi_direct_status_text), this).execute();
		mManager.createGroup(mChannel, this);
	}
	
	public void submitFiles(){
		
		final String url = this.getString(R.string.PostURL);
		
		File receiveFolder = new File (writeDirectory);
		
		if(!receiveFolder.isDirectory()){
			return;
		}
		
		File[] files = receiveFolder.listFiles();
		
		int formsOnSD = files.length;
				
		
		//if there're no forms to dump, just return
		if(formsOnSD == 0){
			myStatusText.setText(Localization.get("bulk.form.no.unsynced.submit"));
			TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
			return;
		}
		
		SharedPreferences settings = CommCareApplication._().getCurrentApp().getAppPreferences();
		SendTask<CommCareWiFiDirectActivity> mSendTask = new SendTask<CommCareWiFiDirectActivity>(getApplicationContext(), CommCareApplication._().getCurrentApp().getCommCarePlatform(), 
				settings.getString("PostURL", url), myStatusText, receiveFolder){
			
			protected int taskId = BULK_SEND_ID;
			
			@Override
			protected void deliverResult( CommCareWiFiDirectActivity receiver, Boolean result) {
				
				if(result == Boolean.TRUE){
					CommCareApplication._().clearNotifications(CommCareHomeActivity.AIRPLANE_MODE_CATEGORY);
			        Intent i = new Intent(getIntent());
			        //i.putExtra(KEY_NUMBER_DUMPED, formsOnSD);
					receiver.setResult(BULK_SEND_ID, i);
					receiver.finish();
					return;
				} else {
					//assume that we've already set the error message, but make it look scary
					CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Sync_AirplaneMode, CommCareHomeActivity.AIRPLANE_MODE_CATEGORY));
					receiver.TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
				}
			}

			@Override
			protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
				receiver.updateProgress(BULK_SEND_ID, update[0]);
				receiver.myStatusText.setText(update[0]);
			}

			@Override
			protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
				receiver.myStatusText.setText(Localization.get("bulk.form.error", new String[] {e.getMessage()}));
				receiver.TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
			}
		};
		mSendTask.connect(CommCareWiFiDirectActivity.this);
		mSendTask.execute();
	}
	
	public void unzipFiles(){
		
		Log.d(TAG, "creating unzip task");
		
		CommCareTask<String, String, Boolean, CommCareWiFiDirectActivity> task = new CommCareTask<String, String, Boolean, CommCareWiFiDirectActivity>() {

			@Override
			protected Boolean doTaskBackground(String... params) {
				File archive = new File(params[0]);
				File destination = new File(params[1]);
				
				Log.d(TAG, "unzipping with archive: " + archive + " , dest: " + destination);
				
				int count = 0;
				ZipFile zipfile;
				//From stackexchange
				try {
					zipfile = new ZipFile(archive);
				} catch(IOException ioe) {
					publishProgress(Localization.get("mult.install.bad"));
					return false;
				}
                for (Enumeration e = zipfile.entries(); e.hasMoreElements();) {
                	Localization.get("mult.install.progress", new String[] {String.valueOf(count)});
                	count++;
                    ZipEntry entry = (ZipEntry) e.nextElement();
                    
                    if (entry.isDirectory()) {
                    	FileUtil.createFolder(new File(destination, entry.getName()).toString());
                    	//If it's a directory we can move on to the next one
                    	continue;
                    }

                    File outputFile = new File(destination, entry.getName());
                    if (!outputFile.getParentFile().exists()) {
                    	FileUtil.createFolder(outputFile.getParentFile().toString());
                    }
                    if(outputFile.exists()) {
                    	//Try to overwrite if we can
                    	if(!outputFile.delete()) {
                    		//If we couldn't, just skip for now
                    		continue;
                    	}
                    }
                    BufferedInputStream inputStream;
                    try {
                    	inputStream = new BufferedInputStream(zipfile.getInputStream(entry));
                    } catch(IOException ioe) {
                		this.publishProgress(Localization.get("mult.install.progress.badentry", new String[] {entry.getName()}));
                		return false;
                    }
                    
                    BufferedOutputStream outputStream;
                    try {
                    	outputStream = new BufferedOutputStream(new FileOutputStream(outputFile));
                    } catch(IOException ioe) {
                		this.publishProgress(Localization.get("mult.install.progress.baddest", new String[] {outputFile.getName()}));
                		return false;
                	}

                    try {
                    	try {
                    		AndroidStreamUtil.writeFromInputToOutput(inputStream, outputStream);
                    	} catch(IOException ioe) {
                    		this.publishProgress(Localization.get("mult.install.progress.errormoving"));
                    		return false;
                    	}
                    } finally {
                    	try {
                        outputStream.close();
                    	} catch(IOException ioe) {}
                    	try {
                        inputStream.close();
                    	} catch(IOException ioe) {}
                    }
                }

				
				return true;
			}

			@Override
			protected void deliverResult( CommCareWiFiDirectActivity receiver, Boolean result) {
				if(result == Boolean.TRUE){
					//receiver.done = true;
					receiver.setResult(Activity.RESULT_OK);
					receiver.finish();
					return;
				} else {
					//assume that we've already set the error message, but make it look scary
					receiver.TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
				}
			}

			@Override
			protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
				receiver.updateProgress(CommCareTask.GENERIC_TASK_ID, update[0]);
				receiver.myStatusText.setText(update[0]);
			}

			@Override
			protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
				Log.d(TAG, "unzip deliver error: " + e.getMessage());
				receiver.myStatusText.setText(Localization.get("mult.install.error", new String[] {e.getMessage()}));
				receiver.TransplantStyle(myStatusText, R.layout.template_text_notification_problem);
			}
		};
		
		task.connect(CommCareWiFiDirectActivity.this);
		Log.d(TAG, "executing task with: " + receiveZipDirectory + " , " + writeDirectory);
		task.execute(receiveZipDirectory, writeDirectory);
	}
	
	/* if successful, broadcasts WIFI_P2P_Peers_CHANGED_ACTION intent with list of peers
	 * received in WiFiDirectBroadcastReceiver class
	 */
	public void discoverPeers(){
		
		Log.d(TAG, "discoverPeers");
		
		if(!isWifiP2pEnabled){
            Toast.makeText(CommCareWiFiDirectActivity.this, "WiFi Direct is Off",
                    Toast.LENGTH_SHORT).show();
            return;
		}
		
        final DeviceListFragment fragment = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);
		fragment.onInitiateDiscovery();
        
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(CommCareWiFiDirectActivity.this, "Discovery Initiated",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(CommCareWiFiDirectActivity.this, "Discovery Failed : " + reasonCode,
                        Toast.LENGTH_SHORT).show();
            }
        });
	}
	
    public void resetData() {
        DeviceListFragment fragmentList = (DeviceListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_list);
        DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
        if (fragmentDetails != null) {
            fragmentDetails.resetViews();
        }
    }

    @Override
    public void showDetails(WifiP2pDevice device) {
    	
    	Log.d(TAG, "showDetails");
    	
        DeviceDetailFragment fragment = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.showDetails(device);
    }
    
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void connect(WifiP2pConfig config) {
    	
    	Log.d(TAG, "connect in activity");
    	
        mManager.connect(mChannel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(CommCareWiFiDirectActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void disconnect() {
        final DeviceDetailFragment fragment = (DeviceDetailFragment) getSupportFragmentManager()
                .findFragmentById(R.id.frag_detail);
        fragment.resetViews();
        mManager.removeGroup(mChannel, new ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

            }

            @Override
            public void onSuccess() {
                fragment.getView().setVisibility(View.GONE);
            }

        });
    }

    @Override
    public void onChannelDisconnected() {
        // we will try once more
        if (mManager != null) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
            //resetData();
            mManager.initialize(this, getMainLooper(), this);
        } else {
            Toast.makeText(this,
                    "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void cancelDisconnect() {

        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (mManager != null) {
            final DeviceListFragment fragment = (DeviceListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.frag_list);
            if (fragment.getDevice() == null
                    || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
                    || fragment.getDevice().status == WifiP2pDevice.INVITED) {

                mManager.cancelConnect(mChannel, new ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(CommCareWiFiDirectActivity.this, "Aborting connection",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(CommCareWiFiDirectActivity.this,
                                "Connect abort request failed. Reason Code: " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

    }
    
    public static void deleteIfExists(String filePath){
    	File toDelete = new File(filePath);
    	if(toDelete.exists()){
    		toDelete.delete();
    	}
    }
    
    public void prepareFileTransfer(){
    	Log.d(CommCareWiFiDirectActivity.TAG, "Preparing File Transfer");
    	CommCareWiFiDirectActivity.deleteIfExists(sourceZipDirectory);
    	zipFiles();
    }
    
    public void onZipSuccesful(){
    	Log.d(CommCareWiFiDirectActivity.TAG, "Zup successful, attempting to send");
    	myStatusText.setText("Zip successful, attempting to send files...");
    	CommCareWiFiDirectActivity.deleteIfExists(sourceDirectory);
    	sendFiles();
    }
    
    public void onZipError(){
    	Log.d(CommCareWiFiDirectActivity.TAG, "Zup unsuccesful");
    	
    }
    
    public void zipFiles(){
    	Log.d(CommCareWiFiDirectActivity.TAG, "Zipping Files2");
			WiFiDirectTask mDirectTask = new WiFiDirectTask(this, CommCareApplication._().getCurrentApp().getCommCarePlatform(), 
					myStatusText){

				protected int taskId = BULK_DUMP_ID;
				
				@Override
				protected void deliverResult( CommCareWiFiDirectActivity receiver, Boolean result) {
					if(result == Boolean.TRUE){
						receiver.onZipSuccesful();
						return;
					} else {
						receiver.onZipError();
						return;
					}
				}

				@Override
				protected void deliverUpdate(CommCareWiFiDirectActivity receiver, String... update) {
					receiver.updateProgress(BULK_DUMP_ID, update[0]);
					receiver.myStatusText.setText(update[0]);
				}

				@Override
				protected void deliverError(CommCareWiFiDirectActivity receiver, Exception e) {
					receiver.myStatusText.setText("error zipping files");
					receiver.TransplantStyle(receiver.myStatusText, R.layout.template_text_notification_problem);
				}

			};
			mDirectTask.connect(CommCareWiFiDirectActivity.this);
			mDirectTask.execute();
    }
    
    public void sendFiles(){
    	TextView statusText = (TextView) this.findViewById(R.id.wifi_direct_status_text);
    	statusText.setText("Sending files..." );
    	Log.d(CommCareWiFiDirectActivity.TAG, "Intent----------- " );
    	Intent serviceIntent = new Intent(this, FormTransferService.class);
    	serviceIntent.setAction(FormTransferService.ACTION_SEND_FORM);
    	serviceIntent.putExtra(FormTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
            info.groupOwnerAddress.getHostAddress());
    	serviceIntent.putExtra(FormTransferService.EXTRAS_GROUP_OWNER_PORT, 8988);
    	String filePath = sourceZipDirectory;
    	statusText.setText("Sending files from zipFile: " + filePath );
    	
    	serviceIntent.putExtra(FormTransferService.EXTRAS_FILE_PATH, filePath);
    	this.startService(serviceIntent);
        Log.d(CommCareWiFiDirectActivity.TAG, " service started");
    }
    
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {

		ProgressDialog progressDialog = new ProgressDialog(this);
		progressDialog.setTitle("Progress Title");
		progressDialog.setMessage("Progress message");
		progressDialog.setCancelable(false);
		return progressDialog;
	}

	@Override
	public void onConnectionInfoAvailable(WifiP2pInfo info) {
    	Log.d(CommCareWiFiDirectActivity.TAG, "activity onConnectionInfoAvailable");
    	
    	this.info = info;

        setIsOwner(info.groupFormed && info.isGroupOwner);
        
        myStatusText.setText("Connected to group");
        
	}
	
    public static class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;
        private CommCareWiFiDirectActivity mListener;

        /**
         * @param context
         * @param statusText
         */
        public FileServerAsyncTask(Context context, View statusText, CommCareWiFiDirectActivity mListener) {
        	Log.d(CommCareWiFiDirectActivity.TAG, "new fileasync task");
            this.context = context;
            this.statusText = (TextView) statusText;
            this.mListener = mListener;
            
        }

        @Override
        protected String doInBackground(Void... params) {
            try {
                ServerSocket serverSocket = new ServerSocket(8988);
                Socket client = serverSocket.accept();
                final File f = new File(receiveZipDirectory);

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();

                Log.d(CommCareWiFiDirectActivity.TAG, "server: copying files " + f.toString());
                InputStream inputstream = client.getInputStream();
                copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
                return f.getAbsolutePath();
            } catch (IOException e) {
                Log.e(CommCareWiFiDirectActivity.TAG, e.getMessage());
                return null;
            }
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                statusText.setText("File copied - " + result);
            }
            mListener.unzipFiles();
        }

        /*
         * (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a server socket");
        }

    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
    	Log.d(CommCareWiFiDirectActivity.TAG, "Copying file");
    	if(inputStream == null){
    		Log.d(CommCareWiFiDirectActivity.TAG, "Input Null");
    	}
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
            	Log.d(CommCareWiFiDirectActivity.TAG, "Copying file : " + buf);
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(CommCareWiFiDirectActivity.TAG, e.toString());
            return false;
        }
        return true;
    }

	@Override
	public void onFailure(int reason) {
		Log.d(CommCareWiFiDirectActivity.TAG, "new onFailure: " + reason);
		
	}

	@Override
	public void onSuccess() {
		Log.d(CommCareWiFiDirectActivity.TAG, "new onSuccess");
		
	}
	
	public void setIsOwner(boolean owner){
		if(owner){
			ownerStatusText.setText("This device is the owner.");
		}
		else{
			ownerStatusText.setText("This device is not the owner.");
		}
	}
	
	public void updateStatusText(){
		
		String statusText = "";
		
    	SqlStorage<FormRecord> storage =  CommCareApplication._().getUserStorage(FormRecord.class);
    	//Get all forms which are either unsent or unprocessed
    	Vector<Integer> ids = storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_UNSENT});
    	ids.addAll(storage.getIDsForValues(new String[] {FormRecord.META_STATUS}, new Object[] {FormRecord.STATUS_COMPLETE}));
    	
    	int numUnsyncedForms = ids.size();
    	
    	statusText = "You have: " + numUnsyncedForms + "unsynced forms. ";
    	
    	File zipSource = new File(sourceZipDirectory);
    	if(zipSource.exists()){
    		statusText += " There is a zip source file present.";
    	}
    	
    	File zipDest = new File(receiveZipDirectory);
    	if(zipDest.exists()){
    		statusText += " There is a zip destination file present.";
    	}
    	
    	myStatusText.setText(statusText);
    	
	}

}
