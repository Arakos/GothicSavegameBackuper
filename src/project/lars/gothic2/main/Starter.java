package project.lars.gothic2.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Starter {
	static String CMD_COMMAND_PREFIX = "cmd /c ";
	static String GOTHIC_DNDR_REGISTRY_KEY = "REG QUERY \"HKEY_CLASSES_ROOT\\Software\\JoWooD Productions Software AG\\Gothic II Gold\"";
	static String GOTHIC_REGISTRY_VALUE = "RUN_CMD";
	static String REGISTRY_ENTRY_TYPE = "REG_SZ";
	static String GOTHIC_II_EXE = "system\\gothic2.exe";
	static String SAVES_FOLDER = "saves";
	static String BACKUP_SAVES_FOLDER = "saves_backup";

	
	private static class BackupService {
		
		final DateFormat DATE_FORMATTER = new SimpleDateFormat("dd_MM_yyyy HH-mm-ss");

		final File backupSavesDir;
		
		final File origSaveGameDir;
		
		long lastStart = 0;
		
		
		
		public BackupService(File origSaveGameDir, File backupSavesDir) {
			this.origSaveGameDir = origSaveGameDir;
			this.backupSavesDir = backupSavesDir;
		}

		protected void doBackup(long delay) {
			if (lastStart + 1000 < System.currentTimeMillis()) {
				System.out.println("Change to directoy '" + origSaveGameDir + "' detected! Backup will be created in " + (delay / 1000) + " seconds.");
				ForkJoinPool.commonPool().execute(() -> doSave(delay));
				lastStart = System.currentTimeMillis();
			}
		}
		
		protected void stop() {
			long sleeptime = lastStart + 5000 - System.currentTimeMillis();
			if (sleeptime > 0) {
				try {
					Thread.sleep(sleeptime);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		protected void doSave(long delay) {
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

    		File backupSaveGameDir = new File(backupSavesDir, DATE_FORMATTER.format(new Date()) + " " + origSaveGameDir.getName());
    		backupSaveGameDir.delete();
    		backupSaveGameDir.mkdir();
    		for(File file : origSaveGameDir.listFiles()) {
    			try {
					Files.copy(file.toPath(), new File(backupSaveGameDir, file.getName()).toPath());
				} catch (IOException e) {
					System.err.println("Failed to copy File " + file.getName() + " to backup folder!");
					e.printStackTrace();
				}
    		}
			System.out.println("Backup created: '" + backupSaveGameDir.getName() + "'");
		}
	}

	public static void main(String[] args) throws Exception {
		
		final Set<Thread> runners = new HashSet<>();
		
		for(String dirString : findInstallationPaths(args)) {
			Thread runner = new Thread( ()-> {
				try {
					manageInstallationPath(dirString);
				} catch (Exception e) {
					System.out.println("An unexpected error occured! Dir '" + dirString + "' will no longer be monitored!");
					e.printStackTrace();
				}
			});
			runners.add(runner);
			runner.start();
		}
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
	        public void run() {
                System.out.println("Shutting down ...");
                for (Thread runner : runners) {
                	runner.interrupt();
                }
	        }
	    });
		
		for(Thread t : runners) {
			t.join();
		}
		
	}

	private static void manageInstallationPath(String dirString) throws Exception {
		File allSavesDir = Paths.get(dirString + File.separator + SAVES_FOLDER).toFile();
		if (!allSavesDir.isDirectory()) {
			throw new IllegalStateException("Invalid installation directory specified! Dir: " + allSavesDir.getCanonicalPath());
		}
		
		File backupSavesDir = Paths.get(dirString + File.separator + BACKUP_SAVES_FOLDER).toFile();
		if (!backupSavesDir.exists() || !backupSavesDir.isDirectory()) {
			backupSavesDir.delete();
			backupSavesDir.mkdir();
		}
		
		ConcurrentMap<WatchKey, BackupService> watchkeyToSaveActionMap = new ConcurrentHashMap<>();
		WatchService watchService = allSavesDir.toPath().getFileSystem().newWatchService();
		
		for(File saveGameDir : allSavesDir.listFiles((file, name) -> file.isDirectory() && !"current".equals(name))) {
			WatchKey key = saveGameDir.toPath().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
			watchkeyToSaveActionMap.put(key, new BackupService(saveGameDir, backupSavesDir));
			System.out.println("Monitoring: '" + saveGameDir.getCanonicalPath() + "'");
		}
		
		while (true) {
			try {
			    WatchKey watchKey = watchService.poll(1, TimeUnit.MINUTES);
			    if(watchKey != null) {
			    	watchkeyToSaveActionMap.get(watchKey).doBackup(5000);
			    	watchKey.pollEvents();
				    watchKey.reset();
			    }
			} catch (InterruptedException ie) {
				System.out.println("Shutting down directory listener for " + dirString);
				watchkeyToSaveActionMap.values().forEach(backupService -> backupService.stop());
		    }
		}
	}

	private static Set<String> findInstallationPaths(String[] args) throws Exception {
		Process proc =  Runtime.getRuntime().exec(CMD_COMMAND_PREFIX + GOTHIC_DNDR_REGISTRY_KEY);
		proc.waitFor();
		Set<String> res = new HashSet<>();
		try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), "UTF-8"))) {
			String gothic2dndrDir = br.lines().
				filter(line -> line.contains(GOTHIC_REGISTRY_VALUE))
				.map(line -> line.replace(GOTHIC_REGISTRY_VALUE, "")
						.replace(REGISTRY_ENTRY_TYPE, "")
						.replace(GOTHIC_II_EXE, "")
						.trim())
				.findFirst()
				.orElse("");
			if (!gothic2dndrDir.isEmpty()) {
				System.out.println("Found Gothic II DNdR: " + gothic2dndrDir);
				res.add(gothic2dndrDir);
			}
		}
		for (int i = 0; i < args.length; i++) {
			System.out.println("Adding start-argument: " + args[i]);
			res.add(args[i]);
		}
		if (res.isEmpty()) {
			System.out.println("Could not find any Gothic installation on your system.\nPlease insert the path to your Gothic installation folder.\n Press Enter again when your'e done:");
			try (Scanner s = new Scanner(System.in)) {
				String tmp = "X";
				while(!tmp.isEmpty()) {
					tmp = s.nextLine();
					res.add(tmp);
				}
			}
		}
		return res;
	}

}
