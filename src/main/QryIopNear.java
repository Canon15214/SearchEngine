/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */
import java.io.*;
import java.util.*;

/**
 *  The NEAR operator for all retrieval models.
 *  NEAR/n: return a document if all of the query arguments occur in the document, 
 *  in order, with no more than n-1 terms separating two adjacent terms.
 */
public class QryIopNear extends QryIop {

  private int dist; 
  
  public QryIopNear(int dist){
	  this.dist = dist;
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
      return;
    }

    //  Each pass of the loop adds 1 document to result inverted list
    //  until all of the argument inverted lists are depleted.

    while (true) {
    	//Find the minimum doc id which match all arguments. If there is none, we're done.
    	if(!this.docIteratorHasMatchAll(null))
    		break;
    	int minMatchDocId = this.docIteratorGetMatchCache();
    	
    	//  Create a new posting that is the distanced intersection of the posting lists
        //  that match the minDocid.  Save it.
    	List<Integer> positions = new ArrayList<Integer>();
    	positions.addAll(((QryIop)args.get(0)).docIteratorGetMatchPosting().positions);
    	
    	//Do #Near match pairwise from left to right.
    	//Intermediate results are stored as a temp positions list
    	//and will be used in next iteration.
    	for(int i=1;i<this.args.size();i++){
    		positions = GreedyNearMatch(positions, (QryIop)args.get(i));   		
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
  
  public List<Integer> 	GreedyNearMatch(List<Integer> tmp, QryIop q){
	List<Integer> locations = new ArrayList<Integer>();
	//Iterate the tmp position list, match with positions of current document that q points to.
	for (int i = 0; i < tmp.size() && (q.locIteratorHasMatch());) {
		int loc = q.locIteratorGetMatch();
		if (tmp.get(i) >= loc) {
			q.locIteratorAdvancePast(loc);
		} else {
			//Use greedy strategy, which means if there is a match, both two points would move forward
			//where there is no record for previous pointer therefore no back tracing.
			//Greedy strategy is practical although it does not handle 
			//duplicate arguments (e.g it can only identify one match using #Near/2(a b) on "a a b" )
			if (loc - tmp.get(i) <= this.dist) {
				locations.add(loc);
				i++;
				q.locIteratorAdvancePast(loc);
			} else {
				i++;
			}
		}
	}
	//garbage collection
	//tmp.clear();
	return locations;
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
      return hash+dist;
  }
}
