package com.fuzzjump.game.game.screen.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.fuzzjump.game.game.FuzzJumpGame;
import com.fuzzjump.libgdxscreens.StageUI;
import com.fuzzjump.libgdxscreens.Textures;

import javax.inject.Inject;

public class MainUI extends StageUI {

    @Inject
    public MainUI(Textures textures, Skin skin) {
        super(textures, skin);
    }

    @Override
    public void init() {
    }


    @Override
    public void backPressed() {

    }

}
