/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The SUM operator for all retrieval models.
 *  SUM operator adds up each term's score for each document
 *	The actual input of #SUM must be a score list (if not, make it one)
 */
public class QrySopSum extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchMin(r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  Same implementation for all retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  @Override
  public double getScore (RetrievalModel r) throws IOException {
	  if (r instanceof RetrievalModelBM25) {
		  return this.getScoreBM25 (r);
	  } 
	  else {
		  throw new IllegalArgumentException
	       (r.getClass().getName() + " doesn't support the SCORE operator.");
	  }
  }

	/**
	 *  getScore for the BM25 retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreBM25 (RetrievalModel r) throws IOException{
		  double score = 0.0;
		    if (this.docIteratorHasMatchCache()) {
		    	int docId = this.docIteratorGetMatch();
		    	//Sum operator combines score by adding them up
		    	for(Qry arg : this.args){
		    		if(arg.docIteratorHasMatch(r) && arg.docIteratorGetMatch() == docId){
		    			double argScore = ((QrySop) arg).getScore(r);
		    			score += argScore;	    		
		    		}
				}
		    }
		    return score;
	}

	/**
	 *  Support for Indri best match model. Combine scores for n items.
	 *  @param r A retrieval model that guides initialization
	 *  @param docid The specific doc id associated with the score
	 *  @throws IOException Error accessing the Lucene index.
	 */
	@Override
	public double getDefaultScore (RetrievalModel r,int docId) throws IOException {
			if (r instanceof RetrievalModelIndri){
				double score = 0.0;
				for(Qry arg : this.args){
					score += ((QrySop)arg).getDefaultScore(r, docId);
				}
				return score;
			} else {
				throw new IllegalArgumentException
				("No support for Default Score in " + r.getClass().getName());
			}
	}


}
