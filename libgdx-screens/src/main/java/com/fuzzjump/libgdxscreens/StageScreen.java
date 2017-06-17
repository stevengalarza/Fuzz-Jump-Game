package com.fuzzjump.libgdxscreens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;

public abstract class StageScreen<TUI extends StageUI> extends ScreenAdapter {

    protected ScreenHandler screenHandler;

    private StageUI ui;

    private Stage stage;
    private boolean cleared;

    private long initalFrame;

    public StageScreen(TUI ui) {
        this.ui = ui;
    }

    //init -> add -> showScreen.
    public final void init(Stage stage, ScreenHandler handler) {
        this.stage = stage;
        this.screenHandler = handler;
        if (this.ui != null) {
            this.ui.stageScreen = this;

            try {
                ui.init();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error initializing getUI");
            }
        }
    }

    protected final void clear() {
        cleared = true;
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    protected final void showDialog(Dialog dialog, Stage stage) {
        dialog.show(stage, null);
        dialog.setPosition(Math.round((stage.getWidth() - dialog.getWidth()) / 2), Math.round((stage.getHeight() - dialog.getHeight()) / 2));
    }

    @Override
    public void render(float delta) {
        if (!cleared) {
            clear();
        }
        long currentFrame = Gdx.graphics.getFrameId();
        if (currentFrame - initalFrame == 1) {
            rendered();
        }

        onPreRender(delta);
        stage.act(delta);
        stage.draw();
        onPostRender(delta);

        if (initalFrame == 0) {
            initalFrame = currentFrame;
        }
        cleared = false;
    }

    @Override
    public void resume() {
        super.resume();
    }

    //called after the screen has been drawn once
    public void rendered() {

    }

    public TUI getUI() {
        return (TUI) ui;
    }

    public Stage getStage() {
        return stage;
    }

    public abstract void initialize();

    public void onPreRender(float delta) {

    }

    public void onPostRender(float delta) {

    }

    public abstract void showing();

    public abstract void clicked(int id, Actor actor);

    public void backPressed() {
        if (ui != null)
            ui.backPressed();
    }

    @Override
    public void hide() {
        ui.remove();
        //remove dialogs
        for (Actor actor : ui.getActors()) {
            if (actor instanceof Dialog) {
                ((Dialog) actor).hide();
            }
        }
    }

    public void initCache() {
        if (ui != null) {
            stage.addActor(ui);
            ui.invalidateHierarchy();
        }
    }
}
