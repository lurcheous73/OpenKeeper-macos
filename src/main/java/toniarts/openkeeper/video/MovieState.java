/*
 * Copyright (C) 2014-2015 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenKeeper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenKeeper.  If not, see <http://www.gnu.org/licenses/>.
 */
package toniarts.openkeeper.video;

import com.jme3.app.Application;
import com.jme3.app.state.AbstractAppState;
import com.jme3.app.state.AppStateManager;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.TouchInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.input.controls.TouchTrigger;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Quad;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Paths;
import toniarts.openkeeper.Main;
import toniarts.openkeeper.video.tgq.TgqFrame;

/**
 * AppState for watching TGQ movies, very simple, just create and attach
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public abstract class MovieState extends AbstractAppState {

    private final String movie;
    private Main app;
    private InputManager inputManager;
    private static final String KEY_SKIP = "skip";
    private MovieMaterial movieMaterial;
    private Geometry movieScreen;
    private TgqPlayer player;

    public MovieState(String movie) throws FileNotFoundException {
        if (!Files.exists(Paths.get(movie))) {
            throw new FileNotFoundException("Movie file not found!");
        }
        this.movie = movie;
    }

    @Override
    public void initialize(AppStateManager stateManager, final Application app) {
        super.initialize(stateManager, app);
        this.app = (Main) app;
        this.inputManager = app.getInputManager();

        // Assign video skipping keys
        inputManager.addMapping(KEY_SKIP, new KeyTrigger(KeyInput.KEY_ESCAPE), new KeyTrigger(KeyInput.KEY_SPACE), new KeyTrigger(KeyInput.KEY_RETURN), new MouseButtonTrigger(MouseInput.BUTTON_LEFT), new TouchTrigger(TouchInput.ALL));
        inputManager.addListener(actionListener, KEY_SKIP);

        // Create a unit quad and size/centre it from the live framebuffer.
        // The old code used the saved settings resolution, which leaves movies
        // anchored in the lower-left when macOS fullscreen/Retina uses a
        // different framebuffer size. Geometry sizing also preserves the TGQ
        // aspect ratio without stretching.
        movieMaterial = new MovieMaterial(app, false);
        movieMaterial.setLetterboxColor(ColorRGBA.Black);
        movieScreen = new Geometry("MovieScreen", new Quad(1, 1));
        movieScreen.setMaterial(movieMaterial.getMaterial());
        this.app.getGuiNode().attachChild(movieScreen);
        updateMovieScreenBounds();

        // Create the player
        player = new TgqPlayer(Paths.get(movie)) {
            @Override
            protected void onPlayingEnd() {
                app.getStateManager().detach(MovieState.this);
                MovieState.this.onPlayingEnd();
            }

            @Override
            protected void onNewVideoFrame(TgqFrame frame) {
                movieMaterial.videoFrameUpdated(frame);
            }
        };
        player.play();
    }
    private final ActionListener actionListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean pressed, float tpf) {

            // Skip video
            if (KEY_SKIP.equals(name) && !pressed) {
                if (player != null) {
                    player.stop();
                }
            }
        }
    };

    /**
     * Called when the playing has finished, you probably want to move on with
     * your life
     */
    protected abstract void onPlayingEnd();

    @Override
    public void update(float tpf) {
        movieMaterial.update(tpf);
        updateMovieScreenBounds();
    }

    private void updateMovieScreenBounds() {
        if (movieScreen == null || movieMaterial == null || app == null) {
            return;
        }

        // On macOS/Retina the logical camera size can be smaller than the
        // backing framebuffer. GUI geometry is ultimately rasterised into the
        // framebuffer, so use its real dimensions for true fullscreen movies.
        int width = app.getContext().getFramebufferWidth();
        int height = app.getContext().getFramebufferHeight();
        if (width <= 0 || height <= 0) {
            width = app.getCamera().getWidth();
            height = app.getCamera().getHeight();
        }
        if (width <= 0 || height <= 0) {
            return;
        }

        float movieAspect = movieMaterial.getAspectRatio();
        if (movieAspect <= 0f) {
            movieAspect = width / (float) height;
        }
        float screenAspect = width / (float) height;

        float movieWidth;
        float movieHeight;
        if (movieAspect > screenAspect) {
            movieWidth = width;
            movieHeight = width / movieAspect;
        } else {
            movieHeight = height;
            movieWidth = height * movieAspect;
        }

        movieScreen.setLocalScale(movieWidth, movieHeight, 1f);
        movieScreen.setLocalTranslation((width - movieWidth) * 0.5f,
                (height - movieHeight) * 0.5f, 0f);
    }

    @Override
    public void cleanup() {

        // Make sure the player is stopped
        if (player != null) {
            player.stop();
        }

        // Dispose the movie material
        if (movieMaterial != null) {
            movieMaterial.dispose();
        }

        // Detach the canvas
        if (movieScreen != null) {
            app.getGuiNode().detachChild(movieScreen);
        }

        // Clean our mapping
        inputManager.deleteMapping(KEY_SKIP);
        inputManager.removeListener(actionListener);

        super.cleanup();
    }
}
