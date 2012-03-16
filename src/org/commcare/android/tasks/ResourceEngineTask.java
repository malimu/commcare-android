/**
 * 
 */
package org.commcare.android.tasks;

import java.util.Vector;

import org.commcare.android.application.CommCareApplication;
import org.commcare.android.util.AndroidCommCarePlatform;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.CommCarePlatform;
import org.commcare.xml.util.UnfullfilledRequirementsException;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

/**
 * This task is responsible for 
 * 
 * @author ctsims
 *
 */
public class ResourceEngineTask extends AsyncTask<String, int[], Integer> implements TableStateListener {
	
	ResourceEngineListener listener;
	Context c;
	
	Resource missingResource = null;
	int badReqCode = -1;
	
	public static final int STATUS_INSTALLED = 0;
	public static final int STATUS_MISSING = 1;
	public static final int STATUS_ERROR = 2;
	public static final int STATUS_FAIL_UNKNOWN = 4;
	
	public ResourceEngineTask(Context c) throws SessionUnavailableException{
		this.c = c;
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#doInBackground(Params[])
	 */
	protected Integer doInBackground(String... profileRefs) {
		String profileRef = profileRefs[0];
		AndroidCommCarePlatform platform = CommCareApplication._().getCommCarePlatform();
		
		try {
			//This is replicated in the application in a few places.
    		ResourceTable global = platform.getGlobalResourceTable();
    		
    		//Ok, should figure out what the state of this bad boy is.
    		
    		global.setStateListener(this);
    		
    		platform.init(profileRef, global, false);
    		
			//Initialize them now that they're installed
			CommCareApplication._().initializeGlobalResources();
    		
    		//Alll goood, we need to set our current profile ref to either the one
    		//just used, or the auth ref, if one is available.
    		
    		String authRef = platform.getCurrentProfile().getAuthReference() == null ? profileRef : platform.getCurrentProfile().getAuthReference();
    		
    		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
    		Editor edit = prefs.edit();
    		edit.putString("default_app_server", authRef);
    		edit.commit();
    		
    		return STATUS_INSTALLED;
		} catch (UnfullfilledRequirementsException e) {
			e.printStackTrace();
			badReqCode = e.getRequirementCode();
			
			cleanupFailure(platform);
			
			return STATUS_ERROR;
		} catch (UnresolvedResourceException e) {
			//couldn't find a resource, which isn't good. 
			e.printStackTrace();
			
			cleanupFailure(platform);
			
			missingResource = e.getResource(); 
			return STATUS_MISSING;
		} catch(Exception e) {
			e.printStackTrace();
			
			cleanupFailure(platform);
			
			return STATUS_FAIL_UNKNOWN;
		}
	}
	
	/* (non-Javadoc)
	 * @see android.os.AsyncTask#onProgressUpdate(Progress[])
	 */
	@Override
	protected void onProgressUpdate(int[]... values) {
		super.onProgressUpdate(values);
		if(listener != null) {
			listener.updateProgress(values[0][0], values[0][1]);
		}
	}

	private void cleanupFailure(AndroidCommCarePlatform platform) {
		ResourceTable global = platform.getGlobalResourceTable();
		
		//Install was botched, clear anything left lying around....
		global.clear();

	}
		
	public void setListener(ResourceEngineListener listener) {
		this.listener = listener;
	}

	@Override
	protected void onPostExecute(Integer result) {
		if(listener != null) {
			if(result == STATUS_INSTALLED){
				listener.reportSuccess();
			} else if(result == STATUS_MISSING){
				listener.failMissingResource(missingResource);
			} else if(result == STATUS_MISSING){
				listener.failBadReqs(badReqCode);
			} else {
				listener.failUnknown();
			}
		}
	}

	public void resourceStateUpdated(ResourceTable table) {
		Vector<Resource> resources = CommCarePlatform.getResourceListFromProfile(table);
		
		int score = 0;

		for(Resource r : resources) {
			switch(r.getStatus()) {
			case Resource.RESOURCE_STATUS_INSTALLED:
				score += 1;
				break;
			default:
				score += 0;
				break;
			}
		}
		
		incrementProgress(score, resources.size());
	}

	public void incrementProgress(int complete, int total) {
		this.publishProgress(new int[] {complete, total});
	}
	
}
