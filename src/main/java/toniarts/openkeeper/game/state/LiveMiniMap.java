/*
 * Copyright (C) 2026 OpenKeeper contributors
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package toniarts.openkeeper.game.state;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector2f;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.ui.Picture;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import de.lessvoid.nifty.elements.Element;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import toniarts.openkeeper.game.component.CreatureComponent;
import toniarts.openkeeper.game.component.MapTile;
import toniarts.openkeeper.game.component.MapVisibility;
import toniarts.openkeeper.game.component.Owner;
import toniarts.openkeeper.game.component.Position;
import toniarts.openkeeper.tools.convert.map.IKwdFile;
import toniarts.openkeeper.tools.convert.map.Player;
import toniarts.openkeeper.tools.convert.map.Terrain;
import toniarts.openkeeper.utils.MapThumbnailGenerator;
import toniarts.openkeeper.utils.Point;
import toniarts.openkeeper.utils.WorldUtils;

/**
 * Live Dungeon Keeper II style dynamic map used by the in-game HUD.
 */
final class LiveMiniMap {

    private static final int TEXTURE_SIZE = 128;
    private static final float CENTER = TEXTURE_SIZE / 2f;
    private static final float LENS_RADIUS = 49f;
    private static final int[] ZOOM_TILES_ACROSS = {24, 32, 48};
    private static final java.awt.Color UNKNOWN_COLOR = new java.awt.Color(7, 5, 4, 255);
    private static final java.awt.Color DIG_TAG_COLOR = new java.awt.Color(0, 220, 220, 255);
    private static final java.awt.Color HERO_COLOR = new java.awt.Color(230, 230, 225, 255);
    private static final java.awt.Color NEUTRAL_COLOR = new java.awt.Color(175, 175, 170, 255);

    private final PlayerState state;
    private final AssetManager assetManager;
    private final EntityData entityData;
    private final IKwdFile kwdFile;
    private final short playerId;
    private final EntitySet mapTileEntities;
    private final EntitySet creatureEntities;
    private final BufferedImage image = new BufferedImage(TEXTURE_SIZE, TEXTURE_SIZE, BufferedImage.TYPE_4BYTE_ABGR);
    private final AWTLoader imageLoader = new AWTLoader();

    private Texture2D texture;
    private Picture picture;
    private Element targetElement;
    private int zoomIndex = 1;
    private boolean creatureBlink;

    LiveMiniMap(PlayerState state, AssetManager assetManager, EntityData entityData) {
        this.state = state;
        this.assetManager = assetManager;
        this.entityData = entityData;
        this.kwdFile = state.getKwdFile();
        this.playerId = state.getPlayerId();
        this.mapTileEntities = entityData.getEntities(MapTile.class, Owner.class);
        this.creatureEntities = entityData.getEntities(CreatureComponent.class, Owner.class, Position.class);
    }

    void initialize(Element targetElement) {
        if (targetElement == null) {
            return;
        }

        this.targetElement = targetElement;
        mapTileEntities.applyChanges();
        creatureEntities.applyChanges();
        renderMap();

        texture = new Texture2D(imageLoader.load(image, false));
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);

        picture = new Picture("LiveMiniMap-" + playerId);
        picture.setTexture(assetManager, texture, true);
        updatePictureBounds();
        state.app.getGuiNode().attachChild(picture);
    }

    void update() {
        if (texture == null) {
            return;
        }
        mapTileEntities.applyChanges();
        creatureEntities.applyChanges();
        creatureBlink = !creatureBlink;
        renderMap();
        texture.setImage(imageLoader.load(image, false));
        updatePictureBounds();
    }

    void setVisible(boolean visible) {
        if (picture != null) {
            picture.setCullHint(visible ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
        }
    }

    void cycleZoom() {
        zoomIndex = (zoomIndex + 1) % ZOOM_TILES_ACROSS.length;
        update();
    }

    void cleanup() {
        mapTileEntities.release();
        creatureEntities.release();
        if (picture != null) {
            picture.removeFromParent();
            picture = null;
        }
        targetElement = null;
        texture = null;
    }

    private void updatePictureBounds() {
        if (picture == null || targetElement == null) {
            return;
        }
        int screenHeight = state.app.getCamera().getHeight();
        picture.setWidth(targetElement.getWidth());
        picture.setHeight(targetElement.getHeight());
        picture.setPosition(targetElement.getX(),
                screenHeight - targetElement.getY() - targetElement.getHeight());
    }

    private void renderMap() {
        Graphics2D g = image.createGraphics();
        try {
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, TEXTURE_SIZE, TEXTURE_SIZE);
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            Ellipse2D.Float lens = new Ellipse2D.Float(CENTER - LENS_RADIUS, CENTER - LENS_RADIUS,
                    LENS_RADIUS * 2f, LENS_RADIUS * 2f);
            Shape previousClip = g.getClip();
            g.setClip(lens);
            g.setColor(UNKNOWN_COLOR);
            g.fill(lens);

            int mapWidth = kwdFile.getMap().getWidth();
            int mapHeight = kwdFile.getMap().getHeight();
            boolean[] explored = new boolean[mapWidth * mapHeight];
            boolean[] currentlyVisible = new boolean[mapWidth * mapHeight];

            Vector2f cameraPoint = getCameraPoint(mapWidth, mapHeight);
            float pixelsPerTile = (LENS_RADIUS * 2f) / ZOOM_TILES_ACROSS[zoomIndex];

            for (Entity entity : mapTileEntities) {
                MapTile tile = entity.get(MapTile.class);
                Owner owner = entity.get(Owner.class);
                if (tile == null || tile.p == null || !insideMap(tile.p, mapWidth, mapHeight)) {
                    continue;
                }

                int index = tile.p.y * mapWidth + tile.p.x;
                Terrain terrain = kwdFile.getTerrain(tile.terrainId);
                boolean tileExplored = isExplored(tile, terrain);
                explored[index] = tileExplored;

                MapVisibility visibility = entityData.getComponent(entity.getId(), MapVisibility.class);
                currentlyVisible[index] = tileExplored && isCurrentlyVisible(visibility, terrain);

                boolean selected = tile.selection != null && Boolean.TRUE.equals(tile.selection.get(playerId));
                if (!tileExplored && !selected) {
                    continue;
                }

                java.awt.Color color = selected ? DIG_TAG_COLOR : getTerrainColor(tile, owner, terrain, mapWidth, mapHeight);
                drawTile(g, tile.p.x, tile.p.y, cameraPoint, pixelsPerTile, color);
            }

            drawCreatureBlips(g, cameraPoint, pixelsPerTile, explored, currentlyVisible, mapWidth, mapHeight);
            drawNorthMarker(g);
            g.setClip(previousClip);
        } finally {
            g.dispose();
        }
    }

    private Vector2f getCameraPoint(int mapWidth, int mapHeight) {
        if (state.cameraState != null && state.cameraState.getCamera() != null) {
            return state.cameraState.getCamera().getLookAtPoint();
        }
        return new Vector2f(mapWidth / 2f, mapHeight / 2f);
    }

    private boolean isExplored(MapTile tile, Terrain terrain) {
        if (terrain.getFlags().contains(Terrain.TerrainFlag.ALWAYS_EXPLORED)
                || terrain.getFlags().contains(Terrain.TerrainFlag.REVEAL_THROUGH_FOG_OF_WAR)) {
            return true;
        }
        return tile.explored != null && Boolean.TRUE.equals(tile.explored.get(playerId));
    }

    private boolean isCurrentlyVisible(MapVisibility visibility, Terrain terrain) {
        if (terrain.getFlags().contains(Terrain.TerrainFlag.ALWAYS_EXPLORED)
                || terrain.getFlags().contains(Terrain.TerrainFlag.REVEAL_THROUGH_FOG_OF_WAR)) {
            return true;
        }
        if (visibility == null) {
            return false;
        }
        boolean perceived = visibility.perceived != null && Boolean.TRUE.equals(visibility.perceived.get(playerId));
        boolean scripted = visibility.scriptedVisible != null && Boolean.TRUE.equals(visibility.scriptedVisible.get(playerId));
        return perceived || scripted;
    }

    private java.awt.Color getTerrainColor(MapTile tile, Owner owner, Terrain terrain, int mapWidth, int mapHeight) {
        int paletteIndex;
        if (tile.p.x == 0 || tile.p.y == 0 || tile.p.x == mapWidth - 1 || tile.p.y == mapHeight - 1) {
            paletteIndex = 46;
        } else if (kwdFile.getMap().getLava().getTerrainId() == tile.terrainId) {
            paletteIndex = 10;
        } else if (kwdFile.getMap().getWater().getTerrainId() == tile.terrainId) {
            paletteIndex = 8;
        } else if (terrain.getFlags().contains(Terrain.TerrainFlag.IMPENETRABLE)) {
            paletteIndex = terrain.getGoldValue() > 0 ? 4 : 2;
        } else if (terrain.getGoldValue() > 0) {
            paletteIndex = 6;
        } else if (!terrain.getFlags().contains(Terrain.TerrainFlag.OWNABLE)) {
            paletteIndex = terrain.getFlags().contains(Terrain.TerrainFlag.SOLID) ? 3 : 1;
        } else {
            short ownerId = owner != null ? owner.ownerId : Player.NEUTRAL_PLAYER_ID;
            if (terrain.getFlags().contains(Terrain.TerrainFlag.ROOM)) {
                paletteIndex = 35 + ownerId;
            } else if (terrain.getFlags().contains(Terrain.TerrainFlag.SOLID)) {
                paletteIndex = 15 + ownerId;
            } else {
                paletteIndex = 25 + ownerId;
            }
        }
        return MapThumbnailGenerator.getMapColor(paletteIndex);
    }

    private void drawTile(Graphics2D g, int tileX, int tileY, Vector2f cameraPoint,
            float pixelsPerTile, java.awt.Color color) {
        float centerX = CENTER + (tileX - cameraPoint.x) * pixelsPerTile;
        float centerY = CENTER + (tileY - cameraPoint.y) * pixelsPerTile;
        int x1 = (int) Math.floor(centerX - pixelsPerTile / 2f);
        int y1 = (int) Math.floor(centerY - pixelsPerTile / 2f);
        int x2 = (int) Math.ceil(centerX + pixelsPerTile / 2f);
        int y2 = (int) Math.ceil(centerY + pixelsPerTile / 2f);
        g.setColor(color);
        g.fillRect(x1, y1, Math.max(1, x2 - x1), Math.max(1, y2 - y1));
    }

    private void drawCreatureBlips(Graphics2D g, Vector2f cameraPoint, float pixelsPerTile,
            boolean[] explored, boolean[] currentlyVisible, int mapWidth, int mapHeight) {
        for (Entity entity : creatureEntities) {
            Owner owner = entity.get(Owner.class);
            Position position = entity.get(Position.class);
            if (owner == null || position == null || position.position == null) {
                continue;
            }

            Point tile = WorldUtils.vectorToPoint(position.position);
            if (!insideMap(tile, mapWidth, mapHeight)) {
                continue;
            }
            int mapIndex = tile.y * mapWidth + tile.x;
            boolean ours = owner.controlId == playerId || owner.ownerId == playerId;
            if (!ours && (!explored[mapIndex] || !currentlyVisible[mapIndex])) {
                continue;
            }
            if (ours && !creatureBlink) {
                continue;
            }

            java.awt.Color color;
            if (ours) {
                color = java.awt.Color.BLACK;
            } else if (owner.ownerId == Player.GOOD_PLAYER_ID) {
                color = HERO_COLOR;
            } else if (owner.ownerId == Player.NEUTRAL_PLAYER_ID) {
                color = NEUTRAL_COLOR;
            } else {
                java.awt.Color playerColor = MapThumbnailGenerator.getPlayerColor(owner.ownerId);
                color = playerColor != null ? playerColor : HERO_COLOR;
            }

            int x = Math.round(CENTER + (position.position.x - cameraPoint.x) * pixelsPerTile);
            int y = Math.round(CENTER + (position.position.z - cameraPoint.y) * pixelsPerTile);
            int size = Math.max(2, Math.round(pixelsPerTile * 0.7f));
            g.setColor(color);
            g.fillOval(x - size / 2, y - size / 2, size, size);
        }
    }

    private void drawNorthMarker(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(225, 225, 215, 230));
        g.setStroke(new BasicStroke(1f));
        g.drawString("N", Math.round(CENTER) - 3, Math.round(CENTER - LENS_RADIUS) + 10);
    }

    private static boolean insideMap(Point p, int width, int height) {
        return p.x >= 0 && p.y >= 0 && p.x < width && p.y < height;
    }
}
