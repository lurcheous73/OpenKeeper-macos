/*
 * Copyright (C) 2014-2026 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.game.logic;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import toniarts.openkeeper.game.component.CreatureComponent;
import toniarts.openkeeper.game.component.Health;
import toniarts.openkeeper.game.component.MapTile;
import toniarts.openkeeper.game.component.Owner;
import toniarts.openkeeper.game.component.Position;
import toniarts.openkeeper.game.controller.IMapController;
import toniarts.openkeeper.game.map.IMapTileController;
import toniarts.openkeeper.tools.convert.map.Creature;
import toniarts.openkeeper.tools.convert.map.IKwdFile;
import toniarts.openkeeper.tools.convert.map.Player;
import toniarts.openkeeper.tools.convert.map.Terrain;
import toniarts.openkeeper.utils.Point;
import toniarts.openkeeper.utils.WorldUtils;

/**
 * Maintains the persistent, per-player explored part of Dungeon Keeper II's
 * fog of war. Terrain exploration is radius based and deliberately does not
 * use wall occlusion: native DKII reveals terrain through solid walls.
 */
public final class FogOfWarSystem implements IGameLogicUpdatable {

    private final IKwdFile kwdFile;
    private final IMapController mapController;
    private final EntitySet creatureEntities;
    private final EntitySet mapTileEntities;
    private final Set<Short> keeperIds = new HashSet<>();

    public FogOfWarSystem(EntityData entityData, IKwdFile kwdFile,
            IMapController mapController, Collection<Short> playerIds) {
        this.kwdFile = kwdFile;
        this.mapController = mapController;
        for (short playerId : playerIds) {
            if (playerId >= Player.KEEPER1_ID) {
                keeperIds.add(playerId);
            }
        }

        creatureEntities = entityData.getEntities(
                CreatureComponent.class, Health.class, Owner.class, Position.class);
        mapTileEntities = entityData.getEntities(MapTile.class, Owner.class);
        initializeVisibility();
    }

    private void initializeVisibility() {
        for (Entity entity : mapTileEntities) {
            revealOwnedOrAlwaysExplored(entity);
        }
    }

    @Override
    public void processTick(float tpf) {
        if (mapTileEntities.applyChanges()) {
            for (Entity entity : mapTileEntities.getAddedEntities()) {
                revealOwnedOrAlwaysExplored(entity);
            }
            for (Entity entity : mapTileEntities.getChangedEntities()) {
                revealOwnedOrAlwaysExplored(entity);
            }
        }

        creatureEntities.applyChanges();
        for (Entity entity : creatureEntities) {
            revealAroundCreature(entity);
        }
    }

    private void revealOwnedOrAlwaysExplored(Entity entity) {
        MapTile mapTile = entity.get(MapTile.class);
        Owner owner = entity.get(Owner.class);
        Terrain terrain = kwdFile.getTerrain(mapTile.terrainId);

        if (keeperIds.contains(owner.ownerId)) {
            revealTile(mapTile.p, owner.ownerId);
        }
        if (terrain.getFlags().contains(Terrain.TerrainFlag.ALWAYS_EXPLORED)) {
            for (short keeperId : keeperIds) {
                revealTile(mapTile.p, keeperId);
            }
        }
    }

    private void revealAroundCreature(Entity entity) {
        Owner owner = entity.get(Owner.class);
        if (!keeperIds.contains(owner.ownerId)) {
            return;
        }

        CreatureComponent creatureComponent = entity.get(CreatureComponent.class);
        Creature creature = kwdFile.getCreature(creatureComponent.creatureId);
        int radius = (int) Math.ceil(creature.getAttributes().getPerceptionRange());
        if (radius <= 0) {
            return;
        }

        Point center = WorldUtils.vectorToPoint(entity.get(Position.class).position);
        int radiusSquared = radius * radius;
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int y = center.y - radius; y <= center.y + radius; y++) {
                int dx = x - center.x;
                int dy = y - center.y;
                if (dx * dx + dy * dy <= radiusSquared) {
                    revealTile(new Point(x, y), owner.ownerId);
                }
            }
        }
    }

    private void revealTile(Point point, short playerId) {
        IMapTileController tile = mapController.getMapData().getTile(point);
        if (tile == null || tile.isExplored(playerId)) {
            return;
        }

        tile.setExplored(true, playerId);

        // A concealed tile is intentionally taggable even if its real terrain is
        // not. Once discovered, remove an impossible dig tag rather than leaking
        // the hidden terrain type before discovery.
        Terrain actualTerrain = kwdFile.getTerrain(tile.getTerrainId());
        if (tile.isSelected(playerId)
                && !actualTerrain.getFlags().contains(Terrain.TerrainFlag.TAGGABLE)) {
            tile.setSelected(false, playerId);
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
        creatureEntities.release();
        mapTileEntities.release();
    }
}
