package com.github.SquidPony;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import squidpony.FakeLanguageGen;
import squidpony.GwtCompatibility;
import squidpony.panel.IColoredString;
import squidpony.squidai.DijkstraMap;
import squidpony.squidgrid.Direction;
import squidpony.squidgrid.FOV;
import squidpony.squidgrid.Radius;
import squidpony.squidgrid.SpatialMap;
import squidpony.squidgrid.gui.gdx.*;
import squidpony.squidgrid.mapping.DungeonGenerator;
import squidpony.squidgrid.mapping.DungeonUtility;
import squidpony.squidgrid.mapping.MixedGenerator;
import squidpony.squidmath.Coord;
import squidpony.squidmath.CoordPacker;
import squidpony.squidmath.RNG;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class SquidShowcaseDemo extends ApplicationAdapter {
    private enum Phase {WAIT, PLAYER_ANIM, MONSTER_ANIM}
    private static class Monster {
        public AnimatedEntity entity;
        public int state;

        public Monster(AnimatedEntity ae, int state)
        {
            entity = ae;
            this.state = state;
        }
        public Monster change(int state)
        {
            this.state = state;
            return this;
        }
    }
    SpriteBatch batch;

    private Phase phase = Phase.WAIT;
    private RNG rng;
    private SquidLayers display;
    private SquidPanel subCell;
    private SquidMessageBox messages;
    /** Non-{@code null} iff '?' was pressed before */
    private /*Nullable*/ Actor help;
    private DungeonGenerator dungeonGen;
    private char[][] decoDungeon, bareDungeon, lineDungeon;
    private double[][] res;
    private int[][] lights;
    private Color[][] colors, bgColors;
    private double[][] fovmap, pathMap;
    private AnimatedEntity player;
    private FOV fov;
    /** In number of cells */
    private int width;
    /** In number of cells */
    private int height;
    /** The pixel width of a cell */
    private int cellWidth;
    /** The pixel height of a cell */
    private int cellHeight;
    private VisualInput input;
    private double counter;
    private boolean[][] seen;
    private int health = 7;
    private SquidColorCenter fgCenter, bgCenter;
    private Color bgColor;
    private SpatialMap<Integer, Monster> monsters;
    private DijkstraMap getToPlayer, playerToCursor;
    private Stage stage;
    private int framesWithoutAnimation = 0;
    private Coord cursor;
    private ArrayList<Coord> toCursor;
    private ArrayList<Coord> awaitedMoves;
    private String lang;
    private SquidColorCenter[] colorCenters;
    private int currentCenter;
    private boolean changingColors = false;
    private TextCellFactory textFactory;
    public static final int INTERNAL_ZOOM = 1;
    private Viewport viewport;
    private float currentZoomX = INTERNAL_ZOOM, currentZoomY = INTERNAL_ZOOM;
    TextureAtlas atlas;
    TextureAtlas.AtlasRegion region;
    @Override
    public void create () {
        // gotta have a random number generator. Here the RNG is unseeded, which means a different dungeon every time,
        // among many other changes.
        rng = new RNG();

        // seeds can be given easily to RNG's constructor. If you want to request the current state from a random number
        // generator or change the state in the middle of usage, you need to use a StatefulRNG (or EditRNG) instead of a
        // normal RNG; StatefulRNG and EditRNG can also be constructed with seeds like RNG can.
        //rng = new RNG("seeeeeeeed");
        //rng = new RNG(0xBADACE);

        // for demo purposes, we allow changing the SquidColorCenter and the filter effect associated with it.
        // next, we populate the colorCenters array with the SquidColorCenters that will modify any colors we request
        // of them using the filter we specify. Only one SquidColorCenter will be used at any time for foreground, and
        // sometimes another will be used for background.
        colorCenters = new SquidColorCenter[18];
        // MultiLerpFilter here is given two colors to tint everything toward one of; this is meant to reproduce the
        // "Hollywood action movie poster" style of using primarily light orange (explosions) and gray-blue (metal).

        colorCenters[0] = new SquidColorCenter(new Filters.MultiLerpFilter(
                new Color[]{SColor.GAMBOGE_DYE, SColor.COLUMBIA_BLUE},
                new float[]{0.25f, 0.2f}
        ));
        colorCenters[1] = colorCenters[0];

        // MultiLerpFilter here is given three colors to tint everything toward one of; this is meant to look bolder.

        colorCenters[2] = new SquidColorCenter(new Filters.MultiLerpFilter(
                new Color[]{SColor.RED_PIGMENT, SColor.MEDIUM_BLUE, SColor.LIME_GREEN},
                new float[]{0.2f, 0.25f, 0.25f}
        ));
        colorCenters[3] = colorCenters[2];

        // ColorizeFilter here is given a slightly-grayish dark brown to imitate a sepia tone.

        colorCenters[4] = new SquidColorCenter(new Filters.ColorizeFilter(SColor.CLOVE_BROWN, 0.7f, -0.05f));
        colorCenters[5] = new SquidColorCenter(new Filters.ColorizeFilter(SColor.CLOVE_BROWN, 0.65f, 0.07f));

        // HallucinateFilter makes all the colors very saturated and move even when you aren't doing anything.

        colorCenters[6] = new SquidColorCenter(new Filters.HallucinateFilter());
        colorCenters[7] = colorCenters[6];

        // SaturationFilter here is used to over-saturate the colors slightly. Background is less saturated.

        colorCenters[8] = new SquidColorCenter(new Filters.SaturationFilter(1.35f));
        colorCenters[9] = new SquidColorCenter(new Filters.SaturationFilter(1.15f));

        // SaturationFilter here is used to de-saturate the colors slightly. Background is less saturated.

        colorCenters[10] = new SquidColorCenter(new Filters.SaturationFilter(0.7f));
        colorCenters[11] = new SquidColorCenter(new Filters.SaturationFilter(0.5f));

        // WiggleFilter here is used to randomize the colors slightly.

        colorCenters[12] = new SquidColorCenter(new Filters.WiggleFilter());
        colorCenters[13] = colorCenters[12];

        // SaturationFilter here is used to de-saturate the colors slightly. Background is less saturated.

        colorCenters[14] = new SquidColorCenter(new Filters.PaletteFilter(SColor.BLUE_GREEN_SERIES));
        colorCenters[15] = new SquidColorCenter(new Filters.PaletteFilter(SColor.ACHROMATIC_SERIES));

        colorCenters[16] = DefaultResources.getSCC();
        colorCenters[17] = colorCenters[16];

        fgCenter = colorCenters[16];
        bgCenter = colorCenters[17];
        currentCenter = 8;
        batch = new SpriteBatch();
        width = 60;
        height = 30;
        //NOTE: cellWidth and cellHeight are assigned values that are significantly larger than the corresponding sizes
        //in the EverythingDemoLauncher's main method. Because they are scaled up by an integer here, they can be scaled
        //down when rendered, allowing certain small details to appear sharper. This _only_ works with distance field,
        //a.k.a. stretchable, fonts! INTERNAL_ZOOM is a tradeoff between rendering more pixels to increase quality (when
        // values are high) or rendering fewer pixels for speed (when values are low). Using 2 seems to work well.
        cellWidth = 13 * INTERNAL_ZOOM;
        cellHeight = 26 * INTERNAL_ZOOM;
        // getStretchableFont loads an embedded font, Inconsolata-LGC-Custom, that is a distance field font as mentioned
        // earlier. We set the smoothing multiplier on it only because we are using internal zoom to increase sharpness
        // on small details, but if the smoothing is incorrect some sizes look blurry or over-sharpened. This can be set
        // manually if you use a constant internal zoom; here we use 1f for internal zoom 1, about 2/3f for zoom 2, and
        // about 1/2f for zoom 3. If you have more zooms as options for some reason, this formula should hold for many
        // cases but probably not all.
        textFactory = DefaultResources.getStretchableFont().setSmoothingMultiplier(2f / (INTERNAL_ZOOM + 1f))
                .width(cellWidth).height(cellHeight).initBySize();
        // Creates a layered series of text grids in a SquidLayers object, using the previously set-up textFactory and
        // SquidColorCenters.
        display = new SquidLayers(width * 2, height, cellWidth, cellHeight,
                textFactory.copy(), bgCenter, fgCenter);
        //subCell is a SquidPanel, the same class that SquidLayers has for each of its layers, but we want to render
        //certain effects on top of all other panels, which can't be done in the all-in-one-pass rendering of the grids
        //in SquidLayers, though it could be done with a slight hassle if the effects are made into AnimatedEntity
        //objects or Actors, then rendered separately like the monsters are (see render() below). It is called subCell
        //because its text will be made smaller than a full cell, and appears in the upper left corner for things like
        //the current health of the player and an '!' for alerted monsters.
        subCell = new SquidPanel(width * 2, height, textFactory.copy(), fgCenter);

        display.setAnimationDuration(0.1f);
        messages = new SquidMessageBox(width * 2, 4,
                textFactory.copy());
        // a bit of a hack to increase the text height slightly without changing the size of the cells they're in.
        // this causes a tiny bit of overlap between cells, which gets rid of an annoying gap between vertical lines.
        // if you use '#' for walls instead of box drawing chars, you don't need this.
        messages.setTextSize(cellWidth, cellHeight + INTERNAL_ZOOM * 2);
        display.setTextSize(cellWidth, cellHeight + INTERNAL_ZOOM * 2);
        //The subCell SquidPanel uses a smaller size here; the numbers 8 and 16 should change if cellWidth or cellHeight
        //change, and the INTERNAL_ZOOM multiplier keeps things sharp, the same as it does all over here.
        subCell.setTextSize(8 * INTERNAL_ZOOM, 16 * INTERNAL_ZOOM);
        viewport = new StretchViewport(width * 2 * cellWidth, (height + 4) * cellHeight);
        stage = new Stage(viewport, batch);

        //These need to have their positions set before adding any entities if there is an offset involved.
        messages.setBounds(0, 0, cellWidth * width * 2, cellHeight * 4);
        display.setPosition(0, messages.getHeight());
        subCell.setPosition(0, messages.getHeight());
        messages.appendWrappingMessage("Use numpad or vi-keys (hjklyubn) to move. Use ? for help, f to change colors, q to quit." +
                " Click the top or bottom border of this box to scroll.");
        counter = 0;

        dungeonGen = new DungeonGenerator(width, height, rng);
        dungeonGen.addWater(8, 6);
        dungeonGen.addGrass(5);
        dungeonGen.addBoulders(10);
        dungeonGen.addDoors(18, false);
        MixedGenerator mix = new MixedGenerator(width, height, rng);
        mix.putCaveCarvers(1);
        mix.putBoxRoomCarvers(1);
        mix.putRoundRoomCarvers(2);
        char[][] mg = mix.generate();
        decoDungeon = dungeonGen.generate(mg);
        // DefaultResources has not only default fonts but now also default icons.
        // These need the actual assets to be downloaded as part of the zip or tar.gz
        // archive of assets, or separately fetched from GitHub in the assets/ folder.
        atlas = DefaultResources.getIconAtlas();
        region = atlas.findRegion("haunting");

        // change the TilesetType to lots of different choices to see what dungeon works best.
        //bareDungeon = dungeonGen.generate(TilesetType.DEFAULT_DUNGEON);
        bareDungeon = dungeonGen.getBareDungeon();
        lineDungeon = DungeonUtility.doubleWidth(DungeonUtility.hashesToLines(decoDungeon, true));
        // it's more efficient to get random floors from a packed set containing only (compressed) floor positions.
        short[] placement = CoordPacker.pack(bareDungeon, '.');
        Coord pl = dungeonGen.utility.randomCell(placement);
        placement = CoordPacker.removePacked(placement, pl.x, pl.y);
        int numMonsters = 60;
        monsters = new SpatialMap<Integer, Monster>(numMonsters);
        for(int i = 0; i < numMonsters; i++)
        {
            Coord monPos = dungeonGen.utility.randomCell(placement);
            placement = CoordPacker.removePacked(placement, monPos.x, monPos.y);
            monsters.put(monPos, i, new Monster(display.animateActor(monPos.x, monPos.y, region,
                    fgCenter.filter(display.getPalette().get(11)), true), 0));
            //monsters.put(monPos, i, new Monster(display.animateActor(monPos.x, monPos.y, 'Я',
            //        fgCenter.filter(display.getPalette().get(11))), 0));
        }
        // your choice of FOV matters here.
        fov = new FOV(FOV.RIPPLE_TIGHT);
        res = DungeonUtility.generateResistances(decoDungeon);
        fovmap = fov.calculateFOV(res, pl.x, pl.y, 8, Radius.SQUARE);
        getToPlayer = new DijkstraMap(decoDungeon, DijkstraMap.Measurement.CHEBYSHEV);
        getToPlayer.rng = rng;
        getToPlayer.setGoal(pl);
        pathMap = getToPlayer.scan(null);

        player = display.animateActor(pl.x, pl.y, '@',
                fgCenter.loopingGradient(SColor.CAPE_JASMINE, SColor.HAN_PURPLE, 45), 1.5f, true);
//                fgCenter.filter(display.getPalette().get(30)));
        cursor = Coord.get(-1, -1);
        toCursor = new ArrayList<Coord>(10);
        awaitedMoves = new ArrayList<Coord>(10);
        playerToCursor = new DijkstraMap(decoDungeon, DijkstraMap.Measurement.EUCLIDEAN);
        final int[][] initialColors = DungeonUtility.generatePaletteIndices(lineDungeon),
                initialBGColors = DungeonUtility.generateBGPaletteIndices(lineDungeon);
        colors = new Color[width*2][height];
        bgColors = new Color[width*2][height];
        ArrayList<Color> palette = display.getPalette();
        bgColor = SColor.DARK_SLATE_GRAY;
        for (int i = 0; i < width * 2; i++) {
            for (int j = 0; j < height; j++) {
                colors[i][j] = palette.get(initialColors[i][j]);
                bgColors[i][j] = palette.get(initialBGColors[i][j]);
            }
        }
        lights = DungeonUtility.generateLightnessModifiers(decoDungeon, counter);
        seen = new boolean[width][height];
        lang = FakeLanguageGen.RUSSIAN_AUTHENTIC.sentence(rng, 4, 6, new String[]{",", ",", ",", " -"},
                new String[]{"..."}, 0.25);
        // this is a big one.
        // SquidInput can be constructed with a KeyHandler (which just processes specific keypresses), a SquidMouse
        // (which is given an InputProcessor implementation and can handle multiple kinds of mouse move), or both.
        // keyHandler is meant to be able to handle complex, modified key input, typically for games that distinguish
        // between, say, 'q' and 'Q' for 'quaff' and 'Quip' or whatever obtuse combination you choose. The
        // implementation here handles hjklyubn keys for 8-way movement, numpad for 8-way movement, arrow keys for
        // 4-way movement, and wasd for 4-way movement. Shifted letter keys produce capitalized chars when passed to
        // KeyHandler.handle(), but we don't care about that so we just use two case statements with the same body,
        // one for the lower case letter and one for the upper case letter.
        // You can also set up a series of future moves by clicking within FOV range, using mouseMoved to determine the
        // path to the mouse position with a DijkstraMap (called playerToCursor), and using touchUp to actually trigger
        // the event when someone clicks.
        input = new VisualInput(new SquidInput.KeyHandler() {
            @Override
            public void handle(char key, boolean alt, boolean ctrl, boolean shift) {
                switch (key)
                {
                    case SquidInput.UP_ARROW:
                    case 'k':
                    case 'w':
                    case 'K':
                    case 'W':
                    {
                        move(0, -1);
                        break;
                    }
                    case SquidInput.DOWN_ARROW:
                    case 'j':
                    case 's':
                    case 'J':
                    case 'S':
                    {
                        move(0, 1);
                        break;
                    }
                    case SquidInput.LEFT_ARROW:
                    case 'h':
                    case 'a':
                    case 'H':
                    case 'A':
                    {
                        move(-1, 0);
                        break;
                    }
                    case SquidInput.RIGHT_ARROW:
                    case 'l':
                    case 'd':
                    case 'L':
                    case 'D':
                    {
                        move(1, 0);
                        break;
                    }

                    case SquidInput.UP_LEFT_ARROW:
                    case 'y':
                    case 'Y':
                    {
                        move(-1, -1);
                        break;
                    }
                    case SquidInput.UP_RIGHT_ARROW:
                    case 'u':
                    case 'U':
                    {
                        move(1, -1);
                        break;
                    }
                    case SquidInput.DOWN_RIGHT_ARROW:
                    case 'n':
                    case 'N':
                    {
                        move(1, 1);
                        break;
                    }
                    case SquidInput.DOWN_LEFT_ARROW:
                    case 'b':
                    case 'B':
                    {
                        move(-1, 1);
                        break;
                    }
                    case '?': {
                        toggleHelp();
                        break;
                    }
                    case 'Q':
                    case 'q':
                    case SquidInput.ESCAPE:
                    {
                        Gdx.app.exit();
                        break;
                    }
                    case 'f':
                    case 'F':
                    {
                        currentCenter = (currentCenter + 1) % 9;
                        // idx is 3 when we use the HallucinateFilter, which needs special work
                        changingColors = currentCenter == 3;
                        fgCenter = colorCenters[currentCenter * 2];
                        bgCenter = colorCenters[currentCenter * 2 + 1];
                        display.setFGColorCenter(fgCenter);
                        display.setBGColorCenter(bgCenter);
                        break;
                    }
                }
            }
        }, new SquidMouse(cellWidth, cellHeight, width * 2, height, 0, 0, new InputAdapter() {

            // if the user clicks within FOV range and there are no awaitedMoves queued up, generate toCursor if it
            // hasn't been generated already by mouseMoved, then copy it over to awaitedMoves.
            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if(fovmap[(screenX) / 2][screenY] > 0.0 && awaitedMoves.isEmpty()) {
                    if (toCursor.isEmpty()) {
                        cursor = Coord.get((screenX) / 2, screenY);
                        //Uses DijkstraMap to get a path. from the player's position to the cursor
                        toCursor = playerToCursor.findPath(30, null, null, Coord.get(player.gridX, player.gridY), cursor);
                    }
                    awaitedMoves = new ArrayList<Coord>(toCursor);
                }
                return false;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                return mouseMoved(screenX, screenY);
            }

            // causes the path to the mouse position to become highlighted (toCursor contains a list of points that
            // receive highlighting). Uses DijkstraMap.findPath() to find the path, which is surprisingly fast.
            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                if(!awaitedMoves.isEmpty())
                    return false;
                if(cursor.x == screenX && cursor.y == screenY)
                {
                    return false;
                }
                if(fovmap[(screenX) / 2][screenY] > 0.0) {
                    cursor = Coord.get((screenX) / 2, screenY);
                    //Uses DijkstraMap to get a path. from the player's position to the cursor
                    toCursor = playerToCursor.findPath(30, null, null, Coord.get(player.gridX, player.gridY), cursor);
                }
                return false;
            }
        }));
        //set this to true to test visual input on desktop
        input.forceButtons = false;
        //actions to give names to in the visual input menu
        input.init("filter", "??? help?", "quit");
        // ABSOLUTELY NEEDED TO HANDLE INPUT
        Gdx.input.setInputProcessor(new InputMultiplexer(input, stage));
        subCell.setOffsetY(messages.getGridHeight() * cellHeight);
        // and then add display and messages, our two visual components, to the list of things that act in Stage.
        stage.addActor(display);
        // stage.addActor(subCell); // this is not added since it is manually drawn after other steps
        stage.addActor(messages);
        viewport = input.resizeInnerStage(stage);
    }

    @Override
    public void resume() {
        super.resume();
        atlas = DefaultResources.getIconAtlas();
        region = atlas.findRegion("haunting");
        for(Monster mon : monsters)
        {
            mon.entity = display.animateActor(mon.entity.gridX, mon.entity.gridY,
                    region, fgCenter.filter(display.getPalette().get(11)), true);
        }
    }

    /**
     * Move the player or open closed doors, remove any monsters the player bumped, then update the DijkstraMap and
     * have the monsters that can see the player try to approach.
     * In a fully-fledged game, this would not be organized like this, but this is a one-file demo.
     * @param xmod
     * @param ymod
     */
    private void move(int xmod, int ymod) {
        clearHelp();

        if(health <= 0) return;

        int newX = player.gridX + xmod, newY = player.gridY + ymod;
        if (newX >= 0 && newY >= 0 && newX < width && newY < height
                && bareDungeon[newX][newY] != '#')
        {
            // '+' is a door.
            if (decoDungeon[newX][newY] == '+') {
                decoDungeon[newX][newY] = '/';
                lineDungeon[newX*2][newY] = '/';
                // changes to the map mean the resistances for FOV need to be regenerated.
                res = DungeonUtility.generateResistances(decoDungeon);
                // recalculate FOV, store it in fovmap for the render to use.
                fovmap = fov.calculateFOV(res, player.gridX, player.gridY, 8, Radius.SQUARE);
            } else {
                // recalculate FOV, store it in fovmap for the render to use.
                fovmap = fov.calculateFOV(res, newX, newY, 8, Radius.SQUARE);
                display.slide(player, newX, newY);
                monsters.remove(Coord.get(newX, newY));
            }

            phase = Phase.PLAYER_ANIM;
        }
    }

    // check if a monster's movement would overlap with another monster.
    private boolean checkOverlap(Monster mon, int x, int y, ArrayList<Coord> futureOccupied)
    {
        if(monsters.containsPosition(Coord.get(x, y)) && !mon.equals(monsters.get(Coord.get(x, y))))
            return true;
        for(Coord p : futureOccupied)
        {
            if(x == p.x && y == p.y)
                return true;
        }
        return false;
    }

    private void postMove()
    {

        phase = Phase.MONSTER_ANIM;
        // The next two lines are important to avoid monsters treating cells the player WAS in as goals.
        getToPlayer.clearGoals();
        getToPlayer.resetMap();
        // now that goals are cleared, we can mark the current player position as a goal.
        getToPlayer.setGoal(player.gridX, player.gridY);
        // this is an important piece of DijkstraMap usage; the argument is a Set of Points for squares that
        // temporarily cannot be moved through (not walls, which are automatically known because the map char[][]
        // was passed to the DijkstraMap constructor, but things like moving creatures and objects).
        LinkedHashSet<Coord> monplaces = monsters.positions();

        pathMap = getToPlayer.scan(monplaces);

        // recalculate FOV, store it in fovmap for the render to use.
        fovmap = fov.calculateFOV(res, player.gridX, player.gridY, 8, Radius.SQUARE);
        // handle monster turns
        ArrayList<Coord> nextMovePositions = new ArrayList<Coord>(25);
        for(Coord pos : monsters.positions())
        {
            Monster mon = monsters.get(pos);
            // monster values are used to store their aggression, 1 for actively stalking the player, 0 for not.
            if(mon.state > 0 || fovmap[pos.x][pos.y] > 0.1)
            {
                if(mon.state == 0)
                {
                    messages.appendMessage("The PHANTOM cackles at you, \"" +
                            FakeLanguageGen.RUSSIAN_AUTHENTIC.sentence(rng, 1, 3,
                                    new String[]{",", ",", ",", " -"}, new String[]{"!"}, 0.25) + "\"");
                }
                // this block is used to ensure that the monster picks the best path, or a random choice if there
                // is more than one equally good best option.
                Direction choice = null;
                double best = 9999.0;
                Direction[] ds = new Direction[8];
                rng.shuffle(Direction.OUTWARDS, ds);
                for(Direction d : ds)
                {
                    Coord tmp = pos.translate(d);
                    if(pathMap[tmp.x][tmp.y] < best &&
                            !checkOverlap(mon, tmp.x, tmp.y, nextMovePositions))
                    {
                        // pathMap is a 2D array of doubles where 0 is the goal (the player).
                        // we use best to store which option is closest to the goal.
                        best = pathMap[tmp.x][tmp.y];
                        choice = d;
                    }
                }
                if(choice != null) {
                    Coord tmp = pos.translate(choice);
                    // if we would move into the player, instead damage the player and give newMons the current
                    // position of this monster.
                    if (tmp.x == player.gridX && tmp.y == player.gridY) {
                        display.tint(player.gridX * 2, player.gridY, SColor.PURE_CRIMSON, 0, 0.415f);
                        display.tint(player.gridX * 2 + 1, player.gridY, SColor.PURE_CRIMSON, 0, 0.415f);
                        health--;
                        //player.setText("" + health);
                        monsters.positionalModify(pos, mon.change(1));
                    }
                    // otherwise store the new position in newMons.
                    else {
                        /*if (fovmap[mon.getKey().x][mon.getKey().y] > 0.0) {
                            display.put(mon.getKey().x, mon.getKey().y, 'M', 11);
                        }*/
                        nextMovePositions.add(tmp);
                        monsters.positionalModify(pos, mon.change(1));
                        monsters.move(pos, tmp);
                        display.slide(mon.entity, tmp.x, tmp.y);

                    }
                }
                else
                {
                    monsters.positionalModify(pos, mon.change(1));
                }
            }
        }

    }

    private void toggleHelp() {
        if (help != null) {
            clearHelp();
            return;
        }
        final int nbMonsters = monsters.size();

		/* Prepare the String to display */
        final IColoredString<Color> cs = new IColoredString.Impl<Color>();
        cs.append("Still ", null);
        final Color nbColor;
        if (nbMonsters <= 1)
            /* Green */
            nbColor = Color.GREEN;
        else if (nbMonsters <= 5)
            /* Orange */
            nbColor = Color.ORANGE;
        else
            /* Red */
            nbColor = Color.RED;
        cs.appendInt(nbMonsters, nbColor);
        cs.append(" monster" + (nbMonsters == 1 ? "" : "s") + " to kill", null);

        IColoredString<Color> helping1 = new IColoredString.Impl<Color>("Use numpad or vi-keys (hjklyubn) to move.", Color.WHITE);
        IColoredString<Color> helping2 = new IColoredString.Impl<Color>("Use ? for help, f to change colors, q to quit.", Color.WHITE);
        IColoredString<Color> helping3 = new IColoredString.Impl<Color>("Click the top or bottom border of the lower message box to scroll.", Color.WHITE);

        final Actor a;
			/*
			 * Use TextPanel. There's less work to do than with
			 * GroupCombinedPanel, and we can use a more legible variable-width font.
			 * It doesn't seem like it when reading this code, but this actually does
			 * much more than GroupCombinedPanel,  because we do line wrapping and
			 * justifying, without having to worry about sizes since TextPanel lays
			 * itself out.
			 */
        TextCellFactory tf = DefaultResources.getStretchableFont()
                //.setSmoothingMultiplier(2f / (INTERNAL_ZOOM + 1f))
                .width(cellWidth + 1).height(cellHeight + 5).initBySize();
        final TextPanel<Color> tp = new TextPanel<Color>(new GDXMarkup(), tf);
        tp.backgroundColor = SColor.DARK_SLATE_GRAY;

        final List<IColoredString<Color>> text = new ArrayList<IColoredString<Color>>();
        text.add(cs);
			/* No need to call IColoredString::wrap, TextPanel does it on its own */
        text.add(helping1);
        text.add(helping2);
        text.add(helping3);

        final float w = width * cellWidth, aw = helping3.length() * tf.width() * 0.8f * INTERNAL_ZOOM;
        final float h = height * cellHeight, ah = tf.height() * 7f * INTERNAL_ZOOM;
        tp.init(aw, ah, text);
        a = tp.getScrollPane();
        final float x = (w - aw) / 2f;
        final float y = (h - ah) / 2f;
        a.setPosition(x, y);
        stage.setScrollFocus(a);

        help = a;

        stage.addActor(a);
    }

    private void clearHelp() {
        if (help == null)
			/* Nothing to do */
            return;
        help.clear();
        stage.getActors().removeValue(help, true);
        help = null;
    }

    public void putMap()
    {
        display.erase();
        boolean overlapping;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                overlapping = (!monsters.isEmpty() && monsters.containsPosition(Coord.get(i,j))) || (!monsters.isEmpty() && player.gridX == i*2 && player.gridY == j);
                // if we see it now, we remember the cell and show a lit cell based on the fovmap value (between 0.0
                // and 1.0), with 1.0 being almost pure white at +215 lightness and 0.0 being rather dark at -105.
                if (fovmap[i][j] > 0.0) {
                    seen[i][j] = true;
                    display.put(i*2, j, (overlapping) ? ' ' : lineDungeon[i*2][j], fgCenter.filter(colors[i*2][j]), bgCenter.filter(bgColors[i*2][j]),
                            lights[i][j] + (int) (-105 + 320 * fovmap[i][j]));
                    display.put(i*2+1, j, (overlapping) ? ' ' : lineDungeon[i*2+1][j], fgCenter.filter(colors[i*2][j]), bgCenter.filter(bgColors[i*2][j]),
                            lights[i][j] + (int) (-105 + 320 * fovmap[i][j]));
                    // if we don't see it now, but did earlier, use a very dark background, but lighter than black.
                } else if (seen[i][j]) {
                    display.put(i*2, j, lineDungeon[i*2][j], fgCenter.filter(colors[i*2][j]), bgCenter.filter(bgColors[i*2][j]), -140);
                    display.put(i*2+1, j, lineDungeon[i*2+1][j], fgCenter.filter(colors[i*2][j]), bgCenter.filter(bgColors[i*2][j]), -140);
                }
            }
        }
        for (Coord pt : toCursor)
        {
            // use a brighter light to trace the path to the cursor, from 170 max lightness to 0 min.
            display.highlight(pt.x*2, pt.y, lights[pt.x][pt.y] + (int) (170 * fovmap[pt.x][pt.y]));
            display.highlight(pt.x*2+1, pt.y, lights[pt.x][pt.y] + (int) (170 * fovmap[pt.x][pt.y]));
        }

    }
    @Override
    public void render () {
        // standard clear the background routine for libGDX
        Gdx.gl.glClearColor(bgColor.r / 255.0f, bgColor.g / 255.0f, bgColor.b / 255.0f, 1.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        // not sure if this is always needed...
        //Gdx.gl.glEnable(GL20.GL_BLEND);

        // used as the z-axis when generating Simplex noise to make water seem to "move"
        counter += Gdx.graphics.getDeltaTime() * 15;
        // this does the standard lighting for walls, floors, etc. but also uses counter to do the Simplex noise thing.
        lights = DungeonUtility.generateLightnessModifiers(decoDungeon, counter);
        //textFactory.configureShader(batch);

        // you done bad. you done real bad.
        if (health <= 0) {
            // still need to display the map, then write over it with a message.
            toCursor.clear();
            monsters.clear();
            display.setLightnesses(GwtCompatibility.fill2D(-200, width*2, height));
            putMap();
            display.putBoxedString(width - 18, height / 2 - 6, " THE PHANTOMS HAVE EATEN YOUR SOUL! ");
            display.putBoxedString(width - 18, height / 2 - 4,  "   AT LAST, YOU HEAR THEM MUTTER,   ");
            display.putBoxedString(width - lang.length() / 2, height / 2, lang);
            display.putBoxedString(width - 18, height / 2 + 4,  "      ...WHATEVER THAT MEANS.       ");
            display.putBoxedString(width - 18, height / 2 + 9, "             q to quit.             ");

            // because we return early, we still need to draw.
            stage.draw();
            stage.act();
            // q still needs to quit.
            if(input.hasNext())
                input.next();
            return;
        }
        // need to display the map every frame, since we clear the screen to avoid artifacts.
        putMap();
        // if the user clicked, we have a list of moves to perform.
        if(!awaitedMoves.isEmpty())
        {
            // extremely similar to the block below that also checks if animations are done
            // this doesn't check for input, but instead processes and removes Points from awaitedMoves.
            if(!display.hasActiveAnimations()) {
                ++framesWithoutAnimation;
                if (framesWithoutAnimation >= 3) {
                    framesWithoutAnimation = 0;
                    switch (phase) {
                        case WAIT:
                        case MONSTER_ANIM:
                            Coord m = awaitedMoves.remove(0);
                            toCursor.remove(0);
                            move(m.x - player.gridX, m.y - player.gridY);
                            break;
                        case PLAYER_ANIM:
                            postMove();
                            break;
                    }
                }
            }
        }
        // if we are waiting for the player's input and get input, process it.
        else if(input.hasNext() && !display.hasActiveAnimations() && phase == Phase.WAIT) {
            input.next();
        }
        // if the previous blocks didn't happen, and there are no active animations, then either change the phase
        // (because with no animations running the last phase must have ended), or start a new animation soon.
        else if(!display.hasActiveAnimations()) {
            ++framesWithoutAnimation;
            if (framesWithoutAnimation >= 3) {
                framesWithoutAnimation = 0;
                switch (phase) {
                    case WAIT:
                        break;
                    case MONSTER_ANIM: {
                        phase = Phase.WAIT;
                    }
                    break;
                    case PLAYER_ANIM: {
                        postMove();

                    }
                }
            }
        }
        // if we do have an animation running, then how many frames have passed with no animation needs resetting
        else
        {
            framesWithoutAnimation = 0;
        }

        input.show();
        // stage has its own batch and must be explicitly told to draw(). this also causes it to act().
        stage.getViewport().apply(true);
        stage.draw();
        stage.act();

        subCell.erase();
        if(help == null) {
            // display does not draw all AnimatedEntities by default, since FOV often changes how they need to be drawn.
            batch.begin();
            // the player needs to get drawn every frame, of course.
            display.drawActor(batch, 1.0f, player);
            subCell.put(player.gridX * 2 + 1, player.gridY, Character.forDigit(health, 10), SColor.DARK_PINK);

            for (Monster mon : monsters) {
                // monsters are only drawn if within FOV.
                if (fovmap[mon.entity.gridX][mon.entity.gridY] > 0.0) {
                    display.drawActor(batch, 1.0f, mon.entity);
                    if(mon.state > 0)
                        subCell.put(mon.entity.gridX * 2, mon.entity.gridY, '!', SColor.DARK_RED);
                }
            }
            subCell.draw(batch, 1.0F);
            // batch must end if it began.
            batch.end();
        }
        // if using a filter that changes each frame, clear the known relationship between requested and actual colors
        if(changingColors) {
            fgCenter.clearCache();
            bgCenter.clearCache();
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);

        // message box won't respond to clicks on the far right if the stage hasn't been updated with a larger size
        currentZoomX = width * 1f / (this.width * 2);
        // total new screen height in pixels divided by total number of rows on the screen
        currentZoomY = height * 1f / (this.height + messages.getGridHeight());
        // message box should be given updated bounds since I don't think it will do this automatically
        messages.setBounds(0, 0, width, currentZoomY * messages.getGridHeight());
        // SquidMouse turns screen positions to cell positions, and needs to be told that cell sizes have changed
        input.reinitialize(currentZoomX, currentZoomY, (this.width * 2), this.height, 0, 0, width, height);
        currentZoomX = cellWidth / currentZoomX;
        currentZoomY = cellHeight / currentZoomY;
        input.update(width, height, true);
        stage.getViewport().update(width, height, true);
    }
}