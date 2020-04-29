package project.lars.gothic2.main;

import static project.lars.gothic2.main.Starter.CURRENT;

import java.io.File;
import java.io.IOException;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PathManager {

	Object LOCK = new Object();

	File sourceDir;

	File backupDir;

	long pollTime = Starter.getProp("polltime");

	boolean isShutdown = false;

	ConcurrentMap<WatchKey, BackupService> watchkeyToSaveActionMap = new ConcurrentHashMap<>();

	public PathManager(File sourceDir, File backupDir) throws IOException {
		if (!sourceDir.isDirectory()) {
			throw new IllegalStateException(
					"Invalid installation directory specified! Dir: " + sourceDir.getAbsolutePath());
		}
		if (!backupDir.exists() || !backupDir.isDirectory()) {
			backupDir.delete();
			backupDir.mkdir();
		}
		this.sourceDir = sourceDir;
		this.backupDir = backupDir;
	}

	void startMonitoring() {
		synchronized (LOCK) {
			try {
				WatchService watchService = sourceDir.toPath().getFileSystem().newWatchService();

				for (File saveGameDir : sourceDir
						.listFiles((file, name) -> file.isDirectory() && !CURRENT.equals(name))) {
					WatchKey key = saveGameDir.toPath().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
					watchkeyToSaveActionMap.put(key, new BackupService(saveGameDir, backupDir));
					System.out.println("Monitoring: '" + saveGameDir.getAbsolutePath() + "'");
				}

				while (!isShutdown) {
					try {
						LOCK.wait(pollTime);
						WatchKey watchKey = watchService.poll();
						if (watchKey != null) {
							watchkeyToSaveActionMap.get(watchKey).doBackup();
							watchKey.pollEvents();
							watchKey.reset();
						}
					} catch (InterruptedException ie) {
						shutdown();
						ie.printStackTrace();
					}
				}
			} catch (Exception e) {
				System.out.println("An unexpected error occured! Dir '" + sourceDir.getAbsolutePath()
						+ "' will no longer be monitored!");
				e.printStackTrace();
			}
		}
	}

	public void shutdown() {
		synchronized (LOCK) {
			System.out.println("Shutting down directory listener for " + sourceDir.getAbsolutePath());
			watchkeyToSaveActionMap.values().forEach(backupService -> backupService.shutdown());
			isShutdown = true;
		}
	}

}
