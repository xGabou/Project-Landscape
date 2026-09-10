package com.gabou.atmospheregen.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Save-local identity, independent of seed/manifest and retained when a save is moved. */
final class ClimateWorldIdentity {
    private ClimateWorldIdentity() { }
    static String loadOrCreate(Path data, boolean historyExists) throws IOException {
        Path file=data.resolve("project_climate_world_id.txt");
        if(Files.exists(file))return UUID.fromString(Files.readString(file).trim()).toString();
        if(historyExists)throw new IllegalStateException("Climate history has no owning save identity; migration required");
        Files.createDirectories(data);
        String id=UUID.randomUUID().toString();
        Files.writeString(file,id+"\n",java.nio.file.StandardOpenOption.CREATE_NEW);
        return id;
    }
}
