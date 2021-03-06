package net.demilich.metastone.game.spells;

import com.github.fromage.quasi.fibers.Suspendable;
import net.demilich.metastone.game.GameContext;
import net.demilich.metastone.game.Player;
import net.demilich.metastone.game.entities.Entity;
import net.demilich.metastone.game.entities.minions.Minion;
import net.demilich.metastone.game.spells.desc.SpellArg;
import net.demilich.metastone.game.spells.desc.SpellDesc;
import net.demilich.metastone.game.targeting.EntityReference;

import java.util.Map;

public class MindControlSpell extends Spell {

	public static SpellDesc create(EntityReference target, TargetPlayer targetPlayer, boolean randomTarget) {
		Map<SpellArg, Object> arguments = new SpellDesc(MindControlSpell.class);
		arguments.put(SpellArg.TARGET, target);
		arguments.put(SpellArg.RANDOM_TARGET, randomTarget);
		arguments.put(SpellArg.TARGET_PLAYER, targetPlayer);
		return new SpellDesc(arguments);
	}

	@Override
	@Suspendable
	protected void onCast(GameContext context, Player player, SpellDesc desc, Entity source, Entity target) {
		Minion minion = (Minion) target;
		context.getLogic().mindControl(player, minion);
	}

}
