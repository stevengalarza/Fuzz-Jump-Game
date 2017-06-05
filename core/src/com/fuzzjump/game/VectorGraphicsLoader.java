package com.fuzzjump.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.steveadoo.customizetextures.CColorGroup;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class VectorGraphicsLoader {

    protected final ExecutorService workerService;
    protected final ExecutorService cacheService;
    private final String cacheLocation;

    public VectorGraphicsLoader(ExecutorService workerService, String cacheLocation) {
        this.cacheLocation = cacheLocation;
        this.workerService = workerService;
        this.cacheService = Executors.newSingleThreadExecutor();
    }


    public TextureRegion loadSVG(final VectorDetails vectorDetails, CColorGroup replaceGroup, CColorGroup baseGroup, final boolean cache) {
        if (cache) {
            if (Gdx.files.isLocalStorageAvailable()) {
                FileHandle file = Gdx.files.local("pngcache/" + vectorDetails.atlas + ".png");
                try {
                    if (file.exists())
                        return new TextureRegion(new Texture(file));
                } catch (Exception e) {
                    e.printStackTrace();
                    file.delete();
                }
            }
        }

        final String name = vectorDetails.filename;
        float targetWidth = getValue(vectorDetails.width);
        float targetHeight = getValue(vectorDetails.height);

        String svgString = Gdx.files.internal(name).readString();

        if (replaceGroup != null) {
            for (int i = 0; i < replaceGroup.colors.length; i++) {
                svgString = svgString.replaceAll(baseGroup.colors[replaceGroup.colors[i].index].colorString, replaceGroup.colors[i].colorString);
                svgString = svgString.replaceAll(baseGroup.colors[replaceGroup.colors[i].index].colorString.toUpperCase(), replaceGroup.colors[i].colorString);
            }
        }

        Future<FileHandle> future = workerService.submit(new Callable<FileHandle>() {
            public FileHandle call() {
                return Gdx.files.internal(name);
            }
        });

        switch(vectorDetails.type) {
            case "svg":
                String svgMarkup = null;
                try {
                    svgMarkup = future.get().readString();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                return new TextureRegion(load(vectorDetails, svgMarkup, targetWidth, targetHeight, cache));
            case "png":
            case "jpg":
                Texture texture = null;
                try {
                    texture = new Texture(future.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                return new TextureRegion(texture);
            default:
                throw new RuntimeException("Error loading TextureRegion, invalid file type");
        }
    }

    public TextureRegion load(final VectorDetails vectorDetails, final boolean cache) {
        if (cache) {
            FileHandle file = Gdx.files.absolute(cacheLocation + "/pngcache/" + vectorDetails.atlas + ".png");
            try {
                if (file.exists())
                    return new TextureRegion(new Texture(file));
            } catch (Exception e) {
                e.printStackTrace();
                file.delete();
            }
        }

        final String name = vectorDetails.filename;
        float targetWidth = getValue(vectorDetails.width);
        float targetHeight = getValue(vectorDetails.height);
        Future<FileHandle> future = workerService.submit(new Callable<FileHandle>() {
            public FileHandle call() {
                return Gdx.files.internal(name);
            }
        });

        switch(vectorDetails.type) {
            case "svg":
                String svgMarkup = null;
                try {
                    svgMarkup = future.get().readString();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                return new TextureRegion(load(vectorDetails, svgMarkup, targetWidth, targetHeight, cache));
            case "png":
            case "jpg":
                Texture texture = null;
                try {
                    texture = new Texture(future.get());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
                return new TextureRegion(texture);
            default:
                throw new RuntimeException("Error loading TextureRegion, invalid file type");
        }
    }

    public Vector2 calculateSize(float targetWidth, float targetHeight, float aspectRatio) {
        Vector2 size = new Vector2(targetWidth, targetHeight);
        if (targetWidth == Float.MIN_VALUE) {
            size.x = targetHeight * aspectRatio;
        } else if (targetHeight == Float.MIN_VALUE) {
            size.y = targetWidth / aspectRatio;
        }
        return size;
    }

    protected void cache(final byte[] data, final VectorDetails vectorDetails) {
        cacheService.submit(new Runnable() {
            public void run() {
                FileHandle file = Gdx.files.absolute(cacheLocation + "/pngcache/" + vectorDetails.getAtlasName() + ".png");
                Pixmap pixmap = new Pixmap(data, 0, data.length);
                try {
                    PixmapIO.writePNG(file, pixmap);
                    pixmap.dispose();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private float getValue(String value) {
        if (value.equals("asp"))
            return Float.MIN_VALUE;
        String[] params = value.split(":");
        float floatValue = Float.parseFloat(params[1]);
        switch (params[0]) {
            case "screen_width":
                return Gdx.graphics.getWidth() * floatValue;
            case "screen_height":
                return Gdx.graphics.getHeight() * floatValue;
            case "absolute":
            default:
                return floatValue;
        }
    }

    public abstract TextureRegion load(VectorDetails vectorDetails, String svgMarkup, float targetWidth, float targetHeight, boolean cache);

    public static class VectorDetails {

        protected String filename;
        protected String atlas;
        protected String width;
        protected String height;
        protected String type;

        public VectorDetails() {
        }

        public VectorDetails(String filename, String atlas, String width, String height) {
            this.filename = filename;
            this.atlas = atlas;
            this.width = width;
            this.height = height;
            this.type = filename.substring(filename.lastIndexOf('.')+1).trim();
        }

        public String getAtlasName() {
            return atlas;
        }
    }
}