package org.padrewin.minecordbridge.lib;

import java.util.List;

public interface AbstractLibraryLoader<Library> {

    List<Library> initLibraries();

    void loadLibraries();

}