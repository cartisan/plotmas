package inBloom.test.story;

import static org.junit.Assert.assertTrue;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import jason.asSemantics.Personality;

import inBloom.LauncherAgent;
import inBloom.graph.Edge;
import inBloom.graph.Vertex;
import inBloom.helper.PerceptAnnotation;
import inBloom.storyworld.Happening;
import inBloom.storyworld.ScheduledHappeningDirector;
import inBloom.test.story.helperClasses.AbstractPlotTest;
import inBloom.test.story.helperClasses.HappeningsCollection;
import inBloom.test.story.helperClasses.TestModel;

public class HappeningCausalityTest extends AbstractPlotTest {

	@BeforeClass
	public static void setUp() throws Exception {
		VISUALIZE = false;
		DEBUG = false;

		// initialize agents
        ImmutableList<LauncherAgent> agents = ImmutableList.of(
							new LauncherAgent("jeremy",
									new Personality(0,  0,  0,  0, 0)
							)
						);


        // Initialize happenings
        ScheduledHappeningDirector hapDir = new ScheduledHappeningDirector();

        // Set up positive happening "find friend" that is triggered by positive action perception "get(drink)"
        hapDir.scheduleHappening(HappeningsCollection.findFriendHap);

        Happening<TestModel> findSecondFriend = new Happening<>(
        		new Predicate<TestModel>() {
					@Override
					public boolean test(TestModel model) {
						if (model.hasFriend) {
							return true;
						}
						return false;
					}

        		},
        		new Consumer<TestModel>() {
					@Override
					public void accept(TestModel model) {
						model.hasFriend = true;
					}
        		},
        		null,		// no need to define patient, it should be inferred: the agent that caused a change in hasFriend
        		"hasFriend",
        		"found(friend2)");

        findSecondFriend.setAnnotation(PerceptAnnotation.fromEmotion("joy"));
        hapDir.scheduleHappening(findSecondFriend);

		startSimulation("agent_happening", agents, hapDir);
	}


	@Test
	public void testCausalityAction() {
		// we have the action get(drink), which causes find(friend) happening
		assertTrue(analyzedGraph.getVertices().stream().anyMatch(v -> v.getLabel().contains("get(drink")));
		Vertex action = analyzedGraph.getVertices().stream().filter(v -> v.getLabel().contains("get(drink"))
															.collect(Collectors.toList()).get(0);

		// vertex of action get(drink) has one causality edge
		assertTrue(analyzedGraph.getIncidentEdges(action).stream()
			             							     .anyMatch(e -> e.getType().equals(Edge.Type.CAUSALITY)));
	}

	@Test
	public void testCausalityHappening() {
		// we have the happening found(friend2) which is caused by happening found(friend)
		assertTrue(analyzedGraph.getVertices().stream().anyMatch(v -> v.getLabel().contains("found(friend2)")));
		Vertex happening = analyzedGraph.getVertices().stream().filter(v -> v.getLabel().contains("found(friend2)"))
															   .collect(Collectors.toList()).get(0);

		// vertex of happening find(friend2) has one causality edge
		assertTrue(analyzedGraph.getIncidentEdges(happening).stream()
			             							        .anyMatch(e -> e.getType().equals(Edge.Type.CAUSALITY)));
	}
}
