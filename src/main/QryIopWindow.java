/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */
import java.io.*;
import java.util.*;

/**
 *  The Window operator for all retrieval models.
 *  Window/n: return a document if all of the query arguments occur in the document 
 *  (or document field) in any order within a window of n terms.
 */
public class QryIopWindow extends QryIop {

  private int windowSize; 
  
  public QryIopWindow(int windowSize){
	  this.windowSize = windowSize;
  }
  /**
   *  Evaluate the query operator; the result is an internal inverted
   *  list that may be accessed via the internal iterators.
   *  @throws IOException Error accessing the Lucene index.
   */
  protected void evaluate () throws IOException {

    //  Create an empty inverted list.  If there are no query arguments,
    //  that's the final result.
    
    this.invertedList = new InvList (this.getField());

    if (args.size () == 0) {
    	this.invertedList = ((QryIop)args.get(0)).invertedList;
    	return;
    }

    //  Each pass of the loop adds 1 document to result inverted list
    //  until all of the argument inverted lists are depleted.

    while (true) {
    	//Find the minimum doc id which match all arguments. If there is none, we're done.
    	if(!this.docIteratorHasMatchAll(null))
    		break;
    	int minMatchDocId = this.docIteratorGetMatchCache();
    	
    	List<Integer> positions = new ArrayList<Integer>();
    	
    	//Consider #WINDOW/n(a b c). Your software iterates down the locations for a, b, and c in parallel. 
    	//Suppose the three iterators all start at the first location for each term. The window size that
    	//covers those 3 term occurrences is of size 1 + Max (a.currentloc, b.currentloc, c.currentloc) - Min (a.currentloc, b.currentloc, c.currentloc). 
    	//If the size is > N, advance the iterator that has the Min location. 
    	//If the size is <= N, you have a match, and you advance all 3 iterators. 
    	//Continue until any iterator reaches the end of its location list.
    	while(true) {
    		int minLoc = Integer.MAX_VALUE;
    		int maxLoc = Integer.MIN_VALUE;
    		int minLocArg = Integer.MIN_VALUE;
    	
    		for (int i=0; i<this.args.size(); i++) {
    			QryIop q = (QryIop) args.get(i);
    			int loc;
    			if (!q.locIteratorHasMatch()) {
    	    		minLocArg = Integer.MIN_VALUE;
    				break;
    			}
    			loc = q.locIteratorGetMatch();
    			if (loc < minLoc) {
    				minLoc = loc;
    				minLocArg = i;
    			}
    			if (loc > maxLoc) {
    				maxLoc = loc;
    			}
    		}
    	
    		
    		if (minLocArg < 0 ) {
    			break;
    		} else if (maxLoc - minLoc +1 <= windowSize) {
    			positions.add(maxLoc);
    			for (int i=0; i<this.args.size(); i++) {
    				((QryIop) args.get(i)).locIteratorAdvance();
    			}
    		} else {
    			((QryIop) args.get(minLocArg)).locIteratorAdvance();
    		}
    	}
    	
    	if (positions.size() > 0) {
    		this.invertedList.appendPosting (minMatchDocId, positions);
   		}
    	
    	//Move docIteratorIndex forward for all arguments.
        for (Qry q_i: this.args) {
           q_i.docIteratorAdvancePast (minMatchDocId);
    	}
    	
    }
  }
  

  
  /*
   * Take the distance into account as well as the name and args.
   */
  @Override
  public int hashCode(){
      int hash = getDisplayName().hashCode();
      for(Qry arg : args){
   	   hash += arg.hashCode();
      }
      return hash + windowSize;
  }
}
