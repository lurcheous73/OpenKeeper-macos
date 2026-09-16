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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private final Map<Short, Set<Point>> perceivedTiles = new HashMap<>();

    public FogOfWarSystem(EntityData entityData, IKwdFile kwdFile,
            IMapController mapController, Collection<Short> playerIds) {
        this.kwdFile = kwdFile;
        this.mapController = mapController;
        for (short playerId : playerIds) {
            if (playerId >= Player.KEEPER1_ID) {
                keeperIds.add(playerId);
                perceivedTiles.put(playerId, new HashSet<>());
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
        Map<Short, Set<Point>> nextPerceivedTiles = new HashMap<>();
        for (short keeperId : keeperIds) {
            nextPerceivedTiles.put(keeperId, new HashSet<>());
        }
        for (Entity entity : creatureEntities) {
            revealAroundCreature(entity, nextPerceivedTiles);
        }
        updatePerception(nextPerceivedTiles);
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

    private void revealAroundCreature(Entity entity, Map<Short, Set<Point>> nextPerceivedTiles) {
        Health health = entity.get(Health.class);
        if (health == null || health.health <= 0) {
            return;
        }

        Owner owner = entity.get(Owner.class);
        short viewerId = keeperIds.contains(owner.controlId) ? owner.controlId : owner.ownerId;
        if (!keeperIds.contains(viewerId)) {
            return;
        }

        CreatureComponent creatureComponent = entity.get(CreatureComponent.class);
        Creature creature = kwdFile.getCreature(creatureComponent.creatureId);
        int radius = Math.max(0, (int) Math.ceil(creature.getAttributes().getPerceptionRange()));
        Point center = WorldUtils.vectorToPoint(entity.get(Position.class).position);
        Set<Point> perceived = nextPerceivedTiles.get(viewerId);
        int radiusSquared = radius * radius;
        for (int x = center.x - radius; x <= center.x + radius; x++) {
            for (int y = center.y - radius; y <= center.y + radius; y++) {
                int dx = x - center.x;
                int dy = y - center.y;
                if (dx * dx + dy * dy <= radiusSquared) {
                    Point point = new Point(x, y);
                    if (mapController.getMapData().getTile(point) != null) {
                        perceived.add(point);
                        revealTile(point, viewerId);
                    }
                }
            }
        }
    }

    private void updatePerception(Map<Short, Set<Point>> nextPerceivedTiles) {
        for (short keeperId : keeperIds) {
            Set<Point> previous = perceivedTiles.get(keeperId);
            Set<Point> next = nextPerceivedTiles.get(keeperId);

            Set<Point> noLongerVisible = new HashSet<>(previous);
            noLongerVisible.removeAll(next);
            for (Point point : noLongerVisible) {
                IMapTileController tile = mapController.getMapData().getTile(point);
                if (tile != null) {
                    tile.setPerceived(false, keeperId);
                }
            }

            Set<Point> newlyVisible = new HashSet<>(next);
            newlyVisible.removeAll(previous);
            for (Point point : newlyVisible) {
                IMapTileController tile = mapController.getMapData().getTile(point);
                if (tile != null) {
                    tile.setPerceived(true, keeperId);
                }
            }

            perceivedTiles.put(keeperId, next);
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
