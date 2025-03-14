package uk.ac.bris.cs.scotlandyard.ui.ai;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.*;
import io.atlassian.fugue.Pair;

import javax.annotation.Nonnull;
import java.util.*;

public final class MirrorGameState implements Board.GameState {

    // ATTRIBUTES
    /*----------------------------------------------------------------*/
    // Includes the graph and the moves
    private GameSetup setup;

    // Set of pieces that still have tickets (So can make a turn)
    private ImmutableSet<Piece> remaining;

    // All the moves made by mrX
    private ImmutableList<LogEntry> log;

    private Player mrX;

    // List of the detectives
    private List<Player> detectives;

    // Available moves
    private ImmutableSet<Move> moves;

    // A set of the winners (the detectives that lose might not be part of this?)
    private ImmutableSet<Piece> winner;

    // A list of visited destinations by makeSingleMoves and makeDoubleMoves
    public ArrayList<Integer> visitedSingleDestinations = new ArrayList<>();

    // A list of potential detective locations
    public  ArrayList<Integer> potentialDetectiveLocations;

    /*-----------------------------------------------------------------*/

    // Constructor
    public MirrorGameState(GameSetup setup,
                           ImmutableSet<Piece> remaining,
                           ImmutableList<LogEntry> log,
                           Player mrX,
                           List<Player> detectives
    ) {

        // Validates Mr X parameter
        if (!(mrX.piece().isMrX())) { throw new IllegalArgumentException(); }

        // Pass the list as a set and then compare the length of the list and the set (since sets don't allow duplicates)
        Set<Player> playerSet = new HashSet<>(detectives);
        if (playerSet.size() != detectives.size()) { throw new IllegalArgumentException(); }

        // Check whether there are duplicate locations
        HashSet<Integer> locationSet = new HashSet<>();
        for (Player detective : detectives) {
            locationSet.add(detective.location());
        }
        if (locationSet.size() != playerSet.size()) { throw new IllegalArgumentException(); }

        // Validates all Player objects in pieces
        for (Player p : detectives) {
            if (!(p.piece().isDetective()) || p.piece()==null) { throw new IllegalArgumentException(); }

            // Ensure detectives do not have double or secret tickets
            ImmutableMap<ScotlandYard.Ticket, Integer> tickets = p.tickets();

        }

        /* Checks that setup contains a valid graph and set of moves and that they are populated
         * (Calling nodes returns a set which you can check is empty)
         */
        if (setup.graph==null || setup.graph.nodes().isEmpty() ){ throw new IllegalArgumentException();}
        if (setup.moves==null || setup.moves.isEmpty() ){ throw new IllegalArgumentException();}


        this.remaining = remaining;
        this.setup = setup;
        this.log = log;
        this.mrX = mrX;
        this.detectives = detectives;
        this.potentialDetectiveLocations = getDetectiveAdjacentNodes();
        this.moves = setAvailableMoves();
        this.winner = setWinner();

        // Once a winner is established no more moves can be made.
        // if (!this.winner.isEmpty()) {this.moves = ImmutableSet.of();}
    }

    // 	Returns set of players that are still to make a move
    // 	If empty, checks who made a move last, and then repopulates set with either Mr X or all active Detectives
    private ImmutableSet<Piece> newRemaining(ImmutableSet<Piece> remaining, Piece p) {
        HashSet<Piece> newSet = new HashSet<>(remaining);
        newSet.remove(p);

        if (newSet.isEmpty() && p.isMrX()) {
            for (Player detective : this.detectives){
                //Only add to remaining if detective has tickets left
                boolean detectiveHasATicket = false;
                for (Integer number : detective.tickets().values()){
                    if (number>0){
                        detectiveHasATicket  =true;
                    }
                }
                if (detectiveHasATicket){
                    newSet.add(detective.piece());
                }
            }
        }
        else if  (newSet.isEmpty() && p.isDetective()) {
            newSet.add(mrX.piece());
        }
        return ImmutableSet.copyOf(newSet);
    }


    // Checks game setup to see if next move is visible to detectives or not, and adds to log accordingly
    private ImmutableList<LogEntry> addToLog (ScotlandYard.Ticket ticket){
        ArrayList<LogEntry> newLog = new ArrayList<>(log);
        if (setup.moves.get(log.size())){
            newLog.add(LogEntry.reveal(ticket, mrX.location()));
        }
        else {
            newLog.add(LogEntry.hidden(ticket));
        }
        return ImmutableList.copyOf(newLog);
    }

    // Update log and mrX position then return new game state
    private MirrorGameState mrXTurn(Move move) {

        // Check the type of move
        if (move instanceof Move.SingleMove) {
            mrX = mrX.at(((Move.SingleMove) move).destination);
            ScotlandYard.Ticket ticket1 = ((Move.SingleMove) move).ticket;
            log  = addToLog(ticket1);
        }
        else if (move instanceof Move.DoubleMove) {
            mrX = mrX.at(((Move.DoubleMove) move).destination1);
            ScotlandYard.Ticket ticket1 = ((Move.DoubleMove) move).ticket1;
            log  = addToLog(ticket1);

            mrX = mrX.at(((Move.DoubleMove) move).destination2);
            ScotlandYard.Ticket ticket2 = ((Move.DoubleMove) move).ticket2;
            log  = addToLog(ticket2);
        }

        // Take the used ticket away from mrX
        mrX = mrX.use(move.tickets());
        ImmutableSet<Piece> remainingPlayersInRound = newRemaining(remaining, mrX.piece());
        return new MirrorGameState(setup, remainingPlayersInRound, log, mrX, detectives);
    }

    // Update detective position and return new game state
    private MirrorGameState detectiveTurn(Move move, Piece detective) {
        Player myDetective = null;
        List<Player> newDetectives = new ArrayList<>(detectives);

        // Iterates through detectives to check which one the piece belongs to
        for (Player p : detectives) {
            if (p.piece().equals(detective)) {
                myDetective = p;
                newDetectives.remove(p);
            }
        }
        if (myDetective == null) {
            throw new IllegalArgumentException();
        }


        // Update the position of player and, tickets for player and mrX
        myDetective = myDetective.at(((Move.SingleMove) move).destination);
        mrX = mrX.give(move.tickets());
        myDetective = myDetective.use(move.tickets());
        newDetectives.add(myDetective);

        ImmutableSet<Piece> remainingPlayersInRound = newRemaining(remaining, myDetective.piece());
        return new MirrorGameState(setup, remainingPlayersInRound, log, mrX, ImmutableList.copyOf(newDetectives));
    }

    // Check that move is legal, then return a new game state, which represents the board after said move
    @Nonnull
    @Override
    public MirrorGameState advance(Move move) {
        // Validates function parameter to make sure it's a legal move
        if(!moves.contains(move)) throw new IllegalArgumentException("Illegal move: "+move);

        // Anonymous class which satisfies visitor interface
        // Double dispatch into mrXTurn or detectiveTurn functions, based on who made the move
        MirrorGameState newState = move.accept(new Move.Visitor<MirrorGameState>(){
            @Override
            public MirrorGameState visit(Move.SingleMove move) {
                Piece original = move.commencedBy();
                if (original.isMrX()) {return mrXTurn(move);}
                else { return detectiveTurn(move, move.commencedBy() );}
            }

            @Override
            public MirrorGameState visit(Move.DoubleMove move) {
                return mrXTurn(move);
            }
        });

        return newState;
    }

    @Nonnull @Override
    public GameSetup getSetup() {
        return setup;
    }

    // Returns an immutable set of each player's piece. Must include all detectives and Mr X.
    @Nonnull @Override
    public ImmutableSet<Piece> getPlayers() {
        HashSet<Piece> pieces = new HashSet<>();
        for(Player p : detectives){
            pieces.add(p.piece());
        }
        pieces.add(mrX.piece());
        return ImmutableSet.copyOf(pieces);
    }

    //	Iterate through detectives set, and if their piece corresponds to function argument, return location
    @Nonnull @Override
    public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {
        for (Player p : detectives) {
            if (p.piece() == detective){return Optional.of(p.location());}
        }
        return Optional.empty();
    }

    // Returns an anonymous class which fulfills the TicketBoard interface.
    @Nonnull @Override
    public Optional<TicketBoard> getPlayerTickets(Piece piece) {
        /* To work with tickets, we require a player. However, since we are given a piece,
         * we iterate through our player objects and match player.piece() with our function parameter
         */

        Player myPlayer = null;
        if (mrX.piece() == piece){ myPlayer = mrX; }
        for (Player p : detectives){
            if (p.piece() == piece){ myPlayer = p; }
        }

        // If player object does not exist, return an empty optional.
        if (myPlayer == null){
            return Optional.empty();
        }

        // Make a local copy of the tickets for the associated player
        ImmutableMap<ScotlandYard.Ticket, Integer> myPlayerTickets = myPlayer.tickets();

        /* Return an implementation of TicketBoard, which provides a method to
         * get the number of each type of ticket.
         */
        return Optional.of(new TicketBoard() {

            @Override
            public int getCount(@Nonnull ScotlandYard.Ticket ticket) {
                return myPlayerTickets.get(ticket);
            }
        });
    }

    @Nonnull @Override
    public ImmutableList<LogEntry> getMrXTravelLog() {
        return log;
    }

    @Nonnull @Override
    public ImmutableSet<Piece> getWinner() {
        return winner;
    }

    // Iterate through all the tickets a detective has and return true if they have no more tickets
    private boolean detectivesExhaustTickets() {
        for (Player p : detectives) {
            for (Integer number : p.tickets().values()){
                if (number>0){return false;}
            }
        }
        return true;
    }

    private boolean detectiveIsOnMrXLocation() {
        for (Player p : detectives) {
            if (p.location() == mrX.location()){
                return  true;
            }
        }
        return false;
    }

    // Determine whether there is a winner
    private ImmutableSet<Piece> setWinner() {
        HashSet<Piece> winners = new HashSet<>();
        boolean detectivesWin = false;
        boolean mrXWin = false;

        // The following booleans are for checking if mrX is cornered
        boolean mrXHasNoMoreMoves = makeMrXSingleMoves(setup, detectives, mrX, mrX.location()).left().isEmpty();
        boolean itIsMrXsTurn = remaining.contains(mrX.piece());
        boolean logFull = this.log.size() == setup.moves.size() && remaining.contains(mrX.piece());

        // Conditions for mrX to win
        if (detectivesExhaustTickets() || logFull) { mrXWin = true;}
        // Condition for detectives to win
        if ((mrXHasNoMoreMoves && (itIsMrXsTurn)) || detectiveIsOnMrXLocation()) { detectivesWin = true; }

        // Add the winners if there are any
        if (detectivesWin) {
            for (Player p : detectives) { winners.add(p.piece()); }
        }
        if (mrXWin){
            winners.add(mrX.piece());
        }
        return ImmutableSet.copyOf(winners);
    }

    @Nonnull @Override
    public ImmutableSet<Move> getAvailableMoves() {
        return moves;
    }

    // Return Set of Legal moves that can be made by players in remaining
    private ImmutableSet<Move> setAvailableMoves() {
        //Check all the players that exist for their location, and add their values to the set.
        Set<Move> availableMoves = new HashSet<>();

        //For a given player, we need to use the graph, to find adjacent nodes to their location.
        //Then we need to look at the players' tickets to see where they are allowed to move.


        //If its MrX's turn, add all valid single and double moves to the set.
        if (remaining.contains(mrX.piece())){

            //System.out.println("1: " + this.potentialDetectiveLocations);
            Pair<Set<Move.SingleMove>, ArrayList<Integer>> singleMovesAndDestinations= makeMrXSingleMoves(setup, detectives, mrX, mrX.location());
//            System.out.println("Adjacent nodes: " + getDetectiveAdjacentNodes());
            this.visitedSingleDestinations = singleMovesAndDestinations.right();
            Set<Move.SingleMove> availableMrXSingleMoves = singleMovesAndDestinations.left();
            //Set<Move.SingleMove> availableMrXSingleMoves = makeSingleMoves(setup, detectives, mrX, mrX.location()).left();
            //UNCOMMENT THE DOUBLE MOVE STUFF
            Set<Move.DoubleMove> availableMrXDoubleMoves = makeDoubleMove(setup, detectives, mrX, mrX.location());
            availableMoves.addAll(availableMrXSingleMoves);
            availableMoves.addAll(availableMrXDoubleMoves);
        }

        //Add all players possible moves to the set.
        for (Player p: detectives) {
            if (remaining.contains(p.piece())){
                //this.visitedSingleDestinations.clear();
                Set<Move.SingleMove> availableDetectiveMoves = makeDetectiveSingleMoves(setup, detectives, p, p.location());
                availableMoves.addAll(availableDetectiveMoves);
            }
        }

        //Return an immutable copy of the set.
        ImmutableSet<Move> availableMovesImmutable = ImmutableSet.copyOf(availableMoves);
        return availableMovesImmutable;
    }


    // Returns location of all players on the board in a map.
    private Map<Player, Integer> getPlayerLocations() {
        Map<Player, Integer> playerLocations = new HashMap<>();
        for  (Player p : detectives) {
            playerLocations.put(p, p.location());
        }
        playerLocations.put(mrX, mrX.location());
        return playerLocations;
    }



    public Set<Move.SingleMove> makeDetectiveSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source) {
        HashSet<Move.SingleMove> availableMoves = new  HashSet<>();
        // Get all the player locations to make sure the current player doesn't go on occupied squares
        Map<Player, Integer> playerLocations = getPlayerLocations();
        // If it is MrX's turn, we remove him from the playerLocations
        // We are removing mrX from the playerLocations because both the detectives and mrX can occupy this space, even if
        // mrX is on it.
        playerLocations.remove(mrX);

        // We then iterate through all the adjacent nodes i.e. places the current player can go to
        for(int destination : setup.graph.adjacentNodes(source)) {
            // Makes sure that the destination is not occupied.
            if (!(playerLocations.containsValue(destination))) {

                // Iterates through every adjacent node (there are numerous ways of transport) to the current player's location.
                for (ScotlandYard.Transport ticket : setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of())) {
                    // If the player has the necessary ticket, this move can be added to our set.
                    // if(player.tickets().get(ticket.requiredTicket()) > 0){
                    if(player.has(ticket.requiredTicket())){
                        Move.SingleMove singleMove = new Move.SingleMove(player.piece(), source, ticket.requiredTicket(), destination);
                        availableMoves.add(singleMove);
                    }
                }

            }
        }
        //System.out.println("Inside detectiveSingleMoves moves: " + availableMoves);
        return availableMoves;
    }
    // (?) This method was static on GitHub, but we got rid of it
    /* We did this, because we believe makeSingleMoves is a function called by a specific game state object
     * rather than it being accessible by all objects, since the moves that can be made are specific to a
     * particular configuration of pieces, which is described in that game state.
     */
    public Pair<Set<Move.SingleMove>, ArrayList<Integer>> makeMrXSingleMoves(GameSetup setup, List<Player> detectives, Player player, int source){
        HashSet<Move.SingleMove> availableMoves = new  HashSet<>();
        // Get all the player locations to make sure the current player doesn't go on occupied squares
        Map<Player, Integer> playerLocations = getPlayerLocations();
        // If it is MrX's turn, we remove him from the playerLocations
        // We are removing mrX from the playerLocations because both the detectives and mrX can occupy this space, even if
        // mrX is on it.
        playerLocations.remove(mrX);
        ArrayList<Integer> localVisitedDestinations = new ArrayList<>();

        // We then iterate through all the adjacent nodes i.e. places the current player can go to
        for(int destination : setup.graph.adjacentNodes(source)) {
            // Makes sure that the destination is not occupied.
            if (!(playerLocations.containsValue(destination))) {

                // Iterates through every adjacent node (there are numerous ways of transport) to the current player's location.
                for (ScotlandYard.Transport ticket : setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of())) {
                    // If the player has the necessary ticket, this move can be added to our set.
                    // if(player.tickets().get(ticket.requiredTicket()) > 0){
                    boolean notPotentialDetectiveLocation = (!this.potentialDetectiveLocations.contains(destination));
                    //System.out.println("2: " + this.potentialDetectiveLocations);
                    if(player.has(ticket.requiredTicket()) & (!localVisitedDestinations.contains(destination)) & notPotentialDetectiveLocation){
                        Move.SingleMove singleMove = new Move.SingleMove(player.piece(), source, ticket.requiredTicket(), destination);
                        availableMoves.add(singleMove);
                        localVisitedDestinations.add(destination);
                    }
                }

                // If the player has a secret ticket, they can use this instead.
                if (player.has(ScotlandYard.Ticket.SECRET) & (!localVisitedDestinations.contains(destination)) & (!this.potentialDetectiveLocations.contains(destination))) {
                    Move.SingleMove singleMove = new Move.SingleMove(player.piece(), source, ScotlandYard.Ticket.SECRET, destination);
                    availableMoves.add(singleMove);
                    localVisitedDestinations.add(destination);
                }

                // Adding the suicide node
//                int suicideDestination = this.potentialDetectiveLocations.get(0);
//                Move.SingleMove suicideMove = new Move.SingleMove(player.piece(), source, ScotlandYard.Ticket.SECRET, suicideDestination);
//                availableMoves.add(suicideMove);
//                localVisitedDestinations.add(suicideDestination);
            }
        }

        return new Pair<>(availableMoves, localVisitedDestinations);
    }

    private Set<Move.DoubleMove> makeDoubleMove(GameSetup setup, List<Player> detectives, Player mrX, int source) {
        HashSet<Move.DoubleMove> availableMoves = new HashSet<>();
        ArrayList<Integer> localVisitedDestinations = new ArrayList<>();

        // Algorithm (high level):
        // Use makeSingleMove and get all the valid adjacent nodes that MrX can move to -- (1)
        // Call the makeSingleMove function on every node from (1) and get all the valid single moves from there
        // Check these are valid in combination (No double use of the same tickets)
        // Store these moves in the availableMoves
        //this.visitedDestinations.clear();
        // Set containing all the valid first moves mrX can make from his initial position
        Set<Move.SingleMove> availableFirstMoves = makeMrXSingleMoves(setup, detectives, mrX, source).left();

        // Iterate through set of single moves, and check which moves are legal from the destination of the first move.
        for (Move.SingleMove move1 : availableFirstMoves) {
            // Get all the valid move 2s by calling makeSingleMoves and setting move1 as the source
            Set<Move.SingleMove> availableSecondMoves = makeMrXSingleMoves(setup, detectives, mrX, move1.destination).left();
            // Iterate through each move2 to see whether it is actually a valid move or not

            for (Move.SingleMove move2 : availableSecondMoves) {
                // Check whether mrX has enough double tickets
                boolean hasEnoughDoubleTickets =  (mrX.hasAtLeast(ScotlandYard.Ticket.DOUBLE, 1));

                // Check if there enough moves left to play a double move
                boolean hasEnoughMovesForDouble = (setup.moves.size() - log.size()) > 1;

                // If the tickets are the same, he needs at least 2 tickets
                boolean enoughTicketsForSame = ( move1.ticket == move2.ticket) && (mrX.hasAtLeast(move1.ticket, 2));


                // If he uses 2 different tickets then they need to have at least 1 of each
                boolean enoughTicketsForDifferent =(move1.ticket != move2.ticket) && mrX.hasAtLeast(move1.ticket ,1) && mrX.hasAtLeast(move2.ticket, 1);

                // Check whether destination2 has already been visited
                boolean destinationNotVisited = (!localVisitedDestinations.contains(move2.destination)) && !(this.visitedSingleDestinations.contains(move2.destination));

                // Check whether a potential detective location is ignored
                boolean destinationNotAPotentialDetectiveLocation = (!this.potentialDetectiveLocations.contains(move2.destination));

                //System.out.println("In Double Moves: " + visitedDestinations);
                // If all the above conditions are true, then it's a valid double move, and we can add it in
                if (hasEnoughDoubleTickets && hasEnoughMovesForDouble && (enoughTicketsForSame || enoughTicketsForDifferent) && destinationNotVisited && destinationNotAPotentialDetectiveLocation) {
                    //System.out.println("Visited: " + visitedDestinations);
                    localVisitedDestinations.add(move2.destination);
                    availableMoves.add(new Move.DoubleMove(mrX.piece(), source, move1.ticket, move1.destination, move2.ticket ,move2.destination));
//                    System.out.println("3: " + this.visitedDestinations);
                }
            }
        }

        return availableMoves;
    }


    public ImmutableSet<Piece> getRemaining() {
        return remaining;
    }

    public Player getMrX() {
        return mrX;
    }

    public ImmutableList<Player> getDetectives(){
        return ImmutableList.copyOf(detectives);
    }


    public ImmutableList<Player> actualGetPlayers(){
        ArrayList players = new ArrayList(detectives);
        players.add(mrX);
        return ImmutableList.copyOf(players);
    }

    public ArrayList<Integer> getDetectiveAdjacentNodes() {
        ArrayList<Integer> adjacentDetectiveNodes = new ArrayList<>();

        for (Player detective: this.detectives) {
            int detectiveLocation = detective.location();
            Set<Integer> potentialDetectiveLocations = getSetup().graph.adjacentNodes(detectiveLocation);
            adjacentDetectiveNodes.addAll(potentialDetectiveLocations);
        }

        return adjacentDetectiveNodes;
    }

}
