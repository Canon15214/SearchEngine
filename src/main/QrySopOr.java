/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchMin (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  @Override
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } 
    else if (r instanceof RetrievalModelRankedBoolean) {
        return this.getScoreRankedBoolean (r);
    } 
    else if (r instanceof RetrievalModelIndri) {
        return this.getScoreIndri (r);
    } 
    else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
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
    	// #OR operator uses the MAX function to combine the scores from the query arguments.
    	for(Qry arg : this.args){
    		if(arg.docIteratorHasMatch(r) && arg.docIteratorGetMatch() == docId){
    			double argRankedBooleanScore = ((QrySop) arg).getScore(r);
    			if(argRankedBooleanScore > score)	score = argRankedBooleanScore;
    		}
		}
    }
    return score;
  }

  /**
   *  Support for Indri best match model. Call this method when the document 
   *  does not have a Indri OR match(not any query term appears in the document).
   *  Must pass the docid since it can be fetched from invlist now.
   *  @param r A retrieval model that guides initialization
   *  @param docid The specific doc id associated with the score
   *  @throws IOException Error accessing the Lucene index.
   */
  @Override
  public double getDefaultScore (RetrievalModel r,int docId) throws IOException {
		if (r instanceof RetrievalModelIndri){
			double score = 1.0;
			//Default score assume the document has no any term match for the query
			//Call getDefaultScore method for every arguments.
			for(Qry arg : this.args){
				score *= 1.0 - ((QrySop) arg).getDefaultScore(r, docId);
			}
			return 1.0 - score;
		} else {
			throw new IllegalArgumentException
			("No support for Default Score in " + r.getClass().getName());
		}
  }

  /**
   *  getScore for the Indri retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreIndri (RetrievalModel r) throws IOException {
	    if (this.docIteratorHasMatchCache()) {
			double score = 1.0;
	    	int docId = this.docIteratorGetMatch();
	    	//If the ith query argument contains document d
    		//Then read its score from the ith score list
    		//Else call the ith query argument's getDefaultScore method
	    	for(Qry arg : this.args){
	    		if(arg.docIteratorHasMatch(r) && arg.docIteratorGetMatch() == docId){
	    			score *= 1.0 - ((QrySop) arg).getScore(r);
	    		} else {
	    			score *= 1.0 - ((QrySop) arg).getDefaultScore(r, docId);
	    		}
			}
	    	return 1.0 - score;
	    } else {
			throw new IllegalArgumentException
			("No support for Indri Score in " + r.getClass().getName());
		}
	  
  }
}
