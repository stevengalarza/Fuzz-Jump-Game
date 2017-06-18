package com.fuzzjump.game.game.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.fuzzjump.game.game.Assets;
import com.fuzzjump.game.game.player.unlockable.UnlockableColorizer;
import com.fuzzjump.game.game.player.unlockable.UnlockableRepository;
import com.fuzzjump.game.game.screen.ui.SplashUI;
import com.fuzzjump.game.service.user.IUserService;
import com.fuzzjump.libgdxscreens.ScreenLoader;
import com.fuzzjump.libgdxscreens.StageScreen;
import com.fuzzjump.libgdxscreens.Textures;
import com.fuzzjump.libgdxscreens.VectorGraphicsLoader;

import org.jrenner.smartfont.SmartFontGenerator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Created by Steven Galarza on 6/15/2017.
 */
public class SplashScreen extends StageScreen<SplashUI> {

    private final IUserService userService;
    private final Textures textures;
    private final Skin skin;
    private final UnlockableRepository definitions;
    private final UnlockableColorizer colorizer;

    @Inject
    public SplashScreen(SplashUI ui, IUserService userService, Textures textures, Skin skin, UnlockableRepository definitions, UnlockableColorizer colorizer) {
        super(ui);
        this.userService = userService;
        this.textures = textures;
        this.skin = skin;
        this.definitions = definitions;
        this.colorizer = colorizer;
    }

    @Override
    public void onReady() {
        ScreenLoader loader = getLoader();
        Color shadow = new Color(0, 0, 0, .4f);
        SmartFontGenerator smartGen = new SmartFontGenerator();
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal("Grandstander-clean.ttf"));
        FreeTypeFontParameter param = new FreeTypeFontParameter();

        param.genMipMaps = true;
        param.shadowColor = shadow;
        param.shadowOffsetY = Gdx.graphics.getHeight() / 375;

        textures.add(new VectorGraphicsLoader.VectorDetail(Assets.LOGO, Assets.UI_LOGO, "screen_width:.8", "screen_width:.6"));

        skin.addRegions(new TextureAtlas("main.pack"));
        skin.add(Assets.UI_BACKGROUND, new TextureRegion(new Texture(Gdx.files.internal(Assets.SUNNY_DAY_SKY))));
        skin.add(Assets.UI_GROUND, new TextureRegion(new Texture(Gdx.files.internal(Assets.SUNNY_DAY_GROUND))));

        int screenHeight = Gdx.graphics.getHeight();

        // Load fonts.


        loader.add(() -> createFont(Assets.LARGE_FONT, smartGen, gen, param, screenHeight / 10));
        loader.add(() -> createFont(Assets.BIG_FONT, smartGen, gen, param, screenHeight / 20));
        loader.add(() -> createFont(Assets.DEFAULT_FONT, smartGen, gen, param, screenHeight / 30));
        loader.add(() -> {
            createFont(Assets.PROFILE_FONT, smartGen, gen, param, screenHeight / 45);
            Assets.DEBUG_FONT = skin.getFont(Assets.PROFILE_FONT);
        });
        loader.add(() -> createFont(Assets.INGAME_FONT, smartGen, gen, param, screenHeight / 60));
        loader.add(() -> createFont(Assets.SMALL_FONT, smartGen, gen, param, screenHeight / 70));
        loader.add(() -> createFont(Assets.SMALL_INGAME_FONT, smartGen, gen, param, 25));

        // Load skin
        loader.add(() -> skin.load(Gdx.files.internal(Assets.SKIN)));

        // Load textures
        loader.add(this::loadTextures);

        // Load unlockable definitions
        loader.add(definitions::init);

        // Preload Fuzzles
        for (int i = 0; i < Assets.FUZZLE_COUNT; i++) {
            final int index = i;
            loader.add(() -> colorizer.getColored(getUI().getTextures(), definitions.getDefinition(index), 0, false));
        }

        // Preload frames and help compiler optimize SVG loading code
        for (int i = 32, n = i + 2; i < n; i++) {
            // TODO Not necessary if Fuzzles haven't been cached, but that is only the case on first run, but we want to prevent this if fuzzles are cached in order to have a shorter first time start up
            final int index = i;
            loader.add(() -> colorizer.getColored(getUI().getTextures(), definitions.getDefinition(index), 0, false));
        }

        loader.onDone(() -> screenHandler.showScreen(MainScreen.class));

        getUI().drawSplash();
    }

    @Override
    public void onPostRender(float delta) {
        ScreenLoader loader = getLoader();
        if (!loader.process()) {
            // Do animation ...
        }
    }

    @Override
    public void showing() {

    }

    @Override
    public void clicked(int id, Actor actor) {

    }

    private void loadTextures() {
        try {
            List<VectorGraphicsLoader.VectorDetail> vectorDetails = new ArrayList<>();

            DocumentBuilder bldr = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = bldr.parse(Gdx.files.internal(Assets.SVG_RES).read());

            NodeList vectors = document.getDocumentElement().getElementsByTagName("svginfo");
            for (int i = 0, n = vectors.getLength(); i < n; i++) {
                Element detail = (Element) vectors.item(i);
                NodeList info = detail.getChildNodes();

                String svg = info.item(1).getTextContent();
                String atlas = info.item(3).getTextContent();
                String width = info.item(5).getTextContent();
                String height = info.item(7).getTextContent();

                vectorDetails.add(new VectorGraphicsLoader.VectorDetail(Assets.SVG_DIR + svg, atlas, width, height));
            }
            textures.add(vectorDetails);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createFont(String name, SmartFontGenerator smartGen, FreeTypeFontGenerator gen, FreeTypeFontParameter param, int size) {
        param.size = size;
        skin.add(name, gen.generateFont(param), BitmapFont.class);
    }
}
