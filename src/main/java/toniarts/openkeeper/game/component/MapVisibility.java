/*
 * Copyright (C) 2014-2026 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.game.component;

import com.simsilica.es.EntityComponent;
import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight per-player live visibility for a map tile. Kept separate from
 * MapTile so creature movement never triggers terrain mesh reconstruction.
 */
public final class MapVisibility implements EntityComponent {

    public Map<Short, Boolean> perceived;
    public Map<Short, Boolean> scriptedVisible;

    public MapVisibility() {
    }

    public MapVisibility(MapVisibility source) {
        if (source.perceived != null && !source.perceived.isEmpty()) {
            perceived = HashMap.newHashMap(4);
            perceived.putAll(source.perceived);
        }
        if (source.scriptedVisible != null && !source.scriptedVisible.isEmpty()) {
            scriptedVisible = HashMap.newHashMap(4);
            scriptedVisible.putAll(source.scriptedVisible);
        }
    }
}
