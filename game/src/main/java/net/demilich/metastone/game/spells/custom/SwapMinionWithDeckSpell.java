package net.demilich.metastone.game.spells.custom;

import com.github.fromage.quasi.fibers.Suspendable;
import net.demilich.metastone.game.GameContext;
import net.demilich.metastone.game.Player;
import net.demilich.metastone.game.cards.Card;
import net.demilich.metastone.game.cards.CardType;
import net.demilich.metastone.game.entities.Entity;
import net.demilich.metastone.game.logic.GameLogic;
import net.demilich.metastone.game.spells.ShuffleMinionToDeckSpell;
import net.demilich.metastone.game.spells.desc.SpellDesc;

public class SwapMinionWithDeckSpell extends ShuffleMinionToDeckSpell {

	@Override
	@Suspendable
	protected void onCast(GameContext context, Player player, SpellDesc desc, Entity source, Entity target) {
		// Check to see if there is a minion before returning to deck!
		if (!player.getDeck().hasCardOfType(CardType.MINION) || player.getMinions().size() >= GameLogic.MAX_MINIONS) {
			return;
		}

		// Summon a random minion and remove the corresponding card
		// before adding the target to your deck!
		Card randomCard = context.getLogic().getRandom(player.getDeck().filtered(c -> c.getCardType() == CardType.MINION));
		context.getLogic().removeCard(randomCard);
		// return target to deck (Now it's safe and won't destroy itself!)


		// If there is no minion to shuffle... Idk, blame a wizard.
		super.onCast(context, player, desc, source, target);

		// Summon the minion, which ALSO won't destroy itself...
		context.getLogic().summon(player.getId(), randomCard.summon(), null, -1, false);
	}

}

