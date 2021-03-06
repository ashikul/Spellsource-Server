package net.demilich.metastone.game.spells.desc.condition;

import net.demilich.metastone.game.GameContext;
import net.demilich.metastone.game.Player;
import net.demilich.metastone.game.entities.Entity;
import net.demilich.metastone.game.spells.desc.filter.EntityFilter;
import net.demilich.metastone.game.targeting.EntityReference;

public class AnyMatchFilterCondition extends Condition {

	public AnyMatchFilterCondition(ConditionDesc desc) {
		super(desc);
	}

	@Override
	protected boolean isFulfilled(GameContext context, Player player, ConditionDesc desc, Entity source, Entity target) {
		EntityReference targetReference = (EntityReference) desc.get(ConditionArg.TARGET);
		EntityFilter filter = (EntityFilter) desc.get(ConditionArg.FILTER);
		if (targetReference == null && target != null) {
			targetReference = target.getReference();
		}
		for (Entity entity : context.resolveTarget(player, source, targetReference)) {
			if (filter == null || filter.matches(context, player, entity, source)) {
				return true;
			}
		}
		return false;
	}

}

