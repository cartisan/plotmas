package inBloom.graph.visitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import inBloom.graph.Edge;
import inBloom.graph.PlotDirectedSparseGraph;
import inBloom.graph.Vertex;
import inBloom.helper.TermParser;
import inBloom.jason.PlotCircumstanceListener;

/**
 * This post-process visitor is intended to be used on the merged plot graph generated by the {@linkplain VertexMergingPPVisitor.}
 * It will create semantic edges based on annotations and structural analysis, in order to create information that allows
 * creating all primitive FU.
 * @author Leonid Berov
 */
public class EdgeGenerationPPVisitor extends PlotGraphVisitor {
	protected static Logger logger = Logger.getLogger(EdgeGenerationPPVisitor.class.getName());
	private static final boolean KEEP_MOTIVATION = true;

	private LinkedList<Vertex> eventList;
	private Vertex currentRoot;
	/** Safes which actions and perceptions were annotated with which crossChar ID.
	 *  So that {@link #postProcessing()} can create edges, when several vertices share the same ID.
	 *  Maps: ID -> Multuple Vertices */
	private ArrayListMultimap<String, Vertex> xCharIDMap;

	public EdgeGenerationPPVisitor() {
		this.eventList = new LinkedList<>();
		this.xCharIDMap = ArrayListMultimap.create();
	}

	public PlotDirectedSparseGraph apply(PlotDirectedSparseGraph graph) {
		super.apply(graph);
		this.postProcessing();

		return this.graph;
	}

	@Override
	public void visitEvent(Vertex vertex) {
		logger.warning("Located semantically underspecified EVENT vertex: " + vertex.getLabel());
	}

	@Override
	public void visitEmotion(Vertex vertex) {
		logger.warning("Found emotion vertex during 2nd preprocessor, should have been deleted: " + vertex.getLabel());
	}

	@Override
	public void visitListen(Vertex vertex) {
		logger.warning("Found listen vertex during 2nd preprocessor, should have been deleted: " + vertex.getLabel());
	}

	@Override
	public void visitRoot(Vertex vertex) {
		logger.fine("visiting root: " + vertex.toString());
		this.eventList.clear();
		this.currentRoot = vertex;
	}

	@Override
	public void visitAction(Vertex vertex) {
		logger.fine("visiting action: " + vertex.toString());
 		// create actualization edges to causing intention
		String intention = TermParser.getAnnotation(vertex.getLabel(), Edge.Type.ACTUALIZATION.toString());

		if(intention.length() > 0) {
			for(Vertex target : this.eventList) {
				if(intention.equals(target.getIntention())) {
					this.graph.addEdge(new Edge(Edge.Type.ACTUALIZATION), target, vertex);
					break;
				}
			}
		} else {
			logger.severe("Found action with no intention annotation: " + vertex.getLabel());
		}

		this.processCrossCharAnnotation(vertex);
		this.eventList.addFirst(vertex);
	}

	@Override
	public void visitPercept(Vertex vertex) {
		logger.fine("visiting percept: " + vertex.toString());

		String cause = TermParser.removeAnnots(vertex.getCause());
		String source = vertex.getSource();

		// check whether this reports a happening (source is not self) with a cause
		if (!source.equals("self") && !cause.isEmpty()) {
			// create causality edge from vertex corresponding to cause annotation
			for(Vertex targetEvent : this.eventList) {
				if(targetEvent.getWithoutAnnotation().equals(cause) |  //our cause was an action, so targetEvent was perceived as-is
						targetEvent.getWithoutAnnotation().equals("+" + cause))  // our cause was a happening, so targetEvent was perceived as +cause
				{
					this.graph.addEdge(new Edge(Edge.Type.CAUSALITY), targetEvent, vertex);
					break;
				}
			}
		}

		this.processCrossCharAnnotation(vertex);

		// this reports a mental note with a cause
		if (source.equals("self") && !cause.isEmpty()) {
			if(vertex.getLabel().startsWith("-")) {
				this.handleIndirectRemoval(vertex, cause);
			} else if (vertex.getLabel().startsWith("+")) {
				this.handleIndirectAddition(vertex, cause);
			}
		}

		if(vertex.hasEmotion()) {
			this.handleBeliefSwitch(vertex);
		}

		this.eventList.addFirst(vertex);
	}

	@Override
	public void visitSpeech(Vertex vertex) {
		logger.fine("visiting speech: " + vertex.toString());
		String intention = TermParser.getAnnotation(vertex.getLabel(), Edge.Type.ACTUALIZATION.toString());
		if(intention.length() > 0) {
			intention = TermParser.removeAnnots(intention);
			for(Vertex target : this.eventList) {
				if(intention.equals(target.getIntention())) {
					this.graph.addEdge(new Edge(Edge.Type.ACTUALIZATION), target, vertex);
					break;
				}
			}
		}

		this.eventList.addFirst(vertex);
	}

	@Override
	public void visitIntention(Vertex vertex) {
		logger.fine("visiting intention: " + vertex.toString());
		String label = vertex.getLabel();
		if(label.startsWith("drop_intention")) {
			this.handleDropIntention(vertex);
			return;
		}

		this.lookForPerseverance(vertex);
		this.attachMotivation(vertex);
		this.eventList.addFirst(vertex);
	}

	private void lookForPerseverance(Vertex vertex) {
		for(Vertex target : this.eventList) {
			if(target.getIntention().equals(vertex.getIntention()) && target.getRoot().equals(vertex.getRoot())  ) {
				this.graph.addEdge(new Edge(Edge.Type.EQUIVALENCE), vertex, target);	//equivalence edges point up
				return;
			}
		}
	}

	public void handleDropIntention(Vertex vertex) {
		String label = vertex.getLabel();
		Pattern pattern = Pattern.compile("drop_intention\\((?<drop>.*?)\\)\\[" + Edge.Type.TERMINATION.toString() + "\\((?<termination>.*)\\)\\]");
		Matcher matcher = pattern.matcher(label);

		// Remove the vertex if it is somehow degenerate (pattern could not be matched)
		if(!matcher.find()) {
			this.removeVertex(vertex);
			return;
		}
		String dropString = TermParser.removeAnnots(matcher.group("drop").substring(2));
		// Determine if the intention drop is relevant
		// (whether or not the intention that was dropped is in the graph)
		Vertex droppedIntention = null;
		for(Vertex drop : this.eventList) {
			if(drop.getIntention().equals(dropString)) {
				droppedIntention = drop;
				break;
			}
		}
		// If it is irrelevant, simply remove the vertex
		if(droppedIntention == null) {
			this.removeVertex(vertex);
			return;
		}

		// Look for the cause in previous vertices
		String causeString = matcher.group("termination");
		if(causeString.startsWith("+!")) {	// we need to match causes of form +self(has_prupose), but also !rethink_life, which appears as +!rethink_life
			causeString = causeString.substring(1);
		} else if (causeString.startsWith("-wish") | causeString.startsWith("-obligation")) { // part of normal wish/obligation management, no need for edges
			this.removeVertex(vertex);
			return;
		}

		Vertex cause = null;
		for(Vertex potentialCause : this.eventList) {
			if(potentialCause.getWithoutAnnotation().equals(causeString)) {
				cause = potentialCause;
				break;
			}
		}

		if(cause != null) {
			this.createTermination(cause, droppedIntention);
			this.removeVertex(vertex);
		} else {
			if(causeString.startsWith("!")) {
				vertex.setType(Vertex.Type.INTENTION);
			} else {
				vertex.setType(Vertex.Type.PERCEPT);
			}
			vertex.setLabel(causeString);
			this.createTermination(vertex, droppedIntention);
			this.eventList.addFirst(vertex);
		}
	}

	private void handleBeliefSwitch(Vertex vertex) {
		for(Vertex target : this.eventList) {
			// If both vertices are the same event (i.e. -has(bread) and +has(bread))
			if(target.getWithoutAnnotation().substring(1).equals(vertex.getWithoutAnnotation().substring(1))) {
				// If the one is an addition while the other is a subtraction of a percept
				if(!target.getWithoutAnnotation().substring(0, 1).equals(vertex.getWithoutAnnotation().substring(0, 1))) {
					// If both vertices belong to same character
					if(target.getRoot().equals(vertex.getRoot())) {
						// Only create termination edge if affects in v and t
						if (target.hasEmotion()) {
							this.createTermination(vertex, target);
							break;
						}
					}
				}
			}
		}
	}

	/**
	 * Add entry to xCharIDMap if cross-character annotation present, so that #postProcessing can create edges between
	 * events with same xCharIDs.
	 * @param vertex
	 */
	private void processCrossCharAnnotation(Vertex vertex) {
		String crossCharID = TermParser.getAnnotation(vertex.getLabel(), Edge.Type.CROSSCHARACTER.toString());
		if (!crossCharID.isEmpty()) {
			this.xCharIDMap.put(crossCharID, vertex);
		}
	}

	private void attachMotivation(Vertex vertex) {
		String label = vertex.getLabel();
		String[] parts = label.split("\\[" + Edge.Type.MOTIVATION.toString() + "\\(");

		if(parts.length > 1) {
			String[] motivations = parts[1].substring(0, parts[1].length() - 2).split(";");
			String resultingLabel = parts[0];
			Set<Vertex> motivationVertices = new HashSet<>();
			for(String motivation : motivations) {
				motivation = TermParser.removeAnnots(motivation);
				for(Vertex target : this.eventList) {
					boolean isMotivation = false;

					// Check for intentions
					isMotivation = isMotivation ||
							motivation.equals(target.getIntention());

					// Check for percepts
					isMotivation = isMotivation ||
							motivation.equals(TermParser.removeAnnots(target.getLabel()));

					// Check for listens
					isMotivation = isMotivation ||
							motivation.equals(TermParser.removeAnnots(target.getLabel()).substring(1));

					if(isMotivation && !motivationVertices.contains(target)) {
						this.graph.addEdge(new Edge(Edge.Type.MOTIVATION), target, vertex);
						motivationVertices.add(target);
						break;
					}
				}
			}

			if(!KEEP_MOTIVATION || !motivationVertices.isEmpty()) {
				vertex.setLabel(resultingLabel);
			}
		}
	}

	/**
	 * Checks if this perception of vertex is the removal of a belief, caused by processing another belief:
	 * {@code -has(bread)[source(is_dropped(bread))]}.
	 * If so, creates a termination edge between the source and the vertex representing the addition of this belief.
	 * @see PlotCircumstanceListener#eventAdded(jason.asSemantics.Event)
	 * @param vertex
	 * @return whether tradeoff was found
	 */
	private boolean handleIndirectRemoval(Vertex vertex, String cause) {
		// Look for vertex noted in cause annotation
		Vertex causeV = null;
		for(Vertex v : this.eventList) {
			if(TermParser.removeAnnots(v.getLabel()).equals(cause) ||
						TermParser.removeAnnots(v.getLabel()).equals(cause.substring(1)) && v.getType().equals(Vertex.Type.ACTION)) {
				// Source found! We take every source!
				causeV = v;
				break;
			}
		}

		if(causeV == null) {
			return false;
		}

		// check if vertex is -wish(X)/-obligation(X) --> something actualized an intention, then we need an A edge from !X to the cause of this vertex
		if(vertex.getWithoutAnnotation().substring(1).startsWith("wish") || vertex.getWithoutAnnotation().substring(1).startsWith("obligation")) {
			// source of edge should be intention !X, since we are -wish(X)
			String source = vertex.getWithoutAnnotation().substring(1).split("wish|obligation")[1];
			source = "!" + source.substring(1, source.length() - 1);

			// Let's find the corresponding addition of this mental note
			for(Vertex sourceV : this.eventList) {
				if(sourceV.getWithoutAnnotation().equals(source)) {
					// Great, found the intention! See if an ACTU edge already exists, we don't want duplication
					SetView<Edge> inter = Sets.intersection(new HashSet<>(this.graph.getIncidentEdges(sourceV)),
									  						new HashSet<>(this.graph.getIncidentEdges(causeV)));

					if (!inter.stream().anyMatch(e -> e.getType() == Edge.Type.ACTUALIZATION)) {
						this.graph.addEdge(new Edge(Edge.Type.ACTUALIZATION), sourceV, causeV);
						return true;
					}
				}
			}

		// vertex is regular belief removal -X --> we need a T edge from the cause of this vertex to +X
		} else {
			// first, add causality edge. We are not interested in them for -wish/-obligations, but this case has been eliminated above
			this.graph.addEdge(new Edge(Edge.Type.CAUSALITY), causeV, vertex);

			// then, see if we can create a termination edge between cause and target +X
			String target = vertex.getWithoutAnnotation().substring(1);

			// Let's find the corresponding addition of this mental note.
			for(Vertex targetV : this.eventList) {
				if(targetV.getWithoutAnnotation().substring(1).equals(target)) {
					if(targetV.getWithoutAnnotation().substring(0, 1).equals("+")) {
						// Great, found the addition!
						this.createTermination(causeV, targetV);
						return true;
					}
				}
			}
		}

		return false;
	}

	private void handleIndirectAddition(Vertex vertex, String cause) {
		// we are not interested in what caused +wish(X)/+obligation(X) beliefs, they are not part of FU graph
		// the corresponding intention !X will have its motivation edge attached via #visitIntention
		if(vertex.getWithoutAnnotation().substring(1).startsWith("wish") ||
				vertex.getWithoutAnnotation().substring(1).startsWith("obligation")) {
			return;
		}

		// Look for cause
		for(Vertex v : this.eventList) {
			if(TermParser.removeAnnots(v.getLabel()).equals(cause) ||
						TermParser.removeAnnots(v.getLabel()).equals(cause.substring(1)) && v.getType().equals(Vertex.Type.ACTION)) {
				// Source found! We take every source!
				this.graph.addEdge(new Edge(Edge.Type.CAUSALITY), v, vertex);
				break;
			}
		}
	}

	/**
	 * Performs all post-processing tasks needed to finish the full graph processing. This includes:
	 * <ul>
	 *   <li> Creating cross-character edges for all perception vertices with same content and step but perceived
	 *        by different agents. That is connecting the vertices of each cell in {@linkplain #stepPerceptTable} that
	 *        has multiple entries. </li>
     * 	 <li> Trimming the repeated actions at the end of the plot that caused execution to pause.</li>
     * </ul>
	 */
	private void postProcessing() {
		logger.fine("starting post processing");
		// iterate over entries of xCharIDMap, and create cross-character edges like below
		for (String id: this.xCharIDMap.keys()) {
			List<Vertex> connectedEvents = this.xCharIDMap.get(id);

			// create edges between all vertices in cell (cell obv. should have more than one vertex
			if (connectedEvents.size() > 1) {
				// create all pairwise combinations for vertices in cell, connect all pairs
				Set<Set<Vertex>> pairs = Sets.combinations(new HashSet<>(connectedEvents), 2);

				for(Set<Vertex> pair : pairs) {
					ArrayList<Vertex> pList = new ArrayList<>(pair);

					// if both vertices belong to same character, no cross-character edge is required
					if (pList.get(0).getRoot().equals(pList.get(1).getRoot())) {
						continue;
					}

					// create x-character edges
					this.graph.addEdge(new Edge(Edge.Type.CROSSCHARACTER), pList);

					// connect bi-directionally by creating reversed edges
					Collections.reverse(pList);
					this.graph.addEdge(new Edge(Edge.Type.CROSSCHARACTER), pList);
				}
			}
		}
	}

	private void removeVertex(Vertex vertex) {
		this.graph.removeVertexAndPatchGraphAuto(this.currentRoot, vertex);
	}

	private void createTermination(Vertex from, Vertex to) {
		this.graph.addEdge(new Edge(Edge.Type.TERMINATION), from, to);
	}
}
