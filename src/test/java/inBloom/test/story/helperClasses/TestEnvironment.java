package inBloom.test.story.helperClasses;

import inBloom.ActionReport;
import inBloom.PlotEnvironment;
import inBloom.storyworld.Character;
import jason.asSyntax.Structure;

public class TestEnvironment extends PlotEnvironment<TestModel> {
	
	@Override
    public ActionReport doExecuteAction(String agentName, Structure action) {
		// let the PlotEnvironment update the plot graph, initializes result as false
		ActionReport result = null;
    	Character agent = getModel().getCharacter(agentName);
    	
    	synchronized(this.getModel()) {
	    	if (action.getFunctor().equals("do_stuff")) {
	    		result = getModel().doStuff(agent);
	    	}
	    	
	    	if(action.getFunctor().equals("search")) {
	    		result = getModel().search(agent, action.getTerm(0).toString());
	    	}
	    	
	    	if(action.getFunctor().equals("clean")) {
	    		result = new ActionReport();		// negative outcome for primitive unite failure
	    	}
	    	
	    	if(action.getFunctor().equals("get")) {
	    		result = getModel().getDrink(agent);
	    	}
    	}
    	
    	return result;
    }
}
