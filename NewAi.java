package de.fhkiel.ki.examples.gui.withAi;

import de.fhkiel.ki.ai.CathedralAI;
import de.fhkiel.ki.cathedral.*;

import java.util.*;

public class NewAi implements CathedralAI {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK_BACKGROUND = "\u001B[40m";
    public static final String ANSI_RED_BACKGROUND = "\u001B[41m";
    public static final String ANSI_GREEN_BACKGROUND = "\u001B[42m";
    public static final String ANSI_YELLOW_BACKGROUND = "\u001B[43m";
    public static final String ANSI_BLUE_BACKGROUND = "\u001B[44m";
    public static final String ANSI_PURPLE_BACKGROUND = "\u001B[45m";
    public static final String ANSI_CYAN_BACKGROUND = "\u001B[46m";
    public static final String ANSI_WHITE_BACKGROUND = "\u001B[47m";

    @Override
    public String name() {
        return "Team ECHO";
    }

    @Override
    public void init(Game game) {

    }

    @Override
    public Placement takeTurn(Game game) {
        // Create copy of the current game state

        // Get the Percept:
        // ---------------
        Game copy = game.copy();

        Set<Placement> possibles = new HashSet<>();
        // Check, if the next turn of the opponent could possibly result in a marked region, and if so, act against it
        // ---------------------------------
        // 0. Fetch a new game copy on which to operate
        Game otherplayer = game.copy();
        // 1. Assume the position of the opponent
        copy.forfeitTurn();
        // 2.1: Fetch the current player
        Color player = copy.getCurrentPlayer();
        // 2.2: Fetch the current board state
        Color[][] field = copy.getBoard().getField();
        // 2.3: Save all the current regions that exist:
        Set<Position> prevReagions = checkReagions(field, player);

        // 3. Check, if the next turn would result in a captured region for the opponent, and if so, prevent that
        for(Building building : copy.getPlacableBuildings()) {
            for(int x = 0; x < 10 ; x++) {
                for(int y = 0; y < 10 ; y++) {
                    for(Direction dir : building.getTurnable().getPossibleDirections()) {
                        Placement possPlacement = new Placement(x,y,dir, building);
                        if(copy.takeTurn(possPlacement, false)) {
                            // Check, if this results in a reagion:

                        }
                        // Undo turn
                    }
                }
            }
        }
        // x. Fetch a new game copy for the actual calculation
        copy = game.copy();

        printBoard(game.getBoard());
        // Test all buildings
        // Test every position on the board and filter any turn that is not possible
        for (Building building : copy.getPlacableBuildings()) {
            // Iterate over the board positions
            for (int x = 0; x < 10; x++) {
                for (int y = 0; y < 10; y++) {
                    // Test all turnables of the building
                    for (Direction direction : building.getTurnable().getPossibleDirections()) {
                        Placement possPlacement = new Placement(x, y, direction, building);
                        // Take a turn using the "fast" method without checking the regions
                        if (copy.takeTurn(possPlacement, true)) {
                            // We can take a turn, thus we add it to the "possible" set
                            possibles.add(possPlacement);
                            copy.undoLastTurn();
                        }
                    }
                }
            }
        }

        // Apply rule-set on possible turns:
        // ---------------------------------
        int highest = 0;
        Placement bestPlacement = null;
        // abort, as there are no placements possible!
        if (possibles.isEmpty()) {
            return null;
        }
        // Select the placement that has the highest point value
        for (Placement place : possibles) {
            if (place.building().score() >= highest) {
                highest = place.building().score();
                bestPlacement = place;
            }
        }
        return bestPlacement;
    }

    @Override
    public void stopAI() {

    }

    // Utility Methods
    // ---------------

    /**
     * Check, if there are reagions captured for this player
     *
     * @param field the field to check
     * @param player the player to check for
     *
     * @return the positions that are captured
     */
    private Set<Position> checkReagions(Color[][] field, Color player) {
        Set<Position> capturables = new HashSet();
        for(int i = 0; i < 10 ; i++) {
            for(int j = 0; j < 10 ; j++) {
                if(field[i][j].getId() == (player.getId()+1)) {
                    capturables.add(new Position(i, j));
                }
            }
        }
        return capturables;
    }
    /**
     * Print the board to the console
     * @param board the board to print
     * @author malte quandt
     * @version 1.0
     */
    private void printBoard(final Board board) {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                // Fetch the current field's data
                Color currentField = board.getField()[i][j];
                // Do nothing for an empty field
                switch(currentField) {
                    case White -> {
                        System.out.print(ANSI_WHITE_BACKGROUND + "   " + ANSI_RESET);
                        break;
                    }
                    case White_Owned -> {
                        System.out.print(ANSI_WHITE_BACKGROUND + " O " + ANSI_RESET);
                        break;
                    }
                    case Black -> {
                        System.out.print(ANSI_BLACK_BACKGROUND + "   " + ANSI_RESET);
                        break;
                    }
                    case Black_Owned -> {
                        System.out.print(ANSI_BLACK_BACKGROUND + " O " + ANSI_RESET);
                        break;
                    }
                    case Blue -> {
                        System.out.print(ANSI_BLUE_BACKGROUND + "   " + ANSI_RESET);
                        break;
                    }
                    case None -> {
                        System.out.print("   ");
                        break;
                    }
                    default -> {
                        System.out.print(currentField + "   ");
                        break;
                    }
                }
            }
            System.out.println();
        }

    }
}
