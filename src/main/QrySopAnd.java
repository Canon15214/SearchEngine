/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchAll(r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } 
    else if (r instanceof RetrievalModelRankedBoolean) {
        return this.getScoreRankedBoolean (r);
    } 
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the AND operator.");
    }
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
	  if (! this.docIteratorHasMatchCache()) {
		  return 0.0;
	  } else {
		  return 1.0;
	  }
  }

  /**
   *  getScore for the RankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
		double score = 0.0;
	    if (this.docIteratorHasMatchCache()) {
	    	int docId = this.docIteratorGetMatch();
	    	// #And operator uses the MIN function to combine the scores from the query arguments.
	    	for(Qry arg : this.args){
	    		if(!arg.docIteratorHasMatch(r) || 
	    				(arg.docIteratorHasMatch(r) && arg.docIteratorGetMatch() != docId))
	    			break;
	    		double argRankedBooleanScore = ((QrySop) arg).getScore(r);
	    		if(argRankedBooleanScore < score || score==0.0)
	    			score = argRankedBooleanScore;	    		
			}
	    }
	    return score;
  }
}
