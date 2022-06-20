package de.fhkiel.ki.examples.gui.withAi.kathedralai;

import de.fhkiel.ki.ai.CathedralAI;
import de.fhkiel.ki.cathedral.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class EchoAI implements CathedralAI {

    /* Colors */
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_BLACK_BACKGROUND = "\u001B[40m";
    public static final String ANSI_BLUE_BACKGROUND = "\u001B[44m";
    public static final String ANSI_WHITE_BACKGROUND = "\u001B[47m";

    // How many processors the current system has available
    private Integer processors;
    // List of all the turns that have happened during the game
    private List<Placement> turns;

    // Store the hull placements for each player, and calulate them each anew at the start of the ki
    private Map<Color, Set<Position>> hullPlayerPositions;
    // Store the turn placements for each player and calculate them each anew at the start of the ki
    private Map<Color, Set<Position>> turnPlayerPositions;
    // Store the positions that the turn player can place their placements to
    private Map<Color, Set<Position>> turnPlayerPlacablePositions;

    @Override
    public String name() {
        return "Team Echo V2";
    }

    @Override
    public void init(Game game) {
        System.out.println(ANSI_GREEN + "[LOG] Start the AI" + ANSI_RESET);
        // Fetch the available processors
        processors = Runtime.getRuntime().availableProcessors();
        turns = new ArrayList<>();
        this.hullPlayerPositions = new HashMap<>();
        this.turnPlayerPositions = new HashMap<>();
        this.turnPlayerPlacablePositions = new HashMap<>();
    }

    @Override
    public Placement takeTurn(Game game) {
        // 0. Set up the required data
        Game copy = game.copy();
        updateTurn(copy);
        updateHull(copy);
        updateTurnPlayerPlacables(copy);
        long startTime = System.currentTimeMillis();
        printPlayerPositionList(this.turnPlayerPlacablePositions.get(game.getCurrentPlayer()).stream().toList(), game.getCurrentPlayer());

        // 1. Calculate the possible positions for the current player
        Set<PlacementData> possibles = getPlacements(copy, 0, 10, false);

        Set<PlacementData> capturingPlacements = new HashSet<>();
        if (!game.getCurrentPlayer().equals(Color.Blue)) {
            capturingPlacements = calculateCapturingPlacements(copy, copy.getCurrentPlayer());
            System.out.println(capturingPlacements.size());
        }
        if(possibles.size() == 0) {return null;}

        PlacementData pd = null;
        if (capturingPlacements.isEmpty() || game.getCurrentPlayer().equals(Color.Blue)) {
            pd = possibles.stream().toList().get(ThreadLocalRandom.current().nextInt(0, possibles.size()));
        } else {
            pd = capturingPlacements.stream().collect(Collectors.toList()).get(ThreadLocalRandom.current().nextInt(0, capturingPlacements.size()));
        }

        long endTime = System.currentTimeMillis();
        printTime(startTime, endTime);
        // And return the calculated best placement to be done
        System.out.println(pd);
        return pd.getPlacement();

    }

    @Override
    public void stopAI() {
        System.out.println(ANSI_GREEN + "[LOG] Stop the AI" + ANSI_RESET);
    }

    // Non-AI Specific Methods
    // -----------------------

    /**
     * Calculate the placement that would capture the most
     * regions/capture the most positions for the current player
     *
     * @param game the game to work on
     * @return the placement that has the highest value for the current player
     */
    public Placement aggressive(Game game) {
        return null;
    }

    /**
     * Calculate the placement that would prevent the opponent from capturing the most placements/regions
     *
     * @param game the game to work on
     * @return the placement that has the lowest value for the opponent
     */
    public Placement defensive(Game game) {
        return null;
    }

    /**
     * Calculate the placement that has the best average value for the player, meaning that would both
     * capture the most regions/positions and prevent the opponent from doing that.
     *
     * @param game the game to work on
     * @return the placement with the highest average value
     */
    public Placement average(Game game) {
        return null;
    }

    public double evaluate(Placement placement, Game game) {
        return 0x0e0;
    }

    // Helper methods
    // --------------

    // Placement finding methods
    // -------------------------

    /**
     * calculate all the placements that would capture a specific region for a given player
     *
     * @param game   the game to work on
     * @param player the player to calculate for
     * @return the placements that are calculated
     */
    private Set<PlacementData> calculateCapturingPlacements(Game game, Color player) {
        // 0. Initialization
        // Get the current score and regions for current player
        int currentScore = getScoreDifference(game, player);
        int oldRegions = checkRegionsSize(game.getBoard().getField(), player);
        // Get the region color for the player
        Color c = Color.getSubColor(player);
        // Iterate over all possible placements
        Set<PlacementData> placementDataList = new HashSet<>();
        for (Building building : game.getPlacableBuildings(player)) {
            for (Direction direction : building.getTurnable().getPossibleDirections()) {
                for (int x = 0; x < 10; x++) {
                    for (int y = 0; y < 10; y++) {
                        Placement placement = new Placement(new Position(x, y), direction, building);
                        // Positions of the placement shifted by the position
                        Set<Position> placementPositions = placement
                                .form()
                                .stream()
                                .map(p -> p.plus(placement.position()))
                                .collect(Collectors.toSet());
                        // Find all positions that are in the placement and the hull of the current player
                        Set<Position> intersection = getSetIntersection(this.hullPlayerPositions.get(player), placementPositions);
                        Set<Position> placableIntersection = getSetIntersection(this.turnPlayerPlacablePositions.get(player), placementPositions);
                        // There are intersection positions, thus we can check this placement
                        if (!intersection.isEmpty() &&  placableIntersection.size() == placementPositions.size()) {
                            printPlayerPositionList(placableIntersection.stream().toList(), player);
                            game.takeTurn(placement, false);

                            int newScore = getScoreDifference(game, player);
                            int newRegions = checkRegionsSize(game.getBoard().getField(), player);

                            placementDataList.add(new PlacementData(placement, newRegions - oldRegions, currentScore - newScore));
                            game.undoLastTurn();
                        }
                    }
                }
            }
        }
        return placementDataList;
    }

    /**
     * Check the current position and return all blocks that fit there
     *
     * @param x               the x coordinate to check for a placement
     * @param y               the y coordinate to check for a placement
     * @param game            the game to test for
     * @param data            the placement data to write to on success
     * @param checkPlacements if the algorithm should check the number of placements
     */
    private void checkPlacementData(int x, int y, Game game, Set<PlacementData> data, boolean checkPlacements) {
        // Fetch the current player
        Color player = game.getCurrentPlayer();
        int oldScore = getScoreDifference(game, player);
        // Check the regions
        int placementsStart = checkRegions(game.getBoard().getField(), player).size();
        // Check, if this current field is applicable for checking it out, specifically if and only if the field belongs

        // to the current player or to no one at all
        for (Building building : game.getPlacableBuildings()) {
            // Test all turnables of the building
            for (Direction direction : building.getTurnable().getPossibleDirections()) {
                Placement possPlacement = new Placement(x, y, direction, building);
                int deltaScore;
                int deltaRegions;
                    // Check, if the placement can be placed
                // Positions of the placement shifted by the position
                Set<Position> placementPositions = possPlacement
                        .form()
                        .stream()
                        .map(p -> p.plus(possPlacement.position()))
                        .collect(Collectors.toSet());
                // Take a turn using the "fast" method, meaning without checking the regions
                if (this.turnPlayerPlacablePositions.get(player).containsAll(placementPositions)) {
                    // If the placements should be checked, we do that now
                    if (checkPlacements) {
                        // Undo the just-took turn to be able to take a closer look at the regions
                        // Fetch the regions that are placed at this point
                        game.takeTurn(possPlacement, false);
                        // Fetch the new score of the starting player
                        int newScore = getScoreDifference(game, player);
                        int placementsEnd = checkRegions(game.getBoard().getField(), player).size();
                        // Calculate the difference between the values regions & score
                        deltaScore = oldScore - newScore;
                        deltaRegions = placementsEnd - placementsStart;
                        game.undoLastTurn();
                    } else {
                        // We don't calculate the placements
                        int newScore = getScore(game, player);
                        deltaRegions = 0;
                        deltaScore = newScore - oldScore;
                    }
                    // Add that placement to the list of possible placements
                    data.add(new PlacementData(possPlacement, deltaRegions, deltaScore));
                }

            }
        }

    }

    /**
     * Get the placements for a given player
     *
     * @param game   the game to get the placements from
     * @param player the player to get the plcements for
     * @return the placements of a given player
     */
    public Set<Placement> getPlayerPlacements(Game game, Color player) {
        return game.getBoard().getPlacedBuildings().stream().filter(placement -> placement.building().getColor().equals(player)).collect(Collectors.toSet());
    }

    // Position methods
    // ----------------

    /**
     * Calculate all positions in a game that belong to a given player
     *
     * @param game   the game to get the positions from
     * @param player the player to get the positions for
     * @return the position set for a given player
     */
    public Set<Position> getPlayerTurn(Game game, Color player) {
        // Get all placements for a given player
        Set<Placement> placements = getPlayerPlacements(game, player);
        // Get all the positions for a given player
        Set<Position> positions = new HashSet<>();
        for (Placement placement : placements) {
            // Only take this placement into account if it belongs to the player 'player'
            Set<Position> placementTurn = calculateTurn(placement, placement.position());
            positions.addAll(placementTurn);
        }
        return positions;
    }

    private Set<Position> calculateTurn(Placement placementData, Position p) {
        return calculateTurn(placementData)
                .stream()
                .map(position -> position.plus(p))
                .collect(Collectors.toSet());
    }

    private Set<Position> calculateTurn(Placement placementData) {
        return new HashSet<>(placementData.building().turn(placementData.direction()));
    }
    // Hull Methods
    // ------------

    /**
     * Calculate the hull around a placement shifted by a placement position
     *
     * @param placementData the data to calculate the hull around
     * @param p             the position to shift by
     * @return the shifted hull around a set of placements
     */
    private Set<Position> calculateHull(Placement placementData, Position p) {
        return calculateHull(placementData)
                .stream()
                .map(position -> position.plus(p))
                .collect(Collectors.toSet());
    }

    /**
     * Calculate the hull of a set of positions
     *
     * @return the hull around the positions
     */
    private Set<Position> calculateHull(Placement placementData) {
        return new HashSet<>(placementData.building().corners(placementData.direction()));
    }

    /**
     * For a given player p, get the positions that represent the hull of each placement on the field
     *
     * @param game   the game to take the placements from
     * @param player the player to take the
     * @return the set of all positions
     * @brief Calculate all hulls positions for given player
     * @author malte quandt
     * @version 1.0
     */
    public Set<Position> getPlayerHull(Game game, Color player) {
        // Get all placements for a given player
        Set<Placement> placements = getPlayerPlacements(game, player);
        // Get all the positions for a given player
        Set<Position> positions = new HashSet<>();
        for (Placement placement : placements) {
            // Only take this placement into account if it belongs to the player 'player'
            Set<Position> placementHull = calculateHull(placement, placement.position());
            positions.addAll(placementHull);
        }
        positions.removeAll(this.turnPlayerPositions.get(player));
        return positions;
    }


    // Utility methods
    // ---------------

    /**
     * Get the intersection of two sets of positions
     *
     * @param a the first position set
     * @param b the second position set
     * @return the set intersection
     */
    private Set<Position> getSetIntersection(Collection<Position> a, Collection<Position> b) {
        return a.stream().filter(position -> b.contains(position)).collect(Collectors.toSet());
    }

    /**
     * From a given set of nanoseconds, calculate the difference in milliseconds
     *
     * @param start the start time of the interval
     * @param end   the end time of the interval
     * @return the interval lenght in milliseconds
     */
    private long getDiffTime(long start, long end) {
        return (end - start) / 1000000;
    }


    /**
     * For both players, update the hull position set
     *
     * @param copy the game on which to operate
     * @before Make sure that the updateTurn method is called before this method!
     */
    private void updateHull(Game copy) {
        // Black:
        this.hullPlayerPositions.put(Color.Black, getPlayerHull(copy, Color.Black));
        // White
        this.hullPlayerPositions.put(Color.White, getPlayerHull(copy, Color.White));
        // Blue
        this.hullPlayerPositions.put(Color.Blue, getPlayerHull(copy, Color.Blue));
    }

    /**
     * For both players, update the turn position set
     *
     * @param copy the game on which to operate
     */
    private void updateTurn(Game copy) {
        // Black
        this.turnPlayerPositions.put(Color.Black, getPlayerTurn(copy, Color.Black));
        this.turnPlayerPositions.put(Color.Black_Owned, getPlayerTurn(copy, Color.Black_Owned));
        // White
        this.turnPlayerPositions.put(Color.White, getPlayerTurn(copy, Color.White));
        this.turnPlayerPositions.put(Color.White_Owned, getPlayerTurn(copy, Color.White_Owned));
        // Blue
        this.turnPlayerPositions.put(Color.Blue, getPlayerTurn(copy, Color.Blue));
    }

    private void updateTurnPlayerPlacables(Game copy) {
        Color player = copy.getCurrentPlayer();
        Set<Position> playerPlacablePositions = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                Color clr = copy.getBoard().getField()[i][j];
                if (clr.equals(player.subColor()) || clr.equals(Color.None)) {
                    playerPlacablePositions.add(new Position(i, j));
                }
            }
        }
        this.turnPlayerPlacablePositions.put(copy.getCurrentPlayer(), playerPlacablePositions);
    }

    /**
     * Print the time that this function took to execute and print it in a more pretty way.
     *
     * @param start the start time point of execution
     * @param end   the end time point of execution
     */
    private void printTime(long start, long end) {
        long duration = getDiffTime(start, end);
        String color;
        // Function took no longer than 1000 milliseconds/1 second
        if (duration < 1000) {
            color = ANSI_GREEN;
        }
        // Function took no longer than 10000 milliseconds/29 seconds
        else if (duration < 10000) {
            color = ANSI_YELLOW;
        } else if (duration < 29000) {
            color = ANSI_CYAN;
        }
        // Function took too long
        else {
            color = ANSI_RED;
        }
        System.out.println(color + "[LOG] The function took " + duration + " milliseconds, or " + (duration / 1000.0) + " Seconds!" + ANSI_RESET);
    }

    /**
     * Print the skip message to the screen
     */
    private void skipMessage() {
        System.out.println(ANSI_GREEN + "[LOG] Skip, as there are no valid placements no more!" + ANSI_RESET);
    }

    /**
     * calculate the score for a given player.
     *
     * @param game   the game to get the score from
     * @param player the player for which to get the score
     * @return the score of the player at a given point in time in the given game instance
     */
    private Integer getScore(Game game, Color player) {
        Object a = game.score().get(player);
        if (a != null) {
            return (int) a;
        } else {
            return 47;
        }
    }

    /**
     * Check, if there are regions captured for this player
     *
     * @param field  the field to check
     * @param player the player to check for
     * @return the regions captured by the current player
     */
    private static Set<Position> checkRegions(Color[][] field, Color player) {
        Set<Position> newPositions = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                Position curr = new Position(i, j);
                // If the field is a capture of the current player, and if the field is not yet captured
                if (field[i][j].getId() == (player.getId() + 1)) {
                    // Add that to the oldPosit
                    newPositions.add(curr);
                }
            }
        }
        return newPositions;
    }

    /**
     * Get the amount of regions captured by the current player at any given point in time
     *
     * @param field  the board to check on
     * @param player the player's regions to check for
     * @return the amount of regions the current player currently has
     */
    private static int checkRegionsSize(Color[][] field, Color player) {
        int regions = 0;
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                // If the field is a capture of the current player, and if the field is not yet captured
                if (field[i][j].getId() == (player.getId() + 1)) regions++;
            }
        }
        return regions;
    }

    /**
     * Print the board to the console in a more stylish fashion
     *
     * @param board the board to print
     * @author malte quandt
     */
    private void printBoard(final Color[][] board) {
        for (int i = 0; i < 10; i++) {
            System.out.print("|");
            for (int j = 0; j < 10; j++) {
                // Fetch the current field's data
                Color currentField = board[i][j];
                // Do nothing for an empty field
                switch (currentField) {
                    case White -> System.out.print(ANSI_WHITE_BACKGROUND + "  " + ANSI_RESET);
                    case White_Owned -> System.out.print(ANSI_WHITE_BACKGROUND + "00" + ANSI_RESET);
                    case Black -> System.out.print(ANSI_BLACK_BACKGROUND + "  " + ANSI_RESET);
                    case Black_Owned -> System.out.print(ANSI_BLACK_BACKGROUND + "00" + ANSI_RESET);
                    case Blue -> System.out.print(ANSI_BLUE_BACKGROUND + "  " + ANSI_RESET);
                    case None -> System.out.print("  ");
                    default -> System.out.print(currentField + "  ");
                }
            }
            System.out.print("|");
            System.out.println();
        }
    }

    private void printPlayerTurns(final Color[][] board, Color player) {
        for (int x = 0; x < 10; x++) {
            System.out.print("|");
            for (int y = 0; y < 10; y++) {
                // Do nothing for an empty field
                String fillColor = player == Color.Black ? ANSI_BLACK_BACKGROUND : ANSI_WHITE_BACKGROUND;
                if (this.turnPlayerPositions.get(player).contains(new Position(x, y))) {
                    System.out.print(fillColor + y + x + ANSI_RESET);
                } else {
                    System.out.print("  ");
                }
            }
            System.out.print("|");
            System.out.println();
        }
    }

    private void printPlayerHull(final Color[][] board, Color player) {
        for (int x = 0; x < 10; x++) {
            System.out.print("|");
            for (int y = 0; y < 10; y++) {
                // Do nothing for an empty field
                String fillColor = player == Color.Black ? ANSI_BLACK_BACKGROUND : ANSI_WHITE_BACKGROUND;
                if (this.hullPlayerPositions.get(player).contains(new Position(x, y))) {
                    System.out.print(fillColor + y + x + ANSI_RESET);
                } else {
                    System.out.print("  ");
                }
            }
            System.out.print("|");
            System.out.println();
        }
    }

    private void printPlayerPositionList(List<Position> positions, Color player) {
        for (int x = 0; x < 10; x++) {
            System.out.print("|");
            for (int y = 0; y < 10; y++) {
                // Do nothing for an empty field
                String fillColor = player == Color.Black ? ANSI_BLACK_BACKGROUND : ANSI_WHITE_BACKGROUND;
                if (positions.contains(new Position(x, y))) {
                    System.out.print(fillColor + y + x + ANSI_RESET);
                } else {
                    System.out.print("  ");
                }
            }
            System.out.print("|");
            System.out.println();
        }
    }

    /**
     * Calculate the placables that can be put onto the field for the current player
     *
     * @param game            the game we are working on
     * @param from            the start of the x coordinates to check
     * @param to              the end of the x coordinates to check
     * @param checkPlacements if the algorithms should check the number of placements
     * @return the among of possible placables on this board for the current player
     */
    private Set<PlacementData> getPlacements(Game game, int from, int to, boolean checkPlacements) {
        Set<PlacementData> possibles = new HashSet<>();
        for (int x = from; x < to; x++) {
            for (int y = 0; y < 10; y++) {
                checkPlacementData(x, y, game, possibles, checkPlacements);
            }
        }
        return possibles;
    }


    /**
     * From any given player or captured region, return the opposite player or opposite players region capture color.
     *
     * @param player the player to get the region for
     * @return the opposite player/player_region
     */
    private Color getOppositePlayer(Color player) {
        switch (player) {
            case White:
                return Color.Black;
            case Black:
                return Color.White;
            case White_Owned:
                return Color.Black_Owned;
            case Black_Owned:
                return Color.White_Owned;
            default:
                return player;
        }
    }

    /**
     * Get the score difference for a specific player
     *
     * @param game   the game to operate on
     * @param player the player to get the difference for
     * @return
     */
    private int getScoreDifference(Game game, Color player) {
        int whiteScore = getScore(game, Color.White);
        int blackScore = getScore(game, Color.Black);
        // Calculate the difference, which would be postivive for white if white has more than black
        int scoreDifference = whiteScore - blackScore;
        return player.equals(Color.White) ? scoreDifference : -scoreDifference;
    }


}
