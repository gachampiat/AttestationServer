package app.attestation.server;

import com.almworks.sqlite4java.SQLiteBackup;
import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

class Maintenance implements Runnable {
    private static final long WAIT_MS = 24 * 60 * 60 * 1000;
    private static final long DELETE_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long INACTIVE_DEVICE_EXPIRY_MS = 90L * 24 * 60 * 60 * 1000;
    private static final boolean PURGE_INACTIVE_DEVICES = true;

    private static final Logger logger = Logger.getLogger(Maintenance.class.getName());

    @Override
    public void run() {
        final SQLiteConnection conn = new SQLiteConnection(AttestationProtocol.ATTESTATION_DATABASE);
        final SQLiteStatement deleteDeletedDevices;
        final SQLiteStatement purgeInactiveDevices;
        try {
            AttestationServer.open(conn, false);
            deleteDeletedDevices = conn.prepare("DELETE FROM Devices WHERE deletionTime < ?");
            purgeInactiveDevices = conn.prepare("UPDATE Devices SET deletionTime = ? " +
                    "WHERE verifiedTimeLast < ? AND deletionTime IS NULL");
        } catch (final SQLiteException e) {
            conn.dispose();
            throw new RuntimeException(e);
        }

        while (true) {
            try {
                Thread.sleep(WAIT_MS);
            } catch (final InterruptedException e) {
                return;
            }
            logger.info("maintenance");

            try {
                conn.exec("ANALYZE");

                final long now = System.currentTimeMillis();

                deleteDeletedDevices.bind(1, now - DELETE_EXPIRY_MS);
                deleteDeletedDevices.step();

                if (PURGE_INACTIVE_DEVICES) {
                    purgeInactiveDevices.bind(1, now);
                    purgeInactiveDevices.bind(2, now - INACTIVE_DEVICE_EXPIRY_MS);
                    purgeInactiveDevices.step();
                    logger.info("cleared " + conn.getChanges() + " inactive devices");
                }

            } catch (final SQLiteException e) {
                logger.log(Level.WARNING, "database error", e);
            } finally {
                try {
                    deleteDeletedDevices.reset();
                    purgeInactiveDevices.reset();
                } catch (final SQLiteException e) {
                    logger.log(Level.WARNING, "database error", e);
                }
            }
        }
    }
}
