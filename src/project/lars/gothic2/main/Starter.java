package project.lars.gothic2.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class Starter {
	static String CMD_COMMAND_PREFIX = "cmd /c ";
	static String GOTHIC_DNDR_REGISTRY_KEY = "REG QUERY \"HKEY_CLASSES_ROOT\\Software\\JoWooD Productions Software AG\\Gothic II Gold\"";
	static String GOTHIC_REGISTRY_VALUE = "RUN_CMD";
	static String REGISTRY_ENTRY_TYPE = "REG_SZ";
	static String GOTHIC_II_EXE = "system\\gothic2.exe";
	static String SAVES_FOLDER = "saves";
	static String BACKUP_SAVES_FOLDER = "saves_backup";
	static String CURRENT = "current";

	static Map<String, Object> props = new HashMap<String, Object>() {
		{
			put("numbness", 1000L);
			put("delay", 5000L);
			put("polltime", 8000L);
		}
	};

	public static <T> T getProp(String key) {
		return (T) props.get(key);
	}

	public static void main(String[] args) throws Exception {
		
		final Map<Thread, PathManager> runningStuff = new HashMap<>();
		
		System.out.println("Savegame-Backuper started. Config:\n" + props);

		for (Path path : findInstallationPaths(args)) {
			File allSavesDir = path.resolve(SAVES_FOLDER).toFile();
			File backupDir = path.resolve(BACKUP_SAVES_FOLDER).toFile();
			PathManager pm = new PathManager(allSavesDir, backupDir);
			Thread runner = new Thread(() -> pm.startMonitoring());
			runningStuff.put(runner, pm);
			runner.start();
		}
		
		Runtime.getRuntime().addShutdownHook(
			new Thread(() -> {
                System.out.println("Shutting down ...");
					for (PathManager pm : runningStuff.values()) {
						pm.shutdown();
                }
	    	})
		);
		
		for (Thread t : runningStuff.keySet()) {
			t.join();
		}
		
	}



	private static Set<Path> findInstallationPaths(String[] args) throws Exception {
		Process proc =  Runtime.getRuntime().exec(CMD_COMMAND_PREFIX + GOTHIC_DNDR_REGISTRY_KEY);
		proc.waitFor();
		Set<Path> res = new HashSet<>();
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
				res.add(Paths.get(gothic2dndrDir));
			}
		}
		for (int i = 0; i < args.length; i++) {
			System.out.println("Adding start-argument: " + args[i]);
			res.add(Paths.get(args[i]));
		}
		if (res.isEmpty()) {
			System.out.println("Could not find any Gothic installation on your system.\nPlease insert the path to your Gothic installation folder.\n Press Enter again when your'e done:");
			try (Scanner s = new Scanner(System.in)) {
				String tmp = "X";
				while(!tmp.isEmpty()) {
					tmp = s.nextLine();
					try {
						res.add(Paths.get(tmp));
					} catch (Exception e) {
						System.err.println("Invalid path syntax (" + e.getMessage() + ")! Try again:");
					}
				}
			}
		}
		return res;
	}

}
