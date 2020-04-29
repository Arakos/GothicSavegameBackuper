package project.lars.gothic2.main;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ForkJoinPool;

public class BackupService {

	final Object LOCK = new Object();

	final DateFormat DATE_FORMATTER = new SimpleDateFormat("dd_MM_yyyy HH-mm-ss");

	final File backupSavesDir;

	final File origSaveGameDir;

	/**
	 * defines the number of milliseconds the service will not react to subsequent
	 * calls of {@link #doBackup()}
	 */
	final long numbness = Starter.getProp("numbness");

	/**
	 * defines the number of milliseconds the service will wait until the backup
	 * will be executed
	 */
	final long delay = Starter.getProp("delay");

	long lastStart = 0;

	boolean isShutdown = false;

	public BackupService(File origSaveGameDir, File backupSavesDir) {
		this.origSaveGameDir = origSaveGameDir;
		this.backupSavesDir = backupSavesDir;
	}

	protected void doBackup() {
		if (isShutdown) {
			throw new IllegalStateException("This backup service was already shudown");
		}
		if (lastStart + numbness < System.currentTimeMillis()) {
			System.out.println("Change to directoy '" + origSaveGameDir + "' detected! Backup will be created in "
					+ (delay / 1000) + " seconds.");
			ForkJoinPool.commonPool().execute(() -> doSave(delay));
			lastStart = System.currentTimeMillis();
		}
	}

	protected void shutdown() {
		synchronized (LOCK) {
			this.isShutdown = true;
			System.out.println("Shutting down BackupService for " + origSaveGameDir.getAbsolutePath());
		}
	}

	protected void doSave(long delay) {
		synchronized (LOCK) {
			try {
				LOCK.wait(delay);
			} catch (InterruptedException e) {
				System.err.println("Failed backup " + origSaveGameDir.getName());
				e.printStackTrace();
				return;
			}

			File backupSaveGameDir = new File(backupSavesDir,
					DATE_FORMATTER.format(new Date()) + " " + origSaveGameDir.getName());
			backupSaveGameDir.delete();
			backupSaveGameDir.mkdir();
			for (File file : origSaveGameDir.listFiles()) {
				try {
					Files.copy(file.toPath(), new File(backupSaveGameDir, file.getName()).toPath());
				} catch (IOException e) {
					System.err.println("Failed to copy File " + file.getName() + " to backup folder!");
					e.printStackTrace();
					return;
				}
			}
			System.out.println("Backup created: '" + backupSaveGameDir.getAbsolutePath() + "'");
		}
	}

}
