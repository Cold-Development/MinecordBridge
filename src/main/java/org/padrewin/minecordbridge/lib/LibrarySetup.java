package org.padrewin.minecordbridge.lib;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.padrewin.minecordbridge.MinecordBridge;
import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;

import java.io.*;
import java.util.Collections;
import java.util.List;


public class LibrarySetup implements AbstractLibraryLoader<Library> {

    // COLORS USED
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";

    private final BukkitLibraryManager bukkitLibraryManager = new BukkitLibraryManager(MinecordBridge.getPlugin(MinecordBridge.class));
    private final MinecordBridge minecord = MinecordBridge.getPlugin();

    public List<Library> initLibraries() {
        List<Library> list = new java.util.ArrayList<>(Collections.emptyList());

        try {
            File jsonFile = getAzimFile();
            ObjectMapper objectMapper = new ObjectMapper();

            for (LibraryObject libraryObject : objectMapper.readValue(jsonFile, LibraryObject[].class)) {
                list.add(createLibrary(libraryObject));
                if (minecord.debugMode)
                    minecord.debug("LibraryObject loaded " + libraryObject.artifactId() + " " + libraryObject.version() + " from " + libraryObject.groupId());
            }
        } catch (IOException e) {
            minecord.error(e.getMessage());
        }

        return list;
    }

    public void loadLibraries() {
        // Temporarily disable console output to suppress library download messages
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;

        System.setOut(new PrintStream(new OutputStream() {
            public void write(int b) {
                // Do nothing to suppress output
            }
        }));
        System.setErr(new PrintStream(new OutputStream() {
            public void write(int b) {
                // Do nothing to suppress error output
            }
        }));

        try {
            bukkitLibraryManager.addMavenCentral();
            bukkitLibraryManager.addMavenLocal();
            bukkitLibraryManager.addJCenter();
            bukkitLibraryManager.addJitPack();

            // Display initial message
            //minecord.log(ANSI_YELLOW + "Downloading libraries.." + ANSI_RESET);

            // Load libraries
            initLibraries().forEach(bukkitLibraryManager::loadLibrary);

        } finally {
            // Restore original console output
            System.setOut(originalOut);
            System.setErr(originalErr);

            // Display completion message
            //minecord.log(ANSI_GREEN + "Download completed. Plugin started." + ANSI_RESET);
        }
    }

    private Library createLibrary(LibraryObject libraryObject) {
        return Library.builder()
                .groupId(libraryObject.groupId())
                .artifactId(libraryObject.artifactId())
                .version(libraryObject.version())
                .relocate(libraryObject.oldRelocation(), libraryObject.newRelocation())
                .build();
    }

    private File getAzimFile() throws IOException {
        InputStream inputStream = minecord.getResource("AzimDP.json");

        // Create a temporary file
        File tempFile = File.createTempFile("temp", ".tmp");

        // Write the content of the InputStream to the temporary file
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            assert inputStream != null;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (AssertionError e) {
            minecord.error("Error creating temp file! Stack Trace:");
            minecord.error(e.getMessage());
        }

        return tempFile;
    }

}

record LibraryObject(String groupId, String artifactId, String version, String oldRelocation, String newRelocation) {
    public LibraryObject {
        if (groupId == null || artifactId == null || version == null) {
            throw new IllegalArgumentException("LibraryObject can't have null groupId, artifactId, or version.");
        }
    }
}
