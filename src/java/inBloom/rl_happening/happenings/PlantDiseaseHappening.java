package inBloom.rl_happening.happenings;

import java.util.function.Predicate;

import inBloom.rl_happening.islandWorld.IslandModel;
import inBloom.storyworld.Character;

public class PlantDiseaseHappening extends ConditionalHappening<IslandModel> {
	
	public PlantDiseaseHappening(Predicate<IslandModel> trigger, String patient, String causalProperty) {
	super(trigger, patient, causalProperty);
	}
	
	@Override
	protected boolean hasEffect(IslandModel model, Character chara) {
		return chara.location.equals(model.island);
	}
	
	@Override
	protected void executeModelEffects(IslandModel model, Character chara) {
		// TODO burning as a boolean in Island
		model.island.killHealingPlants();
		model.getLogger().info("The healing plants are gone!");
	}
	
	@Override
	protected String getConditionalPercept() {
		return "plantDisease";
	}
	
	@Override
	protected String getConditionalEmotion() {
		return "distress";
	}

}
