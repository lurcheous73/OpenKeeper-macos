/*
 * Copyright (C) 2014-2026 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.game.map;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import org.junit.jupiter.api.Test;
import toniarts.openkeeper.game.component.MapTile;
import toniarts.openkeeper.game.component.MapVisibility;
import toniarts.openkeeper.utils.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapVisibilityTest {

    @Test
    void livePerceptionDoesNotMutatePersistentExploration() {
        EntityData entityData = new DefaultEntityData();
        EntityId tileEntity = entityData.createEntity();
        MapTile tile = new MapTile(0, (short) 1, null, new Point(2, 3), 0);
        entityData.setComponent(tileEntity, tile);
        MapTileController controller = new MapTileController(tileEntity, entityData);

        controller.setExplored(true, (short) 3);
        MapTile exploredTile = entityData.getComponent(tileEntity, MapTile.class);
        controller.setPerceived(true, (short) 3);
        controller.setPerceived(false, (short) 3);

        assertTrue(controller.isExplored((short) 3));
        assertFalse(controller.isPerceived((short) 3));
        assertSame(exploredTile, entityData.getComponent(tileEntity, MapTile.class));
    }

    @Test
    void scriptedRevealAndCreaturePerceptionIndependentlyMakeTileVisible() {
        EntityData entityData = new DefaultEntityData();
        EntityId tileEntity = entityData.createEntity();
        entityData.setComponent(tileEntity, new MapTile(0, (short) 1, null, new Point(1, 1), 0));
        MapTileController controller = new MapTileController(tileEntity, entityData);

        assertFalse(controller.isVisible((short) 3));
        controller.setScriptedVisible(true, (short) 3);
        assertTrue(controller.isVisible((short) 3));
        controller.setScriptedVisible(false, (short) 3);
        controller.setPerceived(true, (short) 3);
        assertTrue(controller.isVisible((short) 3));

        MapVisibility visibility = entityData.getComponent(tileEntity, MapVisibility.class);
        assertTrue(visibility.perceived.get((short) 3));
        assertFalse(visibility.scriptedVisible.get((short) 3));
    }
}
