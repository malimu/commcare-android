package org.commcare.android.resource.analytics;

import android.util.Base64;

import org.commcare.resources.model.InstallStatsLogger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.Hashtable;

/**
 * Statistics associated with attempting to stage resources into the app's
 * update table.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpdateStats implements InstallStatsLogger, Serializable {
    private final Hashtable<String, InstallAttempts<String>> installStats;
    private final long startInstallTime;
    private int restartCount = 0;
    private static final long TWO_WEEKS_IN_MS = 1000 * 60 * 60 * 24 * 24;

    public UpdateStats() {
        startInstallTime = new Date().getTime();
        installStats = new Hashtable<>();
    }

    /**
     * Register attempt to download resources into update table.
     */
    public void registerStagingAttempt() {
        restartCount++;
    }

    /**
     * @return Should the update be considered stale due to elapse time or too
     * many unsuccessful installs?
     */
    public boolean isUpgradeStale() {
        // TODO PLM: test this!
        long currentTime = new Date().getTime();
        return (restartCount > 3 ||
                (currentTime - startInstallTime) > TWO_WEEKS_IN_MS);
    }

    @Override
    public void recordResourceInstallFailure(String resourceName,
                                             String errorMsg) {
        InstallAttempts<String> attempts = installStats.get(resourceName);
        if (attempts == null) {
            attempts = new InstallAttempts<>(resourceName);
            installStats.put(resourceName, attempts);
        }
        attempts.addFailure(errorMsg);
    }

    @Override
    public void recordResourceInstallSuccess(String resourceName) {
        InstallAttempts<String> attempts = installStats.get(resourceName);
        if (attempts == null) {
            attempts = new InstallAttempts<>(resourceName);
            installStats.put(resourceName, attempts);
        }
        attempts.wasSuccessful = true;
    }

    public static Object deserialize(String s) throws IOException,
            ClassNotFoundException {
        byte[] data = Base64.decode(s, Base64.DEFAULT);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    public static String serialize(Serializable o) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(o);
        oos.close();
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }
}
