package inBloom.test.story;

import inBloom.graph.Edge;
import inBloom.graph.PlotDirectedSparseGraph;
import inBloom.graph.PlotGraphController;
import inBloom.graph.Vertex;
import inBloom.graph.isomorphism.FunctionalUnit;
import inBloom.graph.isomorphism.FunctionalUnits;
import inBloom.graph.visitor.EdgeLayoutVisitor;

@SuppressWarnings("unused")
public class TestUnits {
	
	public static FunctionalUnit[] ALL;
	public static PlotDirectedSparseGraph ALL_UNITS_GRAPH;
	
	public static final FunctionalUnit MOTIVATION;
	public static final FunctionalUnit PERSEVERANCE;
	public static final FunctionalUnit RESOLUTION;
	public static final FunctionalUnit LOSS;
	public static final FunctionalUnit POSITIVE_TRADEOFF;
	public static final FunctionalUnit NEGATIVE_TRADEOFF;
	public static final FunctionalUnit PROBLEM;
	public static final FunctionalUnit ENABLEMENT;
	public static final FunctionalUnit SUCCESS;
	public static final FunctionalUnit FAILURE;
	public static final FunctionalUnit CHANGE_OF_MIND;
	public static final FunctionalUnit HIDDEN_BLESSING;
	public static final FunctionalUnit COMPLEX_POS_EVENT;
	
	static {
		MOTIVATION = FunctionalUnits.PRIMITIVES[0];
		CHANGE_OF_MIND = FunctionalUnits.PRIMITIVES[1];
		PERSEVERANCE = FunctionalUnits.PRIMITIVES[2];
		ENABLEMENT = FunctionalUnits.PRIMITIVES[3];
		PROBLEM = FunctionalUnits.PRIMITIVES[4];
		SUCCESS = FunctionalUnits.PRIMITIVES[5];
		FAILURE = FunctionalUnits.PRIMITIVES[6];
		LOSS = FunctionalUnits.PRIMITIVES[7];
		RESOLUTION = FunctionalUnits.PRIMITIVES[8];
		NEGATIVE_TRADEOFF = FunctionalUnits.PRIMITIVES[9];
		POSITIVE_TRADEOFF = FunctionalUnits.PRIMITIVES[10];
		HIDDEN_BLESSING = FunctionalUnits.PRIMITIVES[12];
		COMPLEX_POS_EVENT = FunctionalUnits.PRIMITIVES[13];
		
		// Set up all primitive units so they can be displayed as graph in PrimitiveUniteTest
		ALL = new FunctionalUnit[13];
		
		ALL[0] = MOTIVATION;
		ALL[1] = RESOLUTION;
		ALL[2] = LOSS;
		ALL[3] = PERSEVERANCE;
		ALL[4] = POSITIVE_TRADEOFF;
		ALL[5] = NEGATIVE_TRADEOFF;
		ALL[6] = PROBLEM;
		ALL[7] = ENABLEMENT;
		ALL[8] = SUCCESS;
		ALL[9] = FAILURE;
		ALL[10] = CHANGE_OF_MIND;
		ALL[11] = COMPLEX_POS_EVENT;
		ALL[12] = HIDDEN_BLESSING;
		
		PlotDirectedSparseGraph allUnitsGraph = ALL[0].getDisplayGraph();
		for(int i = 1; i < ALL.length; i++) {
			ALL[i].getDisplayGraph().cloneInto(allUnitsGraph);
		}

		allUnitsGraph.setName("Primitive Units");
		ALL_UNITS_GRAPH = allUnitsGraph;
	}
}
