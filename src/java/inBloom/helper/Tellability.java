package inBloom.helper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import inBloom.framing.ConnectivityGraph;
import inBloom.graph.CountingVisitor;
import inBloom.graph.PlotDirectedSparseGraph;
import inBloom.graph.PlotGraphController;
import inBloom.graph.Vertex;
import inBloom.graph.isomorphism.FunctionalUnit;
import inBloom.graph.isomorphism.FunctionalUnits;
import inBloom.graph.isomorphism.UnitFinder;
import inBloom.graph.visitor.EdgeGenerationPPVisitor;
import inBloom.graph.visitor.VertexMergingPPVisitor;

public class Tellability {
	public static int GRAPH_MATCHING_TOLERANCE = 1;

	protected static Logger logger = Logger.getLogger(Tellability.class.getName());

	// plot preconditions
	public CountingVisitor counter;
	public int productiveConflicts = 0;

	// Functional Polyvalence
	public List<FunctionalUnit> plotUnitTypes;
	public int numFunctionalUnits;
	public int numPolyvalentVertices;
	public int numAllVertices;
	public Map<FunctionalUnit, Integer> functionalUnitCount = new HashMap<>();
	public ConnectivityGraph connectivityGraph;

	// Semantic Symmetry

	// Semantic Opposition

	// Suspense
	public int suspense;
	public int plotLength;


	// Dynamic Points



	/**
	 * Takes an analyzed graph and computes all necessary statistics of the plot to compute tellability.
	 * @param graph whose tellability needs to be determined and that has been been processed by 
	 * {@link VertexMergingPPVisitor} and  {@link EdgeGenerationPPVisitor}. 
	 */
	public Tellability(PlotDirectedSparseGraph graph) {
		this.counter = new CountingVisitor();
		this.plotUnitTypes = new LinkedList<>();

		// Find Functional Units and polyvalent Vertices
		this.detectPolyvalence(graph);

		// Perform quantitative analysis of plot
		this.computeSimpleStatistics(graph);
	}

	/**
	 * Computes all statistics that can be determined by counting in a single pass
	 * @param graph a graph that has been processed by both VertexMergingPPVisitor and VisualizationFilterPPVisitor
	 */
	private void computeSimpleStatistics(PlotDirectedSparseGraph graph) {
		this.counter.apply(graph);
		this.productiveConflicts = this.counter.getProductiveConflictNumber();
		this.suspense = this.counter.getSuspense();
		this.plotLength = this.counter.getPlotLength();
		this.numAllVertices = this.counter.getVertexNum();
	}

	/**
	 * Identifies all instances of functional units in the plot graph and detects polyvalent vertices
	 * at their overlap.
	 * @param graph a graph that has been processed by both VertexMergingPPVisitor and VisualizationFilterPPVisitor
	 */
	private void detectPolyvalence(PlotDirectedSparseGraph graph) {
		Map<Vertex, Integer> vertexUnitCount = new HashMap<>();

		UnitFinder finder = new UnitFinder();
		int polyvalentVertices = 0;
		int unitInstances = 0;
		Set<Vertex> polyvalentVertexSet = new HashSet<>();

		this.connectivityGraph = new ConnectivityGraph(graph);

		for(FunctionalUnit unit : FunctionalUnits.ALL) {
			logger.info("Finding units of type: '" + unit.getName() + "'...");
			Set<Map<Vertex, Vertex>> mappings = finder.findUnits(unit.getGraph(), graph, GRAPH_MATCHING_TOLERANCE);
			unitInstances += mappings.size();
			this.functionalUnitCount.put(unit, mappings.size());

			if (mappings.size() > 0 ) {
				PlotGraphController.getPlotListener().addDetectedPlotUnitType(unit);
				this.plotUnitTypes.add(unit);
			}

			// maps from FU vertex to plot graph vertex
			for(Map<Vertex, Vertex> map : mappings) {
				FunctionalUnit.Instance instance = unit.new Instance(graph, map.values(), unit.getName());
				instance.identifySubject(map);
				this.connectivityGraph.addVertex(instance);
				graph.addFUInstance(unit, instance);

				for(Vertex v : map.values()) {
					if(!vertexUnitCount.containsKey(v)) {
						vertexUnitCount.put(v, 1);
					} else {
						int count = vertexUnitCount.get(v);
						count++;
						if(count == 2) {
							polyvalentVertices++;
							polyvalentVertexSet.add(v);
						}
						vertexUnitCount.put(v, count);
					}
				}
			}
		}


		String foundUnits = this.functionalUnitCount.entrySet().stream().filter(entry -> entry.getValue() > 0)
													.map(entry -> entry.getKey().getName() + ": " + entry.getValue())
													.sorted()
													.reduce( (a,b) -> a = a + ", " + b)
													.orElse("<none>");
		logger.info("-> Found units: " + foundUnits);

		for(FunctionalUnit primitiveUnit : FunctionalUnits.PRIMITIVES) {
			Set<Map<Vertex, Vertex>> mappings = finder.findUnits(primitiveUnit.getGraph(), graph);
			for(Map<Vertex, Vertex> map : mappings) {
				FunctionalUnit.Instance instance = primitiveUnit.new Instance(graph, map.values(), primitiveUnit.getName());
				this.connectivityGraph.addVertex(instance);
			}
		}

		this.numFunctionalUnits = unitInstances;
		this.numPolyvalentVertices = polyvalentVertices;

		// Mark polyvalent vertices with asterisk
		for(Vertex v : polyvalentVertexSet) {
			v.setPolyvalent();
		}
	}

	/**
	 * Computes the overall tellability score, by normalizing all features into a range of (0,1) and adding them,
	 * which amounts to assigning each feature equal weight.
	 * @return
	 */
	public double compute() {
		if (this.productiveConflicts < 1) {
			// need at least one conflict and one attempt at resolution, for this to be a plot
			return 0;
		}

		double tellability = (double) this.numPolyvalentVertices / this.numAllVertices +
							 (double) this.suspense / this.plotLength;

		logger.info("Overall tellability: " + tellability);
		return tellability;
	}
}
