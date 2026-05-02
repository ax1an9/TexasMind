package com.texasholdem.ai.grpc;

import com.texasholdem.core.model.*;
import poker_agent.PokerAgentOuterClass;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtoAdapterTest {

    // --- Card conversion ---

    @Test
    void convertsAceOfSpades() {
        Card card = new Card(Rank.ACE, Suit.SPADES);
        PokerAgentOuterClass.Card proto = ProtoAdapter.toProto(card);

        assertEquals(PokerAgentOuterClass.Rank.ACE, proto.getRank());
        assertEquals(PokerAgentOuterClass.Suit.SPADES, proto.getSuit());
    }

    @Test
    void convertsTwoOfHearts() {
        Card card = new Card(Rank.TWO, Suit.HEARTS);
        PokerAgentOuterClass.Card proto = ProtoAdapter.toProto(card);

        assertEquals(PokerAgentOuterClass.Rank.TWO, proto.getRank());
        assertEquals(PokerAgentOuterClass.Suit.HEARTS, proto.getSuit());
    }

    @Test
    void convertsTenOfDiamonds() {
        Card card = new Card(Rank.TEN, Suit.DIAMONDS);
        PokerAgentOuterClass.Card proto = ProtoAdapter.toProto(card);

        assertEquals(PokerAgentOuterClass.Rank.TEN, proto.getRank());
        assertEquals(PokerAgentOuterClass.Suit.DIAMONDS, proto.getSuit());
    }

    // --- Rank conversion ---

    @Test
    void convertsAllRanks() {
        for (Rank rank : Rank.values()) {
            PokerAgentOuterClass.Rank proto = ProtoAdapter.toProto(rank);
            assertNotNull(proto);
            assertEquals(rank.name(), proto.name());
        }
    }

    // --- Suit conversion ---

    @Test
    void convertsAllSuits() {
        for (Suit suit : Suit.values()) {
            PokerAgentOuterClass.Suit proto = ProtoAdapter.toProto(suit);
            assertNotNull(proto);
            assertEquals(suit.name(), proto.name());
        }
    }

    // --- GamePhase conversion ---

    @Test
    void convertsPreFlop() {
        assertEquals(PokerAgentOuterClass.GamePhase.PRE_FLOP,
                ProtoAdapter.toProto(GamePhase.PRE_FLOP));
    }

    @Test
    void convertsFlop() {
        assertEquals(PokerAgentOuterClass.GamePhase.FLOP,
                ProtoAdapter.toProto(GamePhase.FLOP));
    }

    @Test
    void convertsTurn() {
        assertEquals(PokerAgentOuterClass.GamePhase.TURN,
                ProtoAdapter.toProto(GamePhase.TURN));
    }

    @Test
    void convertsRiver() {
        assertEquals(PokerAgentOuterClass.GamePhase.RIVER,
                ProtoAdapter.toProto(GamePhase.RIVER));
    }

    @Test
    void convertsShowdown() {
        assertEquals(PokerAgentOuterClass.GamePhase.SHOWDOWN,
                ProtoAdapter.toProto(GamePhase.SHOWDOWN));
    }

    @Test
    void convertsSettled() {
        assertEquals(PokerAgentOuterClass.GamePhase.SETTLED,
                ProtoAdapter.toProto(GamePhase.SETTLED));
    }

    @Test
    void convertsWaitingToPreFlop() {
        // WAITING has no proto equivalent, should map to PRE_FLOP
        assertEquals(PokerAgentOuterClass.GamePhase.PRE_FLOP,
                ProtoAdapter.toProto(GamePhase.WAITING));
    }

    // --- ActionType conversion ---

    @Test
    void convertsFold() {
        assertEquals(PokerAgentOuterClass.ActionType.FOLD,
                ProtoAdapter.toProto(ActionType.FOLD));
    }

    @Test
    void convertsCheck() {
        assertEquals(PokerAgentOuterClass.ActionType.CHECK,
                ProtoAdapter.toProto(ActionType.CHECK));
    }

    @Test
    void convertsCall() {
        assertEquals(PokerAgentOuterClass.ActionType.CALL,
                ProtoAdapter.toProto(ActionType.CALL));
    }

    @Test
    void convertsBet() {
        assertEquals(PokerAgentOuterClass.ActionType.BET,
                ProtoAdapter.toProto(ActionType.BET));
    }

    @Test
    void convertsRaise() {
        assertEquals(PokerAgentOuterClass.ActionType.RAISE,
                ProtoAdapter.toProto(ActionType.RAISE));
    }

    @Test
    void convertsAllIn() {
        assertEquals(PokerAgentOuterClass.ActionType.ALL_IN,
                ProtoAdapter.toProto(ActionType.ALL_IN));
    }

    // --- PlayerState conversion (with hole cards) ---

    @Test
    void convertsPlayerViewWithHoleCards() {
        List<Card> holeCards = Arrays.asList(
                new Card(Rank.ACE, Suit.SPADES),
                new Card(Rank.KING, Suit.HEARTS));
        PlayerState player = new PlayerState("seat-1", 1000)
                .withHoleCards(holeCards);

        PokerAgentOuterClass.PlayerView proto =
                ProtoAdapter.toProto(player, holeCards);

        assertEquals("seat-1", proto.getSeatId());
        assertEquals(1000, proto.getChips());
        assertEquals(2, proto.getHoleCardsCount());
        assertEquals(PokerAgentOuterClass.Rank.ACE, proto.getHoleCards(0).getRank());
        assertEquals(PokerAgentOuterClass.Suit.SPADES, proto.getHoleCards(0).getSuit());
        assertEquals(PokerAgentOuterClass.Rank.KING, proto.getHoleCards(1).getRank());
        assertEquals(PokerAgentOuterClass.Suit.HEARTS, proto.getHoleCards(1).getSuit());
        assertFalse(proto.getIsFolded());
        assertFalse(proto.getIsAllIn());
    }

    @Test
    void convertsFoldedPlayerView() {
        PlayerState player = new PlayerState("seat-2", 500).fold();

        PokerAgentOuterClass.PlayerView proto =
                ProtoAdapter.toProto(player, Collections.<Card>emptyList());

        assertEquals("seat-2", proto.getSeatId());
        assertEquals(500, proto.getChips());
        assertEquals(0, proto.getHoleCardsCount());
        assertTrue(proto.getIsFolded());
        assertFalse(proto.getIsAllIn());
    }

    // --- PlayerSummary conversion ---

    @Test
    void convertsPlayerSummary() {
        PlayerState player = new PlayerState("seat-3", 750);

        PokerAgentOuterClass.PlayerSummary proto =
                ProtoAdapter.toSummaryProto(player);

        assertEquals("seat-3", proto.getSeatId());
        assertEquals(750, proto.getChips());
        assertFalse(proto.getIsFolded());
        assertFalse(proto.getIsAllIn());
    }

    // --- PotInfo conversion (empty pot) ---

    @Test
    void convertsEmptyPot() {
        PotInfo pot = PotInfo.empty();

        PokerAgentOuterClass.PotInfo proto = ProtoAdapter.toProto(pot);

        assertEquals(0, proto.getTotalPot());
        assertEquals(0, proto.getMainPot());
        assertEquals(0, proto.getSidePotsCount());
    }

    // --- PotSlice conversion ---

    @Test
    void convertsPotSlice() {
        PotSlice slice = new PotSlice(100, 50,
                Arrays.asList("seat-1", "seat-2"));

        PokerAgentOuterClass.PotSlice proto = ProtoAdapter.toProto(slice);

        assertEquals(100, proto.getAmount());
        assertEquals(2, proto.getEligibleSeatIdsCount());
        assertEquals("seat-1", proto.getEligibleSeatIds(0));
        assertEquals("seat-2", proto.getEligibleSeatIds(1));
    }

    // --- ActionEntry conversion ---

    @Test
    void convertsBetActionToEntry() {
        Action action = new BetAction("seat-1", 50);

        PokerAgentOuterClass.ActionEntry proto =
                ProtoAdapter.toActionEntryProto(action, GamePhase.FLOP);

        assertEquals("seat-1", proto.getSeatId());
        assertEquals(PokerAgentOuterClass.ActionType.BET, proto.getActionType());
        assertEquals(50, proto.getAmount());
        assertEquals(PokerAgentOuterClass.GamePhase.FLOP, proto.getPhase());
    }

    @Test
    void convertsFoldActionToEntry() {
        Action action = new FoldAction("seat-2");

        PokerAgentOuterClass.ActionEntry proto =
                ProtoAdapter.toActionEntryProto(action, GamePhase.PRE_FLOP);

        assertEquals("seat-2", proto.getSeatId());
        assertEquals(PokerAgentOuterClass.ActionType.FOLD, proto.getActionType());
        assertEquals(0, proto.getAmount());
    }

    // --- LegalAction conversion ---

    @Test
    void convertsAvailableLegalAction() {
        PokerAgentOuterClass.LegalAction proto =
                ProtoAdapter.toProto(ActionType.CALL, true, 50, 50);

        assertEquals(PokerAgentOuterClass.ActionType.CALL, proto.getType());
        assertTrue(proto.getIsAvailable());
        assertEquals(50, proto.getMinAmount());
        assertEquals(50, proto.getMaxAmount());
    }

    @Test
    void convertsUnavailableLegalAction() {
        PokerAgentOuterClass.LegalAction proto =
                ProtoAdapter.toProto(ActionType.RAISE, false, 0, 0);

        assertEquals(PokerAgentOuterClass.ActionType.RAISE, proto.getType());
        assertFalse(proto.getIsAvailable());
    }

    // --- buildDecisionRequest ---

    @Test
    void buildDecisionRequestContainsGameId() {
        GameState state = createSimpleGameState(GamePhase.PRE_FLOP, 0);
        PlayerState self = state.getPlayers().get(0);

        PokerAgentOuterClass.DecisionRequest req =
                ProtoAdapter.buildDecisionRequest("game-42", state, self);

        assertEquals("game-42", req.getGameId());
    }

    @Test
    void buildDecisionRequestPhaseIsCorrect() {
        GameState state = createSimpleGameState(GamePhase.FLOP, 0);
        PlayerState self = state.getPlayers().get(0);

        PokerAgentOuterClass.DecisionRequest req =
                ProtoAdapter.buildDecisionRequest("game-1", state, self);

        assertEquals(PokerAgentOuterClass.GamePhase.FLOP,
                req.getGameState().getPhase());
    }

    @Test
    void buildDecisionRequestSelfHasHoleCards() {
        List<Card> holeCards = Arrays.asList(
                new Card(Rank.ACE, Suit.SPADES),
                new Card(Rank.KING, Suit.SPADES));
        PlayerState self = new PlayerState("seat-0", 1000).withHoleCards(holeCards);
        PlayerState opponent = new PlayerState("seat-1", 800);

        List<PlayerState> players = Arrays.asList(self, opponent);
        GameState state = new GameState(
                GamePhase.PRE_FLOP,
                Collections.<Card>emptyList(),
                players,
                PotInfo.empty(),
                Collections.<Action>emptyList(),
                0, 0, 0, 1, 1,
                Collections.<Card>emptyList(), 0);

        PokerAgentOuterClass.DecisionRequest req =
                ProtoAdapter.buildDecisionRequest("game-1", state, self);

        PokerAgentOuterClass.PlayerView selfView = req.getGameState().getSelf();
        assertEquals("seat-0", selfView.getSeatId());
        assertEquals(2, selfView.getHoleCardsCount());
        assertEquals(PokerAgentOuterClass.Rank.ACE, selfView.getHoleCards(0).getRank());
        assertEquals(PokerAgentOuterClass.Rank.KING, selfView.getHoleCards(1).getRank());
    }

    @Test
    void buildDecisionRequestHasOpponents() {
        PlayerState self = new PlayerState("seat-0", 1000)
                .withHoleCards(Arrays.asList(new Card(Rank.ACE, Suit.SPADES),
                        new Card(Rank.KING, Suit.SPADES)));
        PlayerState opponent = new PlayerState("seat-1", 800);

        List<PlayerState> players = Arrays.asList(self, opponent);
        GameState state = new GameState(
                GamePhase.PRE_FLOP,
                Collections.<Card>emptyList(),
                players,
                PotInfo.empty(),
                Collections.<Action>emptyList(),
                0, 0, 0, 1, 1,
                Collections.<Card>emptyList(), 0);

        PokerAgentOuterClass.DecisionRequest req =
                ProtoAdapter.buildDecisionRequest("game-1", state, self);

        assertEquals(1, req.getGameState().getOpponentsCount());
        assertEquals("seat-1", req.getGameState().getOpponents(0).getSeatId());
    }

    @Test
    void buildDecisionRequestIncludesLegalActions() {
        PlayerState self = new PlayerState("seat-0", 1000)
                .withHoleCards(Arrays.asList(new Card(Rank.ACE, Suit.SPADES),
                        new Card(Rank.KING, Suit.SPADES)));
        PlayerState opponent = new PlayerState("seat-1", 800);

        List<PlayerState> players = Arrays.asList(self, opponent);
        GameState state = new GameState(
                GamePhase.PRE_FLOP,
                Collections.<Card>emptyList(),
                players,
                PotInfo.empty(),
                Collections.<Action>emptyList(),
                0, 0, 0, 1, 1,
                Collections.<Card>emptyList(), 0);

        PokerAgentOuterClass.DecisionRequest req =
                ProtoAdapter.buildDecisionRequest("game-1", state, self);

        assertTrue(req.getLegalActionsCount() > 0,
                "Should have at least one legal action");
    }

    // --- Helper methods ---

    private GameState createSimpleGameState(GamePhase phase, int currentBet) {
        PlayerState self = new PlayerState("seat-0", 1000)
                .withHoleCards(Arrays.asList(
                        new Card(Rank.ACE, Suit.SPADES),
                        new Card(Rank.KING, Suit.SPADES)));
        PlayerState opponent = new PlayerState("seat-1", 800);

        List<PlayerState> players = Arrays.asList(self, opponent);
        return new GameState(
                phase,
                Collections.<Card>emptyList(),
                players,
                PotInfo.empty(),
                Collections.<Action>emptyList(),
                0, 0, currentBet, 1, 1,
                Collections.<Card>emptyList(), 0);
    }
}
