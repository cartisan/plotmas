/**
 * 
 */
package inBloom.rl_happening;

import com.google.common.collect.ImmutableList;

import inBloom.LauncherAgent;
import inBloom.PlotControlsLauncher;
import inBloom.PlotLauncher;
import inBloom.storyworld.ScheduledHappeningDirector;
import jason.asSemantics.Personality;
import jason.infra.centralised.BaseCentralisedMAS;

/**
 * @author Julia Wippermann
 * @version 29.10.19
 *
 */
public class IslandLauncher extends PlotLauncher<IslandEnvironment, IslandModel> {

	public IslandLauncher() {
		ENV_CLASS = IslandEnvironment.class;
		PlotControlsLauncher.runner = this;
		BaseCentralisedMAS.runner = this;
	}
	
	public static void main(String[] args) {		
		logger.info("Starting up from Launcher");
		
		PlotControlsLauncher.runner = new IslandLauncher();
		
		ImmutableList<LauncherAgent> agents = ImmutableList.of(
				new LauncherAgent("robinson",
						new Personality(0, 0, 0, 0, 0))
				);
		
		// Initialise MAS with a scheduled happening director
		ScheduledHappeningDirector hapDir = new ScheduledHappeningDirector();
		
		IslandModel model = new IslandModel(agents, hapDir);
		
		ribonson.location = model.island.name;
		
		// Execute MAS
		// HERE IS THE LINK TO THE AGENT.ASL FILE!!!
		runner.initialize(args, model, agents, islandAgent);
		runner.run();
	}
}
