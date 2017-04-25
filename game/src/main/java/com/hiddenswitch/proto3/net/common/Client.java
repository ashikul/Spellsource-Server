package com.hiddenswitch.proto3.net.common;

import java.util.List;

import net.demilich.metastone.game.Player;
import net.demilich.metastone.game.TurnState;
import net.demilich.metastone.game.actions.GameAction;
import net.demilich.metastone.game.cards.Card;
import net.demilich.metastone.game.events.GameEvent;

public interface Client {
	void onGameEvent(GameEvent event);

	void onGameEnd(Player winner);

	void setPlayers(Player localPlayer, Player remotePlayer);

	void onActivePlayer(Player activePlayer);

	void onTurnEnd(Player activePlayer, int turnNumber, TurnState turnState);

	void onUpdate(GameState state);

	void onRequestAction(String messageId, GameState state, List<GameAction> actions);
	
	void onMulligan(String messageId, GameState state, List<Card> cards, int playerId);

	void close();

	Object getPrivateSocket();

	void lastEvent();
}